package com.itgeo.fitmate.api.auth.application;

import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.itgeo.fitmate.api.auth.infrastructure.MailService;
import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 邮箱验证码服务，负责校验邮箱、生成验证码、写入 Redis 与发送邮件，并控制发送频率。
 */
@Slf4j
@Service
public class EmailCodeService {

    private static final String EMAIL_CODE_KEY_PREFIX = "fitmate:dev:auth:email-code:";
    private static final String EMAIL_CODE_COOLDOWN_KEY_PREFIX = "fitmate:dev:auth:email-code:cooldown:";
    private static final long EMAIL_CODE_TTL_MINUTES = 5L;
    private static final long EMAIL_CODE_COOLDOWN_SECONDS = 60L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MailService mailService;

    @Resource
    private Environment environment;

    /**
     * 发送邮箱登录验证码。
     * 处理流程：
     * 1. 校验邮箱格式；
     * 2. 校验发送冷却，避免频繁触发；
     * 3. 生成验证码并写入 Redis；
     * 4. 调用邮件服务发送；
     * 5. 按运行环境输出日志。
     *
     * @param email 收件邮箱
     */
    public void sendCode(String email) {
        validateEmail(email);

        // 60 秒内重复发送则拒绝，防止滥用。
        String cooldownKey = buildCooldownKey(email);
        if (Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey))) {
            throw new IllegalArgumentException("验证码发送过于频繁，请稍后再试");
        }

        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(
                buildCodeKey(email),
                code,
                EMAIL_CODE_TTL_MINUTES,
                TimeUnit.MINUTES
        );
        stringRedisTemplate.opsForValue().set(
                cooldownKey,
                "1",
                EMAIL_CODE_COOLDOWN_SECONDS,
                TimeUnit.SECONDS
        );

        mailService.sendVerificationCode(email, code);

        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            log.info("发送邮箱登录验证码成功，email={}, code={}", email, code);
        } else {
            log.info("发送邮箱登录验证码成功，email={}", email);
        }
    }

    /**
     * 校验并消费验证码，校验成功后立即删除，避免重复使用。
     *
     * @param email 邮箱
     * @param code  用户输入的验证码
     */
    public void verifyAndConsume(String email, String code) {
        validateEmail(email);
        if (StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("验证码不能为空");
        }
        String codeKey = buildCodeKey(email);
        String cacheCode = stringRedisTemplate.opsForValue().get(codeKey);
        if (StrUtil.isBlank(cacheCode) || !StrUtil.equals(cacheCode, code)) {
            throw new IllegalArgumentException("验证码错误或已过期");
        }
        stringRedisTemplate.delete(codeKey);
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

    private String buildCodeKey(String email) {
        return EMAIL_CODE_KEY_PREFIX + email;
    }

    private String buildCooldownKey(String email) {
        return EMAIL_CODE_COOLDOWN_KEY_PREFIX + email;
    }
}
