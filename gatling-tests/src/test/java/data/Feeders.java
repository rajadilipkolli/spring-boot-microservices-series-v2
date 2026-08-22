package data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class Feeders {

    public static Iterator<Map<String, Object>> enhancedProductFeeder() {
        return Stream.generate(
                        () -> {
                            ThreadLocalRandom random = ThreadLocalRandom.current();
                            Map<String, Object> data = new HashMap<>();
                            // Product information
                            data.put(
                                    "productCode",
                                    "P"
                                            + String.format("%06d", random.nextInt(10, 100_000))
                                            + "-"
                                            + System.nanoTime());
                            data.put("productName", "Product-" + random.nextInt(1000, 10000));
                            data.put("customerId", random.nextInt(101, 1000));
                            data.put("price", random.nextDouble(10, 1000));
                            data.put("quantity", random.nextInt(1, 50));

                            // Address information
                            data.put("street", "Street " + random.nextInt(1, 100));
                            data.put("city", "City " + random.nextInt(1, 20));
                            data.put(
                                    "zipCode", String.format("%05d", random.nextInt(10000, 99999)));
                            data.put("country", "Country " + random.nextInt(1, 10));

                            return data;
                        })
                .iterator();
    }
}
