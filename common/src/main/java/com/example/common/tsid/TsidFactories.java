/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.tsid;

import io.hypersistence.tsid.TSID;
import java.util.concurrent.ThreadLocalRandom;

public final class TsidFactories {

    private TsidFactories() {}

    /**
     * Builds a {@link TSID.Factory} using {@code tsid.node} system property, falling back to {@code
     * TSID_NODE} environment variable only when the system property is absent. A blank system
     * property suppresses the environment fallback. Null or blank values explicitly select a random
     * node. Surrounding spaces on nonblank values are not trimmed before parsing and will cause a
     * {@link NumberFormatException}.
     *
     * @return the configured identifier factory
     * @throws NumberFormatException if a non-blank node value cannot be parsed as an integer
     * @throws IllegalArgumentException if the node is outside the configured node-bit range or the
     *     builder's node count requires an unsupported bit length
     */
    public static TSID.Factory fromEnvironment() {
        return build(System.getProperty("tsid.node"), System.getenv("TSID_NODE"));
    }

    /**
     * Builds a factory from the supplied node settings, using the environment value only when the
     * system property value is null. A null or blank selected value chooses a random node within
     * the configured node-bit range.
     *
     * @param systemProperty value of {@code tsid.node}, or null if absent
     * @param environmentVariable value of {@code TSID_NODE}, or null if absent
     * @return a new identifier factory
     * @throws NumberFormatException if the selected nonblank value is not an integer in the {@code
     *     int} range; surrounding whitespace is not trimmed
     * @throws IllegalArgumentException if the node is negative or exceeds the configured node-bit
     *     range, or the builder's node count requires an unsupported bit length
     */
    static TSID.Factory build(String systemProperty, String environmentVariable) {
        String nodeStr = resolveNodeValue(systemProperty, environmentVariable);
        NodeBuilder builder = new NodeBuilder();
        int nodeLimit = builder.nodeLimit();
        if (nodeStr != null && !nodeStr.isBlank()) {
            int node = Integer.parseInt(nodeStr);
            if (node < 0 || node >= nodeLimit) {
                throw new IllegalArgumentException(
                        "Node out of range [0, " + (nodeLimit - 1) + "]: " + node);
            }
            builder.withNode(node);
        } else {
            builder.withNode(ThreadLocalRandom.current().nextInt(nodeLimit));
        }
        return builder.build();
    }

    private static final class NodeBuilder extends TSID.Factory.Builder {

        int nodeLimit() {
            return 1 << getNodeBits();
        }
    }

    private static String resolveNodeValue(String systemProperty, String environmentVariable) {
        if (systemProperty != null) {
            return systemProperty;
        }
        return environmentVariable;
    }
}
