/***
<p>
    Licensed under MIT License Copyright (c) 2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;

public class LogWriter {

    public static void write(Class<?> originClass, LogLevel logLevel, String message) {
        Logger logger = LoggerFactory.getLogger(originClass);
        switch (logLevel) {
            case TRACE -> logger.trace(message);
            case DEBUG -> logger.debug(message);
            case INFO -> logger.info(message);
            case WARN -> logger.warn(message);
            case ERROR, FATAL -> logger.error(message);
            default -> logger.warn("No suitable logLevel found");
        }
    }
}
