package com.itgeo.fitmate.api.auth.application.impl;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.itgeo.fitmate.api.auth.application.EmailCodeService;
import com.itgeo.fitmate.api.auth.application.UserService;
import com.itgeo.fitmate.api.auth.dto.LoginUserInfo;
import com.itgeo.fitmate.api.auth.dto.UserLoginResponse;
import com.itgeo.fitmate.api.auth.dto.UserPreferenceItem;
import com.itgeo.fitmate.api.auth.dto.UserProfileResponse;
import com.itgeo.fitmate.api.auth.dto.UserProfileUpdateRequest;
import com.itgeo.fitmate.api.auth.infrastructure.entity.User;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserLoginSession;
import com.itgeo.fitmate.api.auth.infrastructure.entity.UserPreference;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserLoginSessionMapper;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserMapper;
import com.itgeo.fitmate.api.auth.infrastructure.mapper.UserPreferenceMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户登录服务实现，负责验证码登录与登录会话维护。
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService {

    private static final String LOGIN_CODE_KEY_PREFIX = "fitmate:dev:auth:sms-code:";
    private static final long LOGIN_CODE_TTL_MINUTES = 5L;
    private static final long LOGIN_SESSION_TTL_DAYS = 7L;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 密码规则：8-32 位，必须同时包含字母和数字。 */
    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,32}$");

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserLoginSessionMapper userLoginSessionMapper;
    @Resource
    private Environment environment;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private UserPreferenceMapper userPreferenceMapper;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 发送登录验证码。
     * 处理流程：
     * 1. 校验手机号；
     * 2. 生成验证码并写入 Redis；
     * 3. 按运行环境输出日志。
     */
    @Override
    public void sendCode(String phone) {
        // 校验手机号格式。
        validatePhone(phone);

        // 生成验证码并写入 Redis，使用固定 TTL 控制有效期。
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                buildLoginCodeKey(phone),
                code,
                LOGIN_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );

        // 开发环境打印验证码，其他环境只记录手机号，避免敏感信息泄露。
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            log.info("发送登录验证码成功，phone={}, code={}", phone, code);
        } else {
            log.info("发送登录验证码成功，phone={}", phone);
        }
    }

    /**
     * 验证码登录。
     * 处理流程：
     * 1. 校验手机号与验证码参数；
     * 2. 校验并消费缓存验证码；
     * 3. 查询或创建用户并检查账号状态；
     * 4. 更新登录时间并创建登录会话；
     * 5. 组装并返回登录结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginResponse login(String phone, String code, String clientIp, String userAgent) {
        // 校验手机号和验证码参数。
        validatePhone(phone);
        if (StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }

        // 校验缓存验证码并在成功后立即删除，避免重复使用。
        String loginCodeKey = buildLoginCodeKey(phone);
        String cacheCode = stringRedisTemplate.opsForValue().get(loginCodeKey);
        if (StrUtil.isBlank(cacheCode) || !StrUtil.equals(cacheCode, code)) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        stringRedisTemplate.delete(loginCodeKey);

        // 查询或创建用户，并校验账号状态是否允许登录。
        User user = queryUserByPhone(phone);
        boolean isNewUser = false;
        if (user == null) {
            user = createUserWithPhone(phone);
            isNewUser = true;
        }

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("当前用户已被禁用");
        }

        // 更新最近登录时间，创建新的登录会话并生成返回令牌。
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        userMapper.updateById(user);

        String token = IdUtil.fastSimpleUUID();
        LocalDateTime expiredAt = now.plusDays(LOGIN_SESSION_TTL_DAYS);

        UserLoginSession session = new UserLoginSession();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(SecureUtil.sha256(token));
        session.setClientIp(trimToLength(clientIp, 64));
        session.setUserAgent(trimToLength(userAgent, 255));
        session.setExpiredAt(expiredAt);
        userLoginSessionMapper.insert(session);

        // 组装并返回登录响应。
        return buildLoginResponse(user, token, expiredAt, isNewUser);
    }

    /**
     * 发送邮箱登录验证码。
     * 委托 EmailCodeService 完成邮箱校验、验证码生成、Redis 写入与邮件发送。
     */
    @Override
    public void sendEmailCode(String email) {
        emailCodeService.sendCode(email);
    }

    /**
     * 邮箱验证码 + 密码登录。
     * 处理流程：
     * 1. 校验邮箱与密码参数；
     * 2. 校验并消费邮箱验证码；
     * 3. 查询或创建用户并校验账号状态与密码；
     * 4. 更新登录时间并创建登录会话；
     * 5. 组装并返回登录结果。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginResponse emailLogin(String email, String code, String password, String clientIp, String userAgent) {
        // 校验邮箱和密码参数。
        validateEmail(email);
        validatePassword(password);

        // 校验并消费缓存验证码。
        emailCodeService.verifyAndConsume(email, code);

        // 查询或创建用户，并校验账号状态与密码。
        User user = queryUserByEmail(email);
        // 兼容历史数据：可能存在 username=email 但 email 字段为空的记录，回退按用户名查询并补全 email。
        if (user == null) {
            User legacyUser = queryUserByUsername(email);
            if (legacyUser != null && StrUtil.isBlank(legacyUser.getEmail())) {
                legacyUser.setEmail(email);
                userMapper.updateById(legacyUser);
                user = legacyUser;
            }
        }
        boolean isNewUser = false;
        if (user == null) {
            user = createUserWithEmail(email, password);
            isNewUser = true;
        } else {
            if (user.getStatus() != null && user.getStatus() == 0) {
                throw new IllegalArgumentException("当前用户已被禁用");
            }
            if (StrUtil.isBlank(user.getPasswordHash())) {
                // 旧账号未设置密码，按本次输入补齐，便于后续登录校验。
                user.setPasswordHash(BCrypt.hashpw(password));
                userMapper.updateById(user);
            } else if (!BCrypt.checkpw(password, user.getPasswordHash())) {
                throw new IllegalArgumentException("密码错误");
            }
        }

        // 更新最近登录时间，创建新的登录会话并生成返回令牌。
        LocalDateTime now = LocalDateTime.now();
        user.setLastLoginAt(now);
        userMapper.updateById(user);

        String token = IdUtil.fastSimpleUUID();
        LocalDateTime expiredAt = now.plusDays(LOGIN_SESSION_TTL_DAYS);

        UserLoginSession session = new UserLoginSession();
        session.setUserId(user.getId());
        session.setRefreshTokenHash(SecureUtil.sha256(token));
        session.setClientIp(trimToLength(clientIp, 64));
        session.setUserAgent(trimToLength(userAgent, 255));
        session.setExpiredAt(expiredAt);
        userLoginSessionMapper.insert(session);

        return buildLoginResponse(user, token, expiredAt, isNewUser);
    }

    /**
     * 退出登录。
     * 处理流程：
     * 1. 校验 token 并查询有效会话；
     * 2. 找到会话则标记失效，未找到则直接返回。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String token) {
        // 校验令牌是否为空。
        if (StrUtil.isBlank(token)) {
            throw new IllegalArgumentException("登录状态已失效");
        }

        // 查询当前令牌对应的有效会话，存在则标记撤销。
        UserLoginSession session = userLoginSessionMapper.selectOne(new LambdaQueryWrapper<UserLoginSession>()
                .eq(UserLoginSession::getRefreshTokenHash, buildRefreshTokenHash(token))
                .isNull(UserLoginSession::getRevokedAt)
                .last("limit 1"));
        if (session == null) {
            log.info("退出登录时未匹配到有效会话");
            return;
        }

        session.setRevokedAt(LocalDateTime.now());
        userLoginSessionMapper.updateById(session);
    }

    /**
     * 按手机号查询用户。
     */
    private User queryUserByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)
                .last("limit 1"));
    }

    /**
     * 按手机号创建用户，并在并发注册时回查已存在用户。
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setUserKey("u_" + IdUtil.fastSimpleUUID());
        user.setUsername(phone);
        user.setNickname("用户" + lastDigits(phone, 4));
        user.setPhone(phone);
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            log.warn("检测到并发注册，回查已存在用户，phone={}", phone);
            User existingUser = queryUserByPhone(phone);
            if (existingUser != null) {
                return existingUser;
            }
            throw e;
        }
    }

    /**
     * 按邮箱查询用户。
     */
    private User queryUserByEmail(String email) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)
                .last("limit 1"));
    }

    /**
     * 按用户名查询用户，用于兼容历史数据（username=email 但 email 字段为空）。
     */
    private User queryUserByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)
                .last("limit 1"));
    }

    /**
     * 按邮箱创建用户，用户名与邮箱一致，密码使用 BCrypt 哈希存储。
     * 并发注册时回查已存在用户。
     */
    private User createUserWithEmail(String email, String password) {
        User user = new User();
        user.setUserKey("u_" + IdUtil.fastSimpleUUID());
        user.setUsername(email);
        user.setNickname(emailPrefix(email));
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(password));
        user.setStatus(1);
        user.setLastLoginAt(LocalDateTime.now());
        try {
            userMapper.insert(user);
            return user;
        } catch (DuplicateKeyException e) {
            log.warn("检测到并发注册，回查已存在用户，email={}", email);
            User existingUser = queryUserByEmail(email);
            if (existingUser == null) {
                // 兼容历史数据：username=email 但 email 字段为空，回退按用户名查询并补全 email。
                existingUser = queryUserByUsername(email);
                if (existingUser != null && StrUtil.isBlank(existingUser.getEmail())) {
                    existingUser.setEmail(email);
                    userMapper.updateById(existingUser);
                }
            }
            if (existingUser != null) {
                return existingUser;
            }
            throw e;
        }
    }

    /**
     * 校验邮箱非空且格式合法。
     */
    private void validateEmail(String email) {
        if (StrUtil.isBlank(email)) {
            throw new IllegalArgumentException("邮箱不能为空");
        }
        if (!Validator.isEmail(email)) {
            throw new IllegalArgumentException("邮箱格式错误");
        }
    }

    /**
     * 校验密码非空且符合规则：8-32 位且同时包含字母与数字。
     */
    private void validatePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException("密码需为 8-32 位且同时包含字母和数字");
        }
    }

    /**
     * 取邮箱 @ 之前的部分作为默认昵称。
     */
    private String emailPrefix(String email) {
        if (StrUtil.isBlank(email)) {
            return "用户";
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * 组装登录响应对象。
     */
    private UserLoginResponse buildLoginResponse(User user, String token, LocalDateTime expiredAt, boolean isNewUser) {
        LoginUserInfo loginUserInfo = new LoginUserInfo();
        loginUserInfo.setId(user.getUserKey());
        loginUserInfo.setUserId(user.getId());
        loginUserInfo.setUserKey(user.getUserKey());
        loginUserInfo.setUsername(user.getUsername());
        loginUserInfo.setNickname(user.getNickname());
        loginUserInfo.setPhone(user.getPhone());
        loginUserInfo.setEmail(user.getEmail());

        UserLoginResponse response = new UserLoginResponse();
        response.setToken(token);
        response.setExpiresAt(expiredAt.format(DATE_TIME_FORMATTER));
        response.setNewUser(isNewUser);
        response.setUserInfo(loginUserInfo);
        return response;
    }

    /**
     * 校验手机号非空且格式合法。
     */
    private void validatePhone(String phone) {
        if (StrUtil.isBlank(phone)) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (!Validator.isMobile(phone)) {
            throw new IllegalArgumentException("手机号格式错误");
        }
    }

    /**
     * 构建验证码缓存键。
     */
    private String buildLoginCodeKey(String phone) {
        return LOGIN_CODE_KEY_PREFIX + phone;
    }

    /**
     * 计算刷新令牌哈希。
     */
    private String buildRefreshTokenHash(String token) {
        return SecureUtil.sha256(token);
    }

    /**
     * 截取字符串尾部指定长度。
     */
    private String lastDigits(String phone, int length) {
        if (phone.length() <= length) {
            return phone;
        }
        return phone.substring(phone.length() - length);
    }

    /**
     * 空白转空并按最大长度截断。
     */
    private String trimToLength(String value, int maxLength) {
        if (StrUtil.isBlank(value)) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    public UserProfileResponse getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserProfileResponse resp = new UserProfileResponse();
        resp.setNickname(user.getNickname());
        resp.setPhone(user.getPhone());
        resp.setEmail(user.getEmail());
        resp.setUsername(user.getUsername());
        resp.setCreatedAt(user.getCreatedAt());
        resp.setLastLoginAt(user.getLastLoginAt());
        return resp;
    }

    @Override
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(User::getId, userId);
        boolean changed = false;
        if (request != null && request.getNickname() != null) {
            String nickname = request.getNickname().trim();
            if (nickname.isEmpty() || nickname.length() > 100) {
                throw new IllegalArgumentException("昵称长度需为 1-100");
            }
            updateWrapper.set(User::getNickname, nickname);
            changed = true;
        }
        if (request != null && request.getPhone() != null) {
            String phone = request.getPhone().trim();
            if (!phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$")) {
                throw new IllegalArgumentException("手机号格式不正确");
            }
            updateWrapper.set(User::getPhone, phone);
            changed = true;
        }
        if (changed) {
            userMapper.update(null, updateWrapper);
        }
        return getProfile(userId);
    }

    @Override
    public UserPreferenceItem getPreferences(Long userId) {
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference pref = userPreferenceMapper.selectOne(wrapper);
        UserPreferenceItem item = new UserPreferenceItem();
        item.setThemeMode("dark");
        item.setAccentColor("blue");
        if (pref != null && pref.getPreferencesJson() != null) {
            try {
                UserPreferenceItem parsed = objectMapper.readValue(pref.getPreferencesJson(), UserPreferenceItem.class);
                if (parsed != null) {
                    if (parsed.getThemeMode() != null) item.setThemeMode(parsed.getThemeMode());
                    if (parsed.getAccentColor() != null) item.setAccentColor(parsed.getAccentColor());
                }
            } catch (Exception e) {
                log.warn("解析用户偏好 JSON 失败，回退默认值: userId={}, json={}", userId, pref.getPreferencesJson(), e);
            }
        }
        return item;
    }

    @Override
    public UserPreferenceItem savePreferences(Long userId, UserPreferenceItem item) {
        if (item == null) {
            throw new IllegalArgumentException("偏好设置不能为空");
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            throw new IllegalArgumentException("偏好设置序列化失败");
        }
        LambdaQueryWrapper<UserPreference> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserPreference::getUserId, userId);
        UserPreference existing = userPreferenceMapper.selectOne(wrapper);
        if (existing == null) {
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setPreferencesJson(json);
            userPreferenceMapper.insert(pref);
        } else {
            existing.setPreferencesJson(json);
            userPreferenceMapper.updateById(existing);
        }
        return item;
    }
}
