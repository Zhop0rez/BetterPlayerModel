package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import dev.onyxstudios.cca.api.v3.component.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CompoundTag;

public final class StarModelsComponent implements Component {

    private static final String DATA_KEY = "Data";

    private final StarModelsCapability capability = new StarModelsCapability();

    public StarModelsCapability capability() {
        return capability;
    }

    public void readFromNbt_internal(CompoundTag tag) {
        ListTag list = tag.getList("StarModels", 8);
        capability.deserializeNBT(list);
    }

    public void writeToNbt_internal(CompoundTag tag) {
        tag.put("StarModels", capability.serializeNBT());
    }

    @Override
    public void writeToNbt(CompoundTag output) {
        CompoundTag tag = new CompoundTag();
        writeToNbt_internal(tag);
        output.put(DATA_KEY, tag);
    }

    @Override
    public void readFromNbt(CompoundTag input) {
        if (input.contains(DATA_KEY, 10)) { readFromNbt_internal(input.getCompound(DATA_KEY)); }
    }
}
