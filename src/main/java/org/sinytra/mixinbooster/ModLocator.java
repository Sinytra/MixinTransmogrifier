package org.sinytra.mixinbooster;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class ModLocator extends AbstractJarFileModLocator {
    @Override
    public Stream<Path> scanCandidates() {
        if (!MixinTransformationService.SHOULD_LOAD.get()) {
            return Stream.empty();
        }
        return Stream.of(InstrumentationHack.SELF_PATH.resolve("transmog-mod.jar"));
    }

    @Override
    public String name() {
        return "mixin-booster-locator-" + getClass().getPackageName().replace('.', '-');
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {

    }
}
