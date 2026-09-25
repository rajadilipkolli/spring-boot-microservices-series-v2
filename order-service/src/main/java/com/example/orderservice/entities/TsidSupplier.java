/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.entities;

import io.hypersistence.tsid.TSID;
import java.util.function.Supplier;

public class TsidSupplier implements Supplier<TSID.Factory> {
    private static final TSID.Factory FACTORY;

    static {
        String nodeStr = System.getProperty("tsid.node", System.getenv("TSID_NODE"));
        TSID.Factory.Builder builder = TSID.Factory.builder();
        if (nodeStr != null && !nodeStr.trim().isEmpty()) {
            builder.withNode(Integer.parseInt(nodeStr));
        }
        FACTORY = builder.build();
    }

    @Override
    public TSID.Factory get() {
        return FACTORY;
    }
}
