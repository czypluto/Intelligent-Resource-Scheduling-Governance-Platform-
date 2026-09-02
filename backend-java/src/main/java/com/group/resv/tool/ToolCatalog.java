package com.group.resv.tool;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具能力目录（面向 Agent 的注册元数据，执行仍走 REST）。
 * 每个工具对应一个 REST 接口，启动时由 ToolRestValidator 校验对齐。
 */
@Component
public class ToolCatalog {

    public record ToolDescriptor(String name, String method, String path, String desc) {
    }

    public List<ToolDescriptor> tools() {
        return List.of(
                new ToolDescriptor("book_resource", "POST", "/api/seckill",
                        "为当前用户预约/抢购指定资源（resourceId）。内部已做权限校验、限流、幂等、扣库存。"),
                new ToolDescriptor("check_permission", "POST", "/api/perms/check",
                        "校验当前用户对某资源类型(resourceType)是否有预约权限，返回放行结论与原因。"),
                new ToolDescriptor("list_resources", "GET", "/api/resources",
                        "查询当前可预约的资源列表，可按 type 过滤。"));
    }
}
