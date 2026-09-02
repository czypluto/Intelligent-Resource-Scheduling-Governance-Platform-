package com.group.resv.permission;

import com.group.resv.common.ApiResult;
import com.group.resv.security.AuthUser;
import com.group.resv.security.SecurityUtil;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 供 Python/Agent 调用：交易前做确定性权限校验（Java 二次校验）。
 */
@RestController
@RequestMapping("/api/perms")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    public record PermCheckRequest(String resourceType) {
    }

    @PostMapping("/check")
    public ApiResult<PermissionDecision> check(@RequestBody PermCheckRequest req) {
        AuthUser user = SecurityUtil.current();
        return ApiResult.ok(permissionService.decide(user, req.resourceType()));
    }
}
