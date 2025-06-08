package net.axairlem.cg;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Vector;

public class ChunkStorage extends PersistentState {

    public HashMap<String, Vector<NbtCompound>> savedBlocks = new HashMap<String, Vector<NbtCompound>>();
    public HashMap<String, Vector<ChunkPos>> chunksToRegen = new HashMap<String, Vector<ChunkPos>>();
    public HashMap<String, ArrayList<NbtCompound>> savedEntities = new HashMap<String, ArrayList<NbtCompound>>();

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {

        // PUT "savedBlocks" IN PERSISTENT STORAGE
        NbtList blockList = new NbtList();
        for(var entry : savedBlocks.entrySet()) {
            NbtCompound blockData = new NbtCompound();
            blockData.putString("itemID", entry.getKey());

            NbtList blocks = new NbtList();
            blocks.addAll(entry.getValue()); // adds the full list
            blockData.put("blocks", blocks);

            blockList.add(blockData);
        }
        nbt.put("savedBlocks", blockList);

        // PUT "chunksToRegen" IN PERSISTENT STORAGE
        NbtList chunkList = new NbtList();
        for(var entry : chunksToRegen.entrySet()) {
            NbtCompound chunkData = new NbtCompound();
            chunkData.putString("itemID", entry.getKey());

            NbtList chunks = new NbtList();
            for(ChunkPos chunkPos : entry.getValue()) {
                NbtCompound chunkCompound = new NbtCompound();
                chunkCompound.putInt("chunkPosX", chunkPos.x);
                chunkCompound.putInt("chunkPosZ", chunkPos.z);
                chunks.add(chunkCompound);
            }
            chunkData.put("chunks", chunks);

            chunkList.add(chunkData);
        }
        nbt.put("chunksToRegen", chunkList);

        // PUT "savedEntities" IN PERSISTENT STORAGE
        NbtList entitiesList = new NbtList();
        for(var entry : savedEntities.entrySet()) {
            NbtCompound entityData = new NbtCompound();
            entityData.putString("itemID", entry.getKey());

            NbtList entities = new NbtList();
            entities.addAll(entry.getValue());
            entityData.put("entities", entities);

            entitiesList.add(entityData);
        }
        nbt.put("savedEntities", entitiesList);

        return nbt;
    }
    public static ChunkStorage createFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        ChunkStorage storage = new ChunkStorage();

        // ADD "savedBlocks" FROM PERSISTENT STORAGE
        NbtList blockList = tag.getList("savedBlocks", NbtElement.COMPOUND_TYPE);
        for(NbtElement entry : blockList) {
            String itemID = ((NbtCompound)entry).getString("itemID");

            NbtList blocks = ((NbtCompound)entry).getList("blocks", NbtElement.COMPOUND_TYPE);
            Vector<NbtCompound> savedBlocks = new Vector<NbtCompound>();
            for(var block : blocks) {
                savedBlocks.add((NbtCompound)block);
            }

            storage.savedBlocks.put(itemID, savedBlocks);
        }

        // ADD "chunksToRegen" FROM PERSISTENT STORAGE
        NbtList chunkList = tag.getList("chunksToRegen", NbtElement.COMPOUND_TYPE);
        for(NbtElement entry : chunkList) {
            String itemID = ((NbtCompound)entry).getString("itemID");

            NbtList chunks = ((NbtCompound)entry).getList("chunks", NbtElement.COMPOUND_TYPE);
            Vector<ChunkPos> chunksToRegen = new Vector<ChunkPos>();
            for(var chunkCompound : chunks){
                chunksToRegen.add( new ChunkPos(((NbtCompound)chunkCompound).getInt("chunkPosX"), ((NbtCompound)chunkCompound).getInt("chunkPosZ")) );
            }

            storage.chunksToRegen.put(itemID, chunksToRegen);
        }

        // ADD "savedEntities" FROM PERSISTENT STORAGE
        NbtList entitiesList = tag.getList("savedEntities", NbtElement.COMPOUND_TYPE);
        for(NbtElement entry : entitiesList) {
            String itemID = ((NbtCompound)entry).getString("itemID");

            NbtList entities = ((NbtCompound)entry).getList("entities", NbtElement.COMPOUND_TYPE);
            ArrayList<NbtCompound> savedEntities = new ArrayList<>();
            for(NbtElement entity : entities) {
                savedEntities.add((NbtCompound)entity);
            }

            storage.savedEntities.put(itemID, savedEntities);
        }

        return storage;
    }
    public static ChunkStorage createNew(){
        ChunkStorage storage = new ChunkStorage();
        storage.savedBlocks = new HashMap<>();
        storage.chunksToRegen = new HashMap<>();
        storage.savedEntities = new HashMap<>();
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
