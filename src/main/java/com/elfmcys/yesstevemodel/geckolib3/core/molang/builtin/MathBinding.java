package com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.ContextBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.math.*;

public class MathBinding extends ContextBinding {

    public static final MathBinding INSTANCE = new MathBinding();

    private MathBinding() {
        /* Constants */
        constValue("pi", Math.PI);
        constValue("e", Math.E);

        /* Rounding functions */
        function("floor", new Floor());
        function("round", new Round());
        function("ceil", new Ceil());
        function("trunc", new Trunc());

        /* Comparison functions */
        function("clamp", new Clamp());
        function("max", new Max());
        function("min", new Min());

        /* Classic math functions */
        function("abs", new Abs());
        function("exp", new Exp());
        function("ln", new Ln());
        function("sqrt", new Sqrt());
        function("mod", new Mod());
        function("pow", new Pow());

        /* Trigonometric functions */
        function("sin", new Sin());     // degree
        function("cos", new Cos());     // degree
        function("acos", new ACos());
        function("asin", new ASin());
        function("atan", new Atan());
        function("atan2", new ATan2());

        /* Utilities */
        function("lerp", new Lerp());
        function("lerprotate", new LerpRotate());
        function("random", new Random());
        function("random_integer", new RandomInteger());
        function("die_roll", new DieRoll());
        function("die_roll_integer", new DieRollInteger());
        function("hermite_blend", new HermitBlend());

        /* Others */
        function("min_angle", new MinAngle());

        /* Non-standard naming, kept for compatibility with the original geckolib */
        function("randomi", new RandomInteger());
        function("roll", new DieRoll());
        function("rolli", new DieRollInteger());
        function("hermite", new HermitBlend());
    }
}
