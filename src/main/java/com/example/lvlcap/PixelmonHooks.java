package com.example.lvlcap;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.eventbus.api.Event;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

public final class PixelmonHooks {
    private PixelmonHooks() {
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
}
