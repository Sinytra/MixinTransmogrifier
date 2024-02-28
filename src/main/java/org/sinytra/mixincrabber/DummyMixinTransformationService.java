package org.sinytra.mixincrabber;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;

import java.util.List;
import java.util.Set;

public class DummyMixinTransformationService implements ITransformationService {
    @Override
    public String name() {
        return "mixin";
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {}

    @Override
    public void initialize(IEnvironment environment) {
        Constants.LOG.info("Original mixin transformation service successfully crobbed by mixin-transmogrifier!");
    }

    @Override
    public List<ITransformer> transformers() {
        return List.of();
    }
}
