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
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
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
                        player.sendMessage(Text.literal("Ri! RIII! QU'EST-CE T'A FAIT ENCORE!?"));
                    }
                }
            }
        }
    }
}