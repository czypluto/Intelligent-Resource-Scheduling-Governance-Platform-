package com.group.resv.railway;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 高危管理操作二次确认（人工接管式审批）。
 * 流程：请求确认码 -> 系统签发一次性 token(2 分钟) -> 执行高危动作时带上 token 校验，验过即废。
 * 语义：即便角色是 ADMIN，破坏性操作仍需独立的一次性确认，防止误点/误触。
 */
@Service
public class AdminConfirmService {

    private static final Duration TTL = Duration.ofMinutes(2);

    private final StringRedisTemplate redis;

    public AdminConfirmService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public record ConfirmTicket(String token, long expiresSeconds) {
    }

    /** 为高危操作签发确认码，绑定 操作+目标。 */
    public ConfirmTicket issue(String op, Long target) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(key(token), op + ":" + target, TTL);
        return new ConfirmTicket(token, TTL.toSeconds());
    }

    /** 校验并一次性消费确认码。匹配则删除并放行。 */
    public boolean verifyAndConsume(String op, Long target, String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        String expect = op + ":" + target;
        String got = redis.opsForValue().get(key(token));
        if (expect.equals(got)) {
            redis.delete(key(token));
            return true;
        }
        return false;
    }

    private String key(String token) {
        return "rv:confirm:" + token;
    }
}
