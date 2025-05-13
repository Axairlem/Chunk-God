package net.axairlem.cg.mixin;

import net.axairlem.cg.ChunkStorage;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Vector;

@Mixin(PotionEntity.class)
public class SplashPotionItemMixin {

    @Inject(method = "onCollision", at = @At("HEAD"))
    private void onCollide(HitResult hit, CallbackInfo ci) {

        PotionEntity self = (PotionEntity)(Object)this;

        World world = ((Entity)self).getWorld();
        if(world.isClient) return;

        ItemStack itemStack = self.getStack();

        if(itemStack.getItem() == Items.SPLASH_POTION) {
            ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(hit.getPos()));
            Chunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
            int chunkX = chunkPos.x;
            int chunkZ = chunkPos.z;

            // CHECK FOR EXISTING CHUNK DATA
            Text itemNameText = itemStack.get(DataComponentTypes.ITEM_NAME);
            if(itemNameText != null){
                String itemID = itemNameText.toString();
                itemID = itemID.substring(itemID.indexOf('[') + 1, itemID.indexOf(']'));
                self.sendMessage(Text.of(itemID));

                ChunkStorage state = ChunkStorage.get((ServerWorld) world);
                Vector<NbtCompound> blocks = state.savedChunks.get(itemID);
                if(ChunkStorage.savedChunks.containsKey(itemID) ) {

                    // PASTING CHUNKS
                    for(NbtCompound entry : blocks) {
                        BlockPos pos = new BlockPos(entry.getInt("chunkPosX") + (chunkX << 4), entry.getInt("chunkPosY"), entry.getInt("chunkPosZ") + (chunkZ << 4));
                        Identifier id = Identifier.of(entry.getString("state"));

                        world.setBlockState(pos, Registries.BLOCK.get(id).getDefaultState(), 2);
                    }
                    state.savedChunks.remove(itemID);
                    state.markDirty();
                    Entity owner = self.getOwner();
                    if(owner instanceof PlayerEntity player){
                        player.sendMessage(Text.literal("Ri has been replaced!?..."));
                    }
                }
            }
        }
    }
}