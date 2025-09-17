package com.example.lvlcap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.Optional;

public final class PixelmonHooks {
    private PixelmonHooks() {
    }

    private static Class<?>[] npcBaseClasses;
    private static boolean npcClassLookupAttempted;
    private static EntityType<?> cachedNpcEntityType;
    private static boolean npcEntityTypeLookupAttempted;
    private static Object pixelmonEventBus;
    private static boolean pixelmonEventBusLookupAttempted;
    private static final String[] ENTITY_METHOD_NAMES = new String[] {
            "getEntity", "getTrainer", "getTrainers", "getOpponent", "getOpponents",
            "getOpposingTrainer", "getOpposingTrainers", "getOpponentTrainer", "getTargets",
            "getTarget", "getParticipants", "getChallengers", "getLeaders", "getGymLeader",
            "getGymLeaders", "getNpc", "getNPC", "getNpcs", "getNPCs", "getNonPlayerParticipants",
            "getOtherTrainers", "getBattleOpponents", "getBattleOpponent", "getDefendingTrainers",
            "getDefenders", "getAllTrainers", "getAllParticipants", "getBattle", "getBattleController"
    };
    private static final String[] ENTITY_FIELD_NAMES = new String[] {
            "entity", "trainer", "trainers", "opponent", "opponents", "opposingTrainer",
            "opposingTrainers", "npc", "npcTrainer", "npcEntity", "target", "targets", "leader",
            "leaders", "gymLeader", "gymLeaders", "challenger", "challengers", "participant",
            "participants", "battleOpponents", "enemyTrainers", "defenders"
    };
    private static final String[] WINNER_METHOD_NAMES = new String[] {
            "getWinner", "getWinners", "getWinningPlayer", "getWinningPlayers", "getVictors",
            "getVictor", "getWinningTrainers", "getWinningTeam", "getWinningChallengers",
            "getWinningParticipants"
    };
    private static final String[] WINNER_FIELD_NAMES = new String[] {
            "winner", "winners", "winningPlayer", "winningPlayers", "victor", "victors",
            "winningTrainers", "winningTeam", "winningChallengers", "winningParticipants"
    };

    public static void registerPixelmonEventHandler(Object handler) {
        if (handler == null) {
            return;
        }
        Object eventBus = getPixelmonEventBus();
        if (eventBus == null) {
            LevelCapMod.LOGGER.warn("Pixelmon event bus not found; cannot register handler {}", handler.getClass().getName());
            return;
        }
        try {
            Method register = eventBus.getClass().getMethod("register", Object.class);
            register.invoke(eventBus, handler);
        } catch (ReflectiveOperationException e) {
            LevelCapMod.LOGGER.warn("Failed to register Pixelmon event handler {}: {}", handler.getClass().getName(), e.getMessage());
        }
    }

    private static Object getPixelmonEventBus() {
        if (!pixelmonEventBusLookupAttempted) {
            pixelmonEventBusLookupAttempted = true;
            try {
                Class<?> pixelmonClass = Class.forName("com.pixelmonmod.pixelmon.Pixelmon");
                Field field = pixelmonClass.getField("EVENT_BUS");
                pixelmonEventBus = field.get(null);
            } catch (ClassNotFoundException e) {
                LevelCapMod.LOGGER.warn("Pixelmon not detected on the classpath; gym victories will not be tracked");
            } catch (NoSuchFieldException | IllegalAccessException e) {
                LevelCapMod.LOGGER.warn("Unable to access Pixelmon EVENT_BUS: {}", e.getMessage());
            }
        }
        return pixelmonEventBus;
    }

    public static ServerPlayerEntity getPlayerFromEvent(Event event) {
        Object candidate = invokeOptional(event, "getPlayer");
        if (candidate instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) candidate;
        }
        candidate = invokeOptional(event, "getPlayerMP");
        if (candidate instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) candidate;
        }
        candidate = invokeOptional(event, "getPlayerEntity");
        if (candidate instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) candidate;
        }
        Object field = getFieldValue(event, "player", "playerMP", "playerEntity");
        if (field instanceof ServerPlayerEntity) {
            return (ServerPlayerEntity) field;
        }
        return null;
    }

    public static List<ServerPlayerEntity> getPlayersFromEvent(Event event) {
        Set<ServerPlayerEntity> players = new LinkedHashSet<>();
        if (event == null) {
            return new ArrayList<>();
        }
        ServerPlayerEntity direct = getPlayerFromEvent(event);
        if (direct != null) {
            players.add(direct);
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(event);
        collectPlayerCandidates(invokeOptional(event, "getPlayers"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getPlayerList"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getAllPlayers"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getBattlers"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getParticipants"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getTrainers"), players, visited);
        collectPlayerCandidates(getFieldValue(event, "players", "playerList", "challengers", "participants",
                "trainers", "battlers"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getBattle"), players, visited);
        collectPlayerCandidates(invokeOptional(event, "getBattleController"), players, visited);
        return new ArrayList<>(players);
    }

    public static List<ServerPlayerEntity> getWinningPlayers(Event event) {
        Set<ServerPlayerEntity> winners = new LinkedHashSet<>();
        if (event == null) {
            return new ArrayList<>();
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(event);
        for (String method : WINNER_METHOD_NAMES) {
            collectPlayerCandidates(invokeOptional(event, method), winners, visited);
        }
        for (String field : WINNER_FIELD_NAMES) {
            collectPlayerCandidates(getFieldValue(event, field), winners, visited);
        }
        if (winners.isEmpty() && playerWon(event)) {
            ServerPlayerEntity player = getPlayerFromEvent(event);
            if (player != null) {
                winners.add(player);
            }
        }
        return new ArrayList<>(winners);
    }

    public static List<Entity> getGymLeadersFromEvent(Event event) {
        List<Entity> entities = getEntitiesFromEvent(event);
        List<Entity> trainers = new ArrayList<>();
        for (Entity entity : entities) {
            if (entity != null && isPixelmonNpc(entity)) {
                trainers.add(entity);
            }
        }
        return trainers;
    }

    private static void collectPlayerCandidates(Object candidate, Set<ServerPlayerEntity> players, Set<Object> visited) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof ServerPlayerEntity) {
            players.add((ServerPlayerEntity) candidate);
            return;
        }
        if (!visited.add(candidate)) {
            return;
        }
        if (candidate instanceof Collection<?>) {
            for (Object element : (Collection<?>) candidate) {
                collectPlayerCandidates(element, players, visited);
            }
            return;
        }
        if (candidate.getClass().isArray()) {
            int length = Array.getLength(candidate);
            for (int i = 0; i < length; i++) {
                collectPlayerCandidates(Array.get(candidate, i), players, visited);
            }
            return;
        }
        Object extracted = invokeOptional(candidate, "getPlayer");
        if (extracted != null) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getPlayerEntity");
        if (extracted != null) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getPlayerMP");
        if (extracted != null) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getEntity");
        if (extracted != null) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = getFieldValue(candidate, "player", "playerEntity", "playerMP", "entity");
        if (extracted != null) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getPlayers");
        if (extracted != null && extracted != candidate) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getPlayerList");
        if (extracted != null && extracted != candidate) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = invokeOptional(candidate, "getAllPlayers");
        if (extracted != null && extracted != candidate) {
            collectPlayerCandidates(extracted, players, visited);
        }
        extracted = getFieldValue(candidate, "players", "playerList", "participants", "trainers", "challengers", "battlers");
        if (extracted != null && extracted != candidate) {
            collectPlayerCandidates(extracted, players, visited);
        }
    }

    public static String extractBadgeName(Event event) {
        Object badgeObject = invokeOptional(event, "getBadge");
        if (badgeObject == null) {
            badgeObject = getFieldValue(event, "badge", "badgeID", "badgeName");
        }
        if (badgeObject != null) {
            String identified = identifyBadge(badgeObject);
            if (identified != null) {
                return identified;
            }
        }
        Object name = invokeOptional(event, "getBadgeName");
        if (name == null) {
            name = invokeOptional(event, "getGymName");
        }
        if (name == null) {
            name = invokeOptional(event, "getLeaderName");
        }
        if (name == null) {
            name = getFieldValue(event, "gymName", "leaderName");
        }
        return name == null ? null : name.toString();
    }

    public static Set<String> getBadges(ServerPlayerEntity player, Function<String, String> normalizer) {
        Set<String> badges = new LinkedHashSet<>();
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = findCompatibleMethod(storageProxy, "getParty", ServerPlayerEntity.class);
            if (getParty == null) {
                getParty = findCompatibleMethod(storageProxy, "getParty", Class.forName("net.minecraft.entity.player.PlayerEntity"));
            }
            if (getParty == null) {
                LevelCapMod.LOGGER.debug("Unable to find StorageProxy#getParty; badge sync skipped");
                return badges;
            }
            Object storage = getParty.invoke(null, player);
            if (storage == null) {
                return badges;
            }
            Object badgeCase = invokeOptional(storage, "getBadgeCase");
            if (badgeCase == null) {
                badgeCase = invokeOptional(storage, "getBadges");
            }
            Collection<?> badgeCollection = extractBadgeCollection(badgeCase);
            for (Object badge : badgeCollection) {
                String identified = identifyBadge(badge);
                if (identified != null && !identified.isEmpty()) {
                    badges.add(normalizer.apply(identified));
                }
            }
        } catch (ClassNotFoundException e) {
            LevelCapMod.LOGGER.debug("Pixelmon StorageProxy class not found; assuming Pixelmon is unavailable");
        } catch (Exception e) {
            LevelCapMod.LOGGER.error("Failed to synchronise Pixelmon badges for {}", player.getGameProfile().getName(), e);
        }
        return badges;
    }

    public static boolean isBattleSendOut(Event event) {
        Boolean flag = toBoolean(invokeOptional(event, "isBattle"));
        if (flag != null) {
            return flag;
        }
        if (invokeOptional(event, "getBattleController") != null) {
            return true;
        }
        if (invokeOptional(event, "getBattle") != null) {
            return true;
        }
        Object context = invokeOptional(event, "getContext");
        if (context != null) {
            String simpleName = context.getClass().getSimpleName().toLowerCase(Locale.ROOT);
            if (simpleName.contains("battle")) {
                return true;
            }
        }
        String simple = event.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return simple.contains("battle");
    }

    public static Object getPokemonFromEvent(Event event) {
        Object pokemon = invokeOptional(event, "getPokemon");
        if (pokemon == null) {
            pokemon = getFieldValue(event, "pokemon", "storagePokemon");
        }
        return pokemon;
    }

    public static Integer getPokemonLevel(Object pokemon) {
        if (pokemon == null) {
            return null;
        }
        Object value = invokeOptional(pokemon, "getPokemonLevel");
        if (!(value instanceof Number)) {
            value = invokeOptional(pokemon, "getLevel");
        }
        if (!(value instanceof Number)) {
            value = invokeOptional(pokemon, "getLvl");
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    public static String getPokemonDisplayName(Object pokemon) {
        if (pokemon == null) {
            return "Pokemon";
        }
        Object name = invokeOptional(pokemon, "getDisplayName");
        if (name == null) {
            name = invokeOptional(pokemon, "getLocalizedName");
        }
        if (name == null) {
            name = invokeOptional(pokemon, "getName");
        }
        if (name == null) {
            name = getFieldValue(pokemon, "nickname", "pokemonName", "localizedName");
        }
        return name == null ? "Pokemon" : name.toString();
    }

    public static List<Object> getPartyPokemon(ServerPlayerEntity player) {
        List<Object> pokemon = new ArrayList<>();
        if (player == null) {
            return pokemon;
        }
        try {
            Class<?> storageProxy = Class.forName("com.pixelmonmod.pixelmon.api.storage.StorageProxy");
            Method getParty = findCompatibleMethod(storageProxy, "getParty", ServerPlayerEntity.class);
            if (getParty == null) {
                getParty = findCompatibleMethod(storageProxy, "getParty",
                        Class.forName("net.minecraft.entity.player.PlayerEntity"));
            }
            if (getParty == null) {
                LevelCapMod.LOGGER.debug("Unable to locate StorageProxy#getParty; party lookup skipped");
                return pokemon;
            }
            Object partyStorage = getParty.invoke(null, player);
            if (partyStorage == null) {
                return pokemon;
            }
            Collection<?> collection = extractPartyCollection(partyStorage);
            for (Object entry : collection) {
                if (entry != null) {
                    pokemon.add(entry);
                }
            }
            if (pokemon.isEmpty()) {
                collectPartySlots(partyStorage, pokemon);
            }
        } catch (ClassNotFoundException e) {
            LevelCapMod.LOGGER.debug("Pixelmon StorageProxy class not found; assuming Pixelmon is unavailable");
        } catch (Exception e) {
            LevelCapMod.LOGGER.error("Failed to inspect Pixelmon party for {}",
                    player.getGameProfile().getName(), e);
        }
        return pokemon;
    }

    public static boolean isPixelmonNpc(Entity entity) {
        if (entity == null) {
            return false;
        }
        ensureNpcClassesLoaded();
        if (npcBaseClasses != null) {
            for (Class<?> npcClass : npcBaseClasses) {
                if (npcClass.isInstance(entity)) {
                    return true;
                }
            }
        }
        String name = entity.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("pixelmon") && (name.contains("npc") || name.contains("trainer"));
    }

    public static String getNpcGymName(Entity entity) {
        if (entity == null) {
            return null;
        }
        Object gymName = invokeOptional(entity, "getGymName");
        if (gymName == null) {
            gymName = invokeOptional(entity, "getArenaName");
        }
        if (gymName == null) {
            gymName = invokeOptional(entity, "getGymTitle");
        }
        if (gymName == null) {
            gymName = getFieldValue(entity, "gymName", "arenaName", "gymTitle");
        }
        String text = toText(gymName);
        return text == null || text.isEmpty() ? null : text;
    }

    public static String getNpcLeaderName(Entity entity) {
        if (entity == null) {
            return null;
        }
        Object leader = invokeOptional(entity, "getLeaderName");
        if (leader == null) {
            leader = invokeOptional(entity, "getTrainerName");
        }
        if (leader == null) {
            leader = invokeOptional(entity, "getName");
        }
        if (leader == null) {
            leader = getFieldValue(entity, "leaderName", "trainerName", "name");
        }
        String text = toText(leader);
        if (text != null && !text.isEmpty()) {
            return text;
        }
        ITextComponent displayName = entity.getDisplayName();
        if (displayName != null) {
            text = displayName.getString();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return entity.getName().getString();
    }

    public static String getNpcDisplayName(Entity entity) {
        if (entity == null) {
            return "NPC";
        }
        ITextComponent displayName = entity.getDisplayName();
        if (displayName != null) {
            String text = displayName.getString();
            if (!text.isEmpty()) {
                return text;
            }
        }
        return entity.getName().getString();
    }

    public static Entity spawnGymNpc(ServerPlayerEntity player, String gymName, String leaderName, int levelCap) {
        if (player == null || !(player.level instanceof ServerWorld)) {
            return null;
        }
        ServerWorld world = (ServerWorld) player.level;
        EntityType<?> type = resolvePixelmonNpcEntityType();
        if (type == null) {
            return null;
        }
        Entity entity;
        try {
            entity = type.create(world);
        } catch (Exception e) {
            LevelCapMod.LOGGER.error("Failed to instantiate Pixelmon NPC entity for gym spawn", e);
            return null;
        }
        if (entity == null) {
            LevelCapMod.LOGGER.warn("Pixelmon NPC entity type {} returned null when spawning", ForgeRegistries.ENTITIES.getKey(type));
            return null;
        }
        Direction facing = player.getDirection();
        BlockPos targetPos = player.blockPosition().relative(facing);
        double x = targetPos.getX() + 0.5d;
        double y = targetPos.getY();
        double z = targetPos.getZ() + 0.5d;
        entity.moveTo(x, y, z, player.yRot, player.xRot);
        LivingEntity living = null;
        if (entity instanceof LivingEntity) {
            living = (LivingEntity) entity;
            if (entity instanceof MobEntity) {
                ((MobEntity) entity).setPersistenceRequired();
            }
            living.yHeadRot = player.yRot;
            living.yBodyRot = player.yRot;
        }
        applyGymMetadata(entity, gymName, leaderName, levelCap);
        if (living != null) {
            String custom = leaderName;
            if (custom == null || custom.trim().isEmpty()) {
                custom = gymName;
            }
            if (custom == null || custom.trim().isEmpty()) {
                custom = getNpcDisplayName(entity);
            }
            if (custom != null && !custom.trim().isEmpty()) {
                living.setCustomName(new StringTextComponent(custom.trim()));
                living.setCustomNameVisible(true);
            }
        }
        world.addFreshEntity(entity);
        return entity;
    }

    public static void faintPokemon(Event event, Object pokemon) {
        if (pokemon == null) {
            return;
        }
        invokeOptional(pokemon, "setHealth", 0);
        invokeOptional(pokemon, "setHealth", 0.0f);
        invokeOptional(pokemon, "setHealth", 0.0d);
        invokeOptional(pokemon, "setFainted", true);
        Object battlePokemon = invokeOptional(pokemon, "getBattlePokemon");
        if (battlePokemon != null) {
            invokeOptional(battlePokemon, "setHealth", 0);
            invokeOptional(battlePokemon, "setHealth", 0.0f);
            invokeOptional(battlePokemon, "setHealth", 0.0d);
            invokeOptional(battlePokemon, "setFainted", true);
        }
        Object entity = invokeOptional(event, "getEntity");
        if (entity == null) {
            entity = getFieldValue(event, "entity", "pokemonEntity");
        }
        if (entity instanceof LivingEntity) {
            ((LivingEntity) entity).setHealth(0.0f);
        }
    }

    private static List<Entity> getEntitiesFromEvent(Event event) {
        Set<Entity> entities = new LinkedHashSet<>();
        if (event == null) {
            return new ArrayList<>();
        }
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        visited.add(event);
        collectEntityCandidates(event, entities, visited);
        return new ArrayList<>(entities);
    }

    private static void collectEntityCandidates(Object candidate, Set<Entity> entities, Set<Object> visited) {
        if (candidate == null) {
            return;
        }
        if (candidate instanceof Entity) {
            entities.add((Entity) candidate);
            return;
        }
        if (!visited.add(candidate)) {
            return;
        }
        if (candidate instanceof Collection<?>) {
            for (Object element : (Collection<?>) candidate) {
                collectEntityCandidates(element, entities, visited);
            }
            return;
        }
        if (candidate.getClass().isArray()) {
            int length = Array.getLength(candidate);
            for (int i = 0; i < length; i++) {
                collectEntityCandidates(Array.get(candidate, i), entities, visited);
            }
            return;
        }
        for (String method : ENTITY_METHOD_NAMES) {
            collectEntityCandidates(invokeOptional(candidate, method), entities, visited);
        }
        for (String field : ENTITY_FIELD_NAMES) {
            collectEntityCandidates(getFieldValue(candidate, field), entities, visited);
        }
    }

    private static boolean playerWon(Event event) {
        if (event == null) {
            return false;
        }
        String[] flags = new String[] {"didPlayerWin", "didWin", "isPlayerWinner", "playerWon", "challengerWon"};
        for (String name : flags) {
            Boolean flag = toBoolean(invokeOptional(event, name));
            if (flag != null) {
                return flag;
            }
        }
        for (String name : flags) {
            Boolean flag = toBoolean(getFieldValue(event, name));
            if (flag != null) {
                return flag;
            }
        }
        return false;
    }

    private static void applyGymMetadata(Entity entity, String gymName, String leaderName, int levelCap) {
        if (entity == null) {
            return;
        }
        String trimmedGym = gymName == null ? "" : gymName.trim();
        String trimmedLeader = leaderName == null ? "" : leaderName.trim();
        if (!trimmedGym.isEmpty()) {
            invokeOptional(entity, "setGymName", trimmedGym);
            invokeOptional(entity, "setArenaName", trimmedGym);
            invokeOptional(entity, "setGymTitle", trimmedGym);
        }
        if (!trimmedLeader.isEmpty()) {
            invokeOptional(entity, "setLeaderName", trimmedLeader);
            invokeOptional(entity, "setTrainerName", trimmedLeader);
            invokeOptional(entity, "setName", trimmedLeader);
        }
        if (levelCap > 0) {
            invokeOptional(entity, "setLevelCap", levelCap);
            invokeOptional(entity, "setLevel", levelCap);
            invokeOptional(entity, "setTrainerLevel", levelCap);
            invokeOptional(entity, "setMinLevel", levelCap);
            invokeOptional(entity, "setMaxLevel", levelCap);
        }
    }

    private static EntityType<?> resolvePixelmonNpcEntityType() {
        if (cachedNpcEntityType != null) {
            return cachedNpcEntityType;
        }
        if (npcEntityTypeLookupAttempted) {
            return null;
        }
        npcEntityTypeLookupAttempted = true;
        EntityType<?> best = null;
        int bestScore = -1;
        for (EntityType<?> type : ForgeRegistries.ENTITIES.getValues()) {
            ResourceLocation key = ForgeRegistries.ENTITIES.getKey(type);
            if (key == null || !"pixelmon".equals(key.getNamespace())) {
                continue;
            }
            String path = key.getPath().toLowerCase(Locale.ROOT);
            int score = 0;
            if (path.contains("gym")) {
                score = 3;
            } else if (path.contains("trainer")) {
                score = 2;
            } else if (path.contains("npc")) {
                score = 1;
            }
            if (score <= 0) {
                continue;
            }
            if (score > bestScore) {
                best = type;
                bestScore = score;
            }
        }
        if (best != null) {
            cachedNpcEntityType = best;
            return cachedNpcEntityType;
        }
        String[] fallbacks = new String[] {"pixelmon:gym_leader", "pixelmon:npc_trainer", "pixelmon:npc_battle", "pixelmon:npc"};
        for (String id : fallbacks) {
            Optional<EntityType<?>> optional = EntityType.byString(id);
            if (optional.isPresent()) {
                cachedNpcEntityType = optional.get();
                break;
            }
        }
        if (cachedNpcEntityType == null) {
            LevelCapMod.LOGGER.warn("No Pixelmon NPC entity types were found in the registry; gym spawning is unavailable.");
        }
        return cachedNpcEntityType;
    }

    private static Collection<?> extractBadgeCollection(Object badgeCase) {
        if (badgeCase == null) {
            return Collections.emptyList();
        }
        if (badgeCase instanceof Collection) {
            return (Collection<?>) badgeCase;
        }
        Object result = invokeOptional(badgeCase, "getBadges");
        if (!(result instanceof Collection)) {
            result = invokeOptional(badgeCase, "getBadgeList");
        }
        if (!(result instanceof Collection)) {
            result = invokeOptional(badgeCase, "values");
        }
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        Object field = getFieldValue(badgeCase, "badges", "badgeList", "entries");
        if (field instanceof Collection) {
            return (Collection<?>) field;
        }
        if (field != null && field.getClass().isArray()) {
            return arrayToCollection(field);
        }
        return Collections.emptyList();
    }

    private static Collection<?> extractPartyCollection(Object partyStorage) {
        if (partyStorage == null) {
            return Collections.emptyList();
        }
        Object result = invokeOptional(partyStorage, "getTeam");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        result = invokeOptional(partyStorage, "getPokemonList");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        result = invokeOptional(partyStorage, "getPartyPokemon");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        result = invokeOptional(partyStorage, "getAll");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        result = invokeOptional(partyStorage, "getAllPokemon");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        result = invokeOptional(partyStorage, "getPokemons");
        if (result instanceof Collection) {
            return (Collection<?>) result;
        }
        if (result != null && result.getClass().isArray()) {
            return arrayToCollection(result);
        }
        Object field = getFieldValue(partyStorage, "partyPokemon", "pokemonList", "team", "party", "teamPokemon");
        if (field instanceof Collection) {
            return (Collection<?>) field;
        }
        if (field != null && field.getClass().isArray()) {
            return arrayToCollection(field);
        }
        return Collections.emptyList();
    }

    private static void collectPartySlots(Object partyStorage, List<Object> target) {
        for (int i = 0; i < 6; i++) {
            Object entry = invokeOptional(partyStorage, "getPokemon", i);
            if (entry == null) {
                entry = invokeOptional(partyStorage, "get", i);
            }
            if (entry == null) {
                entry = invokeOptional(partyStorage, "getTeamSlot", i);
            }
            if (entry == null) {
                entry = invokeOptional(partyStorage, "getSlot", i);
            }
            if (entry != null) {
                target.add(entry);
            }
        }
    }

    private static Collection<?> arrayToCollection(Object array) {
        int length = Array.getLength(array);
        List<Object> values = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            values.add(Array.get(array, i));
        }
        return values;
    }

    private static String identifyBadge(Object badgeObject) {
        if (badgeObject == null) {
            return null;
        }
        if (badgeObject instanceof String) {
            return badgeObject.toString();
        }
        if (badgeObject instanceof Enum<?>) {
            return ((Enum<?>) badgeObject).name();
        }
        Object name = invokeOptional(badgeObject, "getBadgeName");
        if (name == null) {
            name = invokeOptional(badgeObject, "getIdentifier");
        }
        if (name == null) {
            name = invokeOptional(badgeObject, "getName");
        }
        if (name == null) {
            name = invokeOptional(badgeObject, "getDisplayName");
        }
        if (name == null) {
            name = getFieldValue(badgeObject, "badgeName", "identifier", "name", "displayName");
        }
        return name == null ? badgeObject.toString() : name.toString();
    }

    private static Object invokeOptional(Object target, String methodName, Object... args) {
        if (target == null) {
            return null;
        }
        Method method = findMethod(target.getClass(), methodName, args);
        if (method == null) {
            return null;
        }
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }

    private static Method findMethod(Class<?> type, String methodName, Object... args) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(methodName)) {
                    continue;
                }
                if (method.getParameterCount() != args.length) {
                    continue;
                }
                Class<?>[] parameters = method.getParameterTypes();
                boolean matches = true;
                for (int i = 0; i < parameters.length; i++) {
                    if (args[i] != null && !wrap(parameters[i]).isInstance(args[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findCompatibleMethod(Class<?> type, String name, Class<?>... parameterHints) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if (!method.getName().equals(name)) {
                    continue;
                }
                Class<?>[] params = method.getParameterTypes();
                if (params.length != parameterHints.length) {
                    continue;
                }
                boolean matches = true;
                for (int i = 0; i < params.length; i++) {
                    if (!params[i].isAssignableFrom(parameterHints[i])) {
                        matches = false;
                        break;
                    }
                }
                if (matches) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Class<?> wrap(Class<?> clazz) {
        if (!clazz.isPrimitive()) {
            return clazz;
        }
        if (clazz == int.class) {
            return Integer.class;
        }
        if (clazz == float.class) {
            return Float.class;
        }
        if (clazz == double.class) {
            return Double.class;
        }
        if (clazz == long.class) {
            return Long.class;
        }
        if (clazz == boolean.class) {
            return Boolean.class;
        }
        if (clazz == short.class) {
            return Short.class;
        }
        if (clazz == byte.class) {
            return Byte.class;
        }
        if (clazz == char.class) {
            return Character.class;
        }
        return clazz;
    }

    private static Object getFieldValue(Object target, String... names) {
        if (target == null) {
            return null;
        }
        for (String name : names) {
            Field field = findField(target.getClass(), name);
            if (field != null) {
                try {
                    field.setAccessible(true);
                    return field.get(target);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        return null;
    }

    private static void ensureNpcClassesLoaded() {
        if (npcClassLookupAttempted) {
            return;
        }
        npcClassLookupAttempted = true;
        List<Class<?>> classes = new ArrayList<>();
        String[] candidates = new String[] {
                "com.pixelmonmod.pixelmon.entities.npcs.EntityNPC",
                "com.pixelmonmod.pixelmon.entities.npcs.NPCEntity",
                "com.pixelmonmod.pixelmon.entities.npcs.EntityNPCTrainer",
                "com.pixelmonmod.pixelmon.entities.npcs.EntityNPCBattle",
                "com.pixelmonmod.pixelmon.entities.npcs.trainers.EntityTrainer"
        };
        for (String className : candidates) {
            try {
                classes.add(Class.forName(className));
            } catch (ClassNotFoundException ignored) {
            }
        }
        npcBaseClasses = classes.isEmpty() ? null : classes.toArray(new Class<?>[0]);
    }

    private static String toText(Object value) {
        if (value instanceof ITextComponent) {
            return ((ITextComponent) value).getString();
        }
        return value == null ? null : value.toString();
    }
}
