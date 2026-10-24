/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.common.tsid.TsidFactorySupplier;
import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TsidNativeFixtureTest {

    private static final int CONFIGURED_NODE = 250;
    private static String originalNode;
    private static String originalNodeCount;

    @BeforeAll
    static void setUp() {
        originalNode = System.getProperty("tsid.node");
        originalNodeCount = System.getProperty("tsid.node.count");
        System.setProperty("tsid.node", String.valueOf(CONFIGURED_NODE));
        System.setProperty("tsid.node.count", "1024");
    }

    @AfterAll
    static void tearDown() {
        restoreProperty("tsid.node", originalNode);
        restoreProperty("tsid.node.count", originalNodeCount);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
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
