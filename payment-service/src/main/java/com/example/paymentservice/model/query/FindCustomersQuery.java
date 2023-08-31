/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.model.query;

public record FindCustomersQuery(int pageNo, int pageSize, String sortBy, String sortDir) {}
