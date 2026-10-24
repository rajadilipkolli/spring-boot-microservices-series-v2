/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.tsid;

import io.hypersistence.tsid.TSID;
import java.util.function.Supplier;

public class TsidFactorySupplier implements Supplier<TSID.Factory> {

    public TsidFactorySupplier() {}

    private static final class LazyHolder {
        private static final TSID.Factory INSTANCE = TsidFactories.fromEnvironment();
    }

    @Override
    public TSID.Factory get() {
        return LazyHolder.INSTANCE;
    }
}
