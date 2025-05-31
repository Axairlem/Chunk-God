package net.axairlem.cg.mixin;

import com.mojang.serialization.DataResult;
import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(Item.class)
public abstract class ItemMixin {

    private static boolean firstTimeUse = true;
    private static BlockPos firstBlockPos = null;

    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {

        if(world.isClient()) return;

        ItemStack itemStack = user.getStackInHand(hand);
        if(itemStack.getItem() != Items.WOODEN_SWORD) return;

        HitResult hitResult = user.raycast(user.getBlockInteractionRange(), 0.0f, false);
        if(firstTimeUse) {
            if(hitResult.getType() != HitResult.Type.BLOCK) {
                user.sendMessage(Text.literal("Please, select your first block.").withColor(0xFFAA00));
                return;
            }

            firstBlockPos = ((BlockHitResult)hitResult).getBlockPos();
            user.sendMessage(Text.literal("First point: " + firstBlockPos.getX() + " " + firstBlockPos.getZ()));
            firstTimeUse = false;
        } else {
            if(hitResult.getType() != HitResult.Type.BLOCK) {
                user.sendMessage(Text.literal("Please, select your second block.").withColor(0xFFAA00));
                return;
            }

            BlockPos secondBlockPos = ((BlockHitResult)hitResult).getBlockPos();

            user.sendMessage(Text.literal("Second point: " + secondBlockPos.getX() + " " + secondBlockPos.getZ()));

            // CAPTURE CHUNK
            ChunkStorage serverStorage = ChunkStorage.getServerState(world.getServer()); // to store old blocks pos

            int minX = Math.min(firstBlockPos.getX(), secondBlockPos.getX());
            int maxX = Math.max(firstBlockPos.getX(), secondBlockPos.getX()) + 1; // incremented to create a minimum area of 1 block
            int minZ = Math.min(firstBlockPos.getZ(), secondBlockPos.getZ());
            int maxZ = Math.max(firstBlockPos.getZ(), secondBlockPos.getZ()) + 1;

            int xDistance = maxX - minX;
            int zDistance = maxZ - minZ;
            Vector<NbtCompound> blocks = new Vector<NbtCompound>();
            ItemStack addedStack = new ItemStack(Items.SPLASH_POTION);
            String itemID = UUID.randomUUID().toString();
            for(int x = minX; x < maxX; x++){
                for(int z = minZ; z < maxZ; z++){
                    for(int y = world.getTopY() - 1; y >= -59; y--){
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if(!state.isAir()){
                            NbtCompound blockNbt = new NbtCompound();
                            blockNbt.putInt("posX", x - minX - (xDistance / 2)); // local relative block position
                            blockNbt.putInt("posZ", z - minZ - (zDistance / 2));
                            blockNbt.putInt("posY", y);
                            blockNbt.putString("blockID", Registries.BLOCK.getId(state.getBlock()).toString());

                            // ENCODE BLOCKSTATE TO NbtElement
                            DataResult<NbtElement> result = BlockState.CODEC.encodeStart(NbtOps.INSTANCE, state);
                            result.result().ifPresent(encoded -> {
                                blockNbt.put("blockState", encoded);
                            });

                            // RECORD BLOCKENTITY
                            BlockEntity blockEntity = world.getBlockEntity(pos);
                            if(blockEntity != null){
                                NbtCompound entityNbt = blockEntity.createNbt(world.getRegistryManager());
                                blockNbt.put("blockEntity", entityNbt);
                            }

                            blocks.add(blockNbt);
                        }
                    }
                }
            }

            // ADD CHUNK COORDINATES TO "chunksToRegen"
            maxX--; maxZ--; // revert to original coordinates
            for(int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++){
                for(int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++){
                    serverStorage.chunksToRegen.computeIfAbsent(itemID, k -> new Vector<ChunkPos>()).add(new ChunkPos(chunkX, chunkZ)); // add absolute chunk position
                }
            }

            if(user.getInventory().getEmptySlot() == -1){
                user.sendMessage(Text.literal("Please, free up your inventory first.").withColor(0xFFAA00));
                return;
            } else {
                // ADD NEW SPLASH_POTION
                addedStack.set(DataComponentTypes.ITEM_NAME, Text.literal(itemID)); // find a way to store the key elsewhere
                addedStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                user.giveItemStack(addedStack);

                // ADD CHUNK TO THE PERSISTENT STORAGE
                MinecraftServer server = world.getServer();
                assert server != null;
                serverStorage.savedBlocks.put(itemID, blocks);

                user.sendMessage(Text.literal("Blocks have been captured with success!").withColor(0xFF55FF));
            }

            firstTimeUse = true;
        }
    }
    @Inject(method = "onItemEntityDestroyed", at = @At("HEAD"))
    public void onDelete(ItemEntity entity, CallbackInfo ci) {
        ItemStack itemStack = entity.getStack();
        entity.getOwner().sendMessage(Text.literal("Item detected"));
        if(itemStack.getItem() == Items.SPLASH_POTION){

            // DELETE CHUNK & BLOCK DATA FROM HASHMAPS
            MinecraftServer server = entity.getServer();
            ChunkStorage serverStorage = ChunkStorage.getServerState(server);

            String itemID = itemStack.get(DataComponentTypes.ITEM_NAME).getString();
            if(serverStorage.savedBlocks.containsKey(itemID)){
                entity.getOwner().sendMessage(Text.literal("Removed " + itemID + " from saved blocks"));
                serverStorage.savedBlocks.remove(itemID);
            }
            if(serverStorage.chunksToRegen.containsKey(itemID)){
                entity.getOwner().sendMessage(Text.literal("Removed " + itemID + " from saved chunks"));
                serverStorage.chunksToRegen.remove(itemID);
            }
        }
    }
}