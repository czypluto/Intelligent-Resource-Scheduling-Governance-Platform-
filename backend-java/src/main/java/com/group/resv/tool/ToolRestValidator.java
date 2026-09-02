package com.group.resv.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Set;

/**
 * 启动校验：工具目录里的每个工具都必须存在对应的 REST 接口，防止双契约漂移。
 */
@Component
@Order(30)
public class ToolRestValidator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ToolRestValidator.class);

    private final ToolCatalog catalog;
    private final RequestMappingHandlerMapping handlerMapping;

    public ToolRestValidator(ToolCatalog catalog, RequestMappingHandlerMapping handlerMapping) {
        this.catalog = catalog;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<RequestMappingInfo> infos = handlerMapping.getHandlerMethods().keySet();
        for (ToolCatalog.ToolDescriptor tool : catalog.tools()) {
            boolean matched = infos.stream().anyMatch(info -> matches(info, tool));
            if (matched) {
                log.info("工具 -> REST 已对齐：{} {}", tool.method(), tool.path());
            } else {
                log.error("工具缺少对应 REST 接口：{} {} {}", tool.name(), tool.method(), tool.path());
            }
        }
    }

    private boolean matches(RequestMappingInfo info, ToolCatalog.ToolDescriptor tool) {
        if (!methodMatches(info, tool)) {
            return false;
        }
        Set<String> paths = new java.util.HashSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatterns()
                    .forEach(p -> paths.add(p.getPatternString()));
        }
        if (info.getPatternsCondition() != null) {
            paths.addAll(info.getPatternsCondition().getPatterns());
        }
        return paths.contains(tool.path());
    }

    private boolean methodMatches(RequestMappingInfo info, ToolCatalog.ToolDescriptor tool) {
        var methods = info.getMethodsCondition().getMethods();
        if (methods == null || methods.isEmpty()) {
            return true; // 未限定方法视为匹配
        }
        return methods.stream().anyMatch(m -> m.name().equals(tool.method()));
    }
}
