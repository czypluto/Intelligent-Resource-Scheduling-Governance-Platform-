package com.group.resv.config;

import com.group.resv.domain.PermissionRule;
import com.group.resv.domain.ResvResource;
import com.group.resv.domain.User;
import com.group.resv.redis.StockService;
import com.group.resv.repo.PermissionRuleRepository;
import com.group.resv.repo.ResvResourceRepository;
import com.group.resv.repo.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 首次启动播种演示数据（空表时执行）并预热库存。
 * 正式环境改为初始化 SQL 与独立运维脚本。
 */
@Component
@Order(10)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final ResvResourceRepository resourceRepository;
    private final PermissionRuleRepository ruleRepository;
    private final PasswordEncoder passwordEncoder;
    private final StockService stockService;

    public DataInitializer(UserRepository userRepository,
                           ResvResourceRepository resourceRepository,
                           PermissionRuleRepository ruleRepository,
                           PasswordEncoder passwordEncoder,
                           StockService stockService) {
        this.userRepository = userRepository;
        this.resourceRepository = resourceRepository;
        this.ruleRepository = ruleRepository;
        this.passwordEncoder = passwordEncoder;
        this.stockService = stockService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedUsers();
        seedResources();
        seedRules();
        warmStock();
    }

    private void seedUsers() {
        if (userRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<User> users = List.of(
                user("wangzong", "王建国", "综合管理部", "高管", "ADMIN"),
                user("zhanggong", "张伟", "技术部", "高级工程师", "EMPLOYEE"),
                user("lizhu", "李琳", "人事部", "实习生", "EMPLOYEE"));
        for (User u : users) {
            u.setCreatedAt(now);
        }
        userRepository.saveAll(users);
        log.info("已播种 {} 个演示用户（密码均为 123456）", users.size());
    }

    private void seedResources() {
        if (resourceRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ResvResource> resources = List.of(
                resource("EXEC_SHUTTLE", "总裁班车", "周一至周五晚 18:30 总裁班车", 45),
                resource("SHUTTLE", "员工班车", "周一至周五通勤班车", 90),
                resource("MEETING_A", "第一会议室", "三楼东侧，可坐 12 人", 5),
                resource("WELFARE", "季度福利礼包", "每季度生活用品礼包", 200));
        for (ResvResource r : resources) {
            r.setStatus("OPEN");
            r.setCreatedAt(now);
        }
        resourceRepository.saveAll(resources);
        log.info("已播种 {} 个演示资源", resources.size());
    }

    private void seedRules() {
        if (ruleRepository.count() > 0) {
            return;
        }
        List<PermissionRule> rules = List.of(
                rule("EXEC_SHUTTLE", "高管", "", "总裁班车仅限高管"),
                rule("MEETING_A", "", "技术部,综合管理部", "第一会议室仅限指定部门"),
                rule("WELFARE", "", "", "全员可约"));
        ruleRepository.saveAll(rules);
        log.info("已播种 {} 条权限规则", rules.size());
    }

    private void warmStock() {
        int ok = 0;
        for (ResvResource r : resourceRepository.findAll()) {
            if (stockService.prepare(r)) {
                ok++;
            }
        }
        log.info("库存预热完成：{}/{}", ok, resourceRepository.count());
    }

    private User user(String username, String name, String department, String position, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setName(name);
        u.setDepartment(department);
        u.setPosition(position);
        u.setRole(role);
        return u;
    }

    private ResvResource resource(String type, String name, String desc, int stock) {
        ResvResource r = new ResvResource();
        r.setType(type);
        r.setName(name);
        r.setDescription(desc);
        r.setTotalStock(stock);
        return r;
    }

    private PermissionRule rule(String type, String positions, String departments, String note) {
        PermissionRule r = new PermissionRule();
        r.setResourceType(type);
        r.setRequiredPositions(positions == null || positions.isEmpty() ? null : positions);
        r.setAllowedDepartments(departments == null || departments.isEmpty() ? null : departments);
        r.setNote(note);
        return r;
    }
}
