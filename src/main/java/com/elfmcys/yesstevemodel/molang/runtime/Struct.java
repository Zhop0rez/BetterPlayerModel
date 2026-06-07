package com.elfmcys.yesstevemodel.molang.runtime;

public interface Struct {
    Object getProperty(int name);

    void putProperty(int name, Object value);

    // FIXME: дЅњдёє foreign еЏй‡Џиў«и®їй—®ж—¶жњ‰зєїзЁ‹е®‰е…Ёй—®йў
    Struct copy();
}
