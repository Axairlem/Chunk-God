package net.axairlem.cg;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.joml.Vector2i;

import java.util.HashMap;
import java.util.Vector;

public class ChunkStorage extends PersistentState {

    public static HashMap<String, Vector<NbtCompound>> savedChunks = new HashMap<String, Vector<NbtCompound>>();
    public static HashMap<Vector2i, Vector<BlockPos>> chunksToRegen = new HashMap<Vector2i, Vector<BlockPos>>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList chunkList = new NbtList();

        for(var entry : savedChunks.entrySet()) {
            NbtCompound chunkData = new NbtCompound();
            chunkData.putString("chunkID", entry.getKey());

            NbtList blockList = new NbtList();
            blockList.addAll(entry.getValue()); // adds the full list

            chunkData.put("blocks", blockList);
            chunkList.add(chunkData);
        }

        nbt.put("savedChunks", chunkList);
        return nbt;
    }
    public static ChunkStorage createFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ChunkStorage storage = new ChunkStorage();

        NbtList chunkList = tag.getList("savedChunks", NbtElement.COMPOUND_TYPE);
        for(NbtElement entry : chunkList) {
            String chunkID = ((NbtCompound)entry).getString("chunkID");
            NbtList blockList = ((NbtCompound)entry).getList("blocks", NbtElement.COMPOUND_TYPE);

            Vector<NbtCompound> blocks = new Vector<>();
            for(var b : blockList) {
                blocks.add((NbtCompound)b);
            }

            storage.savedChunks.put(chunkID, blocks);
        }

        return storage;
    }
    public static ChunkStorage createNew(){
        ChunkStorage storage = new ChunkStorage();
        storage.savedChunks = new HashMap<>();
        return storage;
    }


    private static final Type<ChunkStorage> type = new Type<>(
            ChunkStorage::createNew,
            ChunkStorage::createFromNbt,
            null
    );
    public static ChunkStorage getServerState(MinecraftServer server) {
        ServerWorld serverWorld = server.getWorld(World.OVERWORLD);
        assert serverWorld != null;

        ChunkStorage storage = serverWorld.getPersistentStateManager().getOrCreate(type, ChunkNABox.MOD_ID);

        storage.markDirty();

        return storage;
    }
}
