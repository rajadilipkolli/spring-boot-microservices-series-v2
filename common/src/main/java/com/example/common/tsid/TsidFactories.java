/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.tsid;

import io.hypersistence.tsid.TSID;

public final class TsidFactories {

    private TsidFactories() {}

    /**
     * Builds a {@link TSID.Factory} using {@code tsid.node} system property, falling back to {@code
     * TSID_NODE} environment variable only when the system property is absent. A blank system
     * property suppresses the environment fallback. Null or blank values build a factory without an
     * explicit node. Surrounding spaces are not trimmed before parsing and will cause a {@link
     * NumberFormatException}.
     *
     * @return the configured identifier factory
     * @throws NumberFormatException if a non-blank node value cannot be parsed as an integer
     */
    public static TSID.Factory fromEnvironment() {
        return build(System.getProperty("tsid.node"), System.getenv("TSID_NODE"));
    }

    static TSID.Factory build(String systemProperty, String environmentVariable) {
        String nodeStr = resolveNodeValue(systemProperty, environmentVariable);
        TSID.Factory.Builder builder = TSID.Factory.builder();
        if (nodeStr != null && !nodeStr.isEmpty()) {
            builder.withNode(Integer.parseInt(nodeStr));
        }
        return builder.build();
    }

    private static String resolveNodeValue(String systemProperty, String environmentVariable) {
        if (systemProperty != null) {
            return systemProperty;
        }
        return environmentVariable;
    }
}
