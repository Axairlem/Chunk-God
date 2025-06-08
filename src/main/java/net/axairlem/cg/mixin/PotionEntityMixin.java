package net.axairlem.cg.mixin;

import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.Leashable;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.hit.BlockHitResult;
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Vector;

@Mixin(PotionEntity.class)
public class PotionEntityMixin {

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollide(HitResult hit, CallbackInfo ci) throws IOException {

        PotionEntity self = (PotionEntity)(Object)this;

        World world = self.getWorld();
        if(world.isClient) return;

        ItemStack itemStack = self.getStack();

        if(itemStack.getItem() == Items.SPLASH_POTION) {
            BlockPos hitPos = ((BlockHitResult)hit).getBlockPos();

            // CHECK FOR EXISTING BLOCK DATA IN PERSISTENT STORAGE
            Text itemNameText = itemStack.get(DataComponentTypes.ITEM_NAME);
            if(itemNameText != null){
                String itemID = itemNameText.getString();

                MinecraftServer server = world.getServer();
                if(server == null){
                    System.out.println("server @PotionEntityMixin.java [68] was null...");
                    return;
                }
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

                        // PARSE BLOCKSTATE
                        DataResult<BlockState> decoded = BlockState.CODEC.parse(NbtOps.INSTANCE, entry.get("blockState"));
                        decoded.result().ifPresent(loadedState -> {
                            world.setBlockState(pos, loadedState, 3);
                        });

                        // RESTORE BLOCK ENTITY
                        if(entry.contains("blockEntity")){
                            BlockEntity blockEntity = world.getBlockEntity(pos);
                            if(blockEntity == null){
                                System.out.println("blockEntity " + world.getBlockState(pos).toString() + " @PotionEntityMixin.java [93] was null...");
                                continue;
                            }
                            blockEntity.read(entry.getCompound("blockEntity"), world.getRegistryManager());
                        }
                    }


                    // PASTE ENTITIES
                    for(NbtCompound entityNbt : serverStorage.savedEntities.get(itemID)) {
                        try{
                            entityNbt.getUuid("UUID");
                        } catch (NullPointerException e) {
                            System.out.println("UUID not found" + e.getMessage());
                            continue;
                        }

                        NbtList localPos = entityNbt.getList("Pos", NbtElement.DOUBLE_TYPE);
                        double posX = localPos.getDouble(0) + hitPos.getX();
                        double posY = localPos.getDouble(1);
                        double posZ = localPos.getDouble(2) + hitPos.getZ();

                        Entity entity = ((ServerWorld)world).getEntity(entityNbt.getUuid("UUID"));
                        if(entity == null){ // RESPAWN NEW ENTITY
                            entity = EntityType.loadEntityWithPassengers(entityNbt, world, (e) -> {
                                e.refreshPositionAndAngles(posX, posY, posZ, e.getYaw(), e.getPitch());
                                return e;
                            });
                            if(entity == null) {
                                System.out.println("Entity (" + entityNbt.getUuid("UUID") + ") not found...");
                            } else {
                                world.spawnEntity(entity);

                                if(entityNbt.contains("LeashLocalPos")){ // ATTACH NEW LEASH
                                    NbtList leashPos = entityNbt.getList("LeashLocalPos", NbtElement.INT_TYPE);
                                    BlockPos leashBlockPos = new BlockPos(
                                            leashPos.getInt(0) + hitPos.getX(),
                                            leashPos.getInt(1),
                                            leashPos.getInt(2) + hitPos.getZ()
                                    );
                                    LeashKnotEntity leashKnotEntity = LeashKnotEntity.getOrCreate(world, leashBlockPos);
                                    ((Leashable)entity).detachLeash(true, false);
                                    ((Leashable)entity).attachLeash(leashKnotEntity, true);
                                }
                            }
                        } else { // TELEPORT ALREADY EXISTING ENTITY
                            entity.teleport((ServerWorld)world, posX, posY, posZ, PositionFlag.getFlags(0), entity.getYaw(), entity.getPitch());

                            if(entityNbt.contains("LeashLocalPos")){ // ATTACH NEW LEASH
                                NbtList leashPos = entityNbt.getList("LeashLocalPos", NbtElement.INT_TYPE);
                                BlockPos leashBlockPos = new BlockPos(
                                        leashPos.getInt(0) + hitPos.getX(),
                                        leashPos.getInt(1),
                                        leashPos.getInt(2) + hitPos.getZ()
                                );
                                LeashKnotEntity leashKnotEntity = LeashKnotEntity.getOrCreate(world, leashBlockPos);
                                ((Leashable)entity).detachLeash(true, false);
                                ((Leashable)entity).attachLeash(leashKnotEntity, true);
                            }

                            // FORCE REFRESH TO CLIENT
                            ((ServerWorld)world).getChunkManager().unloadEntity(entity);
                            ((ServerWorld)world).getChunkManager().loadEntity(entity);
                        }
                    }


                    // REGENERATE OLD CHUNK(S)
                    Identifier dimensionID = Identifier.of(itemID.substring(itemID.indexOf('[') + 1, itemID.indexOf(']')));
                    ServerWorld serverWorld = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, dimensionID));
                    if(serverWorld == null){
                        System.out.println("serverWorld @PotionEntityMixin.java [164] was null...");
                        return;
                    }
                    ServerChunkLoadingManager loadingManager = serverWorld.getChunkManager().chunkLoadingManager;
                    for(ChunkPos chunkPos : serverStorage.chunksToRegen.get(itemID)) {
                        long chunkPosLong = chunkPos.toLong();

                        try {
                            // REMOVE CHUNK HOLDER AND CLEAR TICKETS
                            Long2ObjectLinkedOpenHashMap<ChunkHolder> holders = ((ServerChunkLoadingManagerMixin)loadingManager).getCurrentChunkHolders();
                            ChunkHolder holder = holders.get(chunkPosLong);
                            if (holder != null) {
                                holders.remove(chunkPosLong);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.FORCED, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.PLAYER, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.UNKNOWN, chunkPos, 1, chunkPos);
                                loadingManager.getTicketManager().removeTicket(ChunkTicketType.START, chunkPos, 1, Unit.INSTANCE);
                            }

                            // GET STORAGE KEY
                            StorageIoWorker storageIoWorker = (StorageIoWorker) serverWorld.getChunkManager().getChunkIoWorker();
                            StorageKey storageKey = storageIoWorker.getStorageKey();

                            // CLEAR REGION CACHE
                            RegionBasedStorage regionBasedStorage = ((StorageIoWorkerMixin)storageIoWorker).getStorage();
                            Long2ObjectLinkedOpenHashMap<RegionFile> regionsCache = ((RegionBasedStorageMixin)(Object)regionBasedStorage).getCachedRegionFiles();
                            regionsCache.clear();

                            // DELETE CHUNK DATA FROM .mca FILE
                            Path worldRootPath = serverWorld.getServer().getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
                            Path folderPath = Paths.get(worldRootPath.toString(), "region");
                            String saveDir = loadingManager.getSaveDir();
                            if(serverWorld.getRegistryKey() == World.NETHER) {
                                folderPath = Paths.get(worldRootPath.toString(), saveDir + "/region");
                            } else if(serverWorld.getRegistryKey() == World.END) {
                                folderPath = Paths.get(worldRootPath.toString(), saveDir + "/region");
                            }
                            Path regionPath = folderPath.resolve(String.format("r.%d.%d.mca", chunkPos.x >> 5, chunkPos.z >> 5));
                            RegionFile regionFile = new RegionFile(storageKey, regionPath, folderPath, true);
                            if (regionFile.hasChunk(chunkPos)) {
                                regionFile.delete(chunkPos);
                            } else {
                                System.out.println("No saved chunk data was found in file: " + regionPath);
                            }

                        } catch (Exception e) {
                            System.out.println("Exception occurred @PotionEntityMixin.java [173 to 206]:  " + e.getMessage());
                        }
                    };

                    // CLEAR MEMORY FROM HASHMAPS
                    serverStorage.savedBlocks.remove(itemID);
                    serverStorage.chunksToRegen.remove(itemID);
                    serverStorage.savedEntities.remove(itemID);
                }
            }
        }
    }
}