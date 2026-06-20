package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public final class AuthModelsComponent {

    private static final String DATA_KEY = "Data";

    private final AuthModelsCapability capability = new AuthModelsCapability();

    public AuthModelsCapability capability() {
        return capability;
    }

    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = tag.getList("AuthModels").orElse(new ListTag());
        capability.deserializeNBT(list);
    }

    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        tag.put("AuthModels", capability.serializeNBT());
    }

    
    }
