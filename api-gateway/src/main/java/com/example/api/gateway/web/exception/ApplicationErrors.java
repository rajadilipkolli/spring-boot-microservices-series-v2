package com.example.api.gateway.web.exception;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ApplicationErrors implements Serializable {

    private final String code;

    private final String message;

    private final List<Error> errors = new ArrayList<>();

    @JsonCreator
    public ApplicationErrors(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public void add(String path, String code, String message) {
        this.errors.add(new Error(path, code, message));
    }
}
