package com.footballmanager.adapters.in.web.auth.dto;

public record LoginRequest(
    String email,
    String password
) {}
