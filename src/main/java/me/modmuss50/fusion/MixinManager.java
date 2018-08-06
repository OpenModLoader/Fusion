package me.modmuss50.fusion;

import cpw.mods.modlauncher.ServiceLoaderStreamUtils;
import cpw.mods.modlauncher.api.ITransformer;
import me.modmuss50.fusion.api.IMixinProvider;
import me.modmuss50.fusion.api.Mixin;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;

public class MixinManager {

	public static List<String> mixinClassList = new ArrayList<>();
	public static HashMap<String, List<String>> mixinTargetMap = new HashMap<>();

	public static List<String> transformedClasses = new ArrayList<>();
	public static Logger logger = LogManager.getFormatterLogger("FusionMixin");

	public static void findMixins(IMixinProvider.IMixinEnvironment environment) {
		ServiceLoader<IMixinProvider> serviceLoader = ServiceLoader.load(IMixinProvider.class);
		ServiceLoaderStreamUtils.forEach(serviceLoader, iMixinProvider -> Arrays.stream(iMixinProvider.getMixins(environment)).forEach(MixinManager::registerMixin));
	}

	public static void registerMixin(String mixinClass) {
		try {
			Class cla = Class.forName(mixinClass);
			registerMixin(cla);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to find mixinclass", e);
		}
	}

	public static void registerMixin(Class mixinClass) {
		Mixin mixin = (Mixin) mixinClass.getAnnotation(Mixin.class);
		Validate.notNull(mixin);
		String target = mixin.value().getName();
		Validate.notNull(target);
		Validate.isTrue(!target.isEmpty());
		registerMixin(mixinClass.getName(), target);
	}

	public static void registerMixin(String mixinClass, String targetClass) {
		mixinClassList.add(mixinClass);
		if (mixinTargetMap.containsKey(targetClass)) {
			mixinTargetMap.get(targetClass).add(mixinClass);
		} else {
			List<String> list = new ArrayList<>();
			list.add(mixinClass);
			mixinTargetMap.put(targetClass, list);
		}
	}

	public static Set<ITransformer.Target> getAllTargets() {
		return mixinTargetMap.keySet().stream().map(ITransformer.Target::targetClass).collect(Collectors.toSet());
	}
}
