package com.group.resv.config;

import com.group.resv.domain.User;
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

/** 空表时播种演示用户。铁路线路数据由 railway.RailwayDataInitializer 播种。 */
@Component
@Order(10)
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<User> users = List.of(
                user("wangzong", "王建国", "ADMIN"),
                user("zhanggong", "张伟", "EMPLOYEE"),
                user("lizhu", "李琳", "EMPLOYEE"));
        for (User u : users) {
            u.setCreatedAt(now);
        }
        userRepository.saveAll(users);
        log.info("已播种 {} 个演示用户（密码 123456）", users.size());
    }

    private User user(String username, String name, String role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setName(name);
        u.setRole(role);
        return u;
    }
}
