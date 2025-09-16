package com.example.lvlcap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.Map;
import java.util.OptionalInt;

public final class LevelCapCommands {
    private LevelCapCommands() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Commands.literal("lvlcap")
                .executes(ctx -> showCap(ctx.getSource()))
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("gym", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setLevelCap(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "gym"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("gym", StringArgumentType.string())
                                .executes(ctx -> removeLevelCap(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "gym")))))
                .then(Commands.literal("list")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> listCaps(ctx.getSource())));
        dispatcher.register(root);
    }

    private static int showCap(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        int cap = LevelCapManager.getLevelCap(player);
        source.sendSuccess(new StringTextComponent("Your Pixelmon level cap is " + cap + "."), false);
        OptionalInt nextCap = LevelCapManager.getNextCap(player);
        nextCap.ifPresent(value -> source.sendSuccess(new StringTextComponent("Next configured cap: " + value + "."), false));
        return cap;
    }

    private static int setLevelCap(CommandSource source, String gymName, int levelCap) {
        int stored = LevelCapManager.getConfig().setGymLevelCap(gymName, levelCap);
        source.sendSuccess(new StringTextComponent("Set level cap for " + gymName + " to " + stored + "."), true);
        LevelCapManager.handleAllOnlinePlayers(LevelCapManager::broadcastLevelCap);
        return stored;
    }

    private static int removeLevelCap(CommandSource source, String gymName) {
        boolean removed = LevelCapManager.getConfig().removeGym(gymName);
        if (removed) {
            source.sendSuccess(new StringTextComponent("Removed level cap for " + gymName + "."), true);
            LevelCapManager.handleAllOnlinePlayers(LevelCapManager::broadcastLevelCap);
            return 1;
        }
        source.sendSuccess(new StringTextComponent("No configured level cap found for " + gymName + "."), false);
        return 0;
    }

    private static int listCaps(CommandSource source) {
        Map<String, Integer> caps = LevelCapManager.getConfig().getAllGymCaps();
        if (caps.isEmpty()) {
            source.sendSuccess(new StringTextComponent("No gym level caps have been configured."), false);
            return 0;
        }
        source.sendSuccess(new StringTextComponent("Configured gym level caps:"), false);
        caps.forEach((name, level) -> source.sendSuccess(new StringTextComponent(" - " + name + ": " + level), false));
        return caps.size();
    }
}
