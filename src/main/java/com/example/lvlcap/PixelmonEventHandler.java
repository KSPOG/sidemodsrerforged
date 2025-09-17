package com.example.lvlcap;

import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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
}
