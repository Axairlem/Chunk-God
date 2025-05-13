package net.axairlem.cg;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Vector;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class ChunkStorage extends PersistentState {
    public static final HashMap<String, Vector<NbtCompound>> savedChunks = new HashMap<>();

    public static final PersistentState.Type<ChunkStorage> TYPE = new PersistentState.Type<>(
            (Supplier<ChunkStorage>)ChunkStorage::new,
            (BiFunction<NbtCompound, RegistryWrapper.WrapperLookup, ChunkStorage>)ChunkStorage::load,
            null
    );

    public static ChunkStorage get(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(TYPE, "chunkgod_chunks");
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList chunkList = new NbtList();

        for(var entry : savedChunks.entrySet()) {
            NbtCompound chunkData = new NbtCompound();
            chunkData.putString("chunkID", entry.getKey());

            NbtList blockList = new NbtList();
            blockList.addAll(entry.getValue());

            chunkData.put("blocks", blockList);
            chunkList.add(chunkData);
        }

        nbt.put("savedChunks", chunkList);
        return nbt;
    }

    public static ChunkStorage load(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        ChunkStorage storage = new ChunkStorage();

        NbtList chunkList = nbt.getList("savedChunks", NbtElement.COMPOUND_TYPE);
        for(NbtElement element : chunkList) {
            NbtCompound chunkData = (NbtCompound)element;
            String chunkID = chunkData.getString("chunkID");

            NbtList blockList = chunkData.getList("blocks", NbtElement.COMPOUND_TYPE);
            Vector<NbtCompound> blocks = new Vector<>();
            for(NbtElement block : blockList) {
                blocks.add((NbtCompound)block);
            }

            storage.savedChunks.put(chunkID, blocks);
        }

        return storage;
    }
}
