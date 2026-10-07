package com.arno.robotica.boss.block;

import com.arno.robotica.boss.BossConfig;
import com.arno.robotica.boss.BossRegistry;
import com.arno.robotica.boss.entity.RoboticaBoss;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Boss altar: right-click it with its summon item to wake its boss on top. One boss at a time per altar, then a cooldown
 * (config, 5 minutes). The Colossus Altar (Signal Flare, Scrap Colossus) stands in every Rusted Foundry, the Forge Altar
 * (Ignition Charge, Forge Tyrant) in every Cinder Forge ({@link #NATURAL}: drops building blocks instead of itself, so the
 * ruin's altar stays where it is). Both are craftable to build your own arena.
 */
public class BossAltarBlock extends Block implements EntityBlock {
    /**
     * What one altar summons. {@code key} names its messages: {@code message.robotica.boss.<key>.awakened / boss_alive /
     * peaceful / ready}. {@code colors} are the firework colors of the summon.
     */
    public record Kind(String key, Supplier<? extends EntityType<? extends Mob>> boss, Supplier<? extends net.minecraft.world.item.Item> summon,
                       int[] colors, int fade) {
        MutableComponent message(String what) {
            return Component.translatable("message.robotica.boss." + key + "." + what);
        }
    }

    /** Lit when it can be awakened again (cosmetic; the block entity holds the real cooldown). */
    public static final BooleanProperty READY = BooleanProperty.create("ready");
    /** Generated in a Rusted Foundry. */
    public static final BooleanProperty NATURAL = BooleanProperty.create("natural");

    public enum Result {
        SPAWNED, COOLDOWN, BOSS_ALIVE, PEACEFUL, BLOCKED
    }

    private final Kind kind;

    public BossAltarBlock(Kind kind, Properties props) {
        super(props);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(READY, true).setValue(NATURAL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(READY, NATURAL);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return BossRegistry.BOSS_ALTAR_BE.get().create(pos, state);
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(kind.summon().get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!(level instanceof ServerLevel server)) return ItemInteractionResult.SUCCESS;
        Result result = awaken(server, pos, player);
        if (result == Result.SPAWNED && !player.getAbilities().instabuild) stack.shrink(1);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof BossAltarBlockEntity altar)) return InteractionResult.PASS;
        long left = altar.cooldownLeft(level.getGameTime());
        if (altar.boss(server) != null) {
            tell(player, kind.message("boss_alive"));
        } else if (left > 0) {
            tell(player, Component.translatable("message.robotica.boss.cooldown", time(left)));
        } else {
            tell(player, kind.message("ready"));
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Wakes the altar's boss on top of it if it is ready. Creative players skip the cooldown (never the one-boss
     * rule). Tells the player why when it does not. The caller consumes the flare on {@link Result#SPAWNED}.
     */
    public static Result awaken(ServerLevel level, BlockPos pos, @Nullable Player player) {
        if (!(level.getBlockEntity(pos) instanceof BossAltarBlockEntity altar)
                || !(level.getBlockState(pos).getBlock() instanceof BossAltarBlock block)) return Result.BLOCKED;
        Kind kind = block.kind;
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            tell(player, kind.message("peaceful"));
            return Result.PEACEFUL;
        }
        if (altar.boss(level) != null) {
            tell(player, kind.message("boss_alive"));
            return Result.BOSS_ALIVE;
        }
        long left = altar.cooldownLeft(level.getGameTime());
        if (left > 0 && (player == null || !player.getAbilities().instabuild)) {
            tell(player, Component.translatable("message.robotica.boss.cooldown", time(left)));
            return Result.COOLDOWN;
        }
        EntityType<? extends Mob> type = kind.boss().get();
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        AABB space = type.getSpawnAABB(x, y, z);
        Mob boss = type.create(level);
        if (boss == null || !level.noCollision(space)) {
            tell(player, Component.translatable("message.robotica.boss.blocked"));
            return Result.BLOCKED;
        }
        float yaw = player != null ? player.getYRot() + 180.0F : level.random.nextFloat() * 360.0F;
        boss.moveTo(x, y, z, yaw, 0.0F);
        boss.setYHeadRot(yaw);
        boss.yBodyRot = yaw;
        boss.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.TRIGGERED, null);
        if (boss instanceof RoboticaBoss altarBoss) altarBoss.setAltarPos(pos);
        if (player != null && !player.getAbilities().instabuild) boss.setTarget(player);
        level.addFreshEntity(boss);

        int cooldown = BossConfig.altarCooldownTicks();
        altar.awakened(boss, level.getGameTime(), cooldown);
        if (cooldown > 0) {
            level.setBlock(pos, level.getBlockState(pos).setValue(READY, false), Block.UPDATE_ALL);
            level.scheduleTick(pos, level.getBlockState(pos).getBlock(), cooldown);
        }
        awakenEffects(level, pos, kind);
        Component news = kind.message("awakened").withStyle(ChatFormatting.GOLD);
        for (ServerPlayer near : level.getPlayers(p -> p.distanceToSqr(x, y, z) < 64.0 * 64.0)) {
            near.sendSystemMessage(news);
        }
        return Result.SPAWNED;
    }

    private static void awakenEffects(ServerLevel level, BlockPos pos, Kind kind) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.0;
        double z = pos.getZ() + 0.5;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 1.0, z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.FLAME, x, y + 0.5, z, 60, 0.8, 1.5, 0.8, 0.05);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.5, z, 40, 1.0, 1.5, 1.0, 0.03);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0F, 0.4F);
        level.playSound(null, pos, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.HOSTILE, 2.0F, 0.5F);
        // The flare itself goes up as a firework high above the altar, so everyone around sees the fight start.
        ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
        rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL,
                IntList.of(kind.colors()), IntList.of(kind.fade()), true, false))));
        level.addFreshEntity(new FireworkRocketEntity(level, x, y + 3.5, z, rocket));
    }

    /**
     * A cooling altar cannot be mined: breaking and placing it again would skip the cooldown. Creative players still can.
     */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!state.getValue(READY) && !player.getAbilities().instabuild) return 0.0F;
        return super.getDestroyProgress(state, player, level, pos);
    }

    /** Also covers area tools and other paths that break the block without mining progress. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!state.getValue(READY) && !player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
                tell(player, Component.translatable("message.robotica.boss.cooling_unbreakable"));
            }
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof BossAltarBlockEntity altar)) return;
        long left = altar.cooldownLeft(level.getGameTime());
        if (left > 0) {
            level.scheduleTick(pos, this, (int) Math.min(Integer.MAX_VALUE, left));
        } else if (!state.getValue(READY)) {
            level.setBlock(pos, state.setValue(READY, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.0F, 0.6F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(READY) || random.nextInt(3) != 0) return;
        level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 1.02,
                pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0.0, 0.01, 0.0);
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 0.0, 0.03, 0.0);
        }
    }

    private static Component time(long ticks) {
        long seconds = (ticks + 19) / 20;
        return Component.literal(String.format("%d:%02d", seconds / 60, seconds % 60));
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) player.displayClientMessage(message, true);
    }
}
