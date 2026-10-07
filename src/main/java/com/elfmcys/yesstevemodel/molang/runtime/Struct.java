package com.elfmcys.yesstevemodel.molang.runtime;

public interface Struct {
    Object getProperty(int name);

    void putProperty(int name, Object value);

    // FIXME: thread-safety issue when accessed as a foreign variable
    Struct copy();
}
