/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.nativefixture.reactive;

import com.example.common.dto.PagedResult;
import com.example.common.logging.Loggable;
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class ReactiveFixtureController {

    @Loggable
    @GetMapping("/api/reactive-fixtures")
    public Mono<PagedResult<ReactiveFixtureItem>> getFixtures() {
        List<ReactiveFixtureItem> content = List.of(new ReactiveFixtureItem("1", "item-1"));
        PageImpl<ReactiveFixtureItem> page = new PageImpl<>(content, PageRequest.of(0, 10), 1);
        return Mono.just(new PagedResult<>(page));
    }
}
