package com.example.lvlcap;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.ResourceLocationException;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.server.ServerLifecycleHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LevelCapManager {
    private static final String PLAYER_DATA_TAG = LevelCapMod.MOD_ID;
    private static final String PLAYER_BADGES_TAG = "badges";

    private static GymLevelCapConfig config;
    private static final Map<UUID, PendingGymCreation> pendingGymCreations = new ConcurrentHashMap<>();

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

    public static List<String> faintOverCapPokemon(ServerPlayerEntity player) {
        List<String> fainted = new ArrayList<>();
        if (player == null) {
            return fainted;
        }
        List<Object> party = PixelmonHooks.getPartyPokemon(player);
        if (party == null || party.isEmpty()) {
            return fainted;
        }
        int cap = getLevelCap(player);
        for (Object pokemon : party) {
            Integer level = PixelmonHooks.getPokemonLevel(pokemon);
            if (level == null || level <= cap) {
                continue;
            }
            PixelmonHooks.faintPokemon(null, pokemon);
            fainted.add(PixelmonHooks.getPokemonDisplayName(pokemon));
        }
        return fainted;
    }

    public static GymVictory applyGymVictory(ServerPlayerEntity player, Entity npcEntity) {
        if (player == null || npcEntity == null || config == null) {
            return null;
        }
        String gymName = trimToNull(PixelmonHooks.getNpcGymName(npcEntity));
        String leaderName = trimToNull(PixelmonHooks.getNpcLeaderName(npcEntity));

        GymVictory victory = applyGymVictory(player, gymName, gymName);
        if (victory != null) {
            if (leaderName != null) {
                config.recordDisplayName(leaderName, leaderName);
            }
            return victory;
        }

        victory = applyGymVictory(player, leaderName, gymName);
        if (victory != null) {
            if (gymName != null) {
                config.recordDisplayName(gymName, gymName);
            }
            return victory;
        }

        String npcId = npcEntity.getUUID().toString();
        victory = applyGymVictory(player, npcId, gymName != null ? gymName : leaderName);
        if (victory != null) {
            if (gymName != null) {
                config.recordDisplayName(gymName, gymName);
            }
            if (leaderName != null) {
                config.recordDisplayName(leaderName, leaderName);
            }
        }
        return victory;
    }

    public static GymVictory applyGymVictory(ServerPlayerEntity player, String alias, String displayHint) {
        if (player == null || config == null) {
            return null;
        }
        String trimmedAlias = trimToNull(alias);
        if (trimmedAlias == null) {
            return null;
        }
        GymLevelCapConfig.GymSummary summary = config.findGymSummary(trimmedAlias);
        if (summary == null) {
            return null;
        }
        String label = summary.getGymName();
        if (label == null || label.isEmpty()) {
            label = summary.getLeaderName();
        }
        if ((label == null || label.isEmpty()) && displayHint != null) {
            String hint = trimToNull(displayHint);
            if (hint != null) {
                label = hint;
            }
        }
        if (label == null || label.isEmpty()) {
            label = trimmedAlias;
        }
        if (label == null || label.isEmpty()) {
            label = summary.getId();
        }
        if (!addBadge(player, summary.getId(), label)) {
            return null;
        }
        config.recordDisplayName(trimmedAlias, label);
        int newCap = getLevelCap(player);
        return new GymVictory(summary, label, newCap);
    }

    public static List<String> grantGymRewards(ServerPlayerEntity player, GymLevelCapConfig.GymSummary summary) {
        List<String> granted = new ArrayList<>();
        if (player == null || summary == null) {
            return granted;
        }
        List<GymLevelCapConfig.ItemReward> rewards = summary.getRewards();
        if (rewards == null || rewards.isEmpty()) {
            return granted;
        }
        for (GymLevelCapConfig.ItemReward reward : rewards) {
            ItemStack stack = createRewardStack(reward, summary.getId());
            if (stack.isEmpty()) {
                continue;
            }
            if (!executeGiveCommand(player, reward, summary.getId())) {
                continue;
            }
            ItemStack displayStack = stack.copy();
            String displayName = displayStack.getHoverName().getString();
            int count = displayStack.getCount();
            if (count <= 1) {
                granted.add(displayName);
            } else {
                granted.add(count + "x " + displayName);
            }
        }
        return granted;
    }

    private static boolean executeGiveCommand(ServerPlayerEntity player, GymLevelCapConfig.ItemReward reward, String gymId) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        Commands commands = server.getCommands();
        if (commands == null) {
            return false;
        }
        CommandSource source = player.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        String command = buildGiveCommand(player, reward);
        try {
            int result = commands.performCommand(source, command);
            if (result > 0) {
                return true;
            }
            LevelCapMod.LOGGER.warn("Failed to execute /give for reward '{}' from gym '{}'", reward.getItemId(), gymId);
        } catch (Exception e) {
            LevelCapMod.LOGGER.warn("Error executing /give for reward '{}' from gym '{}': {}", reward.getItemId(), gymId, e.getMessage());
        }
        return false;
    }

    private static String buildGiveCommand(ServerPlayerEntity player, GymLevelCapConfig.ItemReward reward) {
        StringBuilder command = new StringBuilder("give ");
        command.append(player.getScoreboardName()).append(' ');
        command.append(reward.getItemId());
        int count = Math.max(1, reward.getCount());
        if (count > 1) {
            command.append(' ').append(count);
        }
        String nbt = trimToNull(reward.getNbt());
        if (nbt != null) {
            command.append(' ').append(nbt);
        }
        return command.toString();
    }

    public static void handleAllOnlinePlayers(java.util.function.Consumer<ServerPlayerEntity> consumer) {
        if (ServerLifecycleHooks.getCurrentServer() != null) {
            for (ServerPlayerEntity player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
                consumer.accept(player);
            }
        }
    }

    public static void beginGymCreation(ServerPlayerEntity player, String gymName, int levelCap) {
        if (player == null) {
            return;
        }
        String trimmedName = gymName == null ? "" : gymName.trim();
        pendingGymCreations.put(player.getUUID(), new PendingGymCreation(trimmedName, Math.max(1, levelCap)));
    }

    public static PendingGymCreation getPendingGymCreation(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        return pendingGymCreations.get(player.getUUID());
    }

    public static GymLevelCapConfig.GymSummary completeGymCreation(ServerPlayerEntity player, Entity npcEntity) {
        if (player == null) {
            return null;
        }
        PendingGymCreation pending = pendingGymCreations.remove(player.getUUID());
        if (pending == null) {
            return null;
        }
        if (config == null) {
            pendingGymCreations.put(player.getUUID(), pending);
            return null;
        }
        String leaderName = PixelmonHooks.getNpcLeaderName(npcEntity);
        String gymName = pending.getGymName();
        if (gymName == null || gymName.isEmpty()) {
            gymName = PixelmonHooks.getNpcGymName(npcEntity);
        }
        if (gymName == null || gymName.isEmpty()) {
            gymName = leaderName;
        }
        if (gymName == null || gymName.isEmpty()) {
            gymName = "Gym";
        }
        String npcUuid = npcEntity == null ? null : npcEntity.getUUID().toString();
        GymLevelCapConfig.GymSummary summary = config.createGym(gymName, leaderName, npcUuid, pending.getLevelCap());
        if (summary != null) {
            LevelCapMod.LOGGER.info("Created gym '{}' led by '{}' with level cap {} (player: {})",
                    summary.getGymName(), summary.getLeaderName(), summary.getLevelCap(), player.getGameProfile().getName());
        }
        return summary;
    }

    public static final class PendingGymCreation {
        private final String gymName;
        private final int levelCap;

        private PendingGymCreation(String gymName, int levelCap) {
            this.gymName = gymName;
            this.levelCap = levelCap;
        }

        public String getGymName() {
            return gymName;
        }

        public int getLevelCap() {
            return levelCap;
        }
    }

    public static final class GymVictory {
        private final GymLevelCapConfig.GymSummary summary;
        private final String displayLabel;
        private final int newLevelCap;

        private GymVictory(GymLevelCapConfig.GymSummary summary, String displayLabel, int newLevelCap) {
            this.summary = summary;
            this.displayLabel = displayLabel;
            this.newLevelCap = newLevelCap;
        }

        public GymLevelCapConfig.GymSummary getSummary() {
            return summary;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public int getNewLevelCap() {
            return newLevelCap;
        }
    }

    private static ItemStack createRewardStack(GymLevelCapConfig.ItemReward reward, String gymId) {
        if (reward == null || reward.getItemId() == null) {
            return ItemStack.EMPTY;
        }
        ResourceLocation id;
        try {
            id = new ResourceLocation(reward.getItemId());
        } catch (ResourceLocationException e) {
            LevelCapMod.LOGGER.warn("Skipping reward item '{}' for gym '{}': invalid identifier", reward.getItemId(), gymId);
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null) {
            LevelCapMod.LOGGER.warn("Skipping reward item '{}' for gym '{}': item not found", reward.getItemId(), gymId);
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, reward.getCount()));
        String nbt = reward.getNbt();
        if (nbt != null && !nbt.isEmpty()) {
            try {
                stack.setTag(JsonToNBT.parseTag(nbt));
            } catch (CommandSyntaxException e) {
                LevelCapMod.LOGGER.warn("Skipping NBT on reward item '{}' for gym '{}': {}", reward.getItemId(), gymId, e.getMessage());
            }
        }
        return stack;
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

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
