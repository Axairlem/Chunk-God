package net.axairlem.cg.mixin;

import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(Item.class)
public abstract class GoldenShovelItemMixin {

    private static boolean firstTimeUse = true;
    private static BlockPos firstBlockPos = null;

    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {

        if(world.isClient()) return;

        ItemStack itemStack = user.getStackInHand(hand);
        if(itemStack.getItem() != Items.GOLDEN_SHOVEL) return;

        if(firstTimeUse) {
            firstBlockPos = user.getBlockPos();
            user.sendMessage(Text.literal("First point: " + firstBlockPos.getX() + " " + firstBlockPos.getZ()));
            firstTimeUse = false;
        } else {
            // CAPTURE CHUNK
            ChunkStorage serverStorage = new ChunkStorage(); // to store old blocks pos

            int minX = Math.min(firstBlockPos.getX(), user.getBlockPos().getX());
            int maxX = Math.max(firstBlockPos.getX(), user.getBlockPos().getX());
            int minZ = Math.min(firstBlockPos.getZ(), user.getBlockPos().getZ());
            int maxZ = Math.max(firstBlockPos.getZ(), user.getBlockPos().getZ());

            int xDistance = maxX - minX;
            int zDistance = maxZ - minZ;
            Vector<NbtCompound> blocks = new Vector<NbtCompound>();
            for(int x = minX; x < maxX; x++){
                for(int z = minZ; z < maxZ; z++){
                    for(int y = world.getTopY() - 1; y >= -59; y--){
                        BlockPos pos = new BlockPos(x, y, z);
                        BlockState state = world.getBlockState(pos);
                        if(!state.isAir()){
                            serverStorage.chunksToRegen.computeIfAbsent(new Vector2i(x >> 4, z >> 4), k -> new Vector<BlockPos>()).add(new BlockPos(x, y, z)); // add absolute block pos

                            NbtCompound blockNbt = new NbtCompound();
                            blockNbt.putInt("posX", x - minX - (xDistance / 2)); // local relative pos
                            blockNbt.putInt("posZ", z - minZ - (zDistance / 2));
                            blockNbt.putInt("posY", y);
                            blockNbt.putString("state", Registries.BLOCK.getId(state.getBlock()).toString());

                            blocks.add(blockNbt);
                        }
                    }
                }
            }

            if(user.getInventory().getEmptySlot() == -1){
                user.sendMessage(Text.literal("Please, free up your inventory first."), false);
            } else {
                // ADD NEW SPLASH_POTION
                ItemStack addedStack = new ItemStack(Items.SPLASH_POTION);
                String chunkID = UUID.randomUUID().toString();
                addedStack.set(DataComponentTypes.ITEM_NAME, Text.literal("Chunk God [" + chunkID + "]"));
                addedStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                user.giveItemStack(addedStack);

                // ADD CHUNK TO THE PERSISTENT STORAGE
                MinecraftServer server = world.getServer();
                assert server != null;
                serverStorage.savedChunks.put(chunkID, blocks);

                user.sendMessage(Text.literal("Blocks have been captured with success!"), false);
            }

            firstTimeUse = true;
        }
    }

    @Inject(method = "onItemEntityDestroyed", at = @At("HEAD"))
    public void onDelete(ItemEntity entity, CallbackInfo ci) {
        ItemStack itemStack = entity.getStack();
        if(itemStack.getItem() == Items.SPLASH_POTION){
            // DELETE CHUNK DATA FROM HASHMAP
            MinecraftServer server = entity.getServer();
            ChunkStorage serverStorage = ChunkStorage.getServerState(server);

            String itemID = itemStack.get(DataComponentTypes.ITEM_NAME).toString();
            if(serverStorage.savedChunks.containsKey(itemID)){
                serverStorage.savedChunks.remove(itemID);
            }
        }
    }
}