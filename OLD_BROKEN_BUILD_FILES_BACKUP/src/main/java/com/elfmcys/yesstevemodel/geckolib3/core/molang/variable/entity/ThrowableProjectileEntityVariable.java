package com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.entity;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.LambdaVariable;
import net.minecraft.world.entity.projectile.ThrowableProjectile.ThrowableProjectile;

public class ThrowableProjectileEntityVariable extends LambdaVariable<net.minecraft.world.entity.projectile.ThrowableProjectile.ThrowableProjectile> {
    public ThrowableProjectileEntityVariable(IValueEvaluator<?, IContext<net.minecraft.world.entity.projectile.ThrowableProjectile.ThrowableProjectile>> evaluator) {
        super(evaluator);
    }

    @Override
    public boolean validateContext(IContext<?> context) {
        return context.entity() instanceof net.minecraft.world.entity.projectile.ThrowableProjectile.ThrowableProjectile;
    }
}

