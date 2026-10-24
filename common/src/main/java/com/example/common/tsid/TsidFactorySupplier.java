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

    /**
     * Returns the factory shared by all instances of this supplier, creating it from {@link
     * TsidFactories#fromEnvironment()} on the first call. Later calls reuse the factory without
     * rereading configuration.
     *
     * @return the shared identifier factory
     * @throws ExceptionInInitializerError if the initial factory creation fails with an exception
     * @throws NoClassDefFoundError if an earlier attempt to initialize the shared factory failed
     */
    @Override
    public TSID.Factory get() {
        return LazyHolder.INSTANCE;
    }
}
