package com.example.lvlcap;

import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.PermissionAPI;

/**
 * Centralises permission node declarations for the mod's commands.
 */
public final class PermissionNodes {
    public static final String COMMAND_VIEW = "lvlcap.command.view";
    public static final String COMMAND_FAINT_SELF = "lvlcap.command.faint";
    public static final String COMMAND_FAINT_OTHERS = "lvlcap.command.faint.others";
    public static final String COMMAND_SET = "lvlcap.command.set";
    public static final String COMMAND_REMOVE = "lvlcap.command.remove";
    public static final String COMMAND_LIST = "lvlcap.command.list";
    public static final String COMMAND_SPAWN = "lvlcap.command.spawn";
    public static final String COMMAND_CREATE = "lvlcap.command.create";

    private PermissionNodes() {
    }

    public static void register() {
        PermissionAPI.registerNode(COMMAND_VIEW, DefaultPermissionLevel.ALL, "Allows using /lvlcap to view the cap.");
        PermissionAPI.registerNode(COMMAND_FAINT_SELF, DefaultPermissionLevel.ALL,
                "Allows using /lvlcap faint on yourself.");
        PermissionAPI.registerNode(COMMAND_FAINT_OTHERS, DefaultPermissionLevel.OP,
                "Allows using /lvlcap faint on other players.");
        PermissionAPI.registerNode(COMMAND_SET, DefaultPermissionLevel.OP,
                "Allows setting gym level caps with /lvlcap set.");
        PermissionAPI.registerNode(COMMAND_REMOVE, DefaultPermissionLevel.OP,
                "Allows removing gym level caps with /lvlcap remove.");
        PermissionAPI.registerNode(COMMAND_LIST, DefaultPermissionLevel.OP,
                "Allows listing configured caps with /lvlcap list.");
        PermissionAPI.registerNode(COMMAND_SPAWN, DefaultPermissionLevel.OP,
                "Allows spawning gym NPCs with /lvlcap spawn.");
        PermissionAPI.registerNode(COMMAND_CREATE, DefaultPermissionLevel.OP,
                "Allows creating gyms with /create gym.");
    }

    public static boolean canUse(CommandSource source, String node, int requiredLevel) {
        if (source.hasPermission(requiredLevel)) {
            return true;
        }

        final PlayerEntity player = source.getEntity() instanceof PlayerEntity
                ? (PlayerEntity) source.getEntity()
                : null;
        return player != null && PermissionAPI.hasPermission(player, node);
    }

    public static boolean canUse(CommandSource source, String node) {
        return canUse(source, node, 0);
    }
}
