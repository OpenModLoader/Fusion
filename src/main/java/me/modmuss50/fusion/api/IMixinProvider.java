package me.modmuss50.fusion.api;

import java.util.Map;

public interface IMixinProvider {

	Class[] getMixins(Map<String, String> environmentData);

}
