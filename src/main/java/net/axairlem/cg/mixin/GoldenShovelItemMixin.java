package net.axairlem.cg.mixin;

import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
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
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(Item.class)
public class GoldenShovelItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(World world, PlayerEntity user, Hand hand, CallbackInfoReturnable<TypedActionResult<ItemStack>> cir) {

        if(world.isClient()) return;

        ItemStack itemStack = user.getStackInHand(hand);
        ChunkPos chunkPos = new ChunkPos(user.getBlockPos());
        Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
        int chunkX = chunkPos.x;
        int chunkZ = chunkPos.z;

        if(itemStack.getItem() == Items.GOLDEN_SHOVEL) {

            // CAPTURING CHUNKS
            Vector<NbtCompound> blocks = new Vector<NbtCompound>();
            for(int dx = 0; dx < 16; dx++){
                for(int dz = 0; dz < 16; dz++){
                    int x = (chunkX << 4) + dx;
                    int z = (chunkZ << 4) + dz;
                    int y = world.getTopY();

                    for(int dy = y - 1; dy >= -59; dy--){
                        BlockPos pos = new BlockPos(x, dy, z);
                        BlockState state = world.getBlockState(pos);
                        if(!state.isAir()){
                            NbtCompound block = new NbtCompound();
                            block.putInt("chunkPosX", dx);
                            block.putInt("chunkPosZ", dz);
                            block.putInt("chunkPosY", dy);
                            block.putString("state", Registries.BLOCK.getId(state.getBlock()).toString());

                            blocks.add(block);
                        }
                    }
                }
            }

            // ADD NEW SPLASH_POTION
            if(user.getInventory().getEmptySlot() == -1){
                user.sendMessage(Text.literal("Ri ton inventaire est plein criss. MAUDIT RI!"), false);
            }
            else{
                ItemStack addedStack = new ItemStack(Items.SPLASH_POTION);
                String chunkID = UUID.randomUUID().toString();
                addedStack.set(DataComponentTypes.ITEM_NAME, Text.literal("Chunk God [" + chunkID + "]"));
                addedStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                user.giveItemStack(addedStack);

                // ADD CHUNK TO THE PERSISTENT STORAGE
                MinecraftServer server = world.getServer();
                assert server != null;
                ChunkStorage serverStorage = ChunkStorage.getServerState(server);
                serverStorage.savedChunks.put(chunkID, blocks);

                user.sendMessage(Text.literal("Ri c'est le chunk god #[" + chunkX + ", " + chunkZ + "] !!ATTENTION RI, SI TU LANCE LA POTION, ÇA VA PASTE LE CHUNK AU COMPLET!!"), false);
            }
        }
    }
}