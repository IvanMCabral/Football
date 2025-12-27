package com.footballmanager.application.dto;

public record LoginRequest(
    String email,
    String password
) {}
