package com.footballmanager.domain.port.in.auth;

public record AuthRegisterCommand(String email, String username, String password) {}
