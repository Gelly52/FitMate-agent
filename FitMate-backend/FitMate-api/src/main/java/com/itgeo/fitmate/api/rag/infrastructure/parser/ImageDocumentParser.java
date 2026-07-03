package com.itgeo.fitmate.api.rag.infrastructure.parser;

import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversation;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationParam;
import com.alibaba.dashscope.aigc.multimodalconversation.MultiModalConversationResult;
import com.alibaba.dashscope.common.MultiModalMessage;
import com.alibaba.dashscope.common.Role;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageDocumentParser implements DocumentParser{

    private static final Logger log = LoggerFactory.getLogger(ImageDocumentParser.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "bmp");

    @Value("${rag.ocr.api-key:}")
    private String apiKey;

    @Value("${rag.ocr.model:}")
    private String model;

    @Override
    public boolean supports(String fileExtension) {
        return SUPPORTED_EXTENSIONS.contains(fileExtension.toLowerCase());
    }

    @Override
    public String parse(String filePath) throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("图片 OCR 未配置 rag.ocr.api-key");
        }
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("图片 OCR 未配置 rag.ocr.model");
        }
        byte[] imageBytes = Files.readAllBytes(Paths.get(filePath));
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        String ext = filePath.substring(filePath.lastIndexOf('.') + 1).toLowerCase();
        String mimeType = getMimeType(ext);
        String dataUri = "data:" + mimeType + ";base64," + base64Image;

        MultiModalMessage userMessage = MultiModalMessage.builder()
                .role(Role.USER.getValue())
                .content(Arrays.asList(
                        Collections.singletonMap("image", dataUri),
                        Collections.singletonMap("text",

                                "请提取这张图片中的所有文字内容。如果图片中没有文字，请描述图片的主要内容。请直接输出内容，不要添加额外说明。")
                ))
                .build();

        MultiModalConversationParam param = MultiModalConversationParam.builder()
                .apiKey(apiKey)
                .model(model)
                .message(userMessage)
                .build();

        MultiModalConversation conv = new MultiModalConversation();
        MultiModalConversationResult result = conv.call(param);

        String text =
                result.getOutput().getChoices().get(0).getMessage().getContent().get(0).get("text").toString();
        log.info("图片 OCR 解析完成: {}, 提取文本长度: {}", filePath, text.length());
        return text;
    }

    private String getMimeType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/png";
        };
    }
}
