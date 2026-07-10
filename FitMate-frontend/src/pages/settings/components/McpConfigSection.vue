<!-- FitMate-frontend/src/pages/settings/components/McpConfigSection.vue -->
<template>
  <section class="mcp-config-section">
    <div class="mcp-config-card">
      <div
        v-for="(server, index) in servers"
        :key="index"
        class="mcp-server-row"
      >
        <div class="mcp-server-head">
          <div class="mcp-server-head-left">
            <span class="mcp-server-index">#{{ index + 1 }}</span>
            <span v-if="server.name" class="mcp-server-name">{{ server.name }}</span>
            <span v-else class="mcp-server-name mcp-server-name-empty">未命名</span>
          </div>
          <div class="mcp-server-head-right">
            <button
              type="button"
              class="mcp-toggle"
              :class="{ 'mcp-toggle-on': server.enabled }"
              :title="server.enabled ? '已启用' : '已禁用'"
              @click="server.enabled = !server.enabled"
            >
              <span class="mcp-toggle-knob"></span>
            </button>
            <button
              type="button"
              class="mcp-btn-danger"
              @click="removeServer(index)"
            >
              删除
            </button>
          </div>
        </div>

        <div class="mcp-field">
          <label class="mcp-label">名称 <span class="mcp-required">*</span></label>
          <input
            v-model="server.name"
            type="text"
            class="mcp-input"
            placeholder="例如：weather-server"
          />
        </div>

        <div class="mcp-field">
          <label class="mcp-label">URL <span class="mcp-required">*</span></label>
          <input
            v-model="server.url"
            type="text"
            class="mcp-input"
            placeholder="例如：http://localhost:8765"
          />
        </div>

        <div class="mcp-field">
          <label class="mcp-label">SSE Endpoint</label>
          <input
            v-model="server.sseEndpoint"
            type="text"
            class="mcp-input"
            placeholder="/sse"
          />
        </div>

        <div class="mcp-server-actions">
          <button
            type="button"
            class="mcp-btn-secondary"
            :disabled="testing === index || !server.url"
            @click="onTest(index)"
          >
            {{ testing === index ? "测试中..." : "测试连接" }}
          </button>
        </div>

        <p
          v-if="testResults[index]"
          class="mcp-test-result"
          :class="testResults[index].ok ? 'mcp-test-ok' : 'mcp-test-fail'"
        >
          {{ testResults[index].ok
            ? '✓ 连通 (' + testResults[index].latencyMs + 'ms, ' + (testResults[index].tools ? testResults[index].tools.length : 0) + ' 个工具)'
            : '✗ ' + (testResults[index].error || '失败') }}
        </p>
      </div>

      <div v-if="servers.length === 0" class="mcp-empty">
        暂无 MCP Server 配置，点击下方按钮添加，或加载示例。
      </div>

      <div class="mcp-btn-row">
        <button type="button" class="mcp-btn-add" @click="addServer">
          + 添加 MCP Server
        </button>
        <button type="button" class="mcp-btn-add" @click="loadExample">
          加载示例 (weather)
        </button>
      </div>

      <!-- JSON 双向绑定预览区 -->
      <div class="mcp-json-preview">
        <div class="mcp-json-head">
          <label class="mcp-label">JSON 配置预览（可编辑）</label>
          <div class="mcp-json-actions">
            <button
              type="button"
              class="mcp-btn-secondary"
              @click="formatJson"
              title="格式化 JSON"
            >格式化</button>
            <button
              type="button"
              class="mcp-btn-secondary"
              @click="applyJsonToServers"
              title="将 JSON 同步到表单"
            >同步到表单</button>
          </div>
        </div>
        <p class="mcp-json-hint">
          ℹ️ 当前仅支持 SSE 传输类型。stdio 类型的 MCP server
          请先在本地用 <code>mcp-proxy</code> 或 <code>smithery --transport sse</code> 转换为 SSE 端点后填入。
        </p>
        <textarea
          v-model="jsonText"
          class="mcp-json-textarea"
          spellcheck="false"
          :placeholder='jsonPlaceholder'
          @blur="applyJsonToServers"
        ></textarea>
        <p v-if="jsonError" class="mcp-test-result mcp-test-fail">{{ jsonError }}</p>
      </div>

      <div class="mcp-actions">
        <button
          type="button"
          class="mcp-btn-primary"
          :disabled="saving"
          @click="onSave"
        >
          {{ saving ? "保存中..." : "保存" }}
        </button>
      </div>
    </div>
  </section>
</template>

<script lang="ts">
import doctorApi from "../../../services/doctorApi";
import type { McpServerConfig, McpTestResult } from "../../../types/settings";

export default {
  name: "McpConfigSection",
  data() {
    return {
      servers: [] as McpServerConfig[],
      jsonText: "",
      jsonError: "",
      saving: false,
      testing: -1,
      testResults: {} as Record<number, McpTestResult>,
      // 防止 servers→json 与 json→servers 互相触发死循环
      isApplyingJson: false,
      // Claude Desktop 风格的占位示例
      jsonPlaceholder: [
        '{',
        '  "mcpServers": {',
        '    "weather-server": {',
        '      "url": "http://localhost:8765",',
        '      "sseEndpoint": "/sse",',
        '      "enabled": true',
        '    }',
        '  }',
        '}',
      ].join("\n"),
    };
  },
  watch: {
    servers: {
      handler() {
        if (!this.isApplyingJson) {
          this.syncToJson();
        }
      },
      deep: true,
    },
  },
  mounted() {
    this.loadConfig();
  },
  methods: {
    // 表单 → JSON（输出 Claude Desktop 风格 {mcpServers: {name: {...}}}）
    syncToJson() {
      const obj: { mcpServers: Record<string, any> } = { mcpServers: {} };
      const usedKeys: Record<string, number> = {};
      for (const s of this.servers) {
        let key = (s.name && s.name.trim()) || "unnamed";
        // 处理重名 key
        if (usedKeys[key] !== undefined) {
          usedKeys[key] += 1;
          key = key + "_" + usedKeys[key];
        } else {
          usedKeys[key] = 0;
        }
        obj.mcpServers[key] = {
          type: "sse",
          url: s.url || "",
          sseEndpoint: s.sseEndpoint || "/sse",
          enabled: typeof s.enabled === "boolean" ? s.enabled : true,
        };
      }
      this.jsonText = JSON.stringify(obj, null, 2);
      this.jsonError = "";
    },
    // JSON → 表单（兼容 {mcpServers: {name: {...}}} 与 [{...}] 两种格式）
    applyJsonToServers() {
      const raw = (this.jsonText || "").trim();
      if (!raw) {
        this.servers = [];
        this.jsonError = "";
        return;
      }
      let parsed: any;
      try {
        parsed = JSON.parse(raw);
      } catch (e: any) {
        this.jsonError = "JSON 解析失败: " + (e && e.message ? e.message : String(e));
        return;
      }
      let serverList: any[] = [];
      const warnings: string[] = [];
      if (Array.isArray(parsed)) {
        // 向后兼容：数组形式 [{name, url, ...}]
        serverList = parsed;
      } else if (parsed && typeof parsed === "object" && parsed.mcpServers && typeof parsed.mcpServers === "object") {
        // Claude Desktop 风格：{mcpServers: {name: {...}}}
        for (const key of Object.keys(parsed.mcpServers)) {
          const s = parsed.mcpServers[key];
          if (!s || typeof s !== "object") continue;
          // stdio 类型无法支持，提示用户
          if ((s.type && s.type !== "sse") || (s.command && !s.url)) {
            warnings.push(`"${key}" 是 ${s.type || "stdio"} 类型，当前仅支持 SSE，已跳过`);
            continue;
          }
          // name 取字段值优先，无则用 key
          serverList.push({ ...s, name: s.name || key });
        }
      } else {
        this.jsonError = "JSON 必须是数组或 {mcpServers: {name: {...}}} 形式";
        return;
      }
      const next: McpServerConfig[] = [];
      for (const s of serverList) {
        if (!s || typeof s !== "object") continue;
        next.push({
          name: typeof s.name === "string" ? s.name : "",
          url: typeof s.url === "string" ? s.url : "",
          sseEndpoint: typeof s.sseEndpoint === "string" && s.sseEndpoint ? s.sseEndpoint : "/sse",
          enabled: typeof s.enabled === "boolean" ? s.enabled : true,
        });
      }
      this.isApplyingJson = true;
      this.servers = next;
      this.$nextTick(() => {
        this.isApplyingJson = false;
        // 重新格式化一次，确保 textarea 内容规范
        this.syncToJson();
      });
      this.jsonError = warnings.length > 0 ? warnings.join("; ") : "";
    },
    formatJson() {
      try {
        const parsed = JSON.parse(this.jsonText || "{}");
        this.jsonText = JSON.stringify(parsed, null, 2);
        this.jsonError = "";
      } catch (e: any) {
        this.jsonError = "JSON 解析失败: " + (e && e.message ? e.message : String(e));
      }
    },
    loadExample() {
      this.servers = [
        {
          name: "weather-server",
          url: "http://localhost:8765",
          sseEndpoint: "/sse",
          enabled: true,
        },
      ];
      this.testResults = {};
    },
    async loadConfig() {
      try {
        const res = await doctorApi.getMcpConfig();
        if (res && res.status === 200 && res.data) {
          const data = res.data as { servers?: McpServerConfig[] };
          this.servers = (data.servers || []).map((s) => ({
            name: s.name || "",
            url: s.url || "",
            sseEndpoint: s.sseEndpoint || "/sse",
            enabled: s.enabled !== undefined ? s.enabled : true,
          }));
          this.testResults = {};
          this.syncToJson();
        }
      } catch (e) {
        console.error("加载 MCP 配置失败", e);
        this.$message && this.$message.error && this.$message.error("加载 MCP 配置失败");
      }
    },
    addServer() {
      this.servers.push({
        name: "",
        url: "",
        sseEndpoint: "/sse",
        enabled: true,
      });
    },
    removeServer(index: number) {
      this.servers.splice(index, 1);
      const next = {} as Record<number, McpTestResult>;
      Object.keys(this.testResults).forEach((k) => {
        const i = Number(k);
        if (i < index) {
          next[i] = this.testResults[i];
        } else if (i > index) {
          next[i - 1] = this.testResults[i];
        }
      });
      this.testResults = next;
    },
    async onTest(index: number) {
      const server = this.servers[index];
      if (!server || !server.url) return;
      this.testing = index;
      const fresh = {} as Record<number, McpTestResult>;
      Object.keys(this.testResults).forEach((k) => {
        const i = Number(k);
        if (i !== index) fresh[i] = this.testResults[i];
      });
      this.testResults = fresh;
      try {
        const res = await doctorApi.testMcpConnection({
          name: server.name,
          url: server.url,
          sseEndpoint: server.sseEndpoint,
          enabled: server.enabled,
        });
        // 拦截器已返回 response.data，这里 res 即后端 LeeResult JSON
        if (res && res.status === 200 && res.data) {
          this.testResults = { ...this.testResults, [index]: res.data as McpTestResult };
        } else if (res && res.data) {
          // status 非 200 但有 data，直接用 data
          this.testResults = {
            ...this.testResults,
            [index]: res.data as McpTestResult,
          };
        } else {
          this.testResults = {
            ...this.testResults,
            [index]: { ok: false, latencyMs: 0, error: "测试请求异常", tools: [] },
          };
        }
      } catch (e: any) {
        // axios 取消（超时或主动 cancel）会进入这里，e.code 通常是 'ERR_NETWORK' 或 'ECONNABORTED'
        let msg = "测试请求失败";
        if (e && e.code === "ECONNABORTED") {
          msg = "请求超时（120s），可能 MCP server 响应过慢或网络不通";
        } else if (e && e.code === "ERR_NETWORK") {
          msg = "网络错误，可能后端未启动或 CORS 被拒";
        } else if (e && e.response && e.response.data) {
          msg = e.response.data.msg || e.response.data.error || JSON.stringify(e.response.data);
        } else if (e && e.message) {
          msg = e.message;
        }
        this.testResults = {
          ...this.testResults,
          [index]: { ok: false, latencyMs: 0, error: msg, tools: [] },
        };
      } finally {
        this.testing = -1;
      }
    },
    async onSave() {
      // 保存前先尝试同步一次 JSON（如果用户编辑过 JSON 但没失焦）
      this.applyJsonToServers();
      if (this.jsonError) {
        this.$message && this.$message.error && this.$message.error("JSON 配置有误，请先修正");
        return;
      }
      for (const s of this.servers) {
        if (!s.name || !s.name.trim()) {
          this.$message && this.$message.error && this.$message.error("存在名称为空的 MCP Server");
          return;
        }
        if (!s.url || !s.url.trim()) {
          this.$message && this.$message.error && this.$message.error("存在 URL 为空的 MCP Server");
          return;
        }
      }
      this.saving = true;
      try {
        const payload = {
          servers: this.servers.map((s) => ({
            name: s.name,
            url: s.url,
            sseEndpoint: s.sseEndpoint,
            enabled: s.enabled,
          })),
        };
        const res = await doctorApi.saveMcpConfig(payload);
        if (res && res.status === 200 && res.data) {
          const data = res.data as { servers?: McpServerConfig[] };
          this.servers = (data.servers || []).map((s) => ({
            name: s.name || "",
            url: s.url || "",
            sseEndpoint: s.sseEndpoint || "/sse",
            enabled: s.enabled !== undefined ? s.enabled : true,
          }));
        }
        this.$message && this.$message.success && this.$message.success("MCP 配置已保存");
        await this.loadConfig();
      } catch (e) {
        console.error("保存 MCP 配置失败", e);
        this.$message && this.$message.error && this.$message.error("保存失败");
      } finally {
        this.saving = false;
      }
    },
  },
};
</script>

<style scoped>
.mcp-config-section {
  max-width: 640px;
}
.mcp-config-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 12px;
  background: var(--color-surface-container-lowest, var(--color-background));
}
.mcp-server-row {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 10px;
  background: var(--color-surface-container, var(--color-background));
}
.mcp-server-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.mcp-server-head-left {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.mcp-server-index {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
}
.mcp-server-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-on-surface);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mcp-server-name-empty {
  color: var(--color-on-surface-variant);
  opacity: 0.6;
  font-style: italic;
}
.mcp-server-head-right {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.mcp-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.mcp-label {
  font-size: 13px;
  color: var(--color-on-surface-variant);
  font-weight: 600;
}
.mcp-required {
  color: var(--color-error, #ef4444);
}
.mcp-input {
  padding: 8px 12px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-on-surface);
  font-size: 14px;
  font-family: "Inter", sans-serif;
  outline: none;
  transition: border-color 0.2s ease;
}
.mcp-input:focus {
  border-color: var(--color-primary);
}
.mcp-server-actions {
  display: flex;
  justify-content: flex-start;
}
.mcp-btn-secondary {
  padding: 8px 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}
.mcp-btn-secondary:hover:not(:disabled) {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.mcp-btn-secondary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.mcp-btn-danger {
  padding: 6px 12px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--color-error, #ef4444);
  font-size: 12px;
  cursor: pointer;
  transition: opacity 0.2s ease, border-color 0.2s ease;
}
.mcp-btn-danger:hover {
  opacity: 0.85;
  border-color: var(--color-error, #ef4444);
}
.mcp-test-result {
  font-size: 12px;
  margin: 0;
}
.mcp-test-ok {
  color: var(--color-primary);
}
.mcp-test-fail {
  color: var(--color-error, #ef4444);
}
.mcp-toggle {
  width: 44px;
  height: 24px;
  border-radius: 999px;
  border: none;
  background: var(--color-outline-variant);
  position: relative;
  cursor: pointer;
  transition: background 0.2s ease;
}
.mcp-toggle-on {
  background: var(--color-primary);
}
.mcp-toggle-knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #fff;
  transition: transform 0.2s ease;
}
.mcp-toggle-on .mcp-toggle-knob {
  transform: translateX(20px);
}
.mcp-empty {
  font-size: 13px;
  color: var(--color-on-surface-variant);
  opacity: 0.7;
  padding: 12px 4px;
  text-align: center;
}
.mcp-btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.mcp-btn-add {
  padding: 9px 16px;
  border: 1px dashed var(--color-outline-variant);
  border-radius: 8px;
  background: transparent;
  color: var(--color-on-surface-variant);
  font-size: 13px;
  cursor: pointer;
  transition: color 0.2s ease, border-color 0.2s ease;
}
.mcp-btn-add:hover {
  color: var(--color-primary);
  border-color: var(--color-primary);
}
.mcp-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}
.mcp-btn-primary {
  padding: 9px 24px;
  border: none;
  border-radius: 8px;
  background: var(--color-primary);
  color: var(--color-on-primary);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: opacity 0.2s ease;
}
.mcp-btn-primary:hover:not(:disabled) {
  opacity: 0.85;
}
.mcp-btn-primary:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
/* JSON 预览区 */
.mcp-json-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 10px;
  background: var(--color-surface-container, var(--color-background));
}
.mcp-json-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.mcp-json-actions {
  display: inline-flex;
  gap: 8px;
}
.mcp-json-hint {
  margin: 0;
  padding: 8px 10px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--color-on-surface-variant);
  background: var(--color-surface-container-high, rgba(0, 0, 0, 0.04));
  border-left: 3px solid var(--color-primary);
  border-radius: 4px;
}
.mcp-json-hint code {
  font-family: "JetBrains Mono", "Fira Code", "Consolas", "Menlo", monospace;
  font-size: 11.5px;
  padding: 1px 5px;
  background: var(--color-surface-container-highest, rgba(0, 0, 0, 0.08));
  border-radius: 3px;
  color: var(--color-primary);
}
.mcp-json-textarea {
  width: 100%;
  min-height: 180px;
  padding: 10px 12px;
  border: 1px solid var(--color-outline-variant);
  border-radius: 8px;
  background: var(--color-background);
  color: var(--color-on-surface);
  font-size: 12.5px;
  font-family: "JetBrains Mono", "Fira Code", "Consolas", "Menlo", monospace;
  line-height: 1.55;
  outline: none;
  resize: vertical;
  transition: border-color 0.2s ease;
  box-sizing: border-box;
}
.mcp-json-textarea:focus {
  border-color: var(--color-primary);
}
</style>
