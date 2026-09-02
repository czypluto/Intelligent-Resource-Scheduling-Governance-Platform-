package com.group.resv.security;

/**
 * 从 JWT 解析出的当前登录人身份，权限判定以此为准。
 */
public record AuthUser(
        Long userId,
        String username,
        String name,
        String department,
        String position,
        String role) {
}
