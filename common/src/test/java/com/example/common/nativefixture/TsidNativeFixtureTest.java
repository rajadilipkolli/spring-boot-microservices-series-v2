/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.tsid.TsidFactorySupplier;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TsidNativeFixtureTest {

    private static final int CONFIGURED_NODE = 250;

    @BeforeAll
    static void setUp() {
        System.setProperty("tsid.node", String.valueOf(CONFIGURED_NODE));
    }

    @Test
    void verifiesNodeComponentOfGeneratedIdsEqualsConfiguredNode() {
        TsidFactorySupplier supplier = new TsidFactorySupplier();
        TSID.Factory factory = supplier.get();
        assertThat(factory).isNotNull();

        TSID tsid = factory.generate();
        long tsidValue = tsid.toLong();

        // TSID bit layout: [time: 42 bits] [node: 10 bits] [counter: 12 bits]
        int nodeBits = 10;
        int counterBits = 12;
        int nodeMask = (1 << nodeBits) - 1;
        int extractedNode = (int) ((tsidValue >>> counterBits) & nodeMask);

        assertThat(extractedNode).isEqualTo(CONFIGURED_NODE);
    }
}
