package net.axairlem.cg.mixin;

import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.StorageIoWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StorageIoWorker.class)
public interface StorageIoWorkerMixin {

    @Accessor("storage")
    RegionBasedStorage getStorage();
}
