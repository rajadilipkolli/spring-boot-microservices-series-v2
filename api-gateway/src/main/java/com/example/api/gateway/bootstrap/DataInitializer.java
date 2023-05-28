/* Licensed under Apache-2.0 2021-2022 */
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
