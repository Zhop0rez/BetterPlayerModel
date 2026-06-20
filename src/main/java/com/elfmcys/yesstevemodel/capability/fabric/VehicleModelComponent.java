package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.VehicleModelCapability;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class VehicleModelComponent {

    private static final String DATA_KEY = "Data";

    private final VehicleModelCapability capability = new VehicleModelCapability();

    public VehicleModelCapability capability() {
        return capability;
    }

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.getCompound("VehicleModel").ifPresent(capability::deserializeNBT);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("VehicleModel", capability.serializeNBT());
    }

    
    }
