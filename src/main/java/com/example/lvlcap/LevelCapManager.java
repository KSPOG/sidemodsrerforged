package com.example.lvlcap;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;

public final class LevelCapManager {
    private static final String PLAYER_DATA_TAG = LevelCapMod.MOD_ID;
    private static final String PLAYER_BADGES_TAG = "badges";

    private static GymLevelCapConfig config;

    private LevelCapManager() {
    }

    public static void initialize(Path configDir) {
        if (config == null) {
            config = new GymLevelCapConfig(configDir, ModConfigHolder.getBaseLevelCap());
        } else {
            config.setBaseLevelCap(ModConfigHolder.getBaseLevelCap());
            config.reload();
        }
    }

    public static void reload() {
        if (config != null) {
            config.setBaseLevelCap(ModConfigHolder.getBaseLevelCap());
            config.reload();
        }
    }

    public static void saveConfig() {
        if (config != null) {
            config.save();
        }
    }

    public static GymLevelCapConfig getConfig() {
        return config;
    }

    public static int getLevelCap(ServerPlayerEntity player) {
        Set<String> badges = getStoredBadges(player);
        return computeLevelCap(badges);
    }

    public static int computeLevelCap(Collection<String> normalizedBadges) {
        int cap = config.getDefaultLevelCap();
        for (String badge : normalizedBadges) {
            OptionalInt value = config.getGymLevelCapByNormalized(badge);
            if (value.isPresent()) {
                cap = Math.max(cap, value.getAsInt());
            }
        }
        return cap;
    }

    public static OptionalInt getNextCap(ServerPlayerEntity player) {
        Set<String> badges = getStoredBadges(player);
        int current = computeLevelCap(badges);
        return config.findNextCap(current, badges);
    }

    public static boolean addBadge(ServerPlayerEntity player, String badgeId, String displayName) {
        String normalized = config.normalizeKey(badgeId);
        Set<String> badges = getStoredBadges(player);
        if (badges.add(normalized)) {
            saveBadges(player, badges);
            config.recordDisplayName(normalized, displayName);
            return true;
        }
        return false;
    }

    public static boolean removeBadge(ServerPlayerEntity player, String badgeId) {
        String normalized = config.normalizeKey(badgeId);
        Set<String> badges = getStoredBadges(player);
        if (badges.remove(normalized)) {
            saveBadges(player, badges);
            return true;
        }
        return false;
    }

    public static Set<String> getStoredBadges(ServerPlayerEntity player) {
        CompoundNBT modData = getOrCreateModData(player);
        ListNBT list = modData.getList(PLAYER_BADGES_TAG, 8);
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) {
            result.add(list.getString(i));
        }
        return result;
    }

    public static void saveBadges(ServerPlayerEntity player, Collection<String> badges) {
        CompoundNBT modData = getOrCreateModData(player);
        ListNBT list = new ListNBT();
        for (String badge : badges) {
            list.add(StringNBT.valueOf(badge));
        }
        modData.put(PLAYER_BADGES_TAG, list);
        CompoundNBT persistent = player.getPersistentData();
        CompoundNBT persisted = getOrCreatePersistedTag(persistent);
        persisted.put(PLAYER_DATA_TAG, modData);
        persistent.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
    }

    public static void cloneBadges(ServerPlayerEntity target, ServerPlayerEntity source) {
        saveBadges(target, getStoredBadges(source));
    }

    public static void syncBadgesFromPixelmon(ServerPlayerEntity player) {
        Set<String> normalizedBadges = PixelmonHooks.getBadges(player, badgeName -> {
            String normalized = config.normalizeKey(badgeName);
            config.recordDisplayName(normalized, badgeName);
            return normalized;
        });
        saveBadges(player, normalizedBadges);
    }

    public static void broadcastLevelCap(ServerPlayerEntity player) {
        int cap = getLevelCap(player);
        OptionalInt nextCap = getNextCap(player);
        StringTextComponent message = new StringTextComponent("Your current Pixelmon level cap is " + cap + ".");
        player.sendMessage(message, player.getUUID());
        nextCap.ifPresent(value -> player.sendMessage(new StringTextComponent("Your next cap will be " + value + " after earning another configured badge."), player.getUUID()));
    }

    public static void handleAllOnlinePlayers(java.util.function.Consumer<ServerPlayerEntity> consumer) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayerEntity player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                consumer.accept(player);
            }
        }
    }

    private static CompoundNBT getOrCreateModData(ServerPlayerEntity player) {
        CompoundNBT persistent = player.getPersistentData();
        CompoundNBT persisted = getOrCreatePersistedTag(persistent);
        if (persisted.contains(PLAYER_DATA_TAG, 10)) {
            return persisted.getCompound(PLAYER_DATA_TAG);
        }
        CompoundNBT modData = new CompoundNBT();
        persisted.put(PLAYER_DATA_TAG, modData);
        persistent.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        return modData;
    }

    private static CompoundNBT getOrCreatePersistedTag(CompoundNBT persistent) {
        if (persistent.contains(PlayerEntity.PERSISTED_NBT_TAG, 10)) {
            return persistent.getCompound(PlayerEntity.PERSISTED_NBT_TAG);
        }
        CompoundNBT persisted = new CompoundNBT();
        persistent.put(PlayerEntity.PERSISTED_NBT_TAG, persisted);
        return persisted;
    }
}
