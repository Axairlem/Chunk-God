package net.axairlem.cg.mixin;

import net.minecraft.server.world.ChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.BooleanSupplier;

@Mixin(ChunkTicketManager.class)
public interface ChunkTicketManagerMixin {

    @Invoker("purge")
    void invokePurge();
}
