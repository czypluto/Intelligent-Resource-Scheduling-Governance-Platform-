package com.group.resv.permission;

import com.group.resv.common.BizException;
import com.group.resv.domain.PermissionRule;
import com.group.resv.repo.PermissionRuleRepository;
import com.group.resv.security.AuthUser;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 权限规则判定，纯确定性逻辑，不依赖大模型。
 * 规则：requiredPositions（职级限制）与 allowedDepartments（部门限制）都为"或"上的必要条件；
 * 字段空 = 不限。
 */
@Service
public class PermissionService {

    private final PermissionRuleRepository ruleRepository;

    public PermissionService(PermissionRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public PermissionDecision decide(AuthUser user, String resourceType) {
        return ruleRepository.findByResourceType(resourceType)
                .map(rule -> evaluate(user, rule))
                .orElse(new PermissionDecision(true, "该资源未设置额外限制", ""));
    }

    /** 供交易链路内部调用，不满足直接抛 403。 */
    public void ensureAllowed(AuthUser user, String resourceType) {
        PermissionDecision d = decide(user, resourceType);
        if (!d.allowed()) {
            throw new BizException(403, d.reason());
        }
    }

    private PermissionDecision evaluate(AuthUser user, PermissionRule rule) {
        List<String> requiredPositions = split(rule.getRequiredPositions());
        List<String> allowedDepartments = split(rule.getAllowedDepartments());

        if (!requiredPositions.isEmpty() && !requiredPositions.contains(user.position())) {
            String ruleText = "仅限职级：" + String.join("、", requiredPositions);
            return new PermissionDecision(false,
                    String.format("您当前职级（%s）不可预约，%s。", user.position(), ruleText), ruleText);
        }
        if (!allowedDepartments.isEmpty() && !allowedDepartments.contains(user.department())) {
            String ruleText = "仅限部门：" + String.join("、", allowedDepartments);
            return new PermissionDecision(false,
                    String.format("您所在部门（%s）不可预约，%s。", user.department(), ruleText), ruleText);
        }
        return new PermissionDecision(true, "符合预约条件", rule.getNote());
    }

    private List<String> split(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
