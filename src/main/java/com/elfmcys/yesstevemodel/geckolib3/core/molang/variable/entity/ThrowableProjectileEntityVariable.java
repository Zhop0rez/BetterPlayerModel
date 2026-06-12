package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;

public class ThrowableProjectileEntityVariable extends LambdaVariable<net.minecraft.world.entity.projectile.ThrowableItemProjectile> {
    public ThrowableProjectileEntityVariable(IValueEvaluator<?, IContext<net.minecraft.world.entity.projectile.ThrowableItemProjectile>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.ThrowableItemProjectile;
    }
}

