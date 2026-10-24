/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.tsid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class TsidFactoriesTest {

    private String originalNodeCount;

    @BeforeEach
    void setUp() {
        originalNodeCount = System.getProperty("tsid.node.count");
        System.setProperty("tsid.node.count", "1024");
    }

    @AfterEach
    void tearDown() {
        if (originalNodeCount == null) {
            System.clearProperty("tsid.node.count");
        } else {
            System.setProperty("tsid.node.count", originalNodeCount);
        }
    }

    @Test
    void buildsWithPropertyOnly() {
        TSID.Factory factory = TsidFactories.build("42", null);
        TSID tsid = factory.generate();
        assertThat(nodeOf(tsid, 10)).isEqualTo(42);
    }

    @Test
    void buildsWithEnvironmentOnly() {
        TSID.Factory factory = TsidFactories.build(null, "42");
        TSID tsid = factory.generate();
        assertThat(nodeOf(tsid, 10)).isEqualTo(42);
    }

    @Test
    void propertyTakesPrecedenceOverEnvironment() {
        TSID.Factory factory = TsidFactories.build("10", "20");
        TSID tsid = factory.generate();
        assertThat(nodeOf(tsid, 10)).isEqualTo(10);
    }

    @Test
    void blankPropertySuppressesEnvironmentFallback() {
        System.setProperty("tsid.node.count", "1");
        TSID.Factory factory = TsidFactories.build("", "not-an-integer");
        TSID tsid = factory.generate();
        assertThat(nodeOf(tsid, 0)).isZero();
    }

    @Test
    void unsetValuesBuildFactoryWithoutExplicitNode() {
        TSID.Factory factory = TsidFactories.build(null, null);
        TSID tsid = factory.generate();
        assertThat(tsid).isNotNull();
    }

    @Test
    void surroundingSpacesFailToParse() {
        assertThatThrownBy(() -> TsidFactories.build(" 42 ", null))
                .isInstanceOf(NumberFormatException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "1, 0, 0",
        "256, 255, 8",
        "1024, 1023, 10",
        "4096, 4095, 12",
        "1048576, 1048575, 20"
    })
    void acceptsBoundaryNodesForConfiguredNodeCount(int nodeCount, int maxNode, int nodeBits) {
        System.setProperty("tsid.node.count", String.valueOf(nodeCount));

        assertThat(nodeOf(TsidFactories.build("0", null).generate(), nodeBits)).isZero();
        assertThat(nodeOf(TsidFactories.build(null, String.valueOf(maxNode)).generate(), nodeBits))
                .isEqualTo(maxNode);
    }

    @ParameterizedTest
    @CsvSource({
        "1, -1",
        "1, 1",
        "256, 256",
        "257, 512",
        "1024, -1",
        "1024, 1024",
        "4096, 4096",
        "1048576, 1048576"
    })
    void rejectsNodesOutsideConfiguredNodeBitRange(int nodeCount, String node) {
        System.setProperty("tsid.node.count", String.valueOf(nodeCount));

        assertThatThrownBy(() -> TsidFactories.build(node, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Node out of range");
        assertThatThrownBy(() -> TsidFactories.build(null, node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Node out of range");
    }

    private static int nodeOf(TSID tsid, int nodeBits) {
        return (int) ((tsid.toLong() >>> (22 - nodeBits)) & ((1 << nodeBits) - 1));
    }
}
