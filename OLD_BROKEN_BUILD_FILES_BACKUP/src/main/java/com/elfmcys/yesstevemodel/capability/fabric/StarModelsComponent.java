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

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = tag.getList("StarModels").orElse(new ListTag());
        capability.deserializeNBT(list);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("StarModels", capability.serializeNBT());
    }

    @Override
    public void writeToNbt(CompoundTag output) {
        CompoundTag tag = new CompoundTag();
        writeToNbt(tag, null);
        output.store(DATA_KEY, CompoundTag.CODEC, tag);
    }

    @Override
    public void readFromNbt(CompoundTag input) {
        input.read(DATA_KEY, CompoundTag.CODEC).ifPresent(tag -> readFromNbt(tag, input.lookup()));
    }
}

