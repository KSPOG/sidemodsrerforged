package com.example.lvlcap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Pattern;

public class GymLevelCapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_GYM_ID = "gym";
    private static final Pattern COLOR_CODE_PATTERN = Pattern.compile("(?i)[\\u00A7&][0-9A-FK-ORX]");
    private static final Set<String> IGNORED_ALIAS_TOKENS = new HashSet<>(Arrays.asList(
            "gym", "leader", "npc", "trainer", "the", "badge"));

    private final Path configFile;
    private int baseLevelCap;
    private final Map<String, GymDefinition> gyms = new LinkedHashMap<>();
    private final Map<String, String> aliases = new HashMap<>();

    public GymLevelCapConfig(Path configDir, int baseLevelCap) {
        this.configFile = configDir.resolve("pixelmon-level-caps.json");
        this.baseLevelCap = Math.max(1, baseLevelCap);
        reload();
    }

    public synchronized void setBaseLevelCap(int baseLevelCap) {
        this.baseLevelCap = Math.max(1, baseLevelCap);
    }

    public synchronized void reload() {
        gyms.clear();
        aliases.clear();
        if (!Files.exists(configFile)) {
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            ConfigModel model = GSON.fromJson(reader, ConfigModel.class);
            if (model != null && model.defaultLevelCap > 0) {
                this.baseLevelCap = model.defaultLevelCap;
            }
            if (model != null && model.gyms != null) {
                for (GymEntry entry : model.gyms) {
                    if (entry == null) {
                        continue;
                    }
                    String gymName = trimToNull(entry.name);
                    String leaderName = trimToNull(entry.leader);
                    String npcUuid = trimToNull(entry.npcUuid);
                    int levelCap = entry.levelCap > 0 ? entry.levelCap : this.baseLevelCap;
                    String id = trimToNull(entry.id);
                    if (id != null) {
                        id = normalizeKey(id);
                    }
                    if (id == null || id.isEmpty()) {
                        id = normalizeKey(gymName != null ? gymName : leaderName);
                    }
                    if (id == null || id.isEmpty()) {
                        id = npcUuid != null ? normalizeKey(npcUuid) : null;
                    }
                    id = ensureUniqueId(id);
                    GymDefinition definition = new GymDefinition();
                    definition.id = id;
                    definition.gymName = gymName;
                    definition.leaderName = leaderName;
                    definition.npcUuid = npcUuid;
                    definition.levelCap = levelCap;
                    definition.rewards.clear();
                    if (entry.rewards != null) {
                        for (RewardEntry rewardEntry : entry.rewards) {
                            if (rewardEntry == null) {
                                continue;
                            }
                            ItemReward reward = new ItemReward(rewardEntry.item, rewardEntry.count, rewardEntry.nbt);
                            if (reward.getItemId() != null) {
                                definition.rewards.add(reward);
                            }
                        }
                    }
                    gyms.put(id, definition);
                    refreshAliasesFor(definition);
                }
            }
        } catch (IOException | JsonParseException e) {
            LevelCapMod.LOGGER.error("Failed to read gym level cap configuration", e);
        }
    }

    public synchronized void save() {
        ConfigModel model = new ConfigModel();
        model.defaultLevelCap = baseLevelCap;
        for (GymDefinition definition : gyms.values()) {
            GymEntry entry = new GymEntry();
            entry.id = definition.id;
            entry.name = definition.gymName;
            entry.leader = definition.leaderName;
            entry.npcUuid = definition.npcUuid;
            entry.levelCap = definition.levelCap;
            for (ItemReward reward : definition.rewards) {
                RewardEntry rewardEntry = new RewardEntry();
                rewardEntry.item = reward.getItemId();
                rewardEntry.count = reward.getCount();
                rewardEntry.nbt = reward.getNbt();
                entry.rewards.add(rewardEntry);
            }
            model.gyms.add(entry);
        }
        try {
            Files.createDirectories(configFile.getParent());
            try (BufferedWriter writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(model, writer);
            }
        } catch (IOException e) {
            LevelCapMod.LOGGER.error("Failed to write gym level cap configuration", e);
        }
    }

    public synchronized int getDefaultLevelCap() {
        return baseLevelCap;
    }

    public synchronized OptionalInt getGymLevelCap(String name) {
        return getGymLevelCapByNormalized(normalizeKey(name));
    }

    public synchronized OptionalInt getGymLevelCapByNormalized(String normalized) {
        String id = resolveIdWithFallbacks(normalizeKey(normalized));
        if (id == null) {
            return OptionalInt.empty();
        }
        GymDefinition definition = gyms.get(id);
        return definition == null ? OptionalInt.empty() : OptionalInt.of(definition.levelCap);
    }

    public synchronized GymSummary findGymSummary(String name) {
        String normalized = normalizeKey(name);
        String id = resolveIdWithFallbacks(normalized);
        if (id == null) {
            return null;
        }
        GymDefinition definition = gyms.get(id);
        return definition == null ? null : definition.toSummary();
    }

    public synchronized Map<String, Integer> getAllGymCaps() {
        Map<String, Integer> response = new LinkedHashMap<>();
        for (GymDefinition definition : gyms.values()) {
            response.put(buildDisplayLabel(definition), definition.levelCap);
        }
        return response;
    }

    public synchronized int setGymLevelCap(String name, int levelCap) {
        String normalized = normalizeKey(name);
        int cappedLevel = Math.max(1, levelCap);
        String id = resolveIdWithFallbacks(normalized);
        GymDefinition definition;
        if (id == null) {
            id = ensureUniqueId(normalized);
            definition = new GymDefinition();
            definition.id = id;
            definition.levelCap = cappedLevel;
            definition.gymName = trimToNull(name);
            gyms.put(id, definition);
        } else {
            definition = gyms.get(id);
            if (definition == null) {
                definition = new GymDefinition();
                definition.id = id;
                gyms.put(id, definition);
            }
            definition.levelCap = cappedLevel;
            String trimmed = trimToNull(name);
            if (matches(definition.gymName, normalized) || definition.gymName == null) {
                definition.gymName = trimmed;
            } else if (matches(definition.leaderName, normalized) || definition.leaderName == null) {
                definition.leaderName = trimmed;
            }
        }
        refreshAliasesFor(definition);
        save();
        return definition.levelCap;
    }

    public synchronized boolean removeGym(String name) {
        String id = resolveIdWithFallbacks(normalizeKey(name));
        if (id == null) {
            return false;
        }
        if (gyms.remove(id) != null) {
            clearAliasesFor(id);
            save();
            return true;
        }
        return false;
    }

    public synchronized OptionalInt findNextCap(int currentCap, Collection<String> normalizedBadges) {
        int candidate = Integer.MAX_VALUE;
        boolean found = false;
        for (GymDefinition definition : gyms.values()) {
            if (hasBadge(normalizedBadges, definition.id)) {
                continue;
            }
            int value = definition.levelCap;
            if (value > currentCap && value < candidate) {
                candidate = value;
                found = true;
            }
        }
        return found ? OptionalInt.of(candidate) : OptionalInt.empty();
    }

    public synchronized void recordDisplayName(String normalized, String displayName) {
        String trimmedDisplay = trimToNull(displayName);
        if (trimmedDisplay == null) {
            return;
        }
        String id = resolveIdWithFallbacks(normalizeKey(normalized));
        if (id == null) {
            return;
        }
        GymDefinition definition = gyms.get(id);
        if (definition == null) {
            return;
        }
        if (matches(definition.leaderName, normalizeKey(normalized))) {
            definition.leaderName = trimmedDisplay;
        } else if (definition.gymName == null || definition.gymName.isEmpty() || matches(definition.gymName, normalizeKey(normalized))) {
            definition.gymName = trimmedDisplay;
        }
        refreshAliasesFor(definition);
    }

    public synchronized GymSummary createGym(String gymName, String leaderName, String npcUuid, int levelCap) {
        return createGym(gymName, leaderName, npcUuid, levelCap, Collections.emptyList());
    }

    public synchronized GymSummary createGym(String gymName, String leaderName, String npcUuid, int levelCap,
                                             List<ItemReward> rewards) {
        String trimmedGym = trimToNull(gymName);
        String trimmedLeader = trimToNull(leaderName);
        String trimmedNpc = trimToNull(npcUuid);
        int cappedLevel = Math.max(1, levelCap);

        String id = resolveIdWithFallbacks(normalizeKey(trimmedNpc));
        if (id == null) {
            id = resolveIdWithFallbacks(normalizeKey(trimmedGym));
        }
        if (id == null) {
            id = resolveIdWithFallbacks(normalizeKey(trimmedLeader));
        }
        if (id == null) {
            id = ensureUniqueId(normalizeKey(trimmedGym));
        }
        GymDefinition definition = gyms.get(id);
        if (definition == null) {
            definition = new GymDefinition();
            definition.id = id;
            gyms.put(id, definition);
        }
        definition.levelCap = cappedLevel;
        if (trimmedGym != null) {
            definition.gymName = trimmedGym;
        }
        if (trimmedLeader != null) {
            definition.leaderName = trimmedLeader;
        }
        if (trimmedNpc != null) {
            definition.npcUuid = trimmedNpc;
        }
        definition.rewards.clear();
        if (rewards != null) {
            for (ItemReward reward : rewards) {
                if (reward == null || reward.getItemId() == null) {
                    continue;
                }
                definition.rewards.add(reward.copy());
            }
        }
        refreshAliasesFor(definition);
        save();
        return definition.toSummary();
    }

    public String normalizeKey(String name) {
        if (name == null) {
            return "";
        }
        String stripped = stripFormatting(name);
        return stripped.trim().toLowerCase(Locale.ROOT);
    }

    private String ensureUniqueId(String base) {
        String normalizedBase = normalizeKey(base);
        if (normalizedBase.isEmpty()) {
            normalizedBase = DEFAULT_GYM_ID;
        }
        String candidate = normalizedBase;
        int counter = 2;
        while (gyms.containsKey(candidate)) {
            candidate = normalizedBase + "_" + counter++;
        }
        return candidate;
    }

    private void refreshAliasesFor(GymDefinition definition) {
        if (definition == null) {
            return;
        }
        clearAliasesFor(definition.id);
        addAlias(definition.id, definition.id);
        addAlias(definition.gymName, definition.id);
        addAlias(definition.leaderName, definition.id);
        addAlias(definition.npcUuid, definition.id);
    }

    private void clearAliasesFor(String id) {
        aliases.entrySet().removeIf(entry -> entry.getValue().equals(id));
    }

    private void addAlias(String alias, String id) {
        if (alias == null || id == null) {
            return;
        }
        String normalized = normalizeKey(alias);
        if (!normalized.isEmpty()) {
            aliases.put(normalized, id);
            addAliasTokens(normalized, id);
        }
    }

    private void addAliasTokens(String normalized, String id) {
        if (normalized.indexOf('-') >= 0 && normalized.chars().allMatch(ch -> ch == '-' || (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f'))) {
            return;
        }
        String[] tokens = normalized.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isEmpty() || token.length() < 3 || IGNORED_ALIAS_TOKENS.contains(token)) {
                continue;
            }
            aliases.putIfAbsent(token, id);
        }
    }

    private String resolveId(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        return aliases.get(normalized);
    }

    private String resolveIdWithFallbacks(String normalized) {
        String id = resolveId(normalized);
        if (id != null || normalized == null || normalized.isEmpty()) {
            return id;
        }
        String[] tokens = normalized.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.isEmpty() || token.length() < 3) {
                continue;
            }
            if (IGNORED_ALIAS_TOKENS.contains(token)) {
                continue;
            }
            id = resolveId(token);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private boolean hasBadge(Collection<String> normalizedBadges, String id) {
        if (normalizedBadges == null || id == null) {
            return false;
        }
        for (String badge : normalizedBadges) {
            String resolved = resolveId(normalizeKey(badge));
            if (id.equals(resolved)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String candidate, String normalized) {
        return candidate != null && normalizeKey(candidate).equals(normalized);
    }

    private String buildDisplayLabel(GymDefinition definition) {
        String gymName = definition.gymName;
        String leaderName = definition.leaderName;
        if (gymName == null || gymName.isEmpty()) {
            if (leaderName != null && !leaderName.isEmpty()) {
                return leaderName;
            }
            return definition.id;
        }
        if (leaderName != null && !leaderName.isEmpty()) {
            String normalizedGym = normalizeKey(gymName);
            String normalizedLeader = normalizeKey(leaderName);
            if (!normalizedGym.equals(normalizedLeader)) {
                return gymName + " (Leader: " + leaderName + ")";
            }
        }
        return gymName;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String stripFormatting(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return COLOR_CODE_PATTERN.matcher(value).replaceAll("");
    }

    private static List<ItemReward> copyRewards(List<ItemReward> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<ItemReward> copy = new ArrayList<>();
        for (ItemReward reward : source) {
            if (reward == null || reward.getItemId() == null) {
                continue;
            }
            copy.add(reward.copy());
        }
        return copy;
    }

    private static class GymDefinition {
        private String id;
        private String gymName;
        private String leaderName;
        private String npcUuid;
        private int levelCap;
        private final List<ItemReward> rewards = new ArrayList<>();

        private GymSummary toSummary() {
            return new GymSummary(id, gymName, leaderName, levelCap, copyRewards(rewards));
        }
    }

    public static final class GymSummary {
        private final String id;
        private final String gymName;
        private final String leaderName;
        private final int levelCap;
        private final List<ItemReward> rewards;

        private GymSummary(String id, String gymName, String leaderName, int levelCap, List<ItemReward> rewards) {
            this.id = id;
            this.gymName = gymName;
            this.leaderName = leaderName;
            this.levelCap = levelCap;
            this.rewards = Collections.unmodifiableList(rewards);
        }

        public String getId() {
            return id;
        }

        public String getGymName() {
            return gymName;
        }

        public String getLeaderName() {
            return leaderName;
        }

        public int getLevelCap() {
            return levelCap;
        }

        public List<ItemReward> getRewards() {
            return rewards;
        }
    }

    public static final class ItemReward {
        private final String itemId;
        private final int count;
        private final String nbt;

        public ItemReward(String itemId, int count, String nbt) {
            this.itemId = trimToNull(itemId);
            this.count = Math.max(1, count);
            this.nbt = trimToNull(nbt);
        }

        public String getItemId() {
            return itemId;
        }

        public int getCount() {
            return count;
        }

        public String getNbt() {
            return nbt;
        }

        private ItemReward copy() {
            return new ItemReward(itemId, count, nbt);
        }
    }

    private static class ConfigModel {
        int defaultLevelCap;
        List<GymEntry> gyms = new ArrayList<>();
    }

    private static class GymEntry {
        String id;
        String name;
        String leader;
        String npcUuid;
        int levelCap;
        List<RewardEntry> rewards = new ArrayList<>();
    }

    private static class RewardEntry {
        String item;
        int count = 1;
        String nbt;
    }
}
