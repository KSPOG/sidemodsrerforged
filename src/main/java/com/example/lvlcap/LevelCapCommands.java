package com.example.lvlcap;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;

public final class LevelCapCommands {
    private LevelCapCommands() {
    }

    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> root = Commands.literal("lvlcap")
                .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_VIEW))
                .executes(ctx -> showCap(ctx.getSource()))
                .then(Commands.literal("faint")
                        .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_FAINT_SELF))
                        .executes(ctx -> faintOverCap(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_FAINT_OTHERS, 2))
                                .executes(ctx -> faintOverCap(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set")
                        .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_SET, 2))
                        .then(Commands.argument("gym", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> setLevelCap(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "gym"),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                .then(Commands.literal("remove")
                        .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_REMOVE, 2))
                        .then(Commands.argument("gym", StringArgumentType.string())
                                .executes(ctx -> removeLevelCap(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "gym")))))
                .then(Commands.literal("spawn")
                        .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_SPAWN, 2))
                        .then(Commands.argument("gym", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> spawnGym(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "gym"),
                                                IntegerArgumentType.getInteger(ctx, "level")))
                                        .then(Commands.argument("rewards", StringArgumentType.greedyString())
                                                .executes(ctx -> spawnGym(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "gym"),
                                                        IntegerArgumentType.getInteger(ctx, "level"),
                                                        StringArgumentType.getString(ctx, "rewards")))))))
                .then(Commands.literal("list")
                        .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_LIST, 2))
                        .executes(ctx -> listCaps(ctx.getSource())));
        dispatcher.register(root);
        dispatcher.register(Commands.literal("create")
                .requires(source -> PermissionNodes.canUse(source, PermissionNodes.COMMAND_CREATE, 2))
                .then(Commands.literal("gym")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("level", IntegerArgumentType.integer(1))
                                        .executes(ctx -> createGym(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                IntegerArgumentType.getInteger(ctx, "level")))))));
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

    private static int spawnGym(CommandSource source, String gymName, int levelCap) throws CommandSyntaxException {
        return spawnGym(source, gymName, levelCap, null);
    }

    private static int spawnGym(CommandSource source, String gymName, int levelCap, String rewardSpec) throws CommandSyntaxException {
        GymLevelCapConfig config = LevelCapManager.getConfig();
        if (config == null) {
            source.sendFailure(new StringTextComponent("The level cap configuration is not loaded yet."));
            return 0;
        }
        ServerPlayerEntity player = source.getPlayerOrException();
        String trimmed = gymName == null ? "" : gymName.trim();
        String displayGym = trimmed.isEmpty() ? "Gym" : trimmed;
        int storedLevel = Math.max(1, levelCap);
        String leaderName = displayGym.equals("Gym") ? "Gym Leader" : displayGym + " Leader";
        RewardParseResult parseResult = parseRewardSpec(rewardSpec);
        Entity npc = PixelmonHooks.spawnGymNpc(player, displayGym, leaderName, storedLevel);
        if (npc == null) {
            source.sendFailure(new StringTextComponent("Unable to spawn a Pixelmon gym NPC. Ensure Pixelmon is installed."));
            return 0;
        }
        GymLevelCapConfig.GymSummary summary = config.createGym(displayGym, leaderName,
                npc.getUUID().toString(), storedLevel, parseResult.getRewards());
        String gymLabel = summary.getGymName();
        if (gymLabel == null || gymLabel.isEmpty()) {
            gymLabel = displayGym;
        }
        String leaderLabel = summary.getLeaderName();
        if (leaderLabel == null || leaderLabel.isEmpty()) {
            leaderLabel = PixelmonHooks.getNpcLeaderName(npc);
        }
        StringBuilder message = new StringBuilder("Spawned gym \"")
                .append(gymLabel)
                .append("\" led by \"")
                .append(leaderLabel)
                .append("\" with a level cap of ")
                .append(summary.getLevelCap())
                .append('.');
        List<GymLevelCapConfig.ItemReward> savedRewards = summary.getRewards();
        if (!savedRewards.isEmpty()) {
            String rewardsText = savedRewards.stream()
                    .map(LevelCapCommands::describeReward)
                    .collect(Collectors.joining(", "));
            message.append(" Rewards: ").append(rewardsText).append('.');
        }
        source.sendSuccess(new StringTextComponent(message.toString()), true);
        if (!parseResult.getInvalidTokens().isEmpty()) {
            source.sendSuccess(new StringTextComponent("Ignored invalid reward entries: "
                    + String.join(", ", parseResult.getInvalidTokens()) + "."), false);
        }
        LevelCapManager.handleAllOnlinePlayers(LevelCapManager::broadcastLevelCap);
        return 1;
    }

    private static int faintOverCap(CommandSource source) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        return faintOverCap(source, player);
    }

    private static int faintOverCap(CommandSource source, ServerPlayerEntity target) {
        int cap = LevelCapManager.getLevelCap(target);
        java.util.List<String> fainted = LevelCapManager.faintOverCapPokemon(target);
        if (fainted.isEmpty()) {
            if (source.getEntity() == target) {
                source.sendSuccess(new StringTextComponent("All of your Pixelmon are within the level cap of " + cap + "."), false);
            } else {
                source.sendSuccess(new StringTextComponent(target.getName().getString()
                        + " has no Pixelmon above the level cap of " + cap + "."), true);
            }
            return 0;
        }
        String joined = String.join(", ", fainted);
        boolean same = source.getEntity() == target;
        if (same) {
            source.sendSuccess(new StringTextComponent("Fainted " + fainted.size()
                    + " of your Pixelmon above the level cap of " + cap + ": " + joined + "."), false);
        } else {
            source.sendSuccess(new StringTextComponent("Fainted " + fainted.size() + " of "
                    + target.getName().getString() + "'s Pixelmon above the level cap of " + cap + ": " + joined + "."), true);
            target.sendMessage(new StringTextComponent("Your Pixelmon above the level cap of " + cap
                    + " were fainted: " + joined + "."), target.getUUID());
        }
        return fainted.size();
    }

    private static String describeReward(GymLevelCapConfig.ItemReward reward) {
        if (reward == null || reward.getItemId() == null) {
            return "";
        }
        int count = Math.max(1, reward.getCount());
        if (count == 1) {
            return reward.getItemId();
        }
        return count + "x " + reward.getItemId();
    }

    private static int createGym(CommandSource source, String gymName, int levelCap) throws CommandSyntaxException {
        ServerPlayerEntity player = source.getPlayerOrException();
        String trimmedName = gymName.trim();
        String displayName = trimmedName.isEmpty() ? "gym" : trimmedName;
        int storedLevel = Math.max(1, levelCap);
        LevelCapManager.beginGymCreation(player, trimmedName, storedLevel);
        source.sendSuccess(new StringTextComponent("Right-click the gym leader NPC to bind \"" + displayName +
                "\" with a level cap of " + storedLevel + "."), false);
        return 1;
    }

    private static RewardParseResult parseRewardSpec(String rewardSpec) {
        List<GymLevelCapConfig.ItemReward> rewards = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        if (rewardSpec == null) {
            return new RewardParseResult(rewards, invalid);
        }
        String trimmed = rewardSpec.trim();
        if (trimmed.isEmpty()) {
            return new RewardParseResult(rewards, invalid);
        }
        String[] tokens = trimmed.split("\\s+");
        for (String token : tokens) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            int count = 1;
            String itemId = value;
            int starIndex = value.indexOf('*');
            if (starIndex >= 0) {
                itemId = value.substring(0, starIndex).trim();
                String countPart = value.substring(starIndex + 1).trim();
                if (itemId.isEmpty() || countPart.isEmpty()) {
                    invalid.add(value);
                    continue;
                }
                try {
                    count = Integer.parseInt(countPart);
                } catch (NumberFormatException ex) {
                    invalid.add(value);
                    continue;
                }
            }
            if (itemId.isEmpty()) {
                invalid.add(value);
                continue;
            }
            GymLevelCapConfig.ItemReward reward = new GymLevelCapConfig.ItemReward(itemId, count, null);
            if (reward.getItemId() == null) {
                invalid.add(value);
                continue;
            }
            rewards.add(reward);
        }
        return new RewardParseResult(rewards, invalid);
    }

    private static final class RewardParseResult {
        private final List<GymLevelCapConfig.ItemReward> rewards;
        private final List<String> invalidTokens;

        private RewardParseResult(List<GymLevelCapConfig.ItemReward> rewards, List<String> invalidTokens) {
            this.rewards = rewards;
            this.invalidTokens = invalidTokens;
        }

        public List<GymLevelCapConfig.ItemReward> getRewards() {
            return rewards;
        }

        public List<String> getInvalidTokens() {
            return invalidTokens;
        }
    }
}
