package com.group.resv.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 操作审计：记录业务写动作（谁-何时-动作-参数-结果）。失败不吞，只记录后由上层决定。 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final ActionLogRepository repository;
    private final ObjectMapper objectMapper;

    public AuditService(ActionLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(Long userId, String module, String action, String requestId,
                       Map<String, Object> params, boolean success, String err) {
        try {
            ActionLog a = new ActionLog();
            a.setUserId(userId);
            a.setModule(module);
            a.setAction(action);
            a.setRequestId(requestId);
            a.setDetail(toJson(params));
            a.setSuccess(success);
            a.setErr(err == null ? null : limit(err, 500));
            a.setCreatedAt(LocalDateTime.now());
            repository.save(a);
        } catch (Exception e) {
            // 审计失败不能影响主流程，只告警
            log.error("审计记录失败 action={} requestId={}: {}", action, requestId, e.getMessage());
        }
    }

    public void success(Long userId, String module, String action, String requestId, Map<String, Object> params) {
        record(userId, module, action, requestId, params, true, null);
    }

    public void fail(Long userId, String module, String action, String requestId,
                     Map<String, Object> params, String err) {
        record(userId, module, action, requestId, params, false, err);
    }

    private String toJson(Map<String, Object> params) {
        if (params == null) {
            return null;
        }
        // 可控字段白名单，避免把敏感数据整包落库
        Map<String, Object> safe = new LinkedHashMap<>();
        String[] allowed = {"tripId", "seatClass", "fromStationId", "toStationId", "from", "to",
                "orderNo", "requestId", "status", "priceCents", "date", "trainCode"};
        for (String k : allowed) {
            if (params.containsKey(k)) {
                safe.put(k, params.get(k));
            }
        }
        try {
            return limit(objectMapper.writeValueAsString(safe), 2000);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String limit(String s, int n) {
        if (s == null || s.length() <= n) {
            return s;
        }
        return s.substring(0, n);
    }
}
