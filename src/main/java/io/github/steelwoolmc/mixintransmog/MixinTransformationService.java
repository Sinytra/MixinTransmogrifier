package io.github.steelwoolmc.mixintransmog;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.TransformationServiceDecorator;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.MixinLaunchPlugin;
import org.spongepowered.asm.launch.MixinLaunchPluginLegacy;
import org.spongepowered.asm.mixin.MixinEnvironment;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.steelwoolmc.mixintransmog.Constants.LOG;

public class MixinTransformationService implements ITransformationService {
    public static final TypesafeMap.Key<Map<Class<?>, ArtifactVersion>> INSTALLED_VERSIONS =
            new TypesafeMap.KeyBuilder<Map<Class<?>, ArtifactVersion>>("org.sinytra.mixintransmog.installed_versions", Map.class, IEnvironment.class).get();

    /**
     * Replace the original mixin launch plugin
     */
    private static void replaceMixinLaunchPlugin() {
        try {
            // In production, mixin transmogrifier is loaded from the SERVICE layer and mixin requires access to its classes
            // in order to function properly, so we change the context classloader from BOOT to SERVICE accordingly.
            // Shouldn't break anything thanks to module layer inheritance
            // @see org.spongepowered.asm.service.modlauncher.ModLauncherClassProvider
            var classLoader = MixinTransformationService.class.getClassLoader();
            Thread.currentThread().setContextClassLoader(classLoader);

            // Use reflection to get the loaded launch plugins
            var launcherLaunchPluginsField = Launcher.class.getDeclaredField("launchPlugins");
            launcherLaunchPluginsField.setAccessible(true);
            var launchPluginHandlerPluginsField = LaunchPluginHandler.class.getDeclaredField("plugins");
            launchPluginHandlerPluginsField.setAccessible(true);
            Map<String, ILaunchPluginService> plugins = (Map) launchPluginHandlerPluginsField.get(launcherLaunchPluginsField.get(Launcher.INSTANCE));

            // Replace original mixin with our mixin
            plugins.put("mixin", new MixinLaunchPlugin());
            LOG.debug("Replaced the mixin launch plugin");
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private final AtomicBoolean shouldLoad = new AtomicBoolean(false);

    public MixinTransformationService() {
        final var env = Launcher.INSTANCE.environment();
        final var installed = env.computePropertyIfAbsent(INSTALLED_VERSIONS, k -> Collections.synchronizedMap(new LinkedHashMap<>()));
        installed.put(getClass(), Constants.VERSION);
    }

    @Override
    public String name() {
        return "mixin-transmogrifier-" + getClass().getPackageName().replace('.', '-');
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
        final var winner = env.getProperty(INSTALLED_VERSIONS).orElseThrow()
                .entrySet()
                .stream().sorted(Map.Entry.<Class<?>, ArtifactVersion>comparingByValue().reversed())
                .findFirst()
                .orElseThrow();
        if (winner.getKey() != getClass()) {
            LOG.info("Mixin Transmogrifier {} ({}) lost against version {} ({}). Skipping...", Constants.VERSION, getClass(), winner.getValue(), winner.getKey());
            return;
        }
        shouldLoad.set(true);

        LOG.info("Mixin Transmogrifier {} is definitely up to no good...", getClass().getName());
        try {
            InstrumentationHack.inject();
        } catch (Throwable t) {
            LOG.error("Error replacing mixin module source", t);
            throw new RuntimeException(t);
        }
        replaceMixinLaunchPlugin();
        LOG.info("Crimes against java were committed by {}", getClass().getName());

        LOG.debug("onLoad called");
        LOG.debug(String.join(", ", otherServices));

        try {
            Field handlerField = Launcher.class.getDeclaredField("transformationServicesHandler");
            handlerField.setAccessible(true);
            Object handler = handlerField.get(Launcher.INSTANCE);
            Field serviceLookupField = handler.getClass().getDeclaredField("serviceLookup");
            serviceLookupField.setAccessible(true);
            Map<String, TransformationServiceDecorator> serviceLookup = (Map) serviceLookupField.get(handler);
            Constructor<TransformationServiceDecorator> ctr = TransformationServiceDecorator.class.getDeclaredConstructor(ITransformationService.class);
            ctr.setAccessible(true);
            TransformationServiceDecorator decorator = ctr.newInstance(new DummyMixinTransformationService());
            Method onLoad = TransformationServiceDecorator.class.getDeclaredMethod("onLoad", IEnvironment.class, Set.class);
            onLoad.setAccessible(true);
            onLoad.invoke(decorator, env, otherServices);
            // Silently replace service, avoiding a ConcurrentModificationException
            serviceLookup.put("mixin", decorator);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @Override
    public void initialize(IEnvironment environment) {
        if (!shouldLoad.get()) return;

        try {
            LOG.debug("initialize called");

            var mixinBootstrapStartMethod = MixinBootstrap.class.getDeclaredMethod("start");
            mixinBootstrapStartMethod.setAccessible(true);

            Optional<ILaunchPluginService> plugin = environment.findLaunchPlugin("mixin");
            if (plugin.isEmpty()) {
                throw new Error("Mixin Launch Plugin Service could not be located");
            }
            ILaunchPluginService launchPlugin = plugin.get();
            if (!(launchPlugin instanceof MixinLaunchPluginLegacy)) {
                throw new Error("Mixin Launch Plugin Service is present but not compatible");
            }

            var mixinPluginInitMethod = MixinLaunchPluginLegacy.class.getDeclaredMethod("init", IEnvironment.class, List.class);
            mixinPluginInitMethod.setAccessible(true);

            // The actual init invocations
            mixinBootstrapStartMethod.invoke(null);
            mixinPluginInitMethod.invoke(launchPlugin, environment, List.of());
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        if (!shouldLoad.get()) return List.of();

        // Add mixin remapper after the naming service has been initialized
        if (!FMLEnvironment.production) {
            MixinEnvironment.getDefaultEnvironment().getRemappers().add(new MixinModlauncherRemapper());
        }
        return List.of(new Resource(IModuleLayerManager.Layer.GAME, List.of(new GeneratedMixinClassesSecureJar())));
    }

    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
