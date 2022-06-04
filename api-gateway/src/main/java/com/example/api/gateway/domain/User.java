/* Licensed under Apache-2.0 2021 */
package com.example.api.gateway.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document
public class User {

    @Id private String id;

    @Indexed private String username;

    @JsonIgnore private String password;

    @Email private String email;

    @Builder.Default() private boolean active = true;

    @Builder.Default() private List<String> roles = new ArrayList<>();
}
