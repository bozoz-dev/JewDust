package dev.axziom.manager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.axziom.JewDust;
import dev.axziom.event.impl.render.Render2DEvent;
import dev.axziom.event.impl.render.Render3DEvent;
import dev.axziom.features.Feature;
import dev.axziom.features.commands.ModuleCommand;
import dev.axziom.features.modules.Module;
import dev.axziom.features.modules.combat.*;
import dev.axziom.features.modules.player.*;
import dev.axziom.features.settings.Bind;
import dev.axziom.features.modules.client.ClickGuiModule;
import dev.axziom.features.modules.client.HudClientModule;
import dev.axziom.features.modules.client.NotificationsModule;
import dev.axziom.features.modules.client.TargetsModule;
import dev.axziom.features.modules.movement.SprintModule;
import dev.axziom.features.modules.movement.VelocityModule;
import dev.axziom.features.modules.movement.NoSlowModule;
import dev.axziom.features.modules.movement.ElytraAssistModule;
import dev.axziom.features.modules.movement.ElytraFlyModule;
import dev.axziom.features.modules.movement.ElytraDashModule;
import dev.axziom.features.modules.world.AutoPortalModule;
import dev.axziom.features.modules.world.FastPortalModule;
import dev.axziom.features.modules.world.BomberModule;
import dev.axziom.features.modules.world.SpeedMineModule;
import dev.axziom.features.modules.world.NukerModule;
import dev.axziom.features.modules.world.ScaffoldModule;
import dev.axziom.features.modules.render.BlockEspModule;
import dev.axziom.features.modules.render.BlockHighlightModule;
import dev.axziom.features.modules.render.BreadcrumbsModule;
import dev.axziom.features.modules.render.BreakIndicatorsModule;
import dev.axziom.features.modules.render.CrystalHandModule;
import dev.axziom.features.modules.render.SearchModule;
import dev.axziom.features.modules.render.ShadersModule;
import dev.axziom.features.modules.render.SkyboxModule;
import dev.axziom.features.modules.render.SeeThroughModule;
import dev.axziom.features.modules.render.FullbrightModule;
import dev.axziom.features.modules.render.LogoutSpotsModule;
import dev.axziom.features.modules.render.NametagsModule;
import dev.axziom.features.modules.render.TablistModule;
import dev.axziom.features.modules.render.NoRenderModule;
import dev.axziom.features.modules.render.PopEffectsModule;
import dev.axziom.features.modules.render.ShulkerPreviewModule;
import dev.axziom.features.modules.render.QuadSupremeModule;
import dev.axziom.features.modules.render.ViewModelModule;
import dev.axziom.features.modules.movement.ElytraSwap;
import dev.axziom.features.modules.movement.Pitch40;
import dev.axziom.features.modules.movement.RocketBoost;
import dev.axziom.features.modules.movement.YawLockModule;
import dev.axziom.features.modules.player.FreeLookModule;
import dev.axziom.features.modules.player.FreecamModule;
import dev.axziom.features.modules.render.StorageEspModule;
import dev.axziom.features.modules.render.SwingModule;
import dev.axziom.features.modules.render.TracersModule;
import dev.axziom.features.modules.world.ActivatedSpawnerDetectorModule;
import dev.axziom.features.modules.world.BrokenPortalEspModule;
import dev.axziom.features.modules.world.DubCount;
import dev.axziom.features.modules.world.PortalSkipDetector;
import dev.axziom.features.modules.world.PrinterModule;
import dev.axziom.features.modules.world.StashFinder;
import dev.axziom.features.modules.world.WeirdBlockEspModule;
import dev.axziom.features.modules.world.searcharea.SearchAreaModule;
import dev.axziom.util.traits.Jsonable;
import dev.axziom.util.traits.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Stream;

public class ModuleManager implements Jsonable, Util {
    private static final Logger LOGGER = LoggerFactory.getLogger("ModuleManager");

    private final Map<Class<? extends Module>, Module> fastRegistry = new HashMap<>();
    private final List<Module> modules = new ArrayList<>();

    public void init() {
        register(new HudClientModule());
        register(new ClickGuiModule());
        register(new NotificationsModule());
        register(new TargetsModule());
        register(new AutoTrapModule());
        register(new AutoTotemModule());
        register(new SwordGapModule());
        register(new AutoLogModule());
        register(new AutoSwordModule());
        register(new AutoCrystalModule());
        register(new AutoAnchorModule());
        register(new AutoMineModule());
        register(new AutoXPModule());
        register(new VelocityModule());
        register(new SprintModule());
        register(new ElytraAssistModule());
        register(new ElytraFlyModule());
        register(new ElytraDashModule());
        register(new NoSlowModule());

        register(new AutoPortalModule());
        register(new FastPortalModule());
        register(new FastuseModule());
        register(new BlockHighlightModule());
        register(new BreadcrumbsModule());
        register(new BreakIndicatorsModule());
        register(new CrystalHandModule());
        register(new FullbrightModule());
        register(new NoRenderModule());
        register(new ShadersModule());
        register(new SkyboxModule());
        register(new SeeThroughModule());
        register(new NametagsModule());
        register(new TablistModule());
        register(new LogoutSpotsModule());
        register(new PopEffectsModule());
        register(new ShulkerPreviewModule());
        register(new QuadSupremeModule());
        register(new ViewModelModule());
        register(new MiddleClickExtraModule());
        register(new KeyPotionModule());
        register(new SurroundModule());
        register(new PistonCrystalModule());
        register(new PearlBlockerModule());
        register(new PhaseModule());
        register(new BomberModule());
        register(new SpeedMineModule());
        register(new NukerModule());
        register(new ScaffoldModule());
        register(new ReplenishModule());
        register(new InstantRekitModule());
        register(new NoRotateModule());
        register(new ActivatedSpawnerDetectorModule());
        register(new BrokenPortalEspModule());
        register(new PortalSkipDetector());
        register(new StashFinder());
        register(new SearchAreaModule());
        register(new WeirdBlockEspModule());
        register(new DubCount());
        register(new ElytraSwap());
        register(new Pitch40());
        register(new RocketBoost());
        register(new YawLockModule());
        register(new FreeLookModule());
        register(new FreecamModule());
        register(new StorageEspModule());
        register(new SwingModule());
        register(new TracersModule());
        register(new BlockEspModule());
        register(new SearchModule());
        register(new PrinterModule());
        register(new CrimeStopperModule());

        LOGGER.info("Registered {} modules", modules.size());

        for (Module module : modules) {
            JewDust.commandManager.register(new ModuleCommand(module));
        }

        JewDust.configManager.addConfig(this);
    }

    public void register(Module module) {
        getModules().add(module);
        fastRegistry.put(module.getClass(), module);
    }

    public List<Module> getModules() {
        return modules;
    }

    public Stream<Module> stream() {
        return getModules().stream();
    }

    @SuppressWarnings("unchecked")
    public <T extends Module> T getModuleByClass(Class<T> clazz) {
        return (T) fastRegistry.get(clazz);
    }

    public Module getModuleByName(String name) {
        return stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    public Module getModuleByDisplayName(String display) {
        return stream().filter(m -> m.getDisplayName().equalsIgnoreCase(display)).findFirst().orElse(null);
    }

    public List<Module> getModulesByCategory(Module.Category category) {
        return stream().filter(m -> m.getCategory() == category).toList();
    }

    public List<Module.Category> getCategories() {
        return Arrays.asList(Module.Category.values());
    }

    public void onLoad() {
        getModules().forEach(Module::onLoad);
    }

    public void onTick() {
        stream().filter(Feature::isEnabled).forEach(Module::onTick);
        stream().filter(m -> m.isEnabled()
                        && m.getBindMode() == Module.BindMode.HOLD
                        && !m.getBind().isDown())
                .toList()
                .forEach(Module::disable);
    }

    public void onRender2D(Render2DEvent event) {
        stream().filter(Feature::isEnabled).forEach(module -> module.onRender2D(event));
    }

    public void onRender3D(Render3DEvent event) {
        stream().filter(Feature::isEnabled).forEach(module -> module.onRender3D(event));
    }

    public void onUnload() {
        getModules().forEach(EVENT_BUS::unregister);
        getModules().forEach(Module::onUnload);
    }

    public void onKeyPressed(int key) {
        if (key <= 0 || mc.screen != null) return;
        stream().filter(module -> module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
                if (!module.isEnabled()) module.enable();
            } else {
                module.toggle();
            }
        });
    }

    public void onKeyReleased(int key) {
        if (key <= 0) return;
        stream().filter(module -> module.getBind().getKey() == key
                        && module.getBindMode() == Module.BindMode.HOLD
                        && module.isEnabled())
                .forEach(Module::disable);
    }

    public void onMousePressed(int button) {
        if (mc.screen != null) return;
        int key = Bind.MOUSE_BUTTON_OFFSET + button;
        stream().filter(module -> module.getBind().getKey() == key).forEach(module -> {
            if (module.getBindMode() == Module.BindMode.HOLD) {
                if (!module.isEnabled()) module.enable();
            } else {
                module.toggle();
            }
        });
    }

    public void onMouseReleased(int button) {
        int key = Bind.MOUSE_BUTTON_OFFSET + button;
        stream().filter(module -> module.getBind().getKey() == key
                        && module.getBindMode() == Module.BindMode.HOLD
                        && module.isEnabled())
                .forEach(Module::disable);
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        for (Module module : getModules()) {
            object.add(module.getName(), module.toJson());
        }
        return object;
    }

    @Override
    public void fromJson(JsonElement element) {
        for (Module module : getModules()) {
            try {
                module.fromJson(element.getAsJsonObject().get(module.getName()));
            } catch (Throwable e) {
                LOGGER.error("Failed to load module {}", module.getName(), e);
            }
        }
    }

    @Override
    public String getFileName() {
        return "modules.json";
    }
}
