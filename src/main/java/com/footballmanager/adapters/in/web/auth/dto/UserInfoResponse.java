package com.footballmanager.adapters.in.web.auth.dto;

public class UserInfoResponse {
    public String id;
    public String email;
    public String username;
    // displayName → email → username). Until we add a separate friendlier displayName field
    // (e.g. firstName + lastName), this is a stable 1:1 alias so the frontend can rely on it.
    public String displayName;
    public String teamId;
    public String teamName;
}
