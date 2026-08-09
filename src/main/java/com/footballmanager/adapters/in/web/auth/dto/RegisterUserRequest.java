package com.footballmanager.adapters.in.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RegisterUserRequest(
    @JsonProperty("email") String email,
    @JsonProperty("username") String username,
    @JsonProperty("password") String password
) {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RegisterUserRequest {
    }
}
