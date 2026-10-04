package com.arno.robotica.codex;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /robotica codex                      give yourself a Codex (anyone)
 * /robotica kit <age>                  starter kit for an age, energy items charged (OP)
 * /robotica charge                     fill every energy item you carry (OP)
 * /robotica charge_target              fill the energy block you look at (OP)
 * /robotica give <item> [count]        give a Robotica item by id (OP)
 * /robotica spawn <entity>             spawn a mob 3 blocks ahead (OP)
 */
public final class RoboticaCommands {
    private RoboticaCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("robotica")
                .then(Commands.literal("codex").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ItemStack book = new ItemStack(CodexModule.CODEX.get());
                    if (!p.getInventory().add(book)) p.drop(book, false);
                    return 1;
                }))
                .then(Commands.literal("kit").requires(RoboticaCommands::lab)
                        .then(Commands.argument("age", IntegerArgumentType.integer(0, 4)).executes(ctx -> {
                            LabActions.giveKit(ctx.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(ctx, "age"));
                            return 1;
                        })))
                .then(Commands.literal("charge").requires(RoboticaCommands::lab).executes(ctx -> {
                    int n = LabActions.chargeInventory(ctx.getSource().getPlayerOrException());
                    ctx.getSource().sendSuccess(() -> Component.literal("Charged " + n + " energy items."), false);
                    return n;
                }))
                .then(Commands.literal("charge_target").requires(RoboticaCommands::lab).executes(ctx -> {
                    LabActions.run(ctx.getSource().getPlayerOrException(), "charge_target", "", 0);
                    return 1;
                }))
                .then(Commands.literal("give").requires(RoboticaCommands::lab)
                        .then(Commands.argument("item", ResourceLocationArgument.id())
                                .executes(ctx -> {
                                    LabActions.give(ctx.getSource().getPlayerOrException(), ResourceLocationArgument.getId(ctx, "item").toString(), 1);
                                    return 1;
                                })
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 2304)).executes(ctx -> {
                                    LabActions.give(ctx.getSource().getPlayerOrException(), ResourceLocationArgument.getId(ctx, "item").toString(),
                                            IntegerArgumentType.getInteger(ctx, "count"));
                                    return 1;
                                }))))
                .then(Commands.literal("spawn").requires(RoboticaCommands::lab)
                        .then(Commands.argument("entity", ResourceLocationArgument.id()).executes(ctx -> {
                            LabActions.run(ctx.getSource().getPlayerOrException(), "spawn", ResourceLocationArgument.getId(ctx, "entity").toString(), 0);
                            return 1;
                        }))));
    }

    private static boolean lab(CommandSourceStack source) {
        return CodexModule.labEnabled() && source.hasPermission(2);
    }
}
