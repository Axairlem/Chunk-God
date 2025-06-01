package net.axairlem.cg.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerMixin {
    @Accessor("saveDir")
    String getSaveDir();

    @Accessor("unloadedChunks")
    LongSet getUnloadedChunks();

    @Accessor("currentChunkHolders")
    Long2ObjectLinkedOpenHashMap<ChunkHolder> getCurrentChunkHolders();

    @Invoker("unloadChunks")
    void invokeUnloadChunks(BooleanSupplier shouldKeepTicking);

    @Invoker("tryUnloadChunk")
    void invokeTryUnloadChunk(long pos, ChunkHolder holder);
}
