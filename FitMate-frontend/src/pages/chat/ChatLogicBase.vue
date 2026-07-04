<script lang="ts">
/**
 * ChatLogicBase — logic-only base component.
 *
 * Holds all shared chat / SSE / agent-run / training / metrics / knowledge
 * business logic. It is never rendered on its own; the route pages
 * (ChatPage, TrainingPage, MetricsPage, DashboardPage, KnowledgePage) use
 * Vue `extends` to inherit this logic and supply their own Obsidian
 * Precision templates. Having no template of its own keeps the legacy
 * console UI from ever rendering.
 */
import { marked } from "marked";
import doctorApi from "../../services/doctorApi";
import { clearUserSession, getUserInfo } from "../../services/http";
import { connectSse, closeSse } from "../../services/sseService";
import {
  collectAgentTraceItems,
  isTerminalAgentEvent,
  normalizeAgentRunStatus as normalizeAgentRunStatusValue,
  normalizeAgentTraceEvent,
  normalizeAgentTraceNode,
  normalizeAgentTraceStatus as normalizeAgentTraceStatusValue,
} from "../../utils/agentEventAdapter";
import { extractSourcesFromResponse as extractSourcesFromResponseUtil } from "../../utils/sourceNormalizer";
import { llmConfig } from "../../services/llmConfig";
import { DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS } from "../../types/settings";

export default {
  name: "ChatLogicBase",
  emits: ["logout-success"],
  data() {
    return {
      botMsgId: null,
      currentUserName: null,
      currentUserInfo: null,
      isLoggingOut: false,
      activeView: "chat",
      chatExpanded: false,
      chatSessionList: [],
      activeChatSessionId: null,
      currentSessionCode: null,
      currentSessionSceneType: null,
      chatRecordsLoading: false,
      chatRecordsLoaded: false,
      chatList: [],
      draftMessage: "",

      knowledgeBaseSelected: true,
      ragSelected: false,
      internetSearchSelected: false,
      imageReadSelected: false,
      isSending: false,
      isStreaming: false,
      tokenUsage: null,
      isCompressing: false,
      // 自定义确认条状态（替代 window.confirm，显示在消息列表与输入框之间）
      confirmBar: {
        visible: false,
        text: "",
        confirmText: "确认",
        cancelText: "取消",
        resolve: null,
      },
      showBackToBottom: false,
      selectedUploadName: "",
      sseState: "idle",
      guidanceMessage: "选择任务模式后，输入指令开始执行。",
      activeAgentRun: null,
      agentStepEventReceived: false,
      // Console-specific state
      mobileLeftOpen: false,
      mobileRightOpen: false,
      todayStatus: {
        weight: null,
        bodyFat: null,
        fatigue: null,
        sleep: null,
        lastMuscleGroup: null,
      },
      weekSummary: {
        trainingDays: 0,
        totalVolume: 0,
        trend: "暂无数据",
      },
      resultSummary: {},
      reportContent: "",
      knowledgeSources: [],
      agentSteps: [],
      thinkingContent: "",
      isThinking: false,
      thinkingExpanded: true,
      docCount: 0,
      uploadSynced: false,
      uploadedDocs: [],
      recentTraining: [],
      recentMetrics: [],
      recentCardio: [],
      recentHeartRate: [],
      recentDiet: [],
      trainingSummary: null,
      bodyMetricsSummary: null,
      lastTtft: null,
      lastExecTime: "",
      taskStartTime: null,
      currentModel: "",
      availableModels: [] as Array<{ id: string; ownedBy: string }>,
      thinkingEnabled: true,
      reasoningEffort: "high" as "high" | "max",
      _llmConfigUnsub: null as (() => void) | null,
    };
  },
  computed: {
    canCompressContext() {
      // 有活动会话且消息数 >= 8 时才允许主动压缩
      return (
        this.activeChatSessionId != null &&
        Array.isArray(this.chatList) &&
        this.chatList.filter(function (item) {
          return item && (item.chatType === "user" || item.chatType === "bot");
        }).length >= 8
      );
    },
    activeModeLabel() {
      var mainLabel = "Agent";
      if (this.knowledgeBaseSelected && this.ragSelected) {
        return mainLabel + " + 知识库 Wiki + 原始文档";
      }
      if (this.knowledgeBaseSelected) {
        return mainLabel + " + 知识库 Wiki";
      }
      if (this.internetSearchSelected) {
        return mainLabel + " + 联网补充";
      }
      return mainLabel;
    },
    connectionBadgeText() {
      if (this.isStreaming) {
        return "流式处理中";
      }
      if (this.isSending) {
        return "请求处理中";
      }
      if (this.sseState === "connected") {
        return "SSE 已连接";
      }
      if (this.sseState === "connecting") {
        return "SSE 连接中";
      }
      if (this.sseState === "unsupported") {
        return "SSE 不可用";
      }
      if (this.sseState === "disconnected") {
        return "SSE 已断开";
      }
      return "SSE 待初始化";
    },
    sseBadgeClass() {
      if (this.isStreaming || this.isSending) {
        return "sse-connected";
      }
      return "sse-" + this.sseState;
    },
    connectionStatusText() {
      var userText = this.currentUserName
        ? "当前会话用户：" + this.currentUserName + "。"
        : "当前会话正在初始化。";
      var uploadText = this.selectedUploadName
        ? "已选文档：" + this.selectedUploadName + "。"
        : "未选择知识库文档。";
      var sseText = "SSE 通道尚未建立。";

      if (this.sseState === "connected") {
        sseText = "SSE 通道已连接，可接收实时回复。";
      } else if (this.sseState === "connecting") {
        sseText = "SSE 通道连接中，稍后即可接收实时回复。";
      } else if (this.sseState === "unsupported") {
        sseText = "当前环境暂不支持 SSE，回复可能无法实时展示。";
      } else if (this.sseState === "disconnected") {
        sseText = "SSE 通道已断开，发送新问题时会自动重连。";
      }

      return userText + sseText + uploadText;
    },
  },
  created() {
    this._sseConnection = null;
    this._sseSource = null;
    this._sseConnectingPromise = null;
    this.loadUserSessionFromCookie();
    this.restoreActiveAgentRun();
    this.applyRouteView();
    var stableUserKey = this.resolveStableUserKey();
    if (stableUserKey) {
      this.ensureSseConnection();
    } else {
      this.guidanceMessage = "请先完成手机号登录，再开始你的专属健身会话。";
    }
    this.currentModel = llmConfig.getConfig().model;
    this.availableModels = llmConfig.getModels();
    this.thinkingEnabled = llmConfig.getConfig().thinkingEnabled;
    this.reasoningEffort = llmConfig.getConfig().reasoningEffort;
    this._llmConfigUnsub = llmConfig.subscribe(() => {
      this.currentModel = llmConfig.getConfig().model;
      this.availableModels = llmConfig.getModels();
      this.thinkingEnabled = llmConfig.getConfig().thinkingEnabled;
      this.reasoningEffort = llmConfig.getConfig().reasoningEffort;
    });
    if (this.availableModels.length === 0) {
      llmConfig.fetchModels().catch(() => {});
    }
  },
  mounted() {
    this.scrollToBottom(true);
  },
  beforeUnmount() {
    if (this._stopTimeout) {
      clearTimeout(this._stopTimeout);
    }
    this.teardownSSE({ clearPending: true });
    if (this._llmConfigUnsub) {
      this._llmConfigUnsub();
      this._llmConfigUnsub = null;
    }
  },
  watch: {
    "$route.meta.forceView"(newView) {
      this.applyRouteView();
    },
    "$route.path"() {
      this.applyRouteView();
    },
  },
  methods: {
    applyRouteView() {
      var forceView =
        this.$route && this.$route.meta && this.$route.meta.forceView;
      if (forceView && forceView !== this.activeView) {
        this.activeView = forceView;
        this.handleSwitchView(forceView);
      } else if (!forceView && this.activeView !== "chat") {
        this.activeView = "chat";
      }
    },
    toggleMobileLeft() {
      this.mobileLeftOpen = !this.mobileLeftOpen;
      if (this.mobileLeftOpen) {
        this.mobileRightOpen = false;
      }
    },
    toggleMobileRight() {
      this.mobileRightOpen = !this.mobileRightOpen;
      if (this.mobileRightOpen) {
        this.mobileLeftOpen = false;
      }
    },
    closeMobileDrawers() {
      this.mobileLeftOpen = false;
      this.mobileRightOpen = false;
    },
    handleDirectTask(prompt) {
      this.activeView = "chat";
      this.closeMobileDrawers();
      this.draftMessage = prompt;
      var me = this;
      this.$nextTick(function () {
        me.doChat();
      });
    },
    handleSwitchView(viewName) {
      this.activeView = viewName;
      this.closeMobileDrawers();
      // Fetch contextual data for the target view
      if (viewName === "training-log") {
        this.fetchRecentTraining();
      } else if (viewName === "body-metrics") {
        this.fetchRecentMetrics();
      } else if (viewName === "upload") {
        this.fetchUploadedDocs();
      }
    },
    async handleToggleChatExpand() {
      this.activeView = "chat";
      if (this.chatExpanded) {
        this.chatExpanded = false;
        return;
      }

      this.chatExpanded = true;
      if (!this.chatRecordsLoaded && !this.chatRecordsLoading) {
        await this.fetchChatRecords();
      }
    },
    handleSelectChatSession(sessionId) {
      if (this.isSending || this.isStreaming || this.hasPendingAgentRun()) {
        this.showUiMessage(
          "error",
          "当前有运行中的任务，请稍后再切换聊天记录。"
        );
        return;
      }

      var targetSession = null;
      for (var i = 0; i < this.chatSessionList.length; i++) {
        if (this.chatSessionList[i].sessionId == sessionId) {
          targetSession = this.chatSessionList[i];
          break;
        }
      }

      if (!targetSession) {
        return;
      }

      var mappedChatList = this.mapSessionMessagesToChatList(
        targetSession.messages
      );

      this.clearActiveAgentRun();
      this.clearThinkingState();
      this.activeView = "chat";
      this.activeChatSessionId = targetSession.sessionId;
      this.currentSessionCode = targetSession.sessionCode || null;
      this.currentSessionSceneType = targetSession.sceneType || null;
      this.applyChatMode(this.resolvePreferredModeFromSession(targetSession));
      this.chatList = mappedChatList;
      this.agentSteps = [];
      this.botMsgId = null;
      this.tokenUsage = this.resolveLastUsageFromMessages(
        targetSession.messages
      );
      this.showBackToBottom = false;
      this.knowledgeSources = this.resolveChatHistorySources(mappedChatList);
      this.closeMobileDrawers();
      this.scrollToBottom(true);
    },
    handleCreateChat() {
      if (this.isSending || this.isStreaming || this.hasPendingAgentRun()) {
        this.showUiMessage("error", "当前有运行中的任务，请稍后再新建聊天。");
        return;
      }

      this.clearActiveAgentRun();
      this.clearThinkingState();
      this.activeView = "chat";
      this.activeChatSessionId = null;
      this.currentSessionCode = null;
      this.currentSessionSceneType = null;
      this.chatList = [];
      this.agentSteps = [];
      this.draftMessage = "";
      this.botMsgId = null;
      this.tokenUsage = null;
      this.showBackToBottom = false;
      this.knowledgeSources = [];
      this.closeMobileDrawers();
      this.scrollToBottom(true);
    },
    handleTrainingSubmit(formData) {
      var me = this;
      doctorApi
        .logTraining(formData)
        .then(function (res) {
          me.showUiMessage("success", "训练记录已保存");
          me.activeView = "chat";
        })
        .catch(function () {
          // API unavailable, fallback to agent chat
          me.activeView = "chat";
          me.draftMessage = me.buildTrainingPrompt(formData.exercises);
          me.$nextTick(function () {
            me.doChat();
          });
        });
    },
    handleBodyMetricsSubmit(formData) {
      var me = this;
      doctorApi
        .logBodyMetrics(formData)
        .then(function (res) {
          me.showUiMessage("success", "身体指标已保存");
          me.activeView = "chat";
          // sync todayStatus
          if (formData.weight != null) me.todayStatus.weight = formData.weight;
          if (formData.bodyFat != null)
            me.todayStatus.bodyFat = formData.bodyFat;
          if (formData.sleep != null) me.todayStatus.sleep = formData.sleep;
          if (formData.fatigue) me.todayStatus.fatigue = formData.fatigue;
        })
        .catch(function () {
          me.activeView = "chat";
          me.draftMessage = me.buildBodyMetricsPrompt(formData);
          me.$nextTick(function () {
            me.doChat();
          });
        });
    },
    buildTrainingPrompt(exercises) {
      var lines = ["记录今天训练："];
      for (var i = 0; i < exercises.length; i++) {
        var ex = exercises[i];
        lines.push(
          "- " +
            ex.name +
            "：" +
            ex.sets +
            "组 x " +
            ex.reps +
            "次，" +
            ex.weight +
            "kg"
        );
      }
      return lines.join("\n");
    },
    buildBodyMetricsPrompt(data) {
      var parts = ["记录今天身体指标："];
      if (data.weight != null) parts.push("体重 " + data.weight + "kg");
      if (data.bodyFat != null) parts.push("体脂 " + data.bodyFat + "%");
      if (data.sleep != null) parts.push("睡眠 " + data.sleep + "小时");
      if (data.fatigue) parts.push("疲劳度 " + data.fatigue);
      if (data.note) parts.push("备注：" + data.note);
      return parts.join("，");
    },
    focusUpload() {
      this.activeView = "upload";
      this.fetchUploadedDocs();
    },
    fetchRecentTraining() {
      var me = this;
      doctorApi
        .getRecentTraining(5)
        .then(function (res) {
          var data = res && res.data;
          if (Array.isArray(data)) {
            me.recentTraining = data;
          }
        })
        .catch(function () {
          // API not available, keep empty
        });
    },
    fetchRecentMetrics() {
      var me = this;
      doctorApi
        .getRecentMetrics(5)
        .then(function (res) {
          var data = res && res.data;
          if (Array.isArray(data)) {
            me.recentMetrics = data;
          }
        })
        .catch(function () {
          // API not available, keep empty
        });
    },
    fetchRecentCardio: function () {
      var me = this;
      doctorApi.getRecentCardio(10).then(function (res) {
        me.recentCardio = (res && res.data) || [];
      }).catch(function () { me.recentCardio = []; });
    },
    fetchRecentHeartRate: function () {
      var me = this;
      doctorApi.getRecentHeartRate(10).then(function (res) {
        me.recentHeartRate = (res && res.data) || [];
      }).catch(function () { me.recentHeartRate = []; });
    },
    fetchRecentDiet: function () {
      var me = this;
      doctorApi.getRecentDiet(10).then(function (res) {
        me.recentDiet = (res && res.data) || [];
      }).catch(function () { me.recentDiet = []; });
    },
    fetchTrainingSummary: function () {
      var me = this;
      doctorApi.getTrainingSummary().then(function (res) {
        me.trainingSummary = (res && res.data) || null;
      }).catch(function () { me.trainingSummary = null; });
    },
    fetchBodyMetricsSummary: function () {
      var me = this;
      doctorApi.getBodyMetricsSummary().then(function (res) {
        me.bodyMetricsSummary = (res && res.data) || null;
      }).catch(function () { me.bodyMetricsSummary = null; });
    },
    fetchUploadedDocs() {
      var me = this;
      doctorApi
        .getUploadedDocs()
        .then(function (res) {
          var data = res && res.data;
          if (Array.isArray(data)) {
            me.uploadedDocs = data;
            me.docCount = data.length;
            me.uploadSynced = true;
          } else {
            me.uploadedDocs = [];
            me.docCount = 0;
            me.uploadSynced = false;
          }
        })
        .catch(function () {
          me.uploadSynced = false;
          // API not available, keep empty
        });
    },
    fetchChatRecords() {
      var stableUserKey = this.resolveStableUserKey();
      if (!stableUserKey) {
        this.chatSessionList = [];
        this.chatRecordsLoaded = false;
        return Promise.resolve([]);
      }

      var me = this;
      this.chatRecordsLoading = true;
      return doctorApi
        .getRecords(stableUserKey)
        .then(function (res) {
          var data = me.unwrapApiData(res, "加载聊天记录失败");
          me.chatSessionList = me.normalizeChatSessions(data);
          me.chatRecordsLoaded = true;
          return me.chatSessionList;
        })
        .catch(function (error) {
          console.error("加载聊天记录失败:", error);
          me.chatSessionList = [];
          me.chatRecordsLoaded = false;
          me.showUiMessage(
            "error",
            error && error.message
              ? error.message
              : "加载聊天记录失败，请稍后重试。"
          );
          return [];
        })
        .finally(function () {
          me.chatRecordsLoading = false;
        });
    },
    normalizeChatSessions(recordData) {
      var sessions = [];
      if (Array.isArray(recordData)) {
        sessions = recordData.slice();
      } else if (recordData && Array.isArray(recordData.sessions)) {
        sessions = recordData.sessions.slice();
      }

      var me = this;
      return sessions
        .map(function (session) {
          var messages =
            session && Array.isArray(session.messages)
              ? session.messages.slice()
              : [];
          var updatedAt =
            (session && (session.updatedAt || session.createdAt)) ||
            (messages.length > 0
              ? messages[messages.length - 1].createdAt || null
              : null);

          return {
            sessionId: session ? session.sessionId : null,
            sessionCode:
              session && session.sessionCode
                ? String(session.sessionCode)
                : null,
            sceneType:
              session && session.sceneType
                ? String(session.sceneType).toLowerCase()
                : null,
            lastBotMsgId:
              session && session.lastBotMsgId
                ? session.lastBotMsgId
                : me.resolveLastSessionBotMsgId(messages),
            title: me.buildChatSessionTitle(session, messages),
            updatedAt: updatedAt,
            updatedAtLabel: me.formatChatSessionTime(updatedAt),
            messages: messages,
          };
        })
        .filter(function (session) {
          return session.sessionId != null;
        })
        .sort(function (a, b) {
          var aDate = me.resolveChatSessionDate(a.updatedAt);
          var bDate = me.resolveChatSessionDate(b.updatedAt);
          var aTime = aDate ? aDate.getTime() : 0;
          var bTime = bDate ? bDate.getTime() : 0;
          return bTime - aTime;
        });
    },
    buildChatSessionTitle(session, messages) {
      var explicitTitle =
        session && session.title ? String(session.title).trim() : "";
      if (explicitTitle) {
        return explicitTitle;
      }

      var safeMessages = Array.isArray(messages) ? messages : [];
      for (var i = 0; i < safeMessages.length; i++) {
        var message = safeMessages[i];
        if (!message || message.role !== "user") {
          continue;
        }

        var content =
          message.content == null
            ? ""
            : String(message.content).replace(/\s+/g, " ").trim();
        if (!content) {
          continue;
        }

        return content.length > 18 ? content.slice(0, 18) + "..." : content;
      }

      return "未命名会话";
    },
    formatChatSessionTime(rawValue) {
      var date = this.resolveChatSessionDate(rawValue);
      if (!date) {
        return "";
      }

      var month = String(date.getMonth() + 1).padStart(2, "0");
      var day = String(date.getDate()).padStart(2, "0");
      var hours = String(date.getHours()).padStart(2, "0");
      var minutes = String(date.getMinutes()).padStart(2, "0");
      return month + "-" + day + " " + hours + ":" + minutes;
    },
    resolveChatSessionDate(rawValue) {
      if (!rawValue) {
        return null;
      }

      var date = rawValue instanceof Date ? rawValue : new Date(rawValue);
      if (isNaN(date.getTime())) {
        return null;
      }

      return date;
    },
    resolveLastSessionBotMsgId(messages) {
      if (!Array.isArray(messages)) {
        return null;
      }

      for (var i = messages.length - 1; i >= 0; i--) {
        if (messages[i] && messages[i].botMsgId) {
          return messages[i].botMsgId;
        }
      }

      return null;
    },
    resolvePreferredModeFromSession(session) {
      var sceneType =
        session && session.sceneType === "agent" ? "agent" : "chat";
      var sourceType = "chat";

      var messages =
        session && Array.isArray(session.messages) ? session.messages : [];
      for (var i = messages.length - 1; i >= 0; i--) {
        var message = messages[i];
        var currentSourceType =
          message && message.sourceType
            ? String(message.sourceType).toLowerCase()
            : "";

        if (
          currentSourceType === "rag" ||
          currentSourceType === "internet" ||
          currentSourceType === "chat"
        ) {
          sourceType = currentSourceType;
          break;
        }
      }

      return {
        sceneType: sceneType,
        sourceType: sourceType,
      };
    },
    applyChatMode(modeState) {
      var sourceType =
        modeState && modeState.sourceType
          ? String(modeState.sourceType).toLowerCase()
          : "chat";

      this.imageReadSelected = false;
      this.knowledgeBaseSelected =
        sourceType === "rag" || sourceType === "wiki";
      this.ragSelected = sourceType === "rag";
      this.internetSearchSelected = sourceType === "internet";
    },
    resolveExpectedSessionSceneType() {
      return "agent";
    },
    applyServerSessionMeta(payload, fallbackSceneType) {
      if (!payload || typeof payload !== "object") {
        return;
      }

      if (payload.chatSessionId != null) {
        this.activeChatSessionId = payload.chatSessionId;
      }

      if (payload.sessionCode) {
        this.currentSessionCode = String(payload.sessionCode);
      }

      var nextSceneType =
        payload.sceneType != null && payload.sceneType !== ""
          ? String(payload.sceneType).toLowerCase()
          : fallbackSceneType
          ? String(fallbackSceneType).toLowerCase()
          : null;
      if (nextSceneType) {
        this.currentSessionSceneType = nextSceneType;
      }
    },
    refreshChatRecordsIfNeeded() {
      if (
        (!this.chatExpanded && !this.chatRecordsLoaded) ||
        this.chatRecordsLoading
      ) {
        return Promise.resolve(this.chatSessionList);
      }

      return this.fetchChatRecords();
    },
    showUiMessage(type, text) {
      if (this.$message && typeof this.$message[type] === "function") {
        this.$message[type](text);
      }
    },
    /**
     * 弹出自定义确认条，返回 Promise<boolean>（true=确认，false=取消）。
     * 用于替代 window.confirm，UI 显示在消息列表与输入框之间。
     */
    showConfirmBar(text, options) {
      var me = this;
      return new Promise(function (resolve) {
        me.confirmBar.text = text || "确认操作？";
        me.confirmBar.confirmText = (options && options.confirmText) || "确认";
        me.confirmBar.cancelText = (options && options.cancelText) || "取消";
        me.confirmBar.resolve = resolve;
        me.confirmBar.visible = true;
      });
    },
    confirmBarAccept() {
      var resolve = this.confirmBar.resolve;
      this.confirmBar.visible = false;
      this.confirmBar.resolve = null;
      this.confirmBar.text = "";
      if (typeof resolve === "function") {
        resolve(true);
      }
    },
    confirmBarCancel() {
      var resolve = this.confirmBar.resolve;
      this.confirmBar.visible = false;
      this.confirmBar.resolve = null;
      this.confirmBar.text = "";
      if (typeof resolve === "function") {
        resolve(false);
      }
    },
    unwrapApiData(res, fallbackMsg) {
      if (!res) {
        throw new Error(fallbackMsg || "请求失败");
      }
      if (typeof res.status !== "undefined" && res.status !== 200) {
        throw new Error(res.msg || fallbackMsg || "请求失败");
      }
      return typeof res.data === "undefined" ? res : res.data;
    },
    loadUserSessionFromCookie() {
      var userInfo = getUserInfo();
      if (!userInfo) {
        this.currentUserInfo = null;
        this.currentUserName = null;
        return;
      }
      this.currentUserInfo = userInfo;
      this.currentUserName = userInfo.userKey || userInfo.id || null;
    },
    resolveStableUserKey() {
      if (this.currentUserInfo) {
        return this.currentUserInfo.userKey || this.currentUserInfo.id || null;
      }
      return this.currentUserName || null;
    },
    getActiveAgentRunStorageKey() {
      var stableUserKey = this.resolveStableUserKey();
      if (!stableUserKey || typeof window === "undefined") {
        return null;
      }
      return "fitmate:active-run:" + String(stableUserKey);
    },
    normalizeAgentRunStatus(status) {
      return normalizeAgentRunStatusValue(status);
    },
    normalizeAgentStepStatus(status) {
      return normalizeAgentTraceStatusValue(status);
    },
    isTerminalAgentRunStatus(status) {
      var normalized = this.normalizeAgentRunStatus(status);
      return normalized === "success" || normalized === "failed";
    },
    hasPendingAgentRun() {
      return !!(
        this.activeAgentRun &&
        this.activeAgentRun.runId != null &&
        !this.isTerminalAgentRunStatus(this.activeAgentRun.status)
      );
    },
    normalizeAgentStepItem(step, index) {
      if (!step) {
        return null;
      }
      return normalizeAgentTraceNode(step, index);
    },
    buildActiveAgentRunSnapshot() {
      if (!this.activeAgentRun || this.activeAgentRun.runId == null) {
        return null;
      }
      var steps = [];
      if (Array.isArray(this.agentSteps)) {
        for (var i = 0; i < this.agentSteps.length; i++) {
          var normalizedStep = this.normalizeAgentStepItem(
            this.agentSteps[i],
            i
          );
          if (normalizedStep) {
            steps.push(normalizedStep);
          }
        }
      }
      return {
        version: 2,
        runId: this.activeAgentRun.runId,
        chatSessionId: this.activeAgentRun.chatSessionId || null,
        sessionCode: this.activeAgentRun.sessionCode || null,
        botMsgId: this.activeAgentRun.botMsgId || this.botMsgId || null,
        status: this.normalizeAgentRunStatus(this.activeAgentRun.status),
        requestText: this.activeAgentRun.requestText || "",
        sceneType: this.activeAgentRun.sceneType || "agent",
        sourceType: this.activeAgentRun.sourceType || "chat",
        finishReceived: !!this.activeAgentRun.finishReceived,
        steps: steps,
        traceNodes: steps,
        lastUpdatedAt: new Date().toISOString(),
      };
    },
    snapshotActiveAgentRun() {
      var key = this.getActiveAgentRunStorageKey();
      if (!key || typeof window === "undefined") {
        return;
      }
      var snapshot = this.buildActiveAgentRunSnapshot();
      if (!snapshot || this.isTerminalAgentRunStatus(snapshot.status)) {
        window.sessionStorage.removeItem(key);
        return;
      }
      window.sessionStorage.setItem(key, JSON.stringify(snapshot));
    },
    clearActiveAgentRun(options) {
      var key = this.getActiveAgentRunStorageKey();
      if (key && typeof window !== "undefined") {
        window.sessionStorage.removeItem(key);
      }
      this.activeAgentRun = null;
      this.agentStepEventReceived = false;
      if (!options || options.clearSteps !== false) {
        this.agentSteps = [];
      }
    },
    restoreActiveAgentRun() {
      var key = this.getActiveAgentRunStorageKey();
      if (!key || typeof window === "undefined") {
        return;
      }
      var rawValue = window.sessionStorage.getItem(key);
      if (!rawValue) {
        return;
      }
      var snapshot = this.safeParseJson(rawValue);
      if (!snapshot || typeof snapshot !== "object" || snapshot.runId == null) {
        window.sessionStorage.removeItem(key);
        return;
      }
      if (this.isTerminalAgentRunStatus(snapshot.status)) {
        window.sessionStorage.removeItem(key);
        return;
      }

      var snapshotTraceItems = Array.isArray(snapshot.traceNodes)
        ? snapshot.traceNodes
        : snapshot.steps;
      var steps = [];
      if (Array.isArray(snapshotTraceItems)) {
        for (var i = 0; i < snapshotTraceItems.length; i++) {
          var normalizedStep = this.normalizeAgentStepItem(
            snapshotTraceItems[i],
            i
          );
          if (normalizedStep) {
            steps.push(normalizedStep);
          }
        }
      }

      this.activeAgentRun = {
        runId: snapshot.runId,
        chatSessionId:
          snapshot.chatSessionId != null ? snapshot.chatSessionId : null,
        sessionCode: snapshot.sessionCode ? String(snapshot.sessionCode) : null,
        botMsgId: snapshot.botMsgId ? String(snapshot.botMsgId) : null,
        status: this.normalizeAgentRunStatus(snapshot.status),
        requestText: snapshot.requestText || "",
        sceneType: snapshot.sceneType || "agent",
        sourceType: snapshot.sourceType || "chat",
        steps: steps,
        finishReceived: !!snapshot.finishReceived,
      };
      this.agentStepEventReceived = steps.length > 0;
      this.activeView = "chat";
      if (this.activeAgentRun.chatSessionId != null) {
        this.activeChatSessionId = this.activeAgentRun.chatSessionId;
      }
      if (this.activeAgentRun.sessionCode) {
        this.currentSessionCode = this.activeAgentRun.sessionCode;
      }
      this.currentSessionSceneType = this.activeAgentRun.sceneType || "agent";
      if (this.activeAgentRun.botMsgId) {
        this.botMsgId = this.activeAgentRun.botMsgId;
      }
      if (steps.length > 0) {
        this.agentSteps = steps;
      } else {
        this.agentSteps = [];
        this.guidanceMessage = "正在恢复 Agent 执行轨迹，请稍候。";
      }
      this.silentRestoreChatSession(this.activeAgentRun.chatSessionId);
      this.silentFetchAgentRunDetail(this.activeAgentRun.runId);
    },
    safeParseJson(rawValue) {
      if (rawValue == null) {
        return null;
      }
      if (typeof rawValue === "object") {
        return rawValue;
      }
      try {
        return JSON.parse(String(rawValue));
      } catch (error) {
        return null;
      }
    },
    isAgentRunQueryUnavailable(error) {
      var status =
        error && error.response && error.response.status != null
          ? error.response.status
          : null;
      return status === 404 || status === 405 || status === 501;
    },
    silentRestoreChatSession(sessionId) {
      if (sessionId == null) {
        return Promise.resolve(null);
      }
      var stableUserKey = this.resolveStableUserKey();
      if (!stableUserKey) {
        return Promise.resolve(null);
      }
      var me = this;
      return doctorApi
        .getRecords(stableUserKey, sessionId, 1)
        .then(function (res) {
          var data = me.unwrapApiData(res, "加载聊天记录失败");
          var sessions = me.normalizeChatSessions(data);
          if (!Array.isArray(sessions) || sessions.length === 0) {
            return null;
          }
          var targetSession = sessions[0];
          var mappedChatList = me.mapSessionMessagesToChatList(
            targetSession.messages
          );
          me.activeView = "chat";
          me.activeChatSessionId = targetSession.sessionId;
          me.currentSessionCode =
            targetSession.sessionCode || me.currentSessionCode;
          me.currentSessionSceneType =
            targetSession.sceneType || me.currentSessionSceneType;
          me.chatList = mappedChatList;
          me.knowledgeSources = me.resolveChatHistorySources(mappedChatList);
          me.tokenUsage = me.resolveLastUsageFromMessages(
            targetSession.messages
          );
          me.scrollToBottom(true);
          return targetSession;
        })
        .catch(function (error) {
          console.warn("静默恢复聊天会话失败:", error);
          return null;
        });
    },
    applyAgentRunDetail(detail) {
      if (!detail || typeof detail !== "object") {
        return;
      }
      var runId = detail.runId != null ? detail.runId : null;
      var normalizedStatus = this.normalizeAgentRunStatus(detail.status);
      if (!this.activeAgentRun) {
        this.activeAgentRun = {
          runId: runId,
          chatSessionId: null,
          sessionCode: null,
          botMsgId: null,
          status: normalizedStatus,
          requestText: "",
          sceneType: "agent",
          sourceType: "chat",
          steps: [],
          finishReceived: false,
        };
      }
      if (runId != null) {
        this.activeAgentRun.runId = runId;
      }
      if (detail.chatSessionId != null) {
        this.activeAgentRun.chatSessionId = detail.chatSessionId;
      }
      if (detail.sessionCode) {
        this.activeAgentRun.sessionCode = String(detail.sessionCode);
      }
      if (detail.botMsgId) {
        this.activeAgentRun.botMsgId = String(detail.botMsgId);
      }
      if (detail.requestText) {
        this.activeAgentRun.requestText = detail.requestText;
      }
      this.activeAgentRun.status = normalizedStatus;
      this.activeAgentRun.sceneType = "agent";
      if (detail.sourceType) {
        this.activeAgentRun.sourceType = String(
          detail.sourceType
        ).toLowerCase();
      }

      var traceItems = collectAgentTraceItems(detail);
      var hasServerTrace =
        Array.isArray(detail.steps) ||
        Array.isArray(detail.trace) ||
        Array.isArray(detail.events) ||
        Array.isArray(detail.nodes) ||
        Array.isArray(detail.timeline);
      var steps = [];
      if (traceItems.length > 0) {
        for (var i = 0; i < traceItems.length; i++) {
          var normalizedStep = this.normalizeAgentStepItem(traceItems[i], i);
          if (normalizedStep) {
            steps.push(normalizedStep);
          }
        }
      }
      if (steps.length > 0) {
        this.agentSteps = steps;
        this.agentStepEventReceived = true;
        this.activeAgentRun.steps = steps;
      } else if (hasServerTrace) {
        this.agentSteps = [];
        this.agentStepEventReceived = false;
        this.activeAgentRun.steps = [];
      }

      this.applyServerSessionMeta(detail, "agent");
      if (this.activeAgentRun.chatSessionId != null) {
        this.activeChatSessionId = this.activeAgentRun.chatSessionId;
      }
      if (this.activeAgentRun.sessionCode) {
        this.currentSessionCode = this.activeAgentRun.sessionCode;
      }
      if (this.activeAgentRun.botMsgId) {
        this.botMsgId = this.activeAgentRun.botMsgId;
      }
      this.currentSessionSceneType = "agent";
      this.snapshotActiveAgentRun();
      if (this.isTerminalAgentRunStatus(normalizedStatus)) {
        this.guidanceMessage =
          normalizedStatus === "success"
            ? "本轮任务已完成，可继续发起新任务。"
            : "任务执行失败，请调整后重试。";
      }
    },
    silentFetchAgentRunDetail(runId) {
      if (runId == null) {
        return Promise.resolve(null);
      }
      var me = this;
      return doctorApi
        .getAgentRunDetail(runId)
        .then(function (res) {
          var data = me.unwrapApiData(res, "加载运行详情失败");
          me.applyAgentRunDetail(data);
          return data;
        })
        .catch(function (error) {
          if (me.isAgentRunQueryUnavailable(error)) {
            return null;
          }
          console.warn("静默加载Agent运行详情失败:", error);
          return null;
        });
    },
    applyAgentExecuteAck(
      payload,
      requestPayload,
      expectedSceneType,
      sourceType
    ) {
      if (!payload || payload.runId == null) {
        return false;
      }
      this.activeAgentRun = {
        runId: payload.runId,
        chatSessionId:
          payload.chatSessionId != null ? payload.chatSessionId : null,
        sessionCode: payload.sessionCode ? String(payload.sessionCode) : null,
        botMsgId: payload.botMsgId
          ? String(payload.botMsgId)
          : requestPayload && requestPayload.botMsgId
          ? String(requestPayload.botMsgId)
          : null,
        status: this.normalizeAgentRunStatus(payload.status),
        requestText:
          requestPayload && requestPayload.message
            ? requestPayload.message
            : "",
        sceneType: expectedSceneType || "agent",
        sourceType: sourceType || "chat",
        steps: [],
        finishReceived: false,
      };
      this.agentStepEventReceived = false;
      this.applyServerSessionMeta(payload, expectedSceneType || "agent");
      this.currentSessionSceneType = expectedSceneType || "agent";
      if (this.activeAgentRun.botMsgId) {
        this.botMsgId = this.activeAgentRun.botMsgId;
      }
      this.snapshotActiveAgentRun();
      this.silentFetchAgentRunDetail(payload.runId);
      return true;
    },
    normalizeAddPayload(rawValue) {
      var parsed = this.safeParseJson(rawValue);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        var chunkText = parsed.contentChunk;
        if (chunkText == null) {
          chunkText = parsed.delta;
        }
        if (chunkText == null) {
          chunkText = parsed.content;
        }
        if (chunkText == null) {
          chunkText = parsed.thinkingContent;
        }
        if (chunkText == null) {
          chunkText = parsed.reasoningContent;
        }
        if (chunkText == null) {
          chunkText = parsed.reasoningText;
        }
        if (chunkText == null) {
          chunkText = parsed.reasoning;
        }
        if (chunkText == null) {
          chunkText = parsed.message;
        }
        if (chunkText == null) {
          chunkText = parsed.text;
        }
        return {
          chunkText: chunkText == null ? "" : String(chunkText),
          botMsgId: parsed.botMsgId ? String(parsed.botMsgId) : null,
          runId: parsed.runId != null ? parsed.runId : null,
          chatSessionId:
            parsed.chatSessionId != null ? parsed.chatSessionId : null,
          sessionCode: parsed.sessionCode ? String(parsed.sessionCode) : null,
          sceneType: parsed.sceneType
            ? String(parsed.sceneType).toLowerCase()
            : null,
          sourceType: parsed.sourceType
            ? String(parsed.sourceType).toLowerCase()
            : null,
          chunkType: parsed.chunkType
            ? String(parsed.chunkType).toLowerCase()
            : parsed.type
            ? String(parsed.type).toLowerCase()
            : null,
        };
      }
      return {
        chunkText: rawValue == null ? "" : String(rawValue),
        botMsgId: null,
        runId: null,
        chatSessionId: null,
        sessionCode: null,
        sceneType: null,
        sourceType: null,
        chunkType: null,
      };
    },
    handleThinkingEvent(rawValue) {
      var payload = this.normalizeAddPayload(rawValue);
      if (!payload) {
        return;
      }
      if (
        payload.runId != null &&
        this.activeAgentRun &&
        this.activeAgentRun.runId != null &&
        String(payload.runId) !== String(this.activeAgentRun.runId)
      ) {
        return;
      }
      if (
        payload.chunkType &&
        payload.chunkType !== "thinking" &&
        payload.chunkType !== "reasoning"
      ) {
        return;
      }
      var thinkingText =
        payload.chunkText == null ? "" : String(payload.chunkText);
      if (!thinkingText) {
        return;
      }
      this.thinkingContent = (this.thinkingContent || "") + thinkingText;
      this.isThinking = true;
      if (payload.botMsgId) {
        this.botMsgId = payload.botMsgId;
      }

      var botMsgId =
        payload.botMsgId ||
        (this.activeAgentRun && this.activeAgentRun.botMsgId) ||
        this.botMsgId;
      if (botMsgId) {
        var targetMsg = this.findOrCreateBotMessage(botMsgId, payload);
        if (targetMsg) {
          targetMsg.thinkingContent =
            (targetMsg.thinkingContent || "") + thinkingText;
          targetMsg.isThinking = true;
          if (payload.runId != null) {
            targetMsg.runId = payload.runId;
          }
          if (targetMsg.thinkingExpanded === undefined) {
            targetMsg.thinkingExpanded = true;
          }
        }
      }

      this.applyServerSessionMeta(
        {
          chatSessionId: payload.chatSessionId,
          sessionCode: payload.sessionCode,
          sceneType:
            payload.sceneType ||
            this.currentSessionSceneType ||
            this.resolveExpectedSessionSceneType(),
        },
        payload.sceneType ||
          this.currentSessionSceneType ||
          this.resolveExpectedSessionSceneType()
      );
      if (this.activeAgentRun) {
        if (payload.runId != null) {
          this.activeAgentRun.runId = payload.runId;
        }
        if (payload.chatSessionId != null) {
          this.activeAgentRun.chatSessionId = payload.chatSessionId;
        }
        if (payload.sessionCode) {
          this.activeAgentRun.sessionCode = payload.sessionCode;
        }
        if (payload.botMsgId) {
          this.activeAgentRun.botMsgId = payload.botMsgId;
        }
        this.snapshotActiveAgentRun();
      }
      this.scrollToBottom();
    },
    clearThinkingState() {
      this.thinkingContent = "";
      this.isThinking = false;
      this.thinkingExpanded = true;
    },
    toggleThinkingExpanded(message) {
      if (message && typeof message === "object" && message.botMsgId) {
        message.thinkingExpanded = !message.thinkingExpanded;
        return;
      }
      this.thinkingExpanded = !this.thinkingExpanded;
    },
    findOrCreateBotMessage(botMsgId, payload) {
      var payloadRunId =
        payload && payload.runId != null ? String(payload.runId) : null;
      if (!botMsgId) {
        botMsgId =
          (this.activeAgentRun && this.activeAgentRun.botMsgId) ||
          this.botMsgId;
      }
      for (var i = 0; i < this.chatList.length; i++) {
        // 跳过 user 消息：user 消息也携带 botMsgId（用于回滚定位），
        // 但 THINKING/ADD/FINISH 事件只应匹配 bot 消息，否则会把回答覆盖到用户消息上
        if (
          botMsgId &&
          this.chatList[i].botMsgId == botMsgId &&
          this.chatList[i].chatType !== "user"
        ) {
          return this.chatList[i];
        }
        if (
          payloadRunId &&
          this.chatList[i].runId != null &&
          String(this.chatList[i].runId) === payloadRunId
        ) {
          return this.chatList[i];
        }
      }
      if (!botMsgId) {
        return null;
      }
      var sessionMeta = {
        sessionCode:
          (payload && payload.sessionCode) || this.currentSessionCode || null,
        sceneType:
          (payload && payload.sceneType) ||
          this.currentSessionSceneType ||
          this.resolveExpectedSessionSceneType(),
      };
      var newMsg = {
        id: "temp-" + this.generateRandomId(8),
        content: "",
        userName: "bot",
        chatType: "bot",
        botMsgId: botMsgId,
        runId: payloadRunId,
        createdAt: new Date().toISOString(),
        sources: [],
        sourceType: (payload && payload.sourceType) || null,
        sessionCode: sessionMeta.sessionCode,
        sceneType: sessionMeta.sceneType,
        thinkingContent: "",
        isThinking: false,
        thinkingExpanded: true,
        agentSteps: [],
      };
      this.chatList.push(newMsg);
      return newMsg;
    },
    upsertStreamingBotMessage(payload) {
      if (!payload) {
        return;
      }
      if (payload.chunkType && payload.chunkType !== "content") {
        return;
      }
      if (
        payload.runId != null &&
        this.activeAgentRun &&
        this.activeAgentRun.runId != null &&
        String(payload.runId) !== String(this.activeAgentRun.runId)
      ) {
        return;
      }
      var receiveMsg =
        payload.chunkText == null ? "" : String(payload.chunkText);
      if (!receiveMsg) {
        return;
      }
      var botMsgId =
        payload.botMsgId ||
        (this.activeAgentRun && this.activeAgentRun.botMsgId) ||
        this.botMsgId;
      if (!botMsgId) {
        return;
      }
      if (this.taskStartTime && !this.lastTtft) {
        this.lastTtft = Date.now() - this.taskStartTime;
      }
      this.isSending = false;
      this.isStreaming = true;
      this.guidanceMessage = "正在生成回答，请稍候。";

      var sessionMeta = {
        chatSessionId:
          payload.chatSessionId != null
            ? payload.chatSessionId
            : this.activeAgentRun && this.activeAgentRun.chatSessionId != null
            ? this.activeAgentRun.chatSessionId
            : null,
        sessionCode:
          payload.sessionCode ||
          (this.activeAgentRun && this.activeAgentRun.sessionCode) ||
          this.currentSessionCode ||
          null,
        sceneType:
          payload.sceneType ||
          this.currentSessionSceneType ||
          this.resolveExpectedSessionSceneType(),
      };
      this.applyServerSessionMeta(sessionMeta, sessionMeta.sceneType);

      if (this.activeAgentRun) {
        this.activeAgentRun.botMsgId = botMsgId;
        if (payload.chatSessionId != null) {
          this.activeAgentRun.chatSessionId = payload.chatSessionId;
        }
        if (payload.sessionCode) {
          this.activeAgentRun.sessionCode = payload.sessionCode;
        }
        if (payload.runId != null) {
          this.activeAgentRun.runId = payload.runId;
        }
        if (!this.isTerminalAgentRunStatus(this.activeAgentRun.status)) {
          this.activeAgentRun.status = "running";
        }
        this.snapshotActiveAgentRun();
      }

      var targetChatItem = null;
      for (var i = 0; i < this.chatList.length; i++) {
        var chatItem = this.chatList[i];
        if (chatItem.botMsgId == botMsgId && chatItem.chatType !== "user") {
          targetChatItem = chatItem;
          break;
        }
      }

      if (!targetChatItem) {
        this.chatList.push({
          id: "temp-" + this.generateRandomId(8),
          content: receiveMsg,
          userName: "bot",
          chatType: "bot",
          botMsgId: botMsgId,
          runId:
            payload.runId != null
              ? payload.runId
              : this.activeAgentRun && this.activeAgentRun.runId != null
              ? this.activeAgentRun.runId
              : null,
          createdAt: new Date().toISOString(),
          sources: [],
          sourceType: payload.sourceType || null,
          sessionCode: sessionMeta.sessionCode,
          sceneType: sessionMeta.sceneType,
        });
      } else {
        targetChatItem.content = (targetChatItem.content || "") + receiveMsg;
        targetChatItem.sessionCode =
          targetChatItem.sessionCode || sessionMeta.sessionCode || null;
        targetChatItem.sceneType =
          targetChatItem.sceneType || sessionMeta.sceneType || null;
        targetChatItem.sourceType =
          targetChatItem.sourceType || payload.sourceType || null;
        targetChatItem.runId =
          targetChatItem.runId != null
            ? targetChatItem.runId
            : payload.runId != null
            ? payload.runId
            : this.activeAgentRun && this.activeAgentRun.runId != null
            ? this.activeAgentRun.runId
            : null;
      }
      this.scrollToBottom();
    },
    handleAgentCustomEvent(rawValue) {
      // 优先识别上下文压缩事件（context_compressing / context_compressed / context_compress_failed）
      if (this.handleCompressEvent(rawValue)) {
        return;
      }
      this.handleAgentEvent(rawValue);
    },
    handleCompressEvent(rawValue) {
      var payload = null;
      if (rawValue && typeof rawValue === "object") {
        payload = rawValue;
      } else if (typeof rawValue === "string") {
        try {
          payload = JSON.parse(rawValue);
        } catch (e) {
          payload = null;
        }
      }
      if (!payload || typeof payload !== "object") return false;
      var evt = payload.event;
      if (evt === "context_compressing") {
        this.isCompressing = true;
        return true;
      }
      if (evt === "context_compressed") {
        this.isCompressing = false;
        // 立即刷新右下角用量为压缩后快照
        if (payload.tokenAfter != null) {
          this.tokenUsage = {
            promptTokens: payload.tokenAfter,
            completionTokens: 0,
            totalTokens: payload.tokenAfter,
            cumulativeTotalTokens:
              this.tokenUsage && this.tokenUsage.cumulativeTotalTokens != null
                ? this.tokenUsage.cumulativeTotalTokens
                : payload.tokenAfter,
            contextWindow:
              payload.contextWindow != null
                ? payload.contextWindow
                : (this.tokenUsage && this.tokenUsage.contextWindow) ||
                  llmConfig.getConfig().maxInputContextTokens ||
                  DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS,
            cacheHitTokens: null,
            cacheMissTokens: null,
            reasoningTokens: null,
          };
        }

        this.chatList.push({
          id: "compress-" + Date.now(),
          chatType: "system",
          compressCount: payload.compressedCount || 0,
          summaryContent: "",
          createdAt: new Date().toISOString(),
        });
        this.scrollToBottom();
        return true;
      }
      if (evt === "context_compress_failed") {
        this.isCompressing = false;
        if (this.toast && typeof this.toast.info === "function") {
          this.toast.info(
            "上下文压缩失败" + (payload.reason ? "：" + payload.reason : "")
          );
        }
        return true;
      }
      return false;
    },
    handleAgentEvent(rawValue) {
      var event = normalizeAgentTraceEvent(rawValue);
      if (!event) {
        return;
      }
      if (
        event.runId != null &&
        this.activeAgentRun &&
        this.activeAgentRun.runId != null &&
        String(event.runId) !== String(this.activeAgentRun.runId)
      ) {
        return;
      }
      var normalizedStep = this.normalizeAgentStepItem(
        event,
        this.agentSteps.length
      );
      if (!normalizedStep) {
        return;
      }
      this.applyAgentStepEvent(normalizedStep, event);
    },
    applyAgentStepEvent(stepEvent, eventPayload) {
      if (!stepEvent) {
        return;
      }
      this.agentStepEventReceived = true;
      var matchedIndex = -1;
      for (var i = 0; i < this.agentSteps.length; i++) {
        var currentStep = this.agentSteps[i];
        var currentId =
          currentStep && currentStep.id ? String(currentStep.id) : null;
        var nextId = stepEvent && stepEvent.id ? String(stepEvent.id) : null;
        var sameStableId = currentId && nextId && currentId === nextId;
        var sameLegacyStep =
          !sameStableId &&
          currentStep &&
          currentStep.stepNo != null &&
          stepEvent.stepNo != null &&
          currentStep.iterationNo == stepEvent.iterationNo &&
          Number(currentStep.stepNo) === Number(stepEvent.stepNo);
        if (sameStableId || sameLegacyStep) {
          matchedIndex = i;
          break;
        }
      }
      if (matchedIndex >= 0) {
        this.agentSteps[matchedIndex] = Object.assign(
          {},
          this.agentSteps[matchedIndex],
          stepEvent
        );
      } else {
        this.agentSteps.push(stepEvent);
      }
      this.agentSteps = this.agentSteps
        .slice()
        .sort(function (a, b) {
          var aSeq = a && a.sequence != null ? Number(a.sequence) : NaN;
          var bSeq = b && b.sequence != null ? Number(b.sequence) : NaN;
          if (!isNaN(aSeq) && !isNaN(bSeq) && aSeq !== bSeq) {
            return aSeq - bSeq;
          }
          var aStepNo = a && a.stepNo != null ? Number(a.stepNo) : NaN;
          var bStepNo = b && b.stepNo != null ? Number(b.stepNo) : NaN;
          if (!isNaN(aStepNo) && !isNaN(bStepNo) && aStepNo !== bStepNo) {
            return aStepNo - bStepNo;
          }
          var aTime = a && a.createdAt ? new Date(a.createdAt).getTime() : 0;
          var bTime = b && b.createdAt ? new Date(b.createdAt).getTime() : 0;
          return aTime - bTime;
        })
        .map(
          function (item, index) {
            return this.normalizeAgentStepItem(item, index);
          }.bind(this)
        )
        .filter(function (item) {
          return !!item;
        });

      if (!this.activeAgentRun) {
        this.activeAgentRun = {
          runId:
            eventPayload && eventPayload.runId != null
              ? eventPayload.runId
              : null,
          chatSessionId: null,
          sessionCode: this.currentSessionCode || null,
          botMsgId: this.botMsgId || null,
          status: "running",
          requestText: "",
          sceneType: "agent",
          sourceType: "chat",
          steps: [],
          finishReceived: false,
        };
      }
      if (eventPayload) {
        if (eventPayload.runId != null) {
          this.activeAgentRun.runId = eventPayload.runId;
        }
        if (eventPayload.chatSessionId != null) {
          this.activeAgentRun.chatSessionId = eventPayload.chatSessionId;
        }
        if (eventPayload.sessionCode) {
          this.activeAgentRun.sessionCode = String(eventPayload.sessionCode);
        }
        if (eventPayload.botMsgId) {
          this.activeAgentRun.botMsgId = String(eventPayload.botMsgId);
        }
      }
      this.activeAgentRun.steps = this.agentSteps.slice();
      var agentBotMsgId =
        (eventPayload && eventPayload.botMsgId) ||
        (this.activeAgentRun && this.activeAgentRun.botMsgId) ||
        this.botMsgId;
      if (agentBotMsgId) {
        var targetMsg = this.findOrCreateBotMessage(
          agentBotMsgId,
          eventPayload
        );
        if (targetMsg) {
          targetMsg.agentSteps = this.agentSteps.slice();
        }
      }
      var normalizedEvent = normalizeAgentTraceEvent(eventPayload || stepEvent);
      var eventType =
        normalizedEvent && normalizedEvent.eventType
          ? normalizedEvent.eventType
          : "";
      var runStatus =
        normalizedEvent && normalizedEvent.runStatus
          ? this.normalizeAgentRunStatus(normalizedEvent.runStatus)
          : null;
      if (
        eventType === "run_failed" ||
        runStatus === "failed" ||
        (!eventType && stepEvent.status === "failed")
      ) {
        this.activeAgentRun.status = "failed";
        this.guidanceMessage =
          stepEvent.errorMessage ||
          stepEvent.message ||
          "任务执行失败，请调整后重试。";
        this.isSending = false;
        this.isStreaming = false;
        this.isThinking = false;
      } else if (
        eventType === "final_answer" ||
        eventType === "run_finished" ||
        runStatus === "success" ||
        isTerminalAgentEvent(normalizedEvent)
      ) {
        this.activeAgentRun.status = "success";
        this.guidanceMessage = "本轮任务已完成，可继续发起新任务。";
        this.isSending = false;
        this.isStreaming = false;
        this.isThinking = false;
        this.botMsgId = null;
      } else if (!this.isTerminalAgentRunStatus(this.activeAgentRun.status)) {
        this.activeAgentRun.status = "running";
      }
      this.snapshotActiveAgentRun();
    },
    applyFinishPayload(chatResponse) {
      var payload =
        chatResponse && typeof chatResponse === "object"
          ? chatResponse
          : { message: chatResponse == null ? "" : String(chatResponse) };
      var isInterrupted = payload && payload.status === "interrupted";

      // 清理停止超时（stopGeneration 设置的 5 秒兜底）
      if (this._stopTimeout) {
        clearTimeout(this._stopTimeout);
        this._stopTimeout = null;
      }
      var message = payload.message == null ? "" : String(payload.message);
      var botMsgId =
        payload.botMsgId ||
        (this.activeAgentRun && this.activeAgentRun.botMsgId) ||
        this.botMsgId;
      var normalizedSources = this.extractSourcesFromResponse(payload);

      // 解析 token 用量快照（Agent FINISH 载荷携带）
      if (payload.usage && typeof payload.usage === "object") {
        this.tokenUsage = payload.usage;
      }

      var matched = false;
      this.applyServerSessionMeta(
        payload,
        this.currentSessionSceneType || this.resolveExpectedSessionSceneType()
      );

      for (var i = 0; i < this.chatList.length; i++) {
        var chatItem = this.chatList[i];
        if (chatItem.botMsgId == botMsgId && chatItem.chatType !== "user") {
          chatItem.content = marked.parse(message || "");
          chatItem.interrupted = isInterrupted;
          chatItem.sources = normalizedSources;
          chatItem.sourceType =
            payload.sourceType || chatItem.sourceType || null;
          chatItem.sessionCode =
            payload.sessionCode || chatItem.sessionCode || null;
          chatItem.sceneType = payload.sceneType || chatItem.sceneType || null;
          matched = true;
        }
      }

      if (!matched && botMsgId) {
        this.chatList.push({
          id: "temp-" + this.generateRandomId(8),
          content: marked.parse(message || ""),
          userName: "bot",
          chatType: "bot",
          botMsgId: botMsgId,
          createdAt: new Date().toISOString(),
          sources: normalizedSources,
          sourceType: payload.sourceType || null,
          sessionCode: payload.sessionCode || this.currentSessionCode || null,
          sceneType: payload.sceneType || this.currentSessionSceneType || null,
        });
      }

      this.knowledgeSources =
        normalizedSources && normalizedSources.length > 0
          ? normalizedSources
          : [];

      if (this.activeAgentRun) {
        if (payload.chatSessionId != null) {
          this.activeAgentRun.chatSessionId = payload.chatSessionId;
        }
        if (payload.sessionCode) {
          this.activeAgentRun.sessionCode = String(payload.sessionCode);
        }
        if (botMsgId) {
          this.activeAgentRun.botMsgId = String(botMsgId);
        }
        this.activeAgentRun.finishReceived = true;
        if (this.normalizeAgentRunStatus(payload.status) === "failed") {
          this.activeAgentRun.status = "failed";
          this.guidanceMessage = "任务执行失败，请稍后重试。";
        } else {
          this.activeAgentRun.status = "success";
          this.guidanceMessage = "本轮任务已完成，可继续发起新任务。";
        }
        this.snapshotActiveAgentRun();
        this.silentFetchAgentRunDetail(
          payload.runId != null ? payload.runId : this.activeAgentRun.runId
        );
      } else {
        this.guidanceMessage = "本轮任务已完成，可继续发起新任务。";
      }

      this.refreshChatRecordsIfNeeded();
      for (var fi = 0; fi < this.chatList.length; fi++) {
        if (this.chatList[fi].botMsgId == botMsgId) {
          this.chatList[fi].isThinking = false;
          break;
        }
      }
      this.botMsgId = null;
      this.isSending = false;
      this.isStreaming = false;
      this.isThinking = false;
      this.scrollToBottom();
    },
    handleLogout() {
      if (this.isLoggingOut) {
        return;
      }

      this.isLoggingOut = true;
      doctorApi
        .userLogout()
        .then(
          function (res) {
            this.unwrapApiData(res, "退出登录失败");
            this.showUiMessage("success", "已退出登录");
          }.bind(this)
        )
        .catch(
          function (error) {
            this.showUiMessage(
              "error",
              error && error.message
                ? error.message
                : "退出登录失败，已清理本地登录态"
            );
          }.bind(this)
        )
        .finally(
          function () {
            this.teardownSSE({ clearPending: true });
            this.clearActiveAgentRun();
            clearUserSession();
            this.currentUserInfo = null;
            this.currentUserName = null;
            this.isLoggingOut = false;
            this.$emit("logout-success");
          }.bind(this)
        );
    },
    teardownSSE(options) {
      closeSse(this._sseConnection || this._sseSource);
      this._sseConnection = null;
      this._sseSource = null;
      if (options && options.clearPending === true) {
        this._sseConnectingPromise = null;
      }
    },
    async ensureSseConnection() {
      if (
        this._sseConnection &&
        (this._sseSource || this._sseConnection.isSupported === false)
      ) {
        return this._sseConnection;
      }

      var stableUserKey = this.resolveStableUserKey();
      if (!stableUserKey) {
        return null;
      }

      if (this._sseConnectingPromise) {
        return this._sseConnectingPromise;
      }

      this._sseConnectingPromise = this.initSSE();

      try {
        return await this._sseConnectingPromise;
      } finally {
        this._sseConnectingPromise = null;
      }
    },
    async initSSE() {
      var me = this;
      var resolvedUserKey = this.resolveStableUserKey();
      if (!resolvedUserKey) {
        this.sseState = "idle";
        this.guidanceMessage = "请先完成手机号登录，再建立实时会话通道。";
        return null;
      }
      var handleCustomEvent = function (event, context) {
        var eventName =
          context && context.eventName ? context.eventName : "customEvent";
        console.log(eventName + "事件...");
        console.log(event && event.lastEventId);
        console.log(event && event.data);
        me.handleAgentCustomEvent(event && event.data);
      };

      this.currentUserName = resolvedUserKey;
      this.teardownSSE();
      this.sseState = "connecting";
      this.guidanceMessage = "正在连接 SSE 实时通道，请稍候。";
      console.log("连接用户=" + resolvedUserKey);

      try {
        var ticketResponse = await doctorApi.createSseTicket();
        var ticketData = this.unwrapApiData(
          ticketResponse,
          "获取 SSE 连接票据失败"
        );
        var ticket =
          ticketData && ticketData.ticket != null
            ? String(ticketData.ticket)
            : "";

        if (!ticket) {
          throw new Error("SSE 连接票据为空");
        }

        return await new Promise(function (resolve) {
          var settled = false;
          var connection = null;
          var connectTimeout = null;
          var settle = function (value) {
            if (settled) {
              return;
            }
            settled = true;
            if (connectTimeout) {
              clearTimeout(connectTimeout);
            }
            resolve(value);
          };

          connection = connectSse({
            ticket: ticket,
            onOpen: function () {
              console.log("建立连接。。。");
              me.sseState = "connected";
              me.guidanceMessage = "SSE 已连接，可开始执行任务。";
              settle(connection);
            },
            onThinking: function (event) {
              console.log("thinking事件...");
              console.log(event && event.data);
              me.handleThinkingEvent(event && event.data);
            },
            onAdd: function (event) {
              var payload = me.normalizeAddPayload(
                event && event.data != null ? event.data : ""
              );
              if (
                payload &&
                payload.chunkType &&
                payload.chunkType !== "content"
              ) {
                return;
              }
              me.upsertStreamingBotMessage(payload);
            },
            onFinish: function (event) {
              console.log("finish事件...");
              console.log(event && event.data);

              if (me.taskStartTime) {
                var elapsed = Date.now() - me.taskStartTime;
                if (elapsed < 1000) {
                  me.lastExecTime = elapsed + "ms";
                } else {
                  me.lastExecTime = (elapsed / 1000).toFixed(1) + "s";
                }
                me.taskStartTime = null;
              }

              try {
                var payload =
                  me.safeParseJson((event && event.data) || "") ||
                  (event && event.data) ||
                  "";
                me.applyFinishPayload(payload);
              } catch (error) {
                console.error("解析finish事件失败:", error);
                me.guidanceMessage = "任务已结束，但结果解析失败，请稍后重试。";
                me.botMsgId = null;
                me.isSending = false;
                me.isStreaming = false;
              }

              me.isThinking = false;
              me.scrollToBottom();
            },
            onError: function (event, context) {
              var source =
                context && context.source ? context.source : me._sseSource;
              var readyState =
                source && typeof source.readyState !== "undefined"
                  ? source.readyState
                  : "unknown";

              console.log("error事件...");
              console.log("e.readyState: " + readyState);

              if (
                typeof EventSource !== "undefined" &&
                source &&
                source.readyState === EventSource.CLOSED
              ) {
                console.log("connection is closed");
              } else {
                console.log("Error occurred", event);
              }

              if (me.isSending || me.isStreaming) {
                me.showUiMessage("error", "响应中断，请稍后重试！");
              }

              if (
                me.activeAgentRun &&
                !me.isTerminalAgentRunStatus(me.activeAgentRun.status)
              ) {
                me.activeAgentRun.status = "failed";
                me.snapshotActiveAgentRun();
              }

              me.clearThinkingState();
              var errBotMsgId =
                (me.activeAgentRun && me.activeAgentRun.botMsgId) ||
                me.botMsgId;
              if (errBotMsgId) {
                for (var ei = 0; ei < me.chatList.length; ei++) {
                  if (me.chatList[ei].botMsgId == errBotMsgId) {
                    me.chatList[ei].isThinking = false;
                    break;
                  }
                }
              }
              me.botMsgId = null;
              me.isSending = false;
              me.isStreaming = false;
              me.sseState = "disconnected";
              me.guidanceMessage = "SSE 通道已断开，发送新任务时会自动重连。";
              me.teardownSSE();
              settle(null);
            },
            onCustomEvent: handleCustomEvent,
            onCustomEventSnake: handleCustomEvent,
            onAgentEvent: handleCustomEvent,
            onTraceEvent: handleCustomEvent,
          });

          me._sseConnection = connection;
          me._sseSource =
            connection && connection.source ? connection.source : null;

          if (!connection || connection.isSupported === false) {
            console.log("浏览器不支持SSE");
            me.sseState = "unsupported";
            me.guidanceMessage =
              "当前环境暂不支持 SSE 实时通道，回复可能无法实时展示。";
            settle(connection);
            return;
          }

          connectTimeout = setTimeout(function () {
            settle(connection);
          }, 3000);
        });
      } catch (error) {
        console.error("建立SSE连接失败:", error);
        this.sseState = "disconnected";
        this.guidanceMessage =
          "SSE 通道初始化失败，发送新任务时会自动重试连接。";
        this._sseConnection = null;
        this._sseSource = null;
        return null;
      }
    },
    uploadDoc(params) {
      var file = params && params.file ? params.file : null;
      if (!file) {
        return;
      }

      this.selectedUploadName = file.name || "";
      this.guidanceMessage = "正在上传知识库文档，请稍候。";

      var formData = new FormData();
      formData.append("file", file);

      return doctorApi
        .uploadRagDoc(formData)
        .then(
          function (response) {
            console.log(response);
            if (response.status == 200) {
              this.guidanceMessage =
                "知识库文档上传成功，可切换到知识库增强模式继续提问。";
              this.showUiMessage("success", "上传知识库文档成功！");
              this.uploadSynced = false;
              this.fetchUploadedDocs();
              if (params && typeof params.onSuccess === "function") {
                params.onSuccess(response, file);
              }
            } else {
              var uploadError = new Error("上传知识库文档失败！");
              this.guidanceMessage =
                "文档已提交，但服务器返回异常，请稍后重试。";
              this.showUiMessage("error", "上传知识库文档失败！");
              if (params && typeof params.onError === "function") {
                params.onError(uploadError);
              }
            }
          }.bind(this)
        )
        .catch(
          function (error) {
            console.error("上传知识库文档请求失败:", error);
            this.guidanceMessage = "知识库文档上传失败，请稍后重试。";
            this.showUiMessage("error", "上传知识库文档失败，请稍后重试！");
            if (params && typeof params.onError === "function") {
              params.onError(error);
            }
          }.bind(this)
        );
    },
    extractSourcesFromResponse(chatResponse) {
      return extractSourcesFromResponseUtil(chatResponse);
    },
    parseRecordSources(sourcesJson) {
      if (!sourcesJson) {
        return [];
      }

      var parsedSources = sourcesJson;
      if (typeof sourcesJson === "string") {
        try {
          parsedSources = JSON.parse(sourcesJson);
        } catch (error) {
          return [];
        }
      }

      if (Array.isArray(parsedSources)) {
        return extractSourcesFromResponseUtil({ sources: parsedSources });
      }

      if (parsedSources && typeof parsedSources === "object") {
        var normalizedCollection =
          parsedSources.sources ||
          parsedSources.items ||
          parsedSources.list ||
          parsedSources.references ||
          parsedSources.citations ||
          parsedSources.docs ||
          parsedSources.sourceList ||
          parsedSources.sourceDocs;

        if (Array.isArray(normalizedCollection)) {
          return extractSourcesFromResponseUtil({
            sources: normalizedCollection,
          });
        }

        return extractSourcesFromResponseUtil({ sources: [parsedSources] });
      }

      if (typeof parsedSources === "string") {
        return extractSourcesFromResponseUtil({ sources: [parsedSources] });
      }

      return [];
    },
    mapSessionMessagesToChatList(messages) {
      if (!Array.isArray(messages)) {
        return [];
      }

      var me = this;
      return messages
        .slice()
        .sort(function (a, b) {
          var aSeq = a && a.seqNo != null ? Number(a.seqNo) : NaN;
          var bSeq = b && b.seqNo != null ? Number(b.seqNo) : NaN;
          if (!isNaN(aSeq) && !isNaN(bSeq) && aSeq !== bSeq) {
            return aSeq - bSeq;
          }

          var aDate = me.resolveChatSessionDate(a && a.createdAt);
          var bDate = me.resolveChatSessionDate(b && b.createdAt);
          var aTime = aDate ? aDate.getTime() : 0;
          var bTime = bDate ? bDate.getTime() : 0;
          return aTime - bTime;
        })
        .map(function (message, index) {
          return me.mapRecordToChatItem(message, index);
        })
        .filter(function (item) {
          return !!item;
        });
    },
    mapRecordToChatItem(message, index) {
      if (!message) {
        return null;
      }

      // 识别上下文压缩摘要记录（role=system, messageType=summary）
      if (message.role === "system" && message.messageType === "summary") {
        var meta = {};
        try {
          meta = message.sourcesJson ? JSON.parse(message.sourcesJson) : {};
        } catch (e) {
          meta = {};
        }
        return {
          id: "summary-" + (message.seqNo != null ? message.seqNo : index),
          content: "",
          summaryContent: message.content || "",
          userName: "system",
          chatType: "system",
          compressCount: meta.compressedCount || 0,
          createdAt: message.createdAt || new Date().toISOString(),
          sessionCode: message.sessionCode || null,
          sceneType: message.sceneType || null,
          sources: [],
        };
      }

      var role = message.role === "assistant" ? "assistant" : "user";
      var rawContent = message.content == null ? "" : String(message.content);
      return {
        id:
          message.messageId != null
            ? String(message.messageId)
            : "record-" + index + "-" + this.generateRandomId(6),
        content: role === "assistant" ? marked.parse(rawContent) : rawContent,
        userName: role === "assistant" ? "bot" : this.currentUserName || "用户",
        chatType: role === "assistant" ? "bot" : "user",
        botMsgId: message.botMsgId || null,
        createdAt: message.createdAt || new Date().toISOString(),
        sessionCode: message.sessionCode || null,
        sceneType: message.sceneType || null,
        sourceType: message.sourceType || null,
        sources:
          role === "assistant"
            ? this.parseRecordSources(message.sourcesJson)
            : [],
        interrupted: role === "assistant" && rawContent.indexOf("已中断") >= 0,
      };
    },
    resolveChatHistorySources(chatItems) {
      if (!Array.isArray(chatItems)) {
        return [];
      }

      for (var i = chatItems.length - 1; i >= 0; i--) {
        var item = chatItems[i];
        if (item && Array.isArray(item.sources) && item.sources.length > 0) {
          return item.sources;
        }
      }

      return [];
    },
    resolveLastUsageFromMessages(messages) {
      if (!Array.isArray(messages)) {
        return null;
      }
      for (var i = messages.length - 1; i >= 0; i--) {
        var msg = messages[i];
        if (msg && msg.role === "assistant" && msg.usageJson) {
          try {
            return JSON.parse(msg.usageJson);
          } catch (e) {
            return null;
          }
        }
      }
      return null;
    },
    handleChatScroll() {
      var chatMessages = document.getElementById("chat-messages");
      if (!chatMessages) {
        return;
      }

      var distanceFromBottom =
        chatMessages.scrollHeight -
        (chatMessages.scrollTop + chatMessages.clientHeight);
      this.showBackToBottom = distanceFromBottom > 120;
    },
    extractMessageText(content) {
      if (content == null) {
        return "";
      }

      var contentString = String(content);
      if (contentString.indexOf("<") === -1) {
        return contentString;
      }

      var tempDiv = document.createElement("div");
      tempDiv.innerHTML = contentString;
      return (tempDiv.textContent || tempDiv.innerText || "").trim();
    },
    copyTextWithFallback(text) {
      var textarea = document.createElement("textarea");
      textarea.value = text;
      textarea.setAttribute("readonly", "readonly");
      textarea.style.position = "fixed";
      textarea.style.top = "-9999px";
      textarea.style.left = "-9999px";

      document.body.appendChild(textarea);
      textarea.focus();
      textarea.select();

      var copied = false;
      try {
        copied = document.execCommand("copy");
      } catch (error) {
        copied = false;
      }

      document.body.removeChild(textarea);
      return copied;
    },
    copyMessageContent(item) {
      var text = this.extractMessageText(item && item.content).trim();
      if (!text) {
        this.showUiMessage("error", "暂无可复制内容");
        return;
      }

      if (
        typeof navigator !== "undefined" &&
        navigator.clipboard &&
        typeof window !== "undefined" &&
        window.isSecureContext
      ) {
        navigator.clipboard
          .writeText(text)
          .then(
            function () {
              this.showUiMessage("success", "复制成功");
            }.bind(this)
          )
          .catch(
            function () {
              var copied = this.copyTextWithFallback(text);
              if (copied) {
                this.showUiMessage("success", "复制成功");
              } else {
                this.showUiMessage("error", "复制失败，请手动复制");
              }
            }.bind(this)
          );
        return;
      }

      var copied = this.copyTextWithFallback(text);
      if (copied) {
        this.showUiMessage("success", "复制成功");
      } else {
        this.showUiMessage("error", "复制失败，请手动复制");
      }
    },
    focusInputPanel(moveCursorToEnd) {
      var panel = this.$refs.chatInputPanel;
      if (!panel) {
        return;
      }

      if (moveCursorToEnd && typeof panel.setCursorToEnd === "function") {
        panel.setCursorToEnd();
        return;
      }

      if (typeof panel.focusInput === "function") {
        panel.focusInput();
      }
    },
    quoteMessageToInput(item) {
      var text = this.extractMessageText(item && item.content).trim();
      if (!text) {
        this.showUiMessage("error", "暂无可引用内容");
        return;
      }

      var quotedText = "引用：\n" + text;
      var currentValue = this.draftMessage || "";
      if (currentValue) {
        currentValue = currentValue.replace(/\s+$/, "");
        this.draftMessage = currentValue + "\n\n" + quotedText;
      } else {
        this.draftMessage = quotedText;
      }

      this.guidanceMessage = "已将所选消息引用到输入框，可继续编辑后发送。";
      this.focusInputPanel(true);
    },
    async stopGeneration(skipConfirm) {
      // 二次确认（retryUserMessage 内部调用时已确认过，跳过）
      if (!skipConfirm) {
        var stopConfirmed = await this.showConfirmBar("确定要停止生成吗？", {
          confirmText: "停止",
          cancelText: "取消",
        });
        if (!stopConfirmed) {
          return;
        }
      }
      // 获取当前 runId
      var runId = (this.activeAgentRun && this.activeAgentRun.runId) || null;
      if (!runId) {
        // 没有 runId 时仅做前端状态重置
        this.botMsgId = null;
        this.isSending = false;
        this.isStreaming = false;
        this.isThinking = false;
        this.guidanceMessage = "已停止生成。";
        return;
      }

      this.guidanceMessage = "正在停止生成…";
      try {
        await doctorApi.cancelAgent(runId);
      } catch (e) {
        console.error("取消Agent请求失败:", e);
      }

      // 前端状态立即重置（后端会推送 interrupted FINISH 事件做最终收尾）
      // 但如果 SSE 通道已断开，FINISH 可能收不到，所以这里也做兜底重置
      // 注意：不完全重置 isStreaming，等 FINISH 事件或超时后再重置
      // 设置一个超时兜底：5秒后如果没收到 FINISH，强制重置
      var me = this;
      if (this._stopTimeout) {
        clearTimeout(this._stopTimeout);
      }
      this._stopTimeout = setTimeout(function () {
        if (me.isStreaming || me.isSending) {
          console.warn("停止超时，强制重置状态");
          me.botMsgId = null;
          me.isSending = false;
          me.isStreaming = false;
          me.isThinking = false;
          me.guidanceMessage = "已停止生成。";
        }
      }, 5000);
    },
    async retryUserMessage(item) {
      var text = this.extractMessageText(item && item.content).trim();
      if (!text) {
        this.showUiMessage("error", "暂无可重试内容");
        return;
      }

      // 二次确认（回滚会删除该消息及之后的历史，不可恢复）
      var rollbackConfirmed = await this.showConfirmBar(
        "确定要回滚到该消息吗？该消息及其后的所有回复将被删除。",
        { confirmText: "回滚", cancelText: "取消" }
      );
      if (!rollbackConfirmed) {
        return;
      }

      // 如果正在输出，先打断（串行：等打断完成再回滚）
      if (this.isSending || this.isStreaming) {
        this.guidanceMessage = "正在停止当前生成，请稍候…";
        await this.stopGeneration(true);

        // 等待 interrupted FINISH 事件到达（stopGeneration 已设置 5 秒超时兜底）
        // 这里用轮询等待 isStreaming 变为 false
        var waitCount = 0;
        while ((this.isStreaming || this.isSending) && waitCount < 60) {
          await new Promise(function (resolve) {
            setTimeout(resolve, 100);
          });
          waitCount++;
        }
      }

      // 在 chatList 中定位被点击的 user 消息 index
      var userIndex = -1;
      for (var i = 0; i < this.chatList.length; i++) {
        if (this.chatList[i] === item) {
          userIndex = i;
          break;
        }
      }
      // 兜底：按内容匹配（item 可能是历史加载的副本，引用不同）
      if (userIndex < 0) {
        for (var k = 0; k < this.chatList.length; k++) {
          if (
            this.chatList[k].chatType === "user" &&
            this.extractMessageText(this.chatList[k].content).trim() === text
          ) {
            userIndex = k;
            break;
          }
        }
      }

      // 找该 user 消息后面紧跟的 assistant 消息的 botMsgId（后端按 botMsgId 定位删除起点）
      var assistantBotMsgId = null;
      if (userIndex >= 0) {
        for (var j = userIndex + 1; j < this.chatList.length; j++) {
          if (
            this.chatList[j].chatType === "bot" ||
            this.chatList[j].chatType === "assistant"
          ) {
            assistantBotMsgId = this.chatList[j].botMsgId;
            break;
          }
        }
      }

      // 调后端回滚接口删除（user 消息 + 之后所有消息）
      var sessionId = this.activeChatSessionId;
      if (assistantBotMsgId && sessionId) {
        try {
          await doctorApi.rollbackMessage(sessionId, assistantBotMsgId);
        } catch (e) {
          console.error("回滚消息失败:", e);
          this.showUiMessage("error", "回滚失败，请稍后重试");
          return;
        }
      }

      // 从前端 chatList 中移除该 user 消息及之后的所有消息
      if (userIndex >= 0) {
        this.chatList.splice(userIndex);
      }

      // 回填输入框
      this.draftMessage = text;
      this.guidanceMessage = "已将历史任务填回输入框，可直接调整后再次发送。";
      this.focusInputPanel(true);
    },
    applyQuickPrompt(promptText) {
      this.draftMessage = promptText || "";
      this.guidanceMessage = "已写入快捷指令，可直接调整内容后发送。";
      this.focusInputPanel(true);
    },
    // Agent steps management
    buildAgentSteps(message) {
      return [];
    },
    updateAgentStepsOnStream() {
      return;
    },
    completeAllAgentSteps() {
      return;
    },
    failCurrentAgentStep() {
      return;
    },
    async triggerManualCompress() {
      if (this.isCompressing || this.isSending || this.isStreaming) {
        return;
      }
      var sessionId = this.activeChatSessionId;
      if (sessionId == null) {
        this.showUiMessage("error", "当前没有活动会话，无法压缩。");
        return;
      }
      this.isCompressing = true;
      try {
        await doctorApi.compressContext(sessionId);
        // 结果由 SSE handler 统一处理（context_compressing / context_compressed / context_compress_failed）
      } catch (e) {
        this.isCompressing = false;
        this.showUiMessage(
          "error",
          "触发压缩失败：" + (e && e.message ? e.message : "未知错误")
        );
      }
    },
    async doChat() {
      var currentUserName = this.currentUserName;

      if (this.isSending || this.isStreaming) {
        this.showUiMessage("error", "正在执行任务，请稍后再发送。");
        return;
      }

      var pendingMsg = (this.draftMessage || "").trim();
      if (pendingMsg === "") {
        return;
      }
      this.clearThinkingState();

      this.isSending = true;
      this.isStreaming = false;
      this.guidanceMessage = "正在建立 SSE 实时通道，请稍候。";

      var sseConnection = null;
      try {
        sseConnection = await this.ensureSseConnection();
      } catch (error) {
        console.error("准备 SSE 连接失败:", error);
      }
      currentUserName = this.currentUserName;

      if (!currentUserName) {
        this.showUiMessage(
          "error",
          "请先完成手机号登录，再开始你的专属健身会话。"
        );
        this.isSending = false;
        this.guidanceMessage = "请先完成手机号登录，再开始你的专属健身会话。";
        return;
      }

      if (!sseConnection) {
        this.showUiMessage("error", "SSE 通道初始化失败，请稍后重试。");
        this.isSending = false;
        this.guidanceMessage = "SSE 通道初始化失败，请稍后重试。";
        return;
      }

      var botMsgId = this.generateRandomId(12);
      this.botMsgId = botMsgId;
      this.taskStartTime = Date.now();
      this.lastTtft = null;

      this.agentSteps = [];

      var internetSearchSelected = this.internetSearchSelected;
      var knowledgeBaseSelected = this.knowledgeBaseSelected;
      var ragSelected = this.ragSelected;
      var currentSourceType = ragSelected
        ? "rag"
        : knowledgeBaseSelected
        ? "wiki"
        : internetSearchSelected
        ? "internet"
        : "chat";
      var expectedSceneType = this.resolveExpectedSessionSceneType();
      var singleChat = {
        currentUserName: currentUserName,
        message: pendingMsg,
        botMsgId: botMsgId,
        sessionCode: this.currentSessionCode || null,
        knowledgeBaseEnabled: knowledgeBaseSelected,
        ragEnabled: ragSelected,
        internetEnabled: internetSearchSelected,
      };

      var requestPromise = null;
      var me = this;

      console.log("agentExecute");
      requestPromise = doctorApi.agentExecute(singleChat);

      if (requestPromise && typeof requestPromise.then === "function") {
        requestPromise = requestPromise.then(function (res) {
          var data = me.unwrapApiData(res, "任务请求失败，请稍后重试！");
          me.applyServerSessionMeta(data, expectedSceneType);
          var ackApplied = me.applyAgentExecuteAck(
            data,
            singleChat,
            expectedSceneType,
            currentSourceType
          );
          if (!ackApplied) {
            me.clearActiveAgentRun();
          }
          return res;
        });
      }

      if (requestPromise && typeof requestPromise.catch === "function") {
        requestPromise.catch(
          function (error) {
            console.error("任务请求失败:", error);
            this.showUiMessage("error", "请求失败，请稍后重试！");
            this.botMsgId = null;
            this.isSending = false;
            this.isStreaming = false;
            this.clearThinkingState();
            this.guidanceMessage = "任务发送失败，请稍后重试。";
            this.failCurrentAgentStep();
          }.bind(this)
        );
      }

      this.chatList.push({
        id: "user-" + this.generateRandomId(8),
        content: pendingMsg,
        userName: currentUserName || "用户",
        chatType: "user",
        createdAt: new Date().toISOString(),
        sessionCode: this.currentSessionCode || null,
        sceneType: expectedSceneType,
        sourceType: currentSourceType,
        botMsgId: botMsgId,
      });

      this.draftMessage = "";
      this.guidanceMessage = "任务已提交，正在执行中。";
      this.scrollToBottom();
    },
    generateRandomId(length) {
      var characters =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
      var result = "";
      var charactersLength = characters.length;
      for (var i = 0; i < length; i++) {
        result += characters.charAt(
          Math.floor(Math.random() * charactersLength)
        );
      }
      return result;
    },
    scrollToBottom(force) {
      var me = this;
      this.$nextTick(function () {
        var chatMessages = document.getElementById("chat-messages");
        if (!chatMessages) {
          return;
        }

        var distanceFromBottom =
          chatMessages.scrollHeight -
          (chatMessages.scrollTop + chatMessages.clientHeight);
        var nearBottom = distanceFromBottom <= 120;

        if (force === true || nearBottom || !me.showBackToBottom) {
          chatMessages.scrollTop = chatMessages.scrollHeight;
        }

        me.handleChatScroll();
      });
    },
    doInternetSearch(internetSearchSelected) {
      this.internetSearchSelected = !internetSearchSelected;

      if (this.internetSearchSelected) {
        this.knowledgeBaseSelected = false;
        this.ragSelected = false;
        this.imageReadSelected = false;
      }

      this.guidanceMessage =
        "\u5DF2\u5207\u6362\u4E3A\u300C" +
        this.activeModeLabel +
        "\u300D\u6A21\u5F0F\u3002";
    },
    doKnowledgeBase(knowledgeBaseSelected) {
      this.knowledgeBaseSelected = !knowledgeBaseSelected;

      if (!this.knowledgeBaseSelected) {
        this.ragSelected = false;
      } else {
        this.internetSearchSelected = false;
        this.imageReadSelected = false;
      }

      this.guidanceMessage =
        "\u5DF2\u5207\u6362\u4E3A\u300C" +
        this.activeModeLabel +
        "\u300D\u6A21\u5F0F\u3002";
    },
    doRag(ragSelected) {
      var nextValue = !ragSelected;
      if (nextValue && !this.knowledgeBaseSelected) {
        return;
      }
      this.ragSelected = nextValue;

      if (this.ragSelected) {
        this.internetSearchSelected = false;
        this.imageReadSelected = false;
      }

      this.guidanceMessage =
        "\u5DF2\u5207\u6362\u4E3A\u300C" +
        this.activeModeLabel +
        "\u300D\u6A21\u5F0F\u3002";
    },
    async onModelSelect(modelId) {
      if (!modelId || modelId === this.currentModel) {
        return;
      }
      try {
        await llmConfig.save({ model: modelId });
        this.currentModel = llmConfig.getConfig().model;
      } catch (e) {
        this.showUiMessage("error", "切换模型失败");
      }
    },
    async onToggleThinking() {
      var next = !this.thinkingEnabled;
      try {
        await llmConfig.save({ thinkingEnabled: next });
        this.thinkingEnabled = llmConfig.getConfig().thinkingEnabled;
      } catch (e) {
        this.showUiMessage("error", "切换思考模式失败");
      }
    },
    async onSelectReasoningEffort(effort) {
      if (!effort || effort === this.reasoningEffort) {
        return;
      }
      try {
        await llmConfig.save({ reasoningEffort: effort });
        this.reasoningEffort = llmConfig.getConfig().reasoningEffort;
      } catch (e) {
        this.showUiMessage("error", "切换推理强度失败");
      }
    },
  },
};
</script>
