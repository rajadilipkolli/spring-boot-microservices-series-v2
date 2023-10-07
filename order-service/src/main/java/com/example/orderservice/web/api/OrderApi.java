/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.web.api;

import com.example.common.dtos.OrderDto;
import com.example.orderservice.utils.AppConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;

public interface OrderApi {

    @Operation(summary = "fetches all orders from Kinesis Stream")
    List<OrderDto> all(
            @Parameter(name = "pageNo", example = AppConstants.DEFAULT_PAGE_SIZE) int pageNo,
            @Parameter(name = "pageSize", example = AppConstants.DEFAULT_PAGE_SIZE) int pageSize);
}
