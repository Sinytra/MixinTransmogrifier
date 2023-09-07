package io.github.steelwoolmc.mixintransmog;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.steelwoolmc.mixintransmog.Constants.LOG;

/**
 * A launch plugin that transforms mod mixin classes to refer to shaded mixin rather than the original
 * (i.e. `org.spongepowered` -> `shadow.spongepowered`)
 */
public class ShadedMixinPluginService implements ILaunchPluginService {
	@Override
	public String name() {
		return "mixin-transmogrifier";
	}

	@Override
	public EnumSet<Phase> handlesClass(Type classType, boolean isEmpty) {
		return EnumSet.of(Phase.BEFORE);
	}

	@Override
	public boolean processClass(final Phase phase, ClassNode classNode, final Type classType, String reason) {
		if (phase != Phase.BEFORE) {
			return false;
		}

		LOG.trace("Processing mixin class: " + classNode.name);
		ClassNode duplicateNode = new ClassNode();
		AtomicBoolean hasMapped = new AtomicBoolean(false);
		ClassRemapper remapper = new ClassRemapper(duplicateNode, new Remapper() {
			@Override
			public String map(String internalName) {
				if (internalName.startsWith("shadowignore/org/spongepowered")) {
					hasMapped.set(true);
					return "org/spongepowered" + internalName.substring("shadowignore/org/spongepowered".length());
				}
				return super.map(internalName);
			}

			@Override
			public Object mapValue(Object value) {
				if (value instanceof String str && str.startsWith("shadowignore.org.spongepowered")) {
					hasMapped.set(true);
					return "org.spongepowered" + str.substring("shadowignore.org.spongepowered".length());
				}
				return super.mapValue(value);
			}
		});
		classNode.accept(remapper);
		if (!hasMapped.get()) {
			return false;
		}

		classNode.version = duplicateNode.version;
		classNode.access = duplicateNode.access;
		classNode.name = duplicateNode.name;
		classNode.signature = duplicateNode.signature;
		classNode.superName = duplicateNode.superName;
		classNode.interfaces = duplicateNode.interfaces;
		classNode.sourceFile = duplicateNode.sourceFile;
		classNode.sourceDebug = duplicateNode.sourceDebug;
		classNode.module = duplicateNode.module;
		classNode.outerClass = duplicateNode.outerClass;
		classNode.outerMethod = duplicateNode.outerMethod;
		classNode.outerMethodDesc = duplicateNode.outerMethodDesc;
		classNode.visibleAnnotations = duplicateNode.visibleAnnotations;
		classNode.invisibleAnnotations = duplicateNode.invisibleAnnotations;
		classNode.visibleTypeAnnotations = duplicateNode.visibleTypeAnnotations;
		classNode.invisibleTypeAnnotations = duplicateNode.invisibleTypeAnnotations;
		classNode.attrs = duplicateNode.attrs;
		classNode.innerClasses = duplicateNode.innerClasses;
		classNode.nestHostClass = duplicateNode.nestHostClass;
		classNode.nestMembers = duplicateNode.nestMembers;
		classNode.permittedSubclasses = duplicateNode.permittedSubclasses;
		classNode.recordComponents = duplicateNode.recordComponents;
		classNode.fields = duplicateNode.fields;
		classNode.methods = duplicateNode.methods;

		ClassWriter writer = new ClassWriter(0);
		classNode.accept(writer);

		return true;
	}

	@Override
	public int processClassWithFlags(final Phase phase, ClassNode classNode, final Type classType, String reason) {
		// We only touch type names; no need to recompute anything - use SIMPLE_REWRITE instead of one of the recompute ones
		return processClass(phase, classNode, classType, reason) ? ComputeFlags.SIMPLE_REWRITE : ComputeFlags.NO_REWRITE;
	}
}
