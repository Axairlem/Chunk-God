package net.axairlem.cg.mixin;

import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;

@Mixin(PotionEntity.class)
public class PotionEntityMixin {

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollide(HitResult hit, CallbackInfo ci) throws IOException {

        PotionEntity self = (PotionEntity)(Object)this;
        Entity owner = self.getOwner();

        World world = self.getWorld();
        if(world.isClient) return;

        ItemStack itemStack = self.getStack();

        if(itemStack.getItem() == Items.SPLASH_POTION) {

            BlockPos hitPos = new BlockPos(
                    (int)hit.getPos().x - 1, // offset to center the pasting op around the center of the potion's hit pos
                    (int)hit.getPos().y,
                    (int)hit.getPos().z - 1
            );

            // CHECK FOR EXISTING BLOCK DATA IN PERSISTENT STORAGE
            Text itemNameText = itemStack.get(DataComponentTypes.ITEM_NAME);
            if(itemNameText != null){
                String itemID = itemNameText.getString();

                MinecraftServer server = world.getServer();
                assert server != null;
                ChunkStorage serverStorage = ChunkStorage.getServerState(server);
                if(serverStorage.savedBlocks.containsKey(itemID) ) {

                    // PASTE BLOCKS
                    Vector<NbtCompound> blocks = serverStorage.savedBlocks.get(itemID);
                    for(NbtCompound entry : blocks) {
                        BlockPos pos = new BlockPos(
                                hitPos.getX() + entry.getInt("posX"),
                                entry.getInt("posY"),
                                hitPos.getZ() + entry.getInt("posZ")
                        );
                        Identifier blockID = Identifier.of(entry.getString("blockID"));
                        Block block = Registries.BLOCK.get(blockID);
                        BlockState state = block.getDefaultState();

                        // PARSE BLOCKSTATE
                        DataResult<BlockState> decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, entry.get("blockState"));
                        decoded.result().ifPresent(loadedState -> {
                            world.setBlockState(pos, loadedState, 2);
                        });

                        // RESTORE BLOCK ENTITY
                        if(entry.contains("blockEntity")){
                            BlockEntity blockEntity = world.getBlockEntity(pos);
                            blockEntity.read(entry.getCompound("blockEntity"), world.getRegistryManager());
                        }
                    }


                    // REGENERATE OLD CHUNK(S)
                    ServerWorld serverWorld = server.getWorld(World.OVERWORLD);
                    assert serverWorld != null;
                    ServerChunkLoadingManager loadingManager = serverWorld.getChunkManager().chunkLoadingManager;
                    for(ChunkPos chunkPos : serverStorage.chunksToRegen.get(itemID)) {
                        long chunkPosLong = chunkPos.toLong();

                        try {
                            NbtScannable ioWorkerNbt = serverWorld.getChunkManager().getChunkIoWorker();

                            // REMOVE CHUNK HOLDER AND CLEAR TICKETS
                            Field chunkHolderMapField = ServerChunkLoadingManager.class.getDeclaredField("currentChunkHolders");
                            chunkHolderMapField.setAccessible(true);
                            Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = (Long2ObjectLinkedOpenHashMap<ChunkHolder>) chunkHolderMapField.get(loadingManager);
                            ChunkHolder holder = holders.get(chunkPosLong);
                            if (holder != null) {
                                owner.sendMessage(Text.literal("Chunk holder @{" + chunkPos.x + ", " + chunkPos.z + "} is not NULL, clearing before deletion...").withColor(0xFFAA00));

                                holders.remove(chunkPosLong);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.FORCED, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.PLAYER, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.UNKNOWN, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.START, chunkPos, 1, Unit.INSTANCE);
                            }

                            Field storageField = ioWorkerNbt.getClass().getDeclaredField("storage");
                            storageField.setAccessible(true);
                            Object regionBasedStorage = storageField.get(ioWorkerNbt);
                            Field storageKeyField = regionBasedStorage.getClass().getDeclaredField("storageKey");
                            storageKeyField.setAccessible(true);
                            StorageKey storageKey = (StorageKey) storageKeyField.get(regionBasedStorage);

                            // CLEAR REGION CACHE
                            Field cachedRegionFilesField = regionBasedStorage.getClass().getDeclaredField("cachedRegionFiles");
                            cachedRegionFilesField.setAccessible(true);
                            Long2ObjectLinkedOpenHashMap<RegionFile> regionsCache = (Long2ObjectLinkedOpenHashMap<RegionFile>) cachedRegionFilesField.get(regionBasedStorage);
                            regionsCache.clear();

                            // DELETE CHUNK DATA FROM .mca FILE
                            String saveDir = loadingManager.getSaveDir();
                            Path folderPath = Paths.get(saveDir, "region");
                            Path regionPath = folderPath.resolve(String.format("r.%d.%d.mca", chunkPos.x >> 5, chunkPos.z >> 5));
                            RegionFile region = new RegionFile(storageKey, regionPath, folderPath, true);
                            if (region.hasChunk(chunkPos)) {
                                region.delete(chunkPos);
                                owner.sendMessage(Text.literal("Chunk {" + chunkPos.x + ", " + chunkPos.z + "} has been successfully deleted from file: " + regionPath).withColor(0x55FF55));
                            } else {
                                owner.sendMessage(Text.literal("No saved chunk data was found in file: " + regionPath).withColor(0xFFAA00));
                            }

                        } catch (Exception e) {
                            owner.sendMessage(Text.literal("Exception occurred :  " + e.getMessage()).withColor(0xFF5555));
                        }
                    };

                    // CLEAR MEMORY FROM HASHMAPS
                    serverStorage.savedBlocks.remove(itemID);
                    serverStorage.chunksToRegen.remove(itemID);

                    owner.sendMessage(Text.literal("Blocks have been pasted successfully!").withColor(0xFF55FF));
                }
            }
        }
    }
}