package com.itgeo.fitmate.api.search.application.impl;

import cn.hutool.json.JSONUtil;
import com.itgeo.fitmate.api.search.application.SearXngService;
import com.itgeo.fitmate.api.search.dto.SearXngResponse;
import com.itgeo.fitmate.api.search.dto.SearchResult;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * SearXng 联网搜索服务实现。
 * <p>
 * 负责组装搜索请求、解析 JSON 响应，并在本地对结果做排序与数量裁剪。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SearXngServiceImpl implements SearXngService {

    @Value("${internet.websearch.searxng.url}")
    private String SEARXNG_URL;
    @Value("${internet.websearch.searxng.counts}")
    private Integer SEARXNG_COUNTS;

    private final OkHttpClient okHttpClient;

    /**
     * 调用 SearXng 执行一次联网搜索。
     * <p>
     * 步骤：
     * 1. 构建只包含 `q` 与 `format=json` 的请求地址；
     * 2. 发起 HTTP 请求并校验响应状态；
     * 3. 解析响应体为 `SearXngResponse`；
     * 4. 对结果做本地排序与数量裁剪后返回。
     * <p>
     * 说明：
     * - 当前请求 URL 并未启用注释掉的 `count` 参数；
     * - HTTP 非 2xx 或 IO 异常会抛出异常；
     * - 只有响应体为空时，才会记录日志并返回空列表。
     */
    @Override
    public List<SearchResult> search(String query) {

        // 1. 构建 SearXng 搜索 URL：当前实际只传 q 与 format=json
        HttpUrl url = HttpUrl.get(SEARXNG_URL)
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "json")
//                .addQueryParameter("count", SEARXNG_COUNTS.toString())
                .build();
        log.info("搜索的SearXng url地址为: {}", url.url());

        // 2. 构建 HTTP 请求对象
        Request request = new Request.Builder()
                .url(url)
                .build();

        // 3. 发起请求：非 2xx 直接抛异常；响应体存在时继续解析
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("SearXng搜索请求失败: " + response.code());
            }

            if (response.body() != null) {
                String responseBody = response.body().string();
                log.info("<UNK>SearXng <UNK>: {}", responseBody);

                // 4. 解析响应并交给本地结果整理逻辑处理
                SearXngResponse searXngResponse = JSONUtil.toBean(responseBody, SearXngResponse.class);
                return dealResults(searXngResponse.getResults());
            }

            // 5. 仅在响应体缺失时回落为空列表
            log.error("搜索失败：{}", response.message());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Collections.emptyList();
    }


    /**
     * 对 SearXng 返回结果做本地排序与数量裁剪。
     * <p>
     * 说明：这里使用配置项 `SEARXNG_COUNTS` 控制最终返回条数，
     * 属于本地后处理逻辑，不代表请求 URL 已携带 `count` 参数。
     */
    private List<SearchResult> dealResults(List<SearchResult> results) {

        return results.subList(0, Math.min(SEARXNG_COUNTS, results.size()))
                .parallelStream()
                .sorted(Comparator.comparingDouble(SearchResult::getScore).reversed())
                .limit(SEARXNG_COUNTS)
                .toList();
    }


}
