package net.axairlem.cg.mixin;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.world.storage.RegionBasedStorage;
import net.minecraft.world.storage.RegionFile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RegionBasedStorage.class)
public interface RegionBasedStorageMixin {

    @Accessor("cachedRegionFiles")
    Long2ObjectLinkedOpenHashMap<RegionFile> getCachedRegionFiles();
}
