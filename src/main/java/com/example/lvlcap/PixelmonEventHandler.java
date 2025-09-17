package com.example.lvlcap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PixelmonEventHandler {
    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.level.isClientSide) {
            return;
        }
        LevelCapManager.syncBadgesFromPixelmon(player);
        LevelCapManager.broadcastLevelCap(player);
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.level.isClientSide) {
            return;
        }
        if (event.getOriginal() instanceof ServerPlayerEntity) {
            LevelCapManager.cloneBadges(player, (ServerPlayerEntity) event.getOriginal());
        }
        LevelCapManager.broadcastLevelCap(player);
    }

    @SubscribeEvent
    public void onPixelmonEvents(Event event) {
        String packageName = event.getClass().getName();
        if (!packageName.startsWith("com.pixelmonmod")) {
            return;
        }
        String simpleName = event.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        if (simpleName.contains("badge")) {
            if (simpleName.contains("receive") || simpleName.contains("earn") || simpleName.contains("gain")) {
                handleBadgeEvent(event, true);
            } else if (simpleName.contains("remove") || simpleName.contains("lost") || simpleName.contains("lose")) {
                handleBadgeEvent(event, false);
            }
        }
        if (simpleName.contains("battle") && (simpleName.contains("start")
                || simpleName.contains("begin") || simpleName.contains("init"))) {
            handleBattleStart(event);
        }
        if ((simpleName.contains("battle") && (simpleName.contains("win") || simpleName.contains("victor")
                || simpleName.contains("end") || simpleName.contains("finish") || simpleName.contains("result")))
                || simpleName.contains("victory") || simpleName.contains("defeat") || simpleName.contains("beat")) {
            handleGymVictory(event);
        }
        if (simpleName.contains("sendout") || simpleName.contains("sentout")) {
            handleSendOut(event);
        }
    }

    private void handleBadgeEvent(Event event, boolean earned) {
        ServerPlayerEntity player = PixelmonHooks.getPlayerFromEvent(event);
        if (player == null || player.level.isClientSide) {
            return;
        }
        String badgeName = PixelmonHooks.extractBadgeName(event);
        if (badgeName == null || badgeName.isEmpty()) {
            LevelCapMod.LOGGER.debug("Pixelmon badge event {} did not contain a badge name", event.getClass().getName());
            return;
        }
        boolean changed;
        if (earned) {
            changed = LevelCapManager.addBadge(player, badgeName, badgeName);
        } else {
            changed = LevelCapManager.removeBadge(player, badgeName);
        }
        if (!changed) {
            return;
        }
        int cap = LevelCapManager.getLevelCap(player);
        if (earned) {
            player.sendMessage(new StringTextComponent("Your badge from " + badgeName + " raised the level cap to " + cap + "."), player.getUUID());
        } else {
            player.sendMessage(new StringTextComponent("Your level cap is now " + cap + " after losing the " + badgeName + " badge."), player.getUUID());
        }
        LevelCapManager.broadcastLevelCap(player);
    }

    private void handleSendOut(Event event) {
        ServerPlayerEntity player = PixelmonHooks.getPlayerFromEvent(event);
        if (player == null || player.level.isClientSide) {
            return;
        }
        if (!PixelmonHooks.isBattleSendOut(event)) {
            return;
        }
        Object pokemon = PixelmonHooks.getPokemonFromEvent(event);
        Integer level = PixelmonHooks.getPokemonLevel(pokemon);
        if (level == null) {
            return;
        }
        int cap = LevelCapManager.getLevelCap(player);
        if (level > cap) {
            PixelmonHooks.faintPokemon(event, pokemon);
            String name = PixelmonHooks.getPokemonDisplayName(pokemon);
            player.sendMessage(new StringTextComponent(name + " fainted for exceeding the level cap of " + cap + "."), player.getUUID());
        }
    }

    private void handleBattleStart(Event event) {
        List<ServerPlayerEntity> players = PixelmonHooks.getPlayersFromEvent(event);
        if (players.isEmpty()) {
            ServerPlayerEntity single = PixelmonHooks.getPlayerFromEvent(event);
            if (single != null) {
                players = java.util.Collections.singletonList(single);
            }
        }
        for (ServerPlayerEntity player : players) {
            if (player == null || player.level.isClientSide) {
                continue;
            }
            List<String> fainted = LevelCapManager.faintOverCapPokemon(player);
            if (fainted.isEmpty()) {
                continue;
            }
            String joined = String.join(", ", fainted);
            player.sendMessage(new StringTextComponent("Fainted " + fainted.size()
                    + " over-cap Pokémon before battle: " + joined + "."), player.getUUID());
        }
    }

    private void handleGymVictory(Event event) {
        List<ServerPlayerEntity> winners = PixelmonHooks.getWinningPlayers(event);
        if (winners.isEmpty()) {
            return;
        }
        List<Entity> trainers = PixelmonHooks.getGymLeadersFromEvent(event);
        String badgeName = PixelmonHooks.extractBadgeName(event);
        for (ServerPlayerEntity winner : winners) {
            if (winner == null || winner.level.isClientSide) {
                continue;
            }
            List<LevelCapManager.GymVictory> victories = new ArrayList<>();
            for (Entity trainer : trainers) {
                LevelCapManager.GymVictory victory = LevelCapManager.applyGymVictory(winner, trainer);
                if (victory != null) {
                    victories.add(victory);
                }
            }
            if (victories.isEmpty() && badgeName != null && !badgeName.isEmpty()) {
                LevelCapManager.GymVictory victory = LevelCapManager.applyGymVictory(winner, badgeName, badgeName);
                if (victory != null) {
                    victories.add(victory);
                }
            }
            if (victories.isEmpty()) {
                continue;
            }
            for (LevelCapManager.GymVictory victory : victories) {
                String label = victory.getDisplayLabel();
                if (label == null || label.isEmpty()) {
                    label = badgeName;
                }
                if (label == null || label.isEmpty()) {
                    label = "the gym";
                }
                int cap = victory.getNewLevelCap();
                if (cap <= 0) {
                    cap = LevelCapManager.getLevelCap(winner);
                }
                winner.sendMessage(new StringTextComponent("Defeating " + label
                        + " raised your level cap to " + cap + "."), winner.getUUID());
                List<String> rewards = LevelCapManager.grantGymRewards(winner, victory.getSummary());
                if (!rewards.isEmpty()) {
                    winner.sendMessage(new StringTextComponent("You received: "
                            + String.join(", ", rewards) + "."), winner.getUUID());
                }
            }
            LevelCapManager.broadcastLevelCap(winner);
        }
    }

    @SubscribeEvent
    public void onNpcInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) {
            return;
        }
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (player.level.isClientSide) {
            return;
        }
        LevelCapManager.PendingGymCreation pending = LevelCapManager.getPendingGymCreation(player);
        if (pending == null) {
            return;
        }
        if (!PixelmonHooks.isPixelmonNpc(event.getTarget())) {
            String pendingName = pending.getGymName();
            if (pendingName == null || pendingName.isEmpty()) {
                pendingName = "gym";
            }
            player.sendMessage(new StringTextComponent("That isn't a Pixelmon NPC. Right-click the gym leader to finish creating \""
                    + pendingName + "\"."), player.getUUID());
            return;
        }
        GymLevelCapConfig.GymSummary summary = LevelCapManager.completeGymCreation(player, event.getTarget());
        if (summary == null) {
            player.sendMessage(new StringTextComponent("Failed to create the gym. Please try again."), player.getUUID());
            return;
        }
        String gymLabel = summary.getGymName();
        if (gymLabel == null || gymLabel.isEmpty()) {
            gymLabel = summary.getLeaderName();
        }
        if (gymLabel == null || gymLabel.isEmpty()) {
            gymLabel = "Gym";
        }
        String leaderLabel = summary.getLeaderName();
        if (leaderLabel == null || leaderLabel.isEmpty()) {
            leaderLabel = PixelmonHooks.getNpcDisplayName(event.getTarget());
        }
        player.sendMessage(new StringTextComponent("Created gym \"" + gymLabel + "\" led by \"" + leaderLabel
                + "\" with a level cap of " + summary.getLevelCap() + "."), player.getUUID());
        LevelCapManager.handleAllOnlinePlayers(LevelCapManager::broadcastLevelCap);
    }
}
