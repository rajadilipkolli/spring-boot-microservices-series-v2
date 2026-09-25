package data;

import static io.gatling.javaapi.core.CoreDsl.csv;

import io.gatling.javaapi.core.FeederBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class Feeders {

    public static FeederBuilder<String> enhancedProductFeeder() {
        return csv("data/products.csv").random();
    }

    /** Feeder for valid product data used in resilience scenarios. */
    public static FeederBuilder<String> validProductFeeder() {
        return csv("data/products.csv").random();
    }

    /**
     * Feeder for intentionally invalid product data used in error-handling resilience scenarios.
     * All values violate business constraints (empty code, oversized name, negative price).
     */
    public static Iterator<Map<String, Object>> invalidProductFeeder() {
        return Stream.generate(
                        () -> {
                            Map<String, Object> data = new HashMap<>();
                            data.put("productCode", "");
                            data.put(
                                    "productName",
                                    ThreadLocalRandom.current().nextBoolean()
                                            ? ""
                                            : "x".repeat(300));
                            data.put(
                                    "price",
                                    ThreadLocalRandom.current().nextBoolean() ? -50.0 : 0.0);
                            data.put("quantity", -10);
                            return data;
                        })
                .iterator();
    }
}
