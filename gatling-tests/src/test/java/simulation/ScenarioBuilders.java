package simulation;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.http.HttpDsl.header;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ChainBuilder;

/**
 * Utility class containing reusable request chains for Gatling simulations. Each chain executes
 * exactly once per invocation.
 */
public class ScenarioBuilders {

    /** Chain to create a new product in the catalog. */
    public static ChainBuilder createProductChain() {
        return exec(http("Create product")
                        .post("/catalog-service/api/catalog")
                        .body(
                                StringBody(
                                        """
                    {
                      "productCode": "#{productCode}",
                      "productName": "#{productName}",
                      "price": #{price},
                      "description": "Performance test product"
                    }
                    """))
                        .asJson()
                        .check(status().is(201))
                        .check(header("location").saveAs("productLocation")))
                .pause(1);
    }

    /** Chain to retrieve product details by product code. */
    public static ChainBuilder getProductChain() {
        return exec(http("Get product detail")
                        .get("/catalog-service/api/catalog/productCode/#{productCode}")
                        .check(status().is(200)))
                .pause(1);
    }

    /** Chain to update inventory for a product. */
    public static ChainBuilder updateInventoryChain() {
        return exec(http("Update inventory")
                        .put("/inventory-service/api/inventory/product/#{productCode}")
                        .body(
                                StringBody(
                                        """
                    {
                      "productCode": "#{productCode}",
                      "quantity": #{quantity}
                    }
                    """))
                        .asJson()
                        .check(status().is(200)))
                .pause(1);
    }

    /** Chain to create a new order. */
    public static ChainBuilder createOrderChain() {
        return exec(http("Place order")
                        .post("/order-service/api/orders")
                        .body(
                                StringBody(
                                        """
                    {
                      "customerId": #{customerId},
                      "shippingAddress": {
                        "street": "#{street}",
                        "city": "#{city}",
                        "zipCode": "#{zipCode}",
                        "country": "#{country}"
                      },
                      "items": [
                        {
                          "productCode": "#{productCode}",
                          "quantity": #{quantity},
                          "productPrice": #{price}
                        }
                      ]
                    }
                    """))
                        .asJson()
                        .check(status().is(201)))
                .pause(1);
    }

    /** Chain to browse the product catalog. */
    public static ChainBuilder browseChain() {
        return exec(http("Browse catalog")
                        .get("/catalog-service/api/catalog?pageNo=0&pageSize=10")
                        .check(status().is(200)))
                .pause(1);
    }

    /** Chain to search for products. */
    public static ChainBuilder searchChain() {
        return exec(http("Search products")
                        .get("/catalog-service/api/catalog/search?term=product")
                        .check(status().is(200)))
                .pause(1);
    }
}
