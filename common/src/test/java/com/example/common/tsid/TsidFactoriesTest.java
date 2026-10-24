/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.tsid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hypersistence.tsid.TSID;
import org.junit.jupiter.api.Test;

class TsidFactoriesTest {

    @Test
    void buildsWithPropertyOnly() {
        TSID.Factory factory = TsidFactories.build("42", null);
        TSID tsid = factory.generate();
        assertThat(tsid).isNotNull();
    }

    @Test
    void buildsWithEnvironmentOnly() {
        TSID.Factory factory = TsidFactories.build(null, "42");
        TSID tsid = factory.generate();
        assertThat(tsid).isNotNull();
    }

    @Test
    void propertyTakesPrecedenceOverEnvironment() {
        TSID.Factory factory = TsidFactories.build("10", "20");
        TSID tsid = factory.generate();
        assertThat(tsid).isNotNull();
    }

    @Test
    void blankPropertySuppressesEnvironmentFallback() {
        TSID.Factory factory = TsidFactories.build("", "20");
        TSID tsid = factory.generate();
        assertThat(tsid).isNotNull();
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
}
