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
     * property suppresses the environment fallback. Null or empty values explicitly select a random
     * node. Surrounding spaces are not trimmed before parsing and will cause a {@link
     * NumberFormatException}.
     *
     * @return the configured identifier factory
     * @throws NumberFormatException if a non-blank node value cannot be parsed as an integer
     * @throws IllegalArgumentException if the node is outside the configured node-bit range
     */
    public static TSID.Factory fromEnvironment() {
        return build(System.getProperty("tsid.node"), System.getenv("TSID_NODE"));
    }

    static TSID.Factory build(String systemProperty, String environmentVariable) {
        String nodeStr = resolveNodeValue(systemProperty, environmentVariable);
        NodeBuilder builder = new NodeBuilder();
        int nodeLimit = builder.nodeLimit();
        if (nodeStr != null && !nodeStr.isEmpty()) {
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
