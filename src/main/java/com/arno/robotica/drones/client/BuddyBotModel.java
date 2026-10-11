package com.arno.robotica.drones.client;

import com.arno.robotica.Robotica;
import com.arno.robotica.drones.entity.BuddyBot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Buddy Bot: a flying chest with one big eye on the front (-Z), a lid that snaps open when it grabs loot (teeth inside),
 * two brows on the lid, a propeller on top, a drill arm on +X and a stubby blaster on -X. Texture 64x64, box layout in
 * scripts/textures/drones.py. Everything is posed from scratch every frame (the model is shared by all bots), emotes
 * are added on top of the idle pose and faded in and out.
 */
public class BuddyBotModel extends EntityModel<BuddyBot> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Robotica.id("buddy_bot"), "main");

    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = PI * 2.0F;
    private static final float DEG = PI / 180.0F;
    /** Body center height (24 is the ground). */
    private static final float ROOT_Y = 19.0F;
    private static final float BROW_Y = -0.5F;
    private static final float BLASTER_Z = -1.5F;

    private final ModelPart root;
    private final ModelPart lid;
    private final ModelPart rotor;
    private final ModelPart browLeft;
    private final ModelPart browRight;
    private final ModelPart pupil;
    private final ModelPart eyelid;
    private final ModelPart armDrill;
    private final ModelPart drill;
    private final ModelPart armBlaster;
    private final ModelPart blaster;
    private float partial;

    public BuddyBotModel(ModelPart part) {
        this.root = part.getChild("root");
        this.lid = root.getChild("lid");
        this.rotor = lid.getChild("rotor");
        this.browLeft = lid.getChild("brow_left");
        this.browRight = lid.getChild("brow_right");
        ModelPart eye = root.getChild("eye");
        this.pupil = eye.getChild("pupil");
        this.eyelid = eye.getChild("eyelid");
        this.armDrill = root.getChild("arm_drill");
        this.drill = armDrill.getChild("drill");
        this.armBlaster = root.getChild("arm_blaster");
        this.blaster = armBlaster.getChild("blaster");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0.0F, ROOT_Y, 0.0F));
        // chest body and the thruster nozzle underneath
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 6.0F, 8.0F)
                .texOffs(36, 14).addBox(-2.0F, 3.0F, -2.0F, 4.0F, 1.0F, 4.0F),
                PartPose.ZERO);
        // lid hinged on the back top edge, overhanging the body by half a pixel
        PartDefinition lid = root.addOrReplaceChild("lid", CubeListBuilder.create()
                .texOffs(0, 14).addBox(-4.5F, -3.0F, -8.5F, 9.0F, 3.0F, 9.0F)
                .texOffs(56, 3).addBox(-0.5F, -5.0F, -4.5F, 1.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, -3.0F, 4.0F));
        lid.addOrReplaceChild("rotor", CubeListBuilder.create()
                .texOffs(56, 6).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 1.0F, 2.0F)
                .texOffs(36, 20).addBox(-5.0F, -0.5F, -1.0F, 10.0F, 1.0F, 2.0F),
                PartPose.offset(0.0F, -5.5F, -4.0F));
        CubeListBuilder brow = CubeListBuilder.create().texOffs(52, 0).addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F);
        lid.addOrReplaceChild("brow_left", brow, PartPose.offset(-1.5F, BROW_Y, -9.0F));
        lid.addOrReplaceChild("brow_right", brow, PartPose.offset(1.5F, BROW_Y, -9.0F));
        // the eye: bezel with a dark lens, a glowing pupil that drifts in front of it and a shutter lid that blinks
        PartDefinition eye = root.addOrReplaceChild("eye", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-3.0F, -3.0F, -1.0F, 6.0F, 6.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, -4.0F));
        eye.addOrReplaceChild("pupil", CubeListBuilder.create()
                .texOffs(46, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F),
                PartPose.offset(0.0F, 0.0F, -1.0F));
        eye.addOrReplaceChild("eyelid", CubeListBuilder.create()
                .texOffs(46, 3).addBox(-2.0F, 0.0F, -0.5F, 4.0F, 4.0F, 1.0F),
                PartPose.offset(0.0F, -2.0F, -1.5F));
        // drill arm (+X) and blaster arm (-X): a round shoulder pod with the tool pointing forward
        PartDefinition armDrill = root.addOrReplaceChild("arm_drill", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-0.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(4.0F, 0.5F, 0.0F));
        armDrill.addOrReplaceChild("drill", CubeListBuilder.create()
                .texOffs(12, 26).addBox(-1.5F, -1.5F, -2.0F, 3.0F, 3.0F, 2.0F)
                .texOffs(22, 26).addBox(-1.0F, -1.0F, -4.0F, 2.0F, 2.0F, 2.0F)
                .texOffs(30, 26).addBox(-0.5F, -0.5F, -6.0F, 1.0F, 1.0F, 2.0F),
                PartPose.offset(1.0F, 0.0F, -1.5F));
        PartDefinition armBlaster = root.addOrReplaceChild("arm_blaster", CubeListBuilder.create()
                .texOffs(0, 26).addBox(-2.5F, -1.5F, -1.5F, 3.0F, 3.0F, 3.0F),
                PartPose.offset(-4.0F, 0.5F, 0.0F));
        armBlaster.addOrReplaceChild("blaster", CubeListBuilder.create()
                .texOffs(36, 26).addBox(-1.0F, -1.0F, -3.0F, 2.0F, 2.0F, 3.0F)
                .texOffs(46, 26).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 1.0F),
                PartPose.offset(-1.0F, 0.0F, BLASTER_Z));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void prepareMobModel(BuddyBot bot, float limbSwing, float limbSwingAmount, float partialTick) {
        partial = partialTick;
    }

    @Override
    public void setupAnim(BuddyBot bot, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float p = partial;
        int seed = Math.floorMod(bot.getId() * 7919, 997);
        float a = ageInTicks + seed;                     // per bot phase, so two bots never move in sync
        boolean low = bot.isLowEnergy();
        boolean digging = bot.isDigging();
        boolean active = bot.isActive();
        BuddyBot.Stance stance = bot.stance();

        // ---------------------------------------------------------------- idle pose
        float rootX = 0.0F;
        float rootY = ROOT_Y + Mth.sin(a * (low ? 0.06F : 0.12F)) * (low ? 0.4F : 0.8F) + (low ? 0.8F : 0.0F);
        float rootZ = 0.0F;
        float pitch = limbSwingAmount * 0.3F + Mth.sin(a * 0.05F) * 0.03F + (low ? 0.18F : 0.0F);
        float yaw = 0.0F;
        float roll = Mth.sin(a * 0.07F) * 0.06F;
        float squashX = 1.0F, squashY = 1.0F;
        // lid: now and then it peeks open a crack
        float lidOpen = Math.max(0.0F, Mth.sin(a * 0.045F) - 0.92F) * 2.0F;
        float rotorSpin = a * (low ? 0.6F : active ? 2.4F : 1.6F);
        float drillSpin = a * (digging ? 1.6F : 0.06F);
        float hang = low ? 0.35F : 0.1F;
        float drillArmX = 0.0F, drillArmZ = hang + Mth.sin(a * 0.1F) * 0.05F;
        float blasterArmX = 0.0F, blasterArmZ = -hang - Mth.sin(a * 0.1F + 1.0F) * 0.05F;
        // the eye wanders, follows the head look and blinks every few seconds (sometimes twice)
        float look = Mth.wrapDegrees(netHeadYaw);
        float pupilX = Mth.clamp(-look / 35.0F, -1.0F, 1.0F) + Mth.sin(a * 0.023F) * 0.5F + Mth.sin(a * 0.061F + 1.3F) * 0.3F;
        float pupilY = Mth.clamp(headPitch / 35.0F, -1.0F, 1.0F) * 0.8F + Mth.sin(a * 0.031F + 2.1F) * 0.35F;
        float pupilSize = 1.0F;
        float cycle = a % 84.0F;
        float blink = blinkAt(cycle) + (((int) (a / 84.0F)) % 3 == 0 ? blinkAt(cycle - 5.0F) : 0.0F);
        float eyelidDown = blink;
        // brows: +tilt is angry (inner ends down), +y is lower; asym raises the left one and drops the right one
        float browY = Mth.sin(a * 0.05F) * 0.15F;
        float browTilt = stance == BuddyBot.Stance.PASSIVE ? -0.15F : 0.0F;
        float browAsym = 0.0F;

        if (low) {
            // running on fumes: heavy half shut eye with slow blinks, droopy arms and brows
            float slow = a % 60.0F;
            eyelidDown = Math.max(eyelidDown, Math.max(0.5F + Mth.sin(a * 0.05F) * 0.1F, slow < 14.0F ? Mth.sin(slow / 14.0F * PI) : 0.0F));
            browTilt = -0.2F;
            browY += 0.3F;
        }
        if (digging) {
            // tunnel job: rattling body, focused squint, drill arm braced forward
            rootX += Mth.sin(a * 3.3F) * 0.12F;
            drillArmX += Mth.sin(a * 2.1F) * 0.04F;
            drillArmZ = 0.0F;
            eyelidDown = Math.max(eyelidDown, 0.25F);
            browTilt = 0.25F;
            lidOpen = Math.max(lidOpen, (Mth.sin(a * 1.7F) * 0.5F + 0.5F) * 0.08F);
        } else if (active) {
            // shooting: blaster tracks the target pitch, brows frown by stance
            blasterArmX = Mth.clamp(headPitch * DEG, -0.6F, 0.6F);
            blasterArmZ = 0.0F;
            browTilt = stance == BuddyBot.Stance.AGGRESSIVE ? 0.4F : 0.25F;
            browY += 0.3F;
        }

        // ---------------------------------------------------------------- loot chomp and recoil
        float chomp = bot.chomp(p);
        if (chomp > 0.0F) {
            float q = 1.0F - chomp;
            float open = q < 0.3F ? smooth(q / 0.3F) : 1.0F - smooth(Mth.clamp((q - 0.3F) / 0.45F, 0.0F, 1.0F));
            float gulp = Mth.sin(Mth.clamp((q - 0.6F) / 0.4F, 0.0F, 1.0F) * PI);
            lidOpen += open * 0.8F;
            browY -= open * 0.6F;
            squashY -= gulp * 0.08F;
            squashX += gulp * 0.05F;
            eyelidDown = Math.max(eyelidDown, gulp * 0.5F);
            rootY -= open * 0.4F;
        }
        float recoil = bot.recoil(p);
        blasterArmX -= recoil * 0.35F;
        rootZ += recoil * 0.3F;

        // ---------------------------------------------------------------- emotes
        BuddyBot.Emote emote = bot.clientEmote();
        if (emote != BuddyBot.Emote.NONE) {
            float t = bot.emoteTime(p);
            float len = emote.ticks;
            float w = smooth(Mth.clamp(t / 4.0F, 0.0F, 1.0F)) * smooth(Mth.clamp((len - t) / 5.0F, 0.0F, 1.0F));
            switch (emote) {
                case SPIN -> {
                    // one full turn with a hop, arms flung out, dizzy eye at the end
                    float s = Mth.clamp(t / len, 0.0F, 1.0F);
                    yaw += smooth(smooth(s)) * TWO_PI;
                    rootY -= Mth.sin(s * PI) * 1.5F;
                    drillArmZ -= 0.7F * w;
                    blasterArmZ += 0.7F * w;
                    pupilX += Mth.sin(t * 0.8F) * 0.7F * w;
                    pupilY += Mth.cos(t * 0.8F) * 0.5F * w;
                    lidOpen += (Mth.sin(t * 1.3F) * 0.5F + 0.5F) * 0.15F * w;
                    browY -= 0.4F * w;
                }
                case WAVE -> {
                    // drill arm up, waving side to side, happy squint
                    float swing = Mth.sin(t * 0.55F);
                    drillArmX -= 1.5F * w;
                    drillArmZ -= (0.45F + 0.3F * swing) * w;
                    drillSpin += t * 0.4F * w;
                    roll -= 0.12F * w;
                    rootY -= Math.abs(swing) * 0.4F * w;
                    pupilX *= 1.0F - w;
                    eyelidDown = Math.max(eyelidDown, 0.3F * w);
                    browY -= 0.6F * w;
                    browTilt -= 0.15F * w;
                }
                case HICCUP -> {
                    // sudden hop, the lid pops, eye goes wide
                    float u = t - 2.0F;
                    float hop = u < 0.0F ? 0.0F : u < 1.5F ? u / 1.5F : Math.max(0.0F, 1.0F - (u - 1.5F) / 8.0F);
                    float pop = u < 0.0F ? 0.0F : u < 1.0F ? u : Math.max(0.0F, 1.0F - (u - 1.0F) / 3.0F);
                    rootY -= 2.2F * hop * w;
                    lidOpen += 0.55F * pop * w;
                    browY -= 0.8F * hop * w;
                    pupilSize -= 0.4F * hop * w;
                    eyelidDown *= 1.0F - hop;
                    drillArmZ -= 0.5F * hop * w;
                    blasterArmZ += 0.5F * hop * w;
                }
                case SNEEZE -> {
                    // ah... ah... (leans back, squints, lid twitches) CHOO (jolts forward, eye shut, lid blows open)
                    float build = smooth(Mth.clamp((t - 2.0F) / 18.0F, 0.0F, 1.0F)) * (0.8F + 0.2F * Mth.sin(t * 0.9F));
                    float pre = t < 21.0F ? build : Math.max(0.0F, 1.0F - (t - 21.0F) / 1.5F) * build;
                    float choo = t < 20.0F ? 0.0F : t < 22.0F ? (t - 20.0F) / 2.0F : Math.max(0.0F, 1.0F - (t - 22.0F) / 9.0F);
                    pitch += (-0.35F * pre + 0.55F * choo) * w;
                    rootZ += (0.8F * pre - 2.0F * choo) * w;
                    rootY -= 0.6F * pre * w;
                    eyelidDown = Math.max(eyelidDown, Math.max(0.7F * pre, choo) * w);
                    lidOpen += (0.15F * pre * (Mth.sin(t * 1.9F) * 0.5F + 0.5F) + 0.7F * choo) * w;
                    browY -= 0.5F * pre * w;
                    browTilt -= 0.3F * pre * w;
                    drillArmX += 0.3F * pre * w;
                    blasterArmX += 0.3F * pre * w;
                    drillArmZ -= 0.4F * choo * w;
                    blasterArmZ += 0.4F * choo * w;
                }
                case NOD -> {
                    // two nods
                    float n = Math.max(0.0F, Mth.sin(t / len * 2.0F * TWO_PI));
                    pitch += 0.35F * n * w;
                    rootY += 0.4F * n * w;
                    eyelidDown = Math.max(eyelidDown, 0.25F * w);
                    browY -= 0.3F * w;
                }
                case LOOK_AROUND -> {
                    // the eye darts left and right, the body follows a beat later, one brow raised
                    float s = Mth.sin(t * TWO_PI / 40.0F);
                    pupilX = pupilX * (1.0F - w) + Mth.clamp(Mth.sin((t + 4.0F) * TWO_PI / 40.0F) * 1.6F, -1.0F, 1.0F) * w;
                    yaw += 0.5F * s * w;
                    roll += 0.12F * w;
                    browAsym += 0.6F * w;
                }
                case BOOP -> {
                    // leans in, bonk (squash, eye shut), then a happy wiggle
                    float lean = smooth(Mth.clamp(t / 14.0F, 0.0F, 1.0F)) * (1.0F - smooth(Mth.clamp((t - 20.0F) / 6.0F, 0.0F, 1.0F)));
                    float bonk = t < 14.0F ? 0.0F : t < 16.0F ? (t - 14.0F) / 2.0F : Math.max(0.0F, 1.0F - (t - 16.0F) / 4.0F);
                    float wiggle = smooth(Mth.clamp((t - 18.0F) / 4.0F, 0.0F, 1.0F));
                    pitch += (0.3F * lean + 0.25F * bonk) * w;
                    rootZ -= (1.2F * lean + 1.8F * bonk) * w;
                    squashX += 0.12F * bonk * w;
                    squashY -= 0.12F * bonk * w;
                    eyelidDown = Math.max(eyelidDown, bonk * w);
                    lidOpen += 0.3F * bonk * w;
                    roll += Mth.sin((t - 18.0F) * 1.1F) * 0.18F * wiggle * w;
                    browY -= 0.5F * wiggle * w;
                }
                case CHEER -> {
                    // both arms up pumping, bouncing, wiggling, lid flapping
                    drillArmX -= 1.9F * w;
                    blasterArmX -= 1.9F * w;
                    drillArmZ -= (0.3F + 0.25F * Mth.sin(t * 0.9F)) * w;
                    blasterArmZ += (0.3F - 0.25F * Mth.sin(t * 0.9F)) * w;
                    rootY -= 1.3F * Math.abs(Mth.sin(t * 0.42F)) * w;
                    roll += Mth.sin(t * 0.84F) * 0.15F * w;
                    lidOpen += (0.5F + 0.5F * Mth.sin(t * 1.25F)) * 0.45F * w;
                    rotorSpin += t * 1.2F * w;
                    eyelidDown = Math.max(eyelidDown, 0.35F * w);
                    browY -= 0.7F * w;
                    browTilt -= 0.2F * w;
                }
                case SHAKE -> {
                    // worried: rapid side shake, darting tiny pupil, arms up to its face, lid clamped shut
                    float s = Mth.sin(t * 2.4F);
                    rootX += s * 0.6F * w;
                    roll += s * 0.1F * w;
                    yaw += Mth.sin(t * 2.4F + 0.5F) * 0.12F * w;
                    pupilX += Mth.sin(t * 1.7F) * 0.8F * w;
                    pupilSize -= 0.3F * w;
                    browTilt -= 0.45F * w;
                    browY -= 0.3F * w;
                    drillArmX -= 0.7F * w;
                    blasterArmX -= 0.7F * w;
                    lidOpen *= 1.0F - w;
                }
                case SLEEPY -> {
                    // droops, heavy slow blinks, nods off and jerks awake
                    float doze = smooth(Mth.clamp((t - 24.0F) / 14.0F, 0.0F, 1.0F)) * (1.0F - smooth(Mth.clamp((t - 38.0F) / 2.0F, 0.0F, 1.0F)));
                    float startle = t < 38.0F ? 0.0F : t < 39.0F ? t - 38.0F : Math.max(0.0F, 1.0F - (t - 39.0F) / 8.0F);
                    rootY += (0.6F + 0.6F * doze - 0.8F * startle) * w;
                    pitch += (0.25F + 0.25F * doze) * w;
                    drillArmZ += 0.35F * w;
                    blasterArmZ -= 0.35F * w;
                    eyelidDown = Math.max(eyelidDown, Math.max(0.65F + 0.35F * Math.max(0.0F, Mth.sin(t * 0.16F)), doze) * w * (1.0F - startle));
                    browY += (0.4F - 1.2F * startle) * w;
                    browTilt -= 0.2F * w;
                    rotorSpin -= t * 1.0F * w;
                }
                default -> {
                }
            }
        }

        // ---------------------------------------------------------------- apply
        root.x = rootX;
        root.y = rootY;
        root.z = rootZ;
        root.xRot = pitch;
        root.yRot = yaw;
        root.zRot = roll;
        root.xScale = squashX;
        root.yScale = squashY;
        root.zScale = squashX;
        lid.xRot = -Mth.clamp(lidOpen, 0.0F, 1.1F);
        rotor.yRot = rotorSpin;
        browLeft.y = BROW_Y + browY - browAsym;
        browRight.y = BROW_Y + browY + browAsym * 0.3F;
        browLeft.zRot = browTilt;
        browRight.zRot = -browTilt;
        pupil.x = Mth.clamp(pupilX, -1.0F, 1.0F);
        pupil.y = Mth.clamp(pupilY, -1.0F, 1.0F);
        pupil.xScale = pupilSize;
        pupil.yScale = pupilSize;
        float shut = Mth.clamp(eyelidDown, 0.0F, 1.0F);
        eyelid.visible = shut > 0.03F;
        eyelid.yScale = Math.max(0.03F, shut);
        armDrill.xRot = drillArmX;
        armDrill.zRot = drillArmZ;
        drill.zRot = drillSpin;
        armBlaster.xRot = blasterArmX;
        armBlaster.zRot = blasterArmZ;
        blaster.z = BLASTER_Z + recoil * 1.2F;
    }

    /** Eyelid of one blink {@code c} ticks after it starts: shut in 1.5 ticks, open again in 2. */
    private static float blinkAt(float c) {
        if (c < 0.0F || c >= 3.5F) return 0.0F;
        return c < 1.5F ? c / 1.5F : 1.0F - (c - 1.5F) / 2.0F;
    }

    private static float smooth(float x) {
        return x * x * (3.0F - 2.0F * x);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        root.render(pose, buffer, packedLight, packedOverlay, color);
    }
}
