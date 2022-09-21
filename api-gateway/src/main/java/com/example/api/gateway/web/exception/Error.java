/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.web.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.io.Serializable;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
class Error implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String path;

    private final String code;

    private final String message;

    @JsonCreator
    Error(String path, String code, String message) {
        this.path = path;
        this.code = code;
        this.message = message;
    }
}
