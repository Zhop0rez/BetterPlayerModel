package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.VehicleModelCapability;
import dev.onyxstudios.cca.api.v3.component.Component;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.CompoundTag;

public final class VehicleModelComponent implements Component {

    private static final String DATA_KEY = "Data";

    private final VehicleModelCapability capability = new VehicleModelCapability();

    public VehicleModelCapability capability() {
        return capability;
    }

    public void readFromNbt_internal(CompoundTag tag) {
        if(tag.contains("VehicleModel", 10)) { capability.deserializeNBT(tag.getCompound("VehicleModel")); }
    }

    public void writeToNbt_internal(CompoundTag tag) {
        tag.put("VehicleModel", capability.serializeNBT());
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
