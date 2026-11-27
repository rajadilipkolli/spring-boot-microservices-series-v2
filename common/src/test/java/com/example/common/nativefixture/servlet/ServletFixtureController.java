/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.servlet;

import com.example.common.dto.PagedResult;
import com.example.common.logging.Loggable;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ServletFixtureController {

    @Loggable
    @GetMapping("/api/fixtures")
    public PagedResult<FixtureItem> getFixtures() {
        List<FixtureItem> content = List.of(new FixtureItem("1", "item-1"));
        PageImpl<FixtureItem> page = new PageImpl<>(content, PageRequest.of(0, 10), 1);
        return new PagedResult<>(page);
    }
}
