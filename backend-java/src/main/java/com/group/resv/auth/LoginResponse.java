package com.group.resv.auth;

public record LoginResponse(
        String token,
        Long userId,
        String username,
        String name,
        String department,
        String position,
        String role) {
}
