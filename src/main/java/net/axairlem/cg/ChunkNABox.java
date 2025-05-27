package net.axairlem.cg;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class ChunkNABox implements ModInitializer {

	public static final String MOD_ID = "chunk-god";

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			TaskQueue.tick();
		});
	}
}