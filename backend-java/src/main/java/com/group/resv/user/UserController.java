package com.group.resv.user;

import com.group.resv.common.ApiResult;
import com.group.resv.common.BizException;
import com.group.resv.domain.Contact;
import com.group.resv.domain.User;
import com.group.resv.repo.ContactRepository;
import com.group.resv.repo.UserRepository;
import com.group.resv.security.AuthUser;
import com.group.resv.security.SecurityUtil;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户资料 + 常用联系人（乘车人）。身份从 Token 取，禁止越权改他人。
 */
@RestController
@RequestMapping("/api/user")
@Transactional
public class UserController {

    private final UserRepository userRepository;
    private final ContactRepository contactRepository;

    public UserController(UserRepository userRepository, ContactRepository contactRepository) {
        this.userRepository = userRepository;
        this.contactRepository = contactRepository;
    }

    // ---------- 个人资料 ----------

    public record ProfileBody(Integer age, String gender, String idType, String idNo) {
    }

    @GetMapping("/profile")
    public ApiResult<User> profile() {
        return ApiResult.ok(me());
    }

    @PutMapping("/profile")
    public ApiResult<User> updateProfile(@RequestBody ProfileBody body) {
        User u = me();
        if (body.age() != null) u.setAge(body.age());
        if (body.gender() != null) u.setGender(body.gender());
        if (body.idType() != null) u.setIdType(body.idType());
        if (body.idNo() != null) u.setIdNo(body.idNo());
        return ApiResult.ok(userRepository.save(u));
    }

    // ---------- 常用联系人 ----------

    public record ContactBody(String name, String idType, String idNo, String phone) {
    }

    @GetMapping("/contacts")
    public ApiResult<List<Contact>> contacts() {
        return ApiResult.ok(contactRepository.findByUserIdOrderByIdAsc(currentId()));
    }

    @PostMapping("/contacts")
    public ApiResult<Contact> addContact(@RequestBody ContactBody body) {
        if (body.name() == null || body.name().isBlank()) {
            throw new BizException(400, "联系人姓名不能为空");
        }
        Contact c = new Contact();
        c.setUserId(currentId());
        c.setName(body.name());
        c.setIdType(body.idType() == null ? "身份证" : body.idType());
        c.setIdNo(body.idNo());
        c.setPhone(body.phone());
        return ApiResult.ok(contactRepository.save(c));
    }

    @PutMapping("/contacts/{id}")
    public ApiResult<Contact> updateContact(@PathVariable Long id, @RequestBody ContactBody body) {
        Contact c = contactRepository.findByIdAndUserId(id, currentId())
                .orElseThrow(() -> new BizException(404, "联系人不存在"));
        if (body.name() != null) c.setName(body.name());
        if (body.idType() != null) c.setIdType(body.idType());
        if (body.idNo() != null) c.setIdNo(body.idNo());
        if (body.phone() != null) c.setPhone(body.phone());
        return ApiResult.ok(contactRepository.save(c));
    }

    @DeleteMapping("/contacts/{id}")
    public ApiResult<Void> deleteContact(@PathVariable Long id) {
        Contact c = contactRepository.findByIdAndUserId(id, currentId())
                .orElseThrow(() -> new BizException(404, "联系人不存在"));
        contactRepository.delete(c);
        return ApiResult.ok();
    }

    private AuthUser auth() {
        return SecurityUtil.current();
    }

    private Long currentId() {
        return auth().userId();
    }

    private User me() {
        return userRepository.findById(currentId())
                .orElseThrow(() -> new BizException(404, "用户不存在"));
    }
}
