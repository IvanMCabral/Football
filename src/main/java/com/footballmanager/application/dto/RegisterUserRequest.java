package com.footballmanager.application.dto;

public record RegisterUserRequest(
    String email,
    String username,
    String password
) {}
