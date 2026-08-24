package dev.axziom;

import dev.axziom.features.GuiMove;
import dev.axziom.manager.*;
import dev.axziom.util.BuildConfig;
import dev.axziom.util.TextUtil;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JewDust implements ModInitializer, ClientModInitializer {
    public static float TIMER = 1f;

    public static final Logger LOGGER = LogManager.getLogger("JewDust");
    public static ColorManager colorManager;
    public static PositionManager positionManager;
    public static EventManager eventManager;
    public static CommandManager commandManager;
    public static FriendManager friendManager;
    public static EnemyManager enemyManager;
    public static PlayerInfoManager playerInfoManager;
    public static ModuleManager moduleManager;
    public static ConfigManager configManager;
    public static PlacementManager placementManager;
    public static RotationManager rotationManager;
    public static SwapManager swapManager;
    public static TPSCounterService tpsCounterService;
    public static GuiMove guiMove;

    @Override
    public void onInitialize() {
        LOGGER.info("Pre-initializing {} v{}",
                BuildConfig.NAME, BuildConfig.VERSION);
        configManager = new ConfigManager();
        eventManager = new EventManager();
        positionManager = new PositionManager();
        friendManager = new FriendManager();
        enemyManager = new EnemyManager();
        playerInfoManager = new PlayerInfoManager();
        colorManager = new ColorManager();
        commandManager = new CommandManager();
        moduleManager = new ModuleManager();
        placementManager = new PlacementManager();
        rotationManager = new RotationManager();
        swapManager = new SwapManager();
        tpsCounterService = new TPSCounterService();
        guiMove = new GuiMove();

        TextUtil.init();
    }

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing {}", BuildConfig.NAME);

        long startTime = System.nanoTime();

        eventManager.init();
        rotationManager.init();
        swapManager.init();
        commandManager.init();
        moduleManager.init();
        friendManager.init();
        enemyManager.init();
        playerInfoManager.init();
        tpsCounterService.init();
        guiMove.init();

        configManager.load();
        if (!commandManager.isFunnyVisible()) {
            moduleManager.stream()
                    .filter(m -> m.getCategory() == dev.axziom.features.modules.Module.Category.FUNNY)
                    .filter(m -> m.isEnabled())
                    .forEach(dev.axziom.features.modules.Module::disable);
        }
        moduleManager.onLoad();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> configManager.save()));

        long endTime = System.nanoTime();

        LOGGER.info("Initialized {} in {}ms",
                BuildConfig.NAME, (endTime - startTime) / 1000000.0);
    }
}
