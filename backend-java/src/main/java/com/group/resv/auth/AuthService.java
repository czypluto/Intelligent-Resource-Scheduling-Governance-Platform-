package com.group.resv.auth;

import com.group.resv.common.BizException;
import com.group.resv.domain.User;
import com.group.resv.repo.UserRepository;
import com.group.resv.security.AuthUser;
import com.group.resv.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BizException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        AuthUser identity = new AuthUser(user.getId(), user.getUsername(), user.getName(),
                user.getDepartment(), user.getPosition(), user.getRole());
        return new LoginResponse(jwtUtil.generate(identity), user.getId(), user.getUsername(),
                user.getName(), user.getDepartment(), user.getPosition(), user.getRole());
    }
}
