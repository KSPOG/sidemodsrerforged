package com.example.lvlcap;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerAboutToStartEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(LevelCapMod.MOD_ID)
public class LevelCapMod {
    public static final String MOD_ID = "lvlcap";
    public static final Logger LOGGER = LogManager.getLogger();

    private final PixelmonEventHandler pixelmonEventHandler = new PixelmonEventHandler();

    public LevelCapMod() {
        ModLoadingContext.get().registerConfig(net.minecraftforge.fml.config.ModConfig.Type.SERVER, ModConfigHolder.SPEC);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onConfigLoading);
        modEventBus.addListener(this::onConfigReloading);

        MinecraftForge.EVENT_BUS.register(pixelmonEventHandler);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerAboutToStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);

        LevelCapManager.initialize(FMLPaths.CONFIGDIR.get());
    }

    private void onCommonSetup(final FMLCommonSetupEvent event) {
        LevelCapMod.LOGGER.info("Pixelmon Level Cap mod initialising");
    }

    private void onConfigLoading(final ModConfig.Loading event) {
        if (event.getConfig().getSpec() == ModConfigHolder.SPEC) {
            LevelCapManager.reload();
        }
    }

    private void onConfigReloading(final ModConfig.Reloading event) {
        if (event.getConfig().getSpec() == ModConfigHolder.SPEC) {
            LevelCapManager.reload();
        }
    }

    private void onRegisterCommands(final RegisterCommandsEvent event) {
        LevelCapCommands.register(event.getDispatcher());
    }

    private void onServerAboutToStart(final FMLServerAboutToStartEvent event) {
        LevelCapManager.reload();
    }

    private void onServerStarting(final FMLServerStartingEvent event) {
        LevelCapManager.reload();
    }

    private void onServerStopping(final FMLServerStoppingEvent event) {
        LevelCapManager.saveConfig();
    }
}
