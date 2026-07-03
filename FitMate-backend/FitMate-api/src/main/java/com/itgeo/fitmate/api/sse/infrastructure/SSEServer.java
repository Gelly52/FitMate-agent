package com.itgeo.fitmate.api.sse.infrastructure;

import com.itgeo.fitmate.api.sse.domain.SSEMsgType;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 连接管理器。
 * <p>
 * 约定：
 * 1. 在线连接以 `sseClientId` 为键维护，不以数据库 `userId` 为维度；
 * 2. 同一 `sseClientId` 重连时，会先替换成新 emitter，再关闭旧 emitter，确保新连接先进入可用状态；
 * 3. `removeIfSame` 只会移除当前 map 中仍指向同一 emitter 的项，避免旧连接回调误删新连接。
 */
@Slf4j
public class SSEServer {

    private static final Map<String, SseEmitter> SSE_CLIENTS = new ConcurrentHashMap<>();

    private SSEServer() {
    }

    /**
     * 建立或替换一个 SSE 连接。
     * <p>
     * 步骤：
     * 1. 按 `sseClientId` 创建新的 emitter 并立即写入连接表；
     * 2. 为当前 emitter 注册超时、完成、异常回调；
     * 3. 若旧 emitter 存在，则在新 emitter 已入表后再安静关闭旧连接。
     */
    public static SseEmitter connect(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("sseClientId不能为空");
        }

        // 1. 先用新的 emitter 替换当前 sseClientId 对应连接，确保重连时新连接优先生效
        SseEmitter newEmitter = new SseEmitter(0L);
        SseEmitter oldEmitter = SSE_CLIENTS.put(clientId, newEmitter);

        // 2. 所有回调都绑定到当前 newEmitter 实例，并通过 removeIfSame 防止误删重连后的新连接
        newEmitter.onTimeout(() -> {
            log.info("SSE连接超时, clientId={}", clientId);
            removeIfSame(clientId, newEmitter);
        });
        newEmitter.onCompletion(() -> {
            log.info("SSE连接完成, clientId={}", clientId);
            removeIfSame(clientId, newEmitter);
        });
        newEmitter.onError(error -> {
            log.warn("SSE连接异常, clientId={}, message={}", clientId, error == null ? null : error.getMessage());
            removeIfSame(clientId, newEmitter);
        });

        // 3. 新连接已经入表后，再关闭旧 emitter，避免旧回调把新连接一起移除
        if (oldEmitter != null && oldEmitter != newEmitter) {
            closeQuietly(oldEmitter);
        }

        log.info("SSE连接建立成功, clientId={}", clientId);
        return newEmitter;
    }

    /**
     * 向指定 `sseClientId` 发送一条 SSE 消息。
     */
    public static void sendMsg(String clientId, String message, SSEMsgType msgType) {
        if (clientId == null || clientId.isBlank() || msgType == null || SSE_CLIENTS.isEmpty()) {
            return;
        }

        SseEmitter emitter = SSE_CLIENTS.get(clientId);
        if (emitter != null) {
            sendEmitterMessage(emitter, clientId, message, msgType);
        }
    }

    /**
     * 向当前所有在线 `sseClientId` 广播 SSE 消息。
     */
    public static void sendMsgToAllUsers(String message) {
        if (SSE_CLIENTS.isEmpty()) {
            return;
        }
        SSE_CLIENTS.forEach((clientId, emitter) -> sendEmitterMessage(emitter, clientId, message, SSEMsgType.MESSAGE));
    }

    /**
     * 实际发送 SSE 消息。
     */
    private static void sendEmitterMessage(SseEmitter emitter,
                                           String clientId,
                                           String message,
                                           SSEMsgType msgType) {
        if (emitter == null) {
            return;
        }

        try {
            SseEmitter.SseEventBuilder msgEvent = SseEmitter.event()
                    .id(clientId)
                    .data(message)
                    .name(msgType.type);
            emitter.send(msgEvent);
        } catch (IOException e) {
            log.info("SSE发送失败, clientId={}, message={}", clientId, e.getMessage());
            removeIfSame(clientId, emitter);
            closeQuietly(emitter);
        }
    }

    /**
     * 按 `sseClientId` 手动移除一个 SSE 连接。
     */
    public static void remove(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return;
        }
        log.info("SSE连接被移除, clientId={}", clientId);
        SSE_CLIENTS.remove(clientId);
    }

    /**
     * 判断指定 `sseClientId` 是否在线。
     */
    public static boolean isConnected(String clientId) {
        return clientId != null && !clientId.isBlank() && SSE_CLIENTS.containsKey(clientId);
    }

    /**
     * 仅当 map 中当前保存的还是同一个 emitter 时才移除。
     * <p>
     * 该方法用于处理重连替换场景：如果旧 emitter 的超时、完成或异常回调晚到，
     * 也不会把已经替换进去的新 emitter 一并删除。
     */
    private static void removeIfSame(String clientId, SseEmitter emitter) {
        SSE_CLIENTS.computeIfPresent(clientId, (key, current) -> current == emitter ? null : current);
    }

    /**
     * 安静关闭旧连接。
     */
    private static void closeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
