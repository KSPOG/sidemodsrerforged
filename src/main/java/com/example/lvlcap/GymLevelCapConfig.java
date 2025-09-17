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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

public class GymLevelCapConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;
    private int baseLevelCap;
    private final Map<String, Integer> gymCaps = new LinkedHashMap<>();
    private final Map<String, String> displayNames = new HashMap<>();

    public GymLevelCapConfig(Path configDir, int baseLevelCap) {
        this.configFile = configDir.resolve("pixelmon-level-caps.json");
        this.baseLevelCap = Math.max(1, baseLevelCap);
        reload();
    }

    public synchronized void setBaseLevelCap(int baseLevelCap) {
        this.baseLevelCap = Math.max(1, baseLevelCap);
    }

    public synchronized void reload() {
        if (!Files.exists(configFile)) {
            save();
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            ConfigModel model = GSON.fromJson(reader, ConfigModel.class);
            if (model != null) {
                if (model.defaultLevelCap > 0) {
                    this.baseLevelCap = model.defaultLevelCap;
                }
                gymCaps.clear();
                displayNames.clear();
                if (model.gyms != null) {
                    for (GymEntry entry : model.gyms) {
                        if (entry == null || entry.name == null || entry.name.trim().isEmpty()) {
                            continue;
                        }
                        int levelCap = entry.levelCap > 0 ? entry.levelCap : this.baseLevelCap;
                        String key = normalizeKey(entry.name);
                        gymCaps.put(key, levelCap);
                        displayNames.put(key, entry.name);
                    }
                }
            }
        } catch (IOException | JsonParseException e) {
            LevelCapMod.LOGGER.error("Failed to read gym level cap configuration", e);
        }
    }

    public synchronized void save() {
        ConfigModel model = new ConfigModel();
        model.defaultLevelCap = baseLevelCap;
        for (Map.Entry<String, Integer> entry : gymCaps.entrySet()) {
            GymEntry gymEntry = new GymEntry();
            gymEntry.name = displayNames.getOrDefault(entry.getKey(), entry.getKey());
            gymEntry.levelCap = entry.getValue();
            model.gyms.add(gymEntry);
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
        Integer value = gymCaps.get(normalized);
        return value == null ? OptionalInt.empty() : OptionalInt.of(value);
    }

    public synchronized Map<String, Integer> getAllGymCaps() {
        Map<String, Integer> response = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : gymCaps.entrySet()) {
            String displayName = displayNames.getOrDefault(entry.getKey(), entry.getKey());
            response.put(displayName, entry.getValue());
        }
        return response;
    }

    public synchronized int setGymLevelCap(String name, int levelCap) {
        String key = normalizeKey(name);
        int cappedLevel = Math.max(1, levelCap);
        gymCaps.put(key, cappedLevel);
        displayNames.put(key, name);
        save();
        return cappedLevel;
    }

    public synchronized boolean removeGym(String name) {
        String key = normalizeKey(name);
        if (gymCaps.remove(key) != null) {
            displayNames.remove(key);
            save();
            return true;
        }
        return false;
    }

    public synchronized OptionalInt findNextCap(int currentCap, Collection<String> normalizedBadges) {
        int candidate = Integer.MAX_VALUE;
        boolean found = false;
        for (Map.Entry<String, Integer> entry : gymCaps.entrySet()) {
            if (normalizedBadges.contains(entry.getKey())) {
                continue;
            }
            int value = entry.getValue();
            if (value > currentCap && value < candidate) {
                candidate = value;
                found = true;
            }
        }
        return found ? OptionalInt.of(candidate) : OptionalInt.empty();
    }

    public synchronized void recordDisplayName(String normalized, String displayName) {
        if (displayName != null && !displayName.isEmpty()) {
            String key = normalizeKey(normalized);
            displayNames.putIfAbsent(key, displayName);
        }
    }

    public String normalizeKey(String name) {
        if (name == null) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static class ConfigModel {
        int defaultLevelCap;
        List<GymEntry> gyms = new ArrayList<>();
    }

    private static class GymEntry {
        String name;
        int levelCap;
    }
}
