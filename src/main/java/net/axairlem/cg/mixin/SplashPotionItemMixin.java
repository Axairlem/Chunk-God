package net.axairlem.cg.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.axairlem.cg.ChunkStorage;
import net.axairlem.cg.TaskQueue;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.*;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Vector;

@Mixin(PotionEntity.class)
public class SplashPotionItemMixin {

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollide(HitResult hit, CallbackInfo ci) throws IOException {

        PotionEntity self = (PotionEntity)(Object)this;

        World world = ((Entity)self).getWorld();
        if(world.isClient) return;

        ItemStack itemStack = self.getStack();

        if(itemStack.getItem() == Items.SPLASH_POTION) {

            BlockPos hitPos = new BlockPos(
                    (int)hit.getPos().x,
                    (int)hit.getPos().y,
                    (int)hit.getPos().z
            );

            // CHECK FOR EXISTING CHUNK DATA IN PERSISTENT STORAGE
            Text itemNameText = itemStack.get(DataComponentTypes.ITEM_NAME);
            if(itemNameText != null){
                String itemID = itemNameText.toString();
                itemID = itemID.substring(itemID.indexOf('[') + 1, itemID.indexOf(']'));
                self.sendMessage(Text.of(itemID));

                MinecraftServer server = world.getServer();
                assert server != null;
                ChunkStorage serverStorage = ChunkStorage.getServerState(server);
                Vector<NbtCompound> blocks = serverStorage.savedChunks.get(itemID);
                if(serverStorage.savedChunks.containsKey(itemID) ) {

                    // PASTE CHUNK
                    for(NbtCompound entry : blocks) {
                        BlockPos pos = new BlockPos(
                                hitPos.getX() + entry.getInt("posX"),
                                entry.getInt("posY"),
                                hitPos.getZ() + entry.getInt("posZ")
                        );
                        Identifier id = Identifier.of(entry.getString("state"));
                        world.setBlockState(pos, Registries.BLOCK.get(id).getDefaultState(), 2);
                    }
                    Entity owner = self.getOwner();
                    if(owner instanceof PlayerEntity player){
                        player.sendMessage(Text.literal("Blocks have been pasted successfully"));
                    }

                    // REGENERATE OLD CHUNK(S) -- On new thread? --
                    for(Map.Entry<Vector2i, Vector<BlockPos>> entry : serverStorage.chunksToRegen.entrySet()){
                        ServerWorld serverWorld = server.getWorld(World.OVERWORLD);
                        ChunkPos chunkPos = new ChunkPos(entry.getKey().x, entry.getKey().y);
                        ServerChunkLoadingManager loadingManager = serverWorld.getChunkManager().chunkLoadingManager;
                        long chunkPosLong = chunkPos.toLong();

//                        serverWorld.setChunkForced(chunkPos.x, chunkPos.z, false);

                        TaskQueue.add(new Runnable() {
                            @Override
                            public void run() {

                                // UNFORCE AND REMOVE TICKETS

//                                ((ChunkTicketManagerMixin)loadingManager.getTicketManager()).invokePurge();

//                                ((ServerChunkLoadingManagerMixin)loadingManager).invokeUnloadChunks(() -> true);

                                ///if (serverWorld.getChunkManager().isChunkLoaded(chunkPos.x, chunkPos.z)) {
                                    //owner.sendMessage(Text.literal("Chunk was unloaded successfully!"));

                                    try {
                                        NbtScannable ioWorkerNbt = serverWorld.getChunkManager().getChunkIoWorker();

                                        Field storageField = ioWorkerNbt.getClass().getDeclaredField("storage");
                                        storageField.setAccessible(true);
                                        Object regionBasedStorage = storageField.get(ioWorkerNbt);

                                        Field storageKeyField = regionBasedStorage.getClass().getDeclaredField("storageKey");
                                        storageKeyField.setAccessible(true);
                                        StorageKey storageKey = (StorageKey) storageKeyField.get(regionBasedStorage);

                                        // REMOVE CHUNK HOLDER
                                        Field chunkHolderMapField = ServerChunkLoadingManager.class.getDeclaredField("currentChunkHolders");
                                        chunkHolderMapField.setAccessible(true);
                                        Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = (Long2ObjectLinkedOpenHashMap<ChunkHolder>) chunkHolderMapField.get(loadingManager);
                                        ChunkHolder holder = holders.get(chunkPosLong);
                                        if(holder == null) {
                                            owner.sendMessage(Text.literal("Chunk holder is NULL, retrying..."));
                                            TaskQueue.add(this, 2);
                                            return;
                                        }
                                        holders.remove(chunkPosLong);
                                        loadingManager.getTicketManager().removeTicket(ChunkTicketType.FORCED, chunkPos, 32, chunkPos);
                                        loadingManager.getTicketManager().removeTicket(ChunkTicketType.PLAYER, chunkPos, 32, chunkPos);
                                        loadingManager.getTicketManager().removeTicket(ChunkTicketType.UNKNOWN, chunkPos, 32, chunkPos);
                                        loadingManager.getTicketManager().removeTicket(ChunkTicketType.START, chunkPos, 32, Unit.INSTANCE);

                                        // CLEAR REGION CACHE
                                        Field cachedRegionFilesField = regionBasedStorage.getClass().getDeclaredField("cachedRegionFiles");
                                        cachedRegionFilesField.setAccessible(true);
                                        Long2ObjectLinkedOpenHashMap<RegionFile> regionsCache = (Long2ObjectLinkedOpenHashMap<RegionFile>) cachedRegionFilesField.get(regionBasedStorage);
                                        regionsCache.clear();

                                        // DELETE CHUNK FROM .mca FILE
                                        String saveDir = loadingManager.getSaveDir();
                                        Path folderPath = Paths.get(saveDir, "region");
                                        Path regionPath = folderPath.resolve(String.format("r.%d.%d.mca", chunkPos.x >> 5, chunkPos.z >> 5));
                                        RegionFile region = new RegionFile(storageKey, regionPath, folderPath, true);
                                        if (region.hasChunk(chunkPos)) {
                                            region.delete(chunkPos);
                                            //owner.sendMessage(Text.literal("Chunk has been successfully deleted from file!"));
                                        } else {
                                            owner.sendMessage(Text.literal("No saved chunk data was found in file!"));
                                        }

                                        // ???
//                                        Field resultsField = StorageIoWorker.class.getDeclaredField("results");
//                                        resultsField.setAccessible(true);
//                                        Map<ChunkPos, ?> results = (Map<ChunkPos, ?>) resultsField.get(ioWorkerNbt);
//                                        results.remove(chunkPos);



                                    } catch (Exception e) {
                                        owner.sendMessage(Text.literal("Exception occurred :  " + e.getMessage()));
                                    }

                                    //loadingManager.updateChunks();
                                    //((ServerChunkLoadingManagerMixin) loadingManager).getUnloadedChunks().remove(chunkPosLong);
                                ///} else {
                                ///    owner.sendMessage(Text.literal("Chunk is not loaded, retrying..."));
                                ///    TaskQueue.add(this, 2);
                                ///}
                            }
                        }, 2);
                    };
                }
            }
        }
    }
}