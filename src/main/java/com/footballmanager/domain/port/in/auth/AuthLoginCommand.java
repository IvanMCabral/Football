package com.footballmanager.domain.port.in.auth;

public record AuthLoginCommand(String email, String password) {}
