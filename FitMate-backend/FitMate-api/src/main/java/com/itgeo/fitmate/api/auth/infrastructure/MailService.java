package com.itgeo.fitmate.api.auth.infrastructure;

import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * 邮件发送服务，负责封装 JavaMailSender 完成验证码邮件发送。
 */
@Slf4j
@Service
public class MailService {

    @Resource
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:your_email@163.com}")
    private String from;

    /**
     * 发送登录验证码邮件。
     *
     * @param email 收件人邮箱
     * @param code  6 位验证码
     */
    public void sendVerificationCode(String email, String code) {
        if (StrUtil.isBlank(email) || StrUtil.isBlank(code)) {
            throw new IllegalArgumentException("邮箱和验证码不能为空");
        }
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email);
            helper.setSubject("FitMate 登录验证码");
            helper.setText(buildVerificationCodeHtml(code), true);
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            log.error("发送验证码邮件失败，email={}，原因：{}", email, e.getMessage());
            throw new IllegalStateException("验证码邮件发送失败，请稍后重试");
        }
    }

    /**
     * 构建验证码邮件 HTML 正文。
     */
    private String buildVerificationCodeHtml(String code) {
        return "<div style=\"font-family:'Helvetica Neue',Arial,sans-serif;max-width:420px;margin:0 auto;padding:24px;background:#f7f8fa;\">"
                + "<div style=\"background:#ffffff;border-radius:8px;padding:24px 28px;border:1px solid #e5e7eb;\">"
                + "<h2 style=\"margin:0 0 12px 0;color:#10131b;font-size:20px;\">FitMate 登录验证码</h2>"
                + "<p style=\"margin:0 0 16px 0;color:#4b5563;font-size:14px;\">您正在登录 FitMate，验证码为：</p>"
                + "<div style=\"text-align:center;margin:16px 0;\">"
                + "<span style=\"display:inline-block;font-size:32px;font-weight:600;letter-spacing:8px;color:#005bc1;background:#eff6ff;padding:12px 24px;border-radius:6px;\">"
                + code
                + "</span>"
                + "</div>"
                + "<p style=\"margin:0 0 4px 0;color:#6b7280;font-size:12px;\">验证码 5 分钟内有效，请勿向他人泄露。</p>"
                + "<p style=\"margin:0;color:#6b7280;font-size:12px;\">如非本人操作，请忽略本邮件。</p>"
                + "</div>"
                + "<p style=\"text-align:center;margin:16px 0 0 0;color:#9ca3af;font-size:11px;\">FitMate OS</p>"
                + "</div>";
    }
}
