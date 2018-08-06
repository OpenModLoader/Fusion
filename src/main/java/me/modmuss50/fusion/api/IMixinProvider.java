package me.modmuss50.fusion.api;

import net.fabricmc.api.Side;

public interface IMixinProvider {

	Class[] getMixins(IMixinEnvironment environment);

	interface IMixinEnvironment {
		Side side();
	}
}
