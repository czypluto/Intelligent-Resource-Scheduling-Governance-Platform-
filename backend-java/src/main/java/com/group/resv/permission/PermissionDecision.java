package com.group.resv.permission;

/**
 * 权限判定结果。reason 用于给用户/模型解释，结论以 allowed 为准（确定性）。
 */
public record PermissionDecision(boolean allowed, String reason, String rule) {
}
