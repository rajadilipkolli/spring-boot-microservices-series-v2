/* Licensed under Apache-2.0 2021-2022 */
package com.example.orderservice.utils;

public final class AppConstants {
    public static final String PROFILE_LOCAL = "local";
    public static final String PROFILE_PROD = "prod";
    public static final String PROFILE_NOT_PROD = "!" + PROFILE_PROD;
    public static final String PROFILE_TEST = "test";
    public static final String ORDERS_TOPIC = "orders";
    public static final String PAYMENT_ORDERS_TOPIC = "payment-orders";
    public static final String STOCK_ORDERS_TOPIC = "stock-orders";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "10";
    public static final String DEFAULT_SORT_BY = "id";
    public static final String DEFAULT_SORT_DIRECTION = "asc";
}
