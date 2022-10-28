/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.web;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest implements Serializable {

    private static final long serialVersionUID = -6986746375915710855L;

    @NotBlank private String username;

    @NotBlank private String password;
}
