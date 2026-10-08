package com.arno.robotica.codex.client.dev.trailer;

import com.arno.robotica.Robotica;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Dev-only: reads the main render target after each frame and pipes it to ffmpeg from a writer thread. Writes
 * {@code <id>_raw.mp4} (every captured frame, nominal 60 fps) and {@code <id>_times.txt} (scene tick of every frame).
 * Frames are never dropped: when the writer is behind, the render thread waits.
 */
public final class FrameRecorder {
    private static final int POOL = 6;

    private final int width, height;
    private final Path timesFile;
    private final Process ffmpeg;
    private final BlockingQueue<ByteBuffer> free = new ArrayBlockingQueue<>(POOL);
    private final BlockingQueue<ByteBuffer> full = new ArrayBlockingQueue<>(POOL);
    private final ByteBuffer END = ByteBuffer.allocate(0);
    private final Thread writer;
    private final List<Double> times = new ArrayList<>();
    private volatile IOException failure;
    private long lastNanos;
    private long lastGc;

    public FrameRecorder(Path outDir, String id, int width, int height) throws IOException {
        this.width = width;
        this.height = height;
        Files.createDirectories(outDir);
        this.timesFile = outDir.resolve(id + "_times.txt");
        Path video = outDir.resolve(id + "_raw.mp4");
        for (int i = 0; i < POOL; i++) free.add(BufferUtils.createByteBuffer(width * height * 4));
        String ffmpegPath = Files.isExecutable(Path.of("/opt/homebrew/bin/ffmpeg")) ? "/opt/homebrew/bin/ffmpeg" : "ffmpeg";
        // GL rows come bottom-up, hence vflip. Alpha is ignored by yuv420p.
        ProcessBuilder pb = new ProcessBuilder(ffmpegPath, "-y", "-loglevel", "error",
                "-f", "rawvideo", "-pix_fmt", "rgba", "-s", width + "x" + height, "-r", "60", "-i", "-",
                "-vf", "vflip,scale=1920:1080:flags=lanczos",
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "16", "-pix_fmt", "yuv420p", video.toString());
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        this.ffmpeg = pb.start();
        this.writer = new Thread(this::writeLoop, "robotica-trailer-writer");
        this.writer.setDaemon(true);
        this.writer.start();
    }

    private void writeLoop() {
        try (OutputStream out = ffmpeg.getOutputStream(); WritableByteChannel channel = Channels.newChannel(out)) {
            while (true) {
                ByteBuffer frame = full.take();
                if (frame == END) return;
                while (frame.hasRemaining()) channel.write(frame);
                free.put(frame);
            }
        } catch (IOException e) {
            failure = e;
            // keep the render thread from blocking forever on a dead pipe
            while (free.remainingCapacity() > 0) free.offer(BufferUtils.createByteBuffer(1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Reads the current frame from {@code target} and queues it, tagged with its scene time in ticks. */
    public void capture(RenderTarget target, double sceneTick) {
        if (failure != null) return;
        long now = System.nanoTime();
        long gc = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(b -> Math.max(0, b.getCollectionTime())).sum();
        if (lastNanos != 0 && now - lastNanos > 120_000_000L) {
            Robotica.LOGGER.info("Trailer: frame stall {} ms before scene tick {} (gc {} ms, queue {})", (now - lastNanos) / 1_000_000,
                    String.format(java.util.Locale.ROOT, "%.2f", sceneTick), gc - lastGc, full.size());
        }
        lastNanos = now;
        lastGc = gc;
        ByteBuffer buf;
        try {
            buf = free.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        if (buf.capacity() < width * height * 4) return;
        buf.clear();
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target.frameBufferId);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        buf.position(0).limit(width * height * 4);
        times.add(sceneTick);
        try {
            full.put(buf);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public int frames() {
        return times.size();
    }

    /** Flushes the queue, waits for ffmpeg and writes the times file. */
    public void close() {
        try {
            full.put(END);
            writer.join();
            int code = ffmpeg.waitFor();
            if (code != 0) Robotica.LOGGER.error("Trailer: ffmpeg exited with {}", code);
            StringBuilder sb = new StringBuilder();
            for (double t : times) sb.append(String.format(java.util.Locale.ROOT, "%.5f%n", t));
            Files.writeString(timesFile, sb.toString());
        } catch (IOException | InterruptedException e) {
            Robotica.LOGGER.error("Trailer: could not finish {}", timesFile, e);
        }
        if (failure != null) Robotica.LOGGER.error("Trailer: ffmpeg pipe failed", failure);
    }
}
