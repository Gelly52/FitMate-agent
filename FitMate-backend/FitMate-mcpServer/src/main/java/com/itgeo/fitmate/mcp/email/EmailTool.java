package com.itgeo.fitmate.mcp.email;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 邮件工具类
 */
@Component
@Slf4j
public class EmailTool {


    private final JavaMailSender mailSender;
    private final String from;

    @Autowired
    public EmailTool(JavaMailSender mailSender, @Value("${spring.mail.username}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Data
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class EmailRequest {
        @ToolParam(description = "收件人邮箱")
        private String email;
        @ToolParam(description = "发送邮件的标题/主题")
        private String subject;
        @ToolParam(description = "发送邮件的消息/正文内容")
        private String message;

        @ToolParam(description = "发送邮件的消息/正文内容的类型，1为Markdown格式，2为HTML格式，0为普通文本格式")
        private Integer contentType;
    }

    @Tool(description = "查询我的邮件/邮箱地址")
    public String getMyEmailAddress() {
        log.info("调用MCP工具：getMyEmailAddress()");
        return "xxxxxxxxxxxx@qq.com";
    }

    @Tool(description = "给指定邮箱发送邮件信息，Email为收件人邮箱，subject为邮件标题，message为邮件的内容")
    public void sendMailMessage(EmailRequest emailRequest) {
        log.info("调用MCP工具：sendMailMessage()");
        log.info(String.format("参数 emailRequest：%s", emailRequest.toString()));

        // 大模型处理得到 contentType 参数
        Integer contentType = emailRequest.getContentType();

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage);

            helper.setFrom(from);
            helper.setTo(emailRequest.getEmail());
            helper.setSubject(emailRequest.getSubject());
            if (contentType == 1) {
                helper.setText(convertToHtml(emailRequest.getMessage()), true);
            } else if (contentType == 2) {
                helper.setText(emailRequest.getMessage(), true);
            } else {
                helper.setText(emailRequest.getMessage());
            }

//            helper.setText(emailRequest.getMessage(), true);
//            helper.setText(convertToHtml(emailRequest.getMessage()), true);

            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
//            throw new RuntimeException(e);
            log.error("发送邮件失败，报错信息：{}", e.getMessage());
        }

    }

    /**
     * 将Markdown格式的字符串转换为HTML格式
     *
     * @param markdownStr Markdown格式的字符串
     * @return HTML格式的字符串
     */
    public static String convertToHtml(String markdownStr) {
        MutableDataSet dataSet = new MutableDataSet();
        // 解析
        Parser parser = Parser.builder(dataSet).build();
        // 渲染Markdown为HTML
        HtmlRenderer renderer = HtmlRenderer.builder(dataSet).build();

        return renderer.render(parser.parse(markdownStr));
    }

}
