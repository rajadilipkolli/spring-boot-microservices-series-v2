/***
<p>
    Licensed under MIT License Copyright (c) 2021-2022 Raja Kolli.
</p>
***/

package com.example.api.gateway.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("start data initialization...");
    }
}
