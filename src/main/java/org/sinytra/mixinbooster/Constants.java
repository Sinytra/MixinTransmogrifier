package org.sinytra.mixinbooster;

import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.nio.file.Files;

public class Constants {
	public static final Logger LOG = LogManager.getLogger("mixin-booster");
	public static final ArtifactVersion VERSION = new DefaultArtifactVersion(LamdbaExceptionUtils.uncheck(() ->
			Files.readString(InstrumentationHack.SELF_PATH.resolve("mixinbooster_version.txt")).trim()));
}
