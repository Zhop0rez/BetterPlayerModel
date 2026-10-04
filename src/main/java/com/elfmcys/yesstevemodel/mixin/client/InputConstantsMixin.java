package com.elfmcys.yesstevemodel.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InputConstants.class)
public class InputConstantsMixin {
    // 26.3 (SDL): isKeyDown читает буфер состояния клавиатуры прямым индексом без проверки границ;
    // невалидный сохранённый биндинг (например "key.keyboard.-1" из старого options.txt) кидает
    // IndexOutOfBoundsException в KeyMapping.setAll и роняет игру при входе в мир
    @Inject(remap = false, method = "isKeyDown(I)Z", at = @At("HEAD"), cancellable = true)
    private static void ysm$guardInvalidKeyIndex(int key, CallbackInfoReturnable<Boolean> cir) {
        if (key < 0 || key > 511) {
            cir.setReturnValue(false);
        }
    }
}
