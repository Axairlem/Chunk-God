package net.axairlem.cg.mixin;

import com.mojang.serialization.DataResult;
import net.axairlem.cg.ChunkStorage;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.*;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
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

        if(user.getInventory().getEmptySlot() == -1){
            user.sendMessage(Text.literal("Please, free up your inventory first.").withColor(0xFFAA00));
            return;
        }

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

            MinecraftServer minecraftServer = world.getServer();
            if(minecraftServer == null) {
                System.out.println("minecraftServer @ItemMixin.java [71] was null...");
                return;
            }
            ChunkStorage serverStorage = ChunkStorage.getServerState(minecraftServer); // data persist through server restarts

            // CAPTURE BLOCKS
            int minX = Math.min(firstBlockPos.getX(), secondBlockPos.getX());
            int maxX = Math.max(firstBlockPos.getX(), secondBlockPos.getX());
            int minZ = Math.min(firstBlockPos.getZ(), secondBlockPos.getZ());
            int maxZ = Math.max(firstBlockPos.getZ(), secondBlockPos.getZ());

            int xDistance = maxX++ - minX; // incremented to create minimum area of 1 block
            int zDistance = maxZ++ - minZ;

            Vector<NbtCompound> blocks = new Vector<NbtCompound>();
            ItemStack addedStack = new ItemStack(Items.SPLASH_POTION);
            String itemID = UUID.randomUUID().toString() + "[" + world.getRegistryKey().getValue() + "]"; // record dimension to query @ chunk regen op
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


            // CAPTURE ENTITIES
            List<Entity> entities = world.getEntitiesByClass(
                    Entity.class,
                    new Box(minX, world.getBottomY(), minZ, maxX, world.getTopY(), maxZ),
                    entity -> entity instanceof Entity
            );
            ArrayList<NbtCompound> entityDatalist = new ArrayList<>();
            for(Entity e : entities){
                if(e.getType() == EntityType.PLAYER || e.getType() == EntityType.LEASH_KNOT) continue;

                NbtCompound entityNbt = new NbtCompound();
                e.saveSelfNbt(entityNbt);

                NbtList positions = new NbtList();
                positions.add(0, NbtDouble.of(e.getX() - minX - (xDistance / 2))); // local relative position
                positions.add(1, NbtDouble.of(e.getY()) );
                positions.add(2, NbtDouble.of(e.getZ() - minZ - (zDistance / 2)));
                entityNbt.put("Pos", positions);

                // CAPTURE LEASH POSITION IF PRESENT
                if(e instanceof Leashable && ((Leashable)e).getLeashHolder() != null){
                    NbtList localPos = new NbtList();
                    int x = ((Leashable)e).getLeashHolder().getBlockPos().getX();
                    int y = ((Leashable)e).getLeashHolder().getBlockPos().getY();
                    int z = ((Leashable)e).getLeashHolder().getBlockPos().getZ();
                    localPos.add(0, NbtInt.of(x - minX - (xDistance / 2)));
                    localPos.add(1, NbtInt.of(y));
                    localPos.add(2, NbtInt.of(z - minZ - (zDistance / 2)));
                    entityNbt.put("LeashLocalPos", localPos);
                }

                entityDatalist.add(entityNbt);
            }
            serverStorage.savedEntities.put(itemID, entityDatalist);


            // ADD CHUNK COORDINATES TO "chunksToRegen"
            maxX--; maxZ--; // revert to original coordinates
            for(int chunkX = minX >> 4; chunkX <= (maxX >> 4); chunkX++){
                for(int chunkZ = minZ >> 4; chunkZ <= (maxZ >> 4); chunkZ++){
                    serverStorage.chunksToRegen.computeIfAbsent(itemID, k -> new Vector<ChunkPos>()).add(new ChunkPos(chunkX, chunkZ)); // add absolute chunk position
                }
            }


            // ADD NEW SPLASH_POTION
            addedStack.set(DataComponentTypes.ITEM_NAME, Text.literal(itemID)); // TODO: find a way to store the key elsewhere
            addedStack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
            user.giveItemStack(addedStack);


            // ADD CHUNK TO THE PERSISTENT STORAGE
            MinecraftServer server = world.getServer();
            if(server == null){
                System.out.println("server @ItemMixin.java [175] was null...");
                return;
            }
            serverStorage.savedBlocks.put(itemID, blocks);


            user.sendMessage(Text.literal("Blocks have been captured with success!").withColor(0xFF55FF));
            firstTimeUse = true;
        }
    }
    @Inject(method = "onItemEntityDestroyed", at = @At("HEAD"))
    public void onDelete(ItemEntity entity, CallbackInfo ci) {
        ItemStack itemStack = entity.getStack();
        if(itemStack.getItem() == Items.SPLASH_POTION){
            // DELETE CHUNK & BLOCK DATA FROM HASHMAPS
            MinecraftServer server = entity.getServer();
            ChunkStorage serverStorage = ChunkStorage.getServerState(server);

            String itemID = itemStack.get(DataComponentTypes.ITEM_NAME).getString();
            if(itemID == null){
                System.out.println("ItemID for " + entity.getStack().getItem().getTranslationKey() + " is null");
                return;
            }
            serverStorage.savedBlocks.remove(itemID);
            serverStorage.chunksToRegen.remove(itemID);
            serverStorage.savedEntities.remove(itemID);
        }
    }
}
