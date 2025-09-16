package com.example.lvlcap;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ModConfigHolder {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue BASE_LEVEL_CAP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.push("level_cap");
        BASE_LEVEL_CAP = builder
                .comment("Default level cap for players that have not earned any gym badges.")
                .defineInRange("baseLevelCap", 20, 1, 1000);
        builder.pop();
        SPEC = builder.build();
    }

    private ModConfigHolder() {
    }

    public static int getBaseLevelCap() {
        return BASE_LEVEL_CAP.get();
    }
}
