package com.itgeo.fitmate.api.rag.infrastructure.embedding;

import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.rag.infrastructure.embedding.dto.BgeM3EmbedRequest;
import com.itgeo.fitmate.api.rag.infrastructure.embedding.dto.BgeM3EmbedResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * bge-m3 embedding 的 HTTP 协议适配器。
 *
 * 该实现把 Spring AI 的 `EmbeddingModel` 调用转换成对外部 `/embed` 接口的 HTTP 请求，
 * 负责协议转换与结果包装，不在当前应用内执行本地模型推理。
 */
@Slf4j
@RequiredArgsConstructor
public class BgeM3HttpEmbeddingModel implements EmbeddingModel {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    /** bge-m3 单批向量化文本数。 */
    private static final int BATCH_SIZE = 8;

    /** bge-m3 单条文本最大 token 数（模型上限 8192，避免长文本被服务端截断导致向量不完整）。 */
    private static final int MAX_LENGTH = 8192;

    private final OkHttpClient okHttpClient;
    private final String serviceUrl;
    private final String modelName;
    private final int dimension;

    /**
     * 通过 HTTP 调用外部 bge-m3 embedding 服务，并转换为 Spring AI 响应对象。
     */
    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> texts = request.getInstructions();
        if (texts == null || texts.isEmpty()) {
            return new EmbeddingResponse(List.of());
        }

        /*
         * 适配流程：
         * 1. 从 `EmbeddingRequest` 中提取待向量化文本列表；
         * 2. 按当前 HTTP 服务约定组装 JSON 请求体，并 POST 到 `${serviceUrl}/embed`；
         * 3. 校验 HTTP 状态码与响应体，确保服务端返回有效结果；
         * 4. 把响应中的向量数组转换为 Spring AI `EmbeddingResponse`。
         *
         * 注意：这里是 HTTP 协议适配器，不是本地推理实现。
         */

        BgeM3EmbedRequest payload = new BgeM3EmbedRequest(texts, BATCH_SIZE, MAX_LENGTH);
        String json = JSONUtil.toJsonStr(payload);

        Request httpRequest = new Request.Builder()
                .url(serviceUrl + "/embed")
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = okHttpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("bge-m3 embedding 请求失败: " + response.code());
            }
            if (response.body() == null) {
                throw new IOException("bge-m3 embedding 响应体为空");
            }

            String body = response.body().string();
            BgeM3EmbedResponse embedResponse = JSONUtil.toBean(body, BgeM3EmbedResponse.class);

            List<Embedding> embeddings = new ArrayList<>();
            if (embedResponse.getData() != null) {
                for (BgeM3EmbedResponse.Item item : embedResponse.getData()) {
                    List<Float> vector = item.getEmbedding();
                    float[] output = new float[vector.size()];
                    for (int i = 0; i < vector.size(); i++) {
                        output[i] = vector.get(i);
                    }
                    embeddings.add(new Embedding(output, item.getIndex()));
                }
            }

            return new EmbeddingResponse(embeddings);
        } catch (IOException e) {
            log.error("调用 bge-m3 embedding 服务失败, serviceUrl={}", serviceUrl, e);
            throw new RuntimeException("调用 bge-m3 embedding 服务失败", e);
        }
    }

    /**
     * 单文档便捷入口，内部委托到字符串 embedding 逻辑。
     */

    @Override
    public float[] embed(Document document) {
        if (document == null || document.getText() == null) {
            return new float[0];
        }
        return this.embed(document.getText());
    }

    /**
     * 单文本便捷入口，返回首条 embedding 结果。
     */
    @Override
    public float[] embed(String text) {
        EmbeddingResponse response = this.call(new EmbeddingRequest(List.of(text), null));
        if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
            return new float[0];
        }
        return response.getResults().get(0).getOutput();
    }

    /**
     * 批量文本便捷入口，按输入顺序返回向量列表。
     */
    @Override
    public List<float[]> embed(List<String> texts) {
        EmbeddingResponse response = this.call(new EmbeddingRequest(texts, null));
        List<float[]> outputs = new ArrayList<>();
        if (response == null || response.getResults() == null) {
            return outputs;
        }
        for (Embedding embedding : response.getResults()) {
            outputs.add(embedding.getOutput());
        }
        return outputs;
    }

    /**
     * 返回当前适配器声明的向量维度。
     */
    @Override
    public int dimensions() {
        return dimension;
    }
}
