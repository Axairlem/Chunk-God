package net.axairlem.cg;

import net.minecraft.nbt.NbtCompound;

import java.util.HashMap;
import java.util.Vector;

public class ChunkStorage {
    public static final HashMap<String, Vector<NbtCompound>> savedChunks = new HashMap<>();

    public static void clear(){
        savedChunks.clear();
    }
}
