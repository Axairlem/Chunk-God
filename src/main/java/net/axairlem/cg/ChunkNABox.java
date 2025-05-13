package net.axairlem.cg;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class ChunkNABox implements ModInitializer {
	public static final String MOD_ID = "chunk-na-box";

	@Override
	public void onInitialize() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ChunkStorage.clear();
		});
	}
}