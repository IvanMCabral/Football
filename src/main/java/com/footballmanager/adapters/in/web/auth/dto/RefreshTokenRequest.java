package com.footballmanager.adapters.in.web.auth.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RefreshTokenRequest(
    @JsonProperty("refreshToken") String refreshToken
) {
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public RefreshTokenRequest {
    }
}
