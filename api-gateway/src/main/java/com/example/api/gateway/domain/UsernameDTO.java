package com.example.api.gateway.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor(onConstructor = @__({@JsonCreator}))
@ToString
public class UsernameDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;
}
