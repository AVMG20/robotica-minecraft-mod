package com.arno.robotica.replicator.logic;

import com.arno.robotica.Robotica;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Harvest mode: rolls a mob's loot table as if a player had killed it. The entity is created but never added to
 * the world, so nothing spawns, nothing can be seen dying and no mob AI runs.
 */
public final class Harvest {
    private Harvest() {}

    /** Name of the fake player. The UUID is the controller owner's, so claim and logging mods can attribute it. */
    private static final String FAKE_NAME = "[Replicator]";
    /** Used when a controller has no owner (placed by a dispenser or a command). */
    private static final UUID NO_OWNER = UUID.fromString("5b0c3a64-3b1a-4f5e-9c4e-0f6f0d0c7e11");

    public record Result(List<ItemStack> drops, int xp) {}

    /**
     * @param looting Looting level 0-3 (the fortune card of the controller). It goes onto the fake player's held
     *                tool because in 1.21 looting is an enchantment that the loot functions read from the attacker.
     * @return null if the entity type can not be created (disabled feature or a broken modded mob)
     */
    @Nullable
    public static Result roll(ServerLevel level, EntityType<?> type, BlockPos origin, @Nullable UUID owner, int looting) {
        Entity entity = null;
        FakePlayer fake = null;
        try {
            entity = type.create(level);
            if (entity == null) return null;
            Vec3 at = Vec3.atCenterOf(origin);
            entity.setPos(at.x, at.y, at.z);

            fake = FakePlayerFactory.get(level, new GameProfile(owner != null ? owner : NO_OWNER, FAKE_NAME));
            fake.setPos(at.x, at.y, at.z);
            if (looting > 0) {
                ItemStack tool = new ItemStack(Items.IRON_SWORD);
                tool.enchant(level.registryAccess().holderOrThrow(Enchantments.LOOTING), looting);
                fake.setItemInHand(InteractionHand.MAIN_HAND, tool);
            } else {
                fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }

            DamageSource source = level.damageSources().playerAttack(fake);
            LootParams params = new LootParams.Builder(level)
                    .withParameter(LootContextParams.THIS_ENTITY, entity)
                    .withParameter(LootContextParams.ORIGIN, at)
                    .withParameter(LootContextParams.DAMAGE_SOURCE, source)
                    .withParameter(LootContextParams.ATTACKING_ENTITY, fake)
                    .withParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, fake)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, fake)
                    .withLuck(fake.getLuck())
                    .create(LootContextParamSets.ENTITY);

            List<ItemStack> drops = new ArrayList<>();
            if (entity instanceof LivingEntity living) {
                LootTable table = level.getServer().reloadableRegistries().getLootTable(living.getLootTable());
                for (ItemStack stack : table.getRandomItems(params)) {
                    if (!stack.isEmpty() && !Essence.isOutputBlacklisted(stack)) drops.add(stack);
                }
                int xp = Math.max(0, living.getExperienceReward(level, fake));
                return new Result(drops, xp);
            }
            return new Result(drops, 0);
        } catch (RuntimeException e) {
            // A modded mob with a broken constructor or loot condition must not crash the server.
            Robotica.LOGGER.warn("Replicator could not roll the loot of {}", type, e);
            return null;
        } finally {
            if (fake != null) fake.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (entity != null) entity.discard();
        }
    }
}
