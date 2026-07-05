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
  extractThinkingFromStepOutput,
  isLlmAnchorStep,
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
      currentUserName: null,
      currentUserInfo: null,
      isLoggingOut: false,
      activeView: "chat",
      chatExpanded: false,
      chatSessionList: [],
      activeChatSessionId: null,
      chatRecordsLoading: false,
      chatRecordsLoaded: false,
      chatList: [],
      draftMessage: "",

      knowledgeBaseSelected: true,
      ragSelected: false,
      internetSearchSelected: true,
      imageReadSelected: false,
      isSending: false,
      isStreaming: false,
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
      // 用户主动向上滚动标志：true 时流式输出不自动跟随，点击"回到底部"重置
      isUserScrolledUp: false,
      // 程序滚动标志：避免程序触发的 scroll 事件被误判为用户主动滚动
      isProgrammaticScroll: false,
      selectedUploadName: "",
      sseState: "idle",
      guidanceMessage: "选择任务模式后，输入指令开始执行。",
      // 多 run 追踪表：按 runId 索引，每个 entry 自带完整 per-run 状态
      activeAgentRuns: {} as Record<string, any>,
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
    // 当前会话对应的 run；切换会话时自动重指向
    currentAgentRun(): any | null {
      if (this.activeChatSessionId == null) return null;
      const runs = this.activeAgentRuns || {};
      return Object.values(runs).find(
        (r: any) => r && r.chatSessionId === this.activeChatSessionId
      ) || null;
    },
    // 当前会话是否有运行中 run
    hasPendingRunInCurrentSession(): boolean {
      const run = this.currentAgentRun;
      return run != null && !this.isTerminalAgentRunStatus(run.status);
    },
    // 任意会话是否有运行中 run（用于全局提示，不再阻止操作）
    hasAnyPendingRun(): boolean {
      const runs = this.activeAgentRuns || {};
      return Object.values(runs).some(
        (r: any) => r && !this.isTerminalAgentRunStatus(r.status)
      );
    },
    // 子组件 prop 视图：聚合当前 run + UI 状态
    currentRunView(): any | null {
      const run = this.currentAgentRun;
      if (!run) return null;
      return {
        ...run,
        isSending: this.isSending,
        isStreaming: this.isStreaming,
        isThinking: this.isThinking,
        thinkingExpanded: this.thinkingExpanded,
      };
    },
    // 派生字段（替代删除的全局字段）——读取 currentAgentRun
    agentSteps(): any[] {
      const run = this.currentAgentRun;
      return run ? run.steps : [];
    },
    thinkingSegments(): any[] {
      const run = this.currentAgentRun;
      return run ? run.thinkingSegments : [];
    },
    thinkingContent(): string {
      const run = this.currentAgentRun;
      return run ? run.thinkingContent : "";
    },
    botMsgId(): any {
      const run = this.currentAgentRun;
      return run ? run.botMsgId : null;
    },
    tokenUsage(): any {
      const run = this.currentAgentRun;
      return run ? run.tokenUsage : null;
    },
    currentSessionCode(): any {
      const run = this.currentAgentRun;
      return run ? run.sessionCode : null;
    },
    currentSessionSceneType(): any {
      const run = this.currentAgentRun;
      return run ? run.sceneType : null;
    },
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
    recentChatSessions() {
      var list = Array.isArray(this.chatSessionList)
        ? this.chatSessionList
        : [];
      return list.slice(0, 12);
    },
    activeModeLabel() {
      var mainLabel = "Agent";
      var parts = [];
      if (this.knowledgeBaseSelected) {
        parts.push("知识库 Wiki");
      }
      if (this.ragSelected) {
        parts.push("原始文档");
      }
      if (this.internetSearchSelected) {
        parts.push("联网补充");
      }
      if (parts.length === 0) {
        return mainLabel;
      }
      return mainLabel + " + " + parts.join(" + ");
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
    // 恢复会话期间的标志：避免 activeChatSessionId 变化时 watch 反向同步 URL/storage 造成循环
    this._isRestoringSession = false;
    this.loadUserSessionFromCookie();
    this.restoreActiveAgentRuns();
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
    if (this._chatHistoryHoverTimer) {
      clearTimeout(this._chatHistoryHoverTimer);
      this._chatHistoryHoverTimer = null;
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
    // 会话 ID 变化时同步到 URL 路径参数与 sessionStorage，
    // 实现切换其他页面再回来时仍能恢复原会话（类似 DeepSeek /chat/s/{id} 行为）
    activeChatSessionId(newVal, oldVal) {
      if (this._isRestoringSession) {
        return;
      }
      if (newVal === oldVal) {
        return;
      }
      this.syncChatSessionToUrlAndStorage();
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
    async handleChatHistoryHover(enter) {
      if (this._chatHistoryHoverTimer) {
        clearTimeout(this._chatHistoryHoverTimer);
        this._chatHistoryHoverTimer = null;
      }
      if (enter) {
        if (this.chatExpanded) {
          return;
        }
        this.chatExpanded = true;
        if (!this.chatRecordsLoaded && !this.chatRecordsLoading) {
          await this.fetchChatRecords();
        }
      } else {
        var me = this;
        this._chatHistoryHoverTimer = setTimeout(function () {
          me.chatExpanded = false;
          me._chatHistoryHoverTimer = null;
        }, 200);
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
      this.applyChatMode(this.resolvePreferredModeFromSession(targetSession));
      this.chatList = mappedChatList;
      var restoreRun = this.currentAgentRun;
      if (restoreRun) {
        restoreRun.tokenUsage = this.resolveLastUsageFromMessages(
          targetSession.messages
        );
      }
      this.showBackToBottom = false;
      this.knowledgeSources = this.resolveChatHistorySources(mappedChatList);
      this.closeMobileDrawers();
      this.chatExpanded = false;
      this.activeView = "chat";
      this.scrollToBottom(true);
    },
    async handleShowAllChatSessions() {
      this.chatExpanded = false;
      this.activeView = "chat-history";
      if (!this.chatRecordsLoaded && !this.chatRecordsLoading) {
        await this.fetchChatRecords();
      }
    },
    handleBackToChat() {
      this.activeView = "chat";
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
      this.chatList = [];
      this.draftMessage = "";
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
    /**
     * 单个 run 的 sessionStorage key。
     */
    getRunStorageKey(runId: string): string | null {
      const userKey = this.resolveStableUserKey();
      if (!userKey || !runId) return null;
      return "fitmate:active-run:" + String(userKey) + ":" + String(runId);
    },
    /**
     * 当前用户的 run storage key 前缀（用于遍历清理/恢复）。
     */
    getRunStorageKeyPrefix(): string | null {
      const userKey = this.resolveStableUserKey();
      if (!userKey) return null;
      return "fitmate:active-run:" + String(userKey) + ":";
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
      const run = this.currentAgentRun;
      return !!(
        run &&
        run.runId != null &&
        !this.isTerminalAgentRunStatus(run.status)
      );
    },
    normalizeAgentStepItem(step, index) {
      if (!step) {
        return null;
      }
      return normalizeAgentTraceNode(step, index);
    },
    /**
     * 写入单个 run 的快照；终态时自动删除 key。
     */
    snapshotRunState(runId: string) {
      const key = this.getRunStorageKey(runId);
      if (!key || typeof window === "undefined") return;
      const run = (this.activeAgentRuns || {})[runId];
      if (!run || this.isTerminalAgentRunStatus(run.status)) {
        window.sessionStorage.removeItem(key);
        return;
      }
      window.sessionStorage.setItem(key, JSON.stringify({ version: 3, ...run }));
    },
    /**
     * 按 runId 取 run entry；不存在时按需创建。
     * @param payload SSE 事件载荷，至少含 runId
     * @param options.createIfMissing 为 true 时，若无 runId 则降级到 currentAgentRun，若仍无则新建空骨架
     */
    resolveRunForEvent(payload: any, options: { createIfMissing?: boolean } = {}): any | null {
      const runId = payload && payload.runId != null ? String(payload.runId) : null;
      if (!runId) {
        return this.currentAgentRun;
      }
      const existing = (this.activeAgentRuns || {})[runId];
      if (existing) return existing;
      if (options.createIfMissing) {
        return this.createRunEntry({ runId });
      }
      return null;
    },
    /**
     * 创建 run entry 并放入 Map。chatSessionId 创建时固定。
     */
    createRunEntry(init: any): any {
      const runId = String(init.runId);
      const run = {
        runId: runId,
        chatSessionId: init.chatSessionId != null ? init.chatSessionId : this.activeChatSessionId,
        sessionCode: init.sessionCode != null ? String(init.sessionCode) : null,
        botMsgId: init.botMsgId != null ? String(init.botMsgId) : null,
        status: init.status || "pending",
        requestText: init.requestText || "",
        sceneType: init.sceneType || "agent",
        sourceType: init.sourceType || "chat",
        finishReceived: false,
        steps: [],
        thinkingSegments: [],
        thinkingContent: "",
        tokenUsage: null,
      };
      this.activeAgentRuns[runId] = run;
      return run;
    },
    /**
     * 从 Map 移除 run entry。
     */
    removeRunEntry(runId: string) {
      if (!runId) return;
      delete this.activeAgentRuns[runId];
    },
    /**
     * 清空 run 追踪。传 runId 删单个；不传清整个 Map（logout 用）。
     */
    clearActiveAgentRun(runId?: string) {
      if (runId) {
        const key = this.getRunStorageKey(runId);
        if (key && typeof window !== "undefined") {
          window.sessionStorage.removeItem(key);
        }
        delete this.activeAgentRuns[runId];
      } else {
        const prefix = this.getRunStorageKeyPrefix();
        if (prefix && typeof window !== "undefined") {
          for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
            const key = window.sessionStorage.key(i);
            if (key && key.indexOf(prefix) === 0) {
              window.sessionStorage.removeItem(key);
            }
          }
        }
        this.activeAgentRuns = {};
      }
    },
    /**
     * 遍历 sessionStorage 前缀重建整个 activeAgentRuns Map。
     * 不切换 activeChatSessionId；由路由恢复流程决定。
     */
    restoreActiveAgentRuns() {
      const prefix = this.getRunStorageKeyPrefix();
      if (!prefix || typeof window === "undefined") return;
      for (let i = window.sessionStorage.length - 1; i >= 0; i--) {
        const key = window.sessionStorage.key(i);
        if (!key || key.indexOf(prefix) !== 0) continue;
        const raw = window.sessionStorage.getItem(key);
        if (!raw) continue;
        try {
          const snap = JSON.parse(raw);
          if (!snap.runId || this.isTerminalAgentRunStatus(snap.status)) {
            window.sessionStorage.removeItem(key);
            continue;
          }
          // hydrate：补全字段
          const run = {
            runId: String(snap.runId),
            chatSessionId: snap.chatSessionId != null ? snap.chatSessionId : null,
            sessionCode: snap.sessionCode || null,
            botMsgId: snap.botMsgId || null,
            status: snap.status,
            requestText: snap.requestText || "",
            sceneType: snap.sceneType || "agent",
            sourceType: snap.sourceType || "chat",
            finishReceived: !!snap.finishReceived,
            steps: Array.isArray(snap.steps) ? snap.steps : (Array.isArray(snap.traceNodes) ? snap.traceNodes : []),
            thinkingSegments: Array.isArray(snap.thinkingSegments) ? snap.thinkingSegments : [],
            thinkingContent: snap.thinkingContent || "",
            tokenUsage: snap.tokenUsage || null,
          };
          this.activeAgentRuns[run.runId] = run;
          // 后台补拉最新详情（覆盖终态等）
          this.silentFetchAgentRunDetail(run.runId);
        } catch (e) {
          window.sessionStorage.removeItem(key);
        }
      }
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
      // 恢复期间设置标志，避免 activeChatSessionId 变化触发 watch 反向同步 URL/storage
      var previousFlag = this._isRestoringSession;
      this._isRestoringSession = true;
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
          me.chatList = mappedChatList;
          me.knowledgeSources = me.resolveChatHistorySources(mappedChatList);
          var restoreRun = me.currentAgentRun;
          if (restoreRun) {
            restoreRun.tokenUsage = me.resolveLastUsageFromMessages(
              targetSession.messages
            );
          }
          me.scrollToBottom(true);
          return targetSession;
        })
        .catch(function (error) {
          console.warn("静默恢复聊天会话失败:", error);
          return null;
        })
        .finally(function () {
          me._isRestoringSession = previousFlag;
        });
    },
    /**
     * 当前用户在 sessionStorage 中持久化"最近活动会话 ID"用的 key。
     * 用于：用户切换到其他页面（如 dashboard、training）再点回聊天入口时，
     * 即使 URL 没带 sessionId，也能从 sessionStorage 兜底恢复到上次的会话。
     */
    getRecentChatSessionStorageKey() {
      var stableUserKey = this.resolveStableUserKey();
      if (!stableUserKey || typeof window === "undefined") {
        return null;
      }
      return "fitmate:recent-chat-session:" + String(stableUserKey);
    },
    /**
     * 将当前会话 ID 写入/移出 sessionStorage（持久化最近活动会话）。
     * sessionId 为 null 时移除记录，对应"新建会话"场景。
     */
    persistRecentChatSession(sessionId) {
      var key = this.getRecentChatSessionStorageKey();
      if (!key || typeof window === "undefined") {
        return;
      }
      try {
        if (sessionId == null) {
          window.sessionStorage.removeItem(key);
        } else {
          window.sessionStorage.setItem(key, String(sessionId));
        }
      } catch (e) {
        // sessionStorage 不可用时静默忽略
      }
    },
    /**
     * 解析可恢复的会话 ID。优先级：
     *   1) URL 路径参数 sessionId（直接访问 /chat/123 或从其他页面 router-link 回到该 URL）
     *   2) sessionStorage 中持久化的最近会话 ID（兜底，覆盖侧栏点击 /chat 入口的场景）
     *   3) 都没有则返回 null（呈现"新建会话"页）
     */
    resolveRestorableSessionId() {
      var urlSessionId =
        this.$route &&
        this.$route.params &&
        this.$route.params.sessionId;
      if (urlSessionId != null && urlSessionId !== "") {
        return { sessionId: urlSessionId, source: "url" };
      }
      var key = this.getRecentChatSessionStorageKey();
      if (key && typeof window !== "undefined") {
        var storedSessionId = null;
        try {
          storedSessionId = window.sessionStorage.getItem(key);
        } catch (e) {
          storedSessionId = null;
        }
        if (storedSessionId != null && storedSessionId !== "") {
          return { sessionId: storedSessionId, source: "storage" };
        }
      }
      return { sessionId: null, source: "none" };
    },
    /**
     * 在 ChatPage mounted 阶段触发：从 URL/sessionStorage 恢复上次会话。
     * 跳过条件：
     *   - 已有活动 Agent run（restoreActiveAgentRuns 已处理）
     *   - chatList 已非空（已处于会话中，如刚发完消息）
     *   - 已有 activeChatSessionId（doChat 已建立会话）
     */
    restoreChatSessionFromRoute() {
      if (this.hasPendingAgentRun()) {
        return Promise.resolve(null);
      }
      if (Array.isArray(this.chatList) && this.chatList.length > 0) {
        return Promise.resolve(null);
      }
      if (this.activeChatSessionId != null) {
        return Promise.resolve(null);
      }
      var restorable = this.resolveRestorableSessionId();
      if (restorable.sessionId == null) {
        return Promise.resolve(null);
      }
      var me = this;
      // silentRestoreChatSession 内部用 _isRestoringSession 跳过 watch 反向同步，
      // 恢复完成后这里主动同步一次 URL，让 /chat 替换为 /chat/{sessionId}
      return this.silentRestoreChatSession(restorable.sessionId).then(
        function () {
          me.syncChatSessionToUrlAndStorage();
        }
      );
    },
    /**
     * 将当前 activeChatSessionId 同步到 URL 路径参数与 sessionStorage。
     * 仅在当前路由为 /chat 时才更新 URL，避免在 training/dashboard 等页面误改 URL。
     */
    syncChatSessionToUrlAndStorage() {
      var sessionId = this.activeChatSessionId;
      // 同步到 sessionStorage（兜底恢复来源）
      this.persistRecentChatSession(sessionId);
      // 同步到 URL 路径参数
      if (!this.$route || !/^\/chat(\/|$)/.test(this.$route.path || "")) {
        return;
      }
      var currentUrlSessionId =
        this.$route.params && this.$route.params.sessionId;
      var nextUrlSessionId =
        sessionId != null ? String(sessionId) : null;
      if (
        String(currentUrlSessionId || "") ===
        String(nextUrlSessionId || "")
      ) {
        return;
      }
      var nextPath =
        nextUrlSessionId != null
          ? "/chat/" + nextUrlSessionId
          : "/chat";
      var me = this;
      // 用 replace 避免污染浏览历史（每次会话变化都堆历史会让后退按钮体验糟糕）
      this.$router.replace(nextPath).catch(function () {
        // 忽略重复路由/导航取消等错误
      });
    },
    applyAgentRunDetail(detail, run) {
      if (!detail || typeof detail !== "object") {
        return;
      }
      if (!run) {
        run = this.resolveRunForEvent(
          { runId: detail.runId },
          { createIfMissing: true }
        );
      }
      if (!run) return;
      var normalizedStatus = this.normalizeAgentRunStatus(detail.status);
      // 用 detail 补全 run 字段
      if (detail.runId != null) {
        run.runId = String(detail.runId);
      }
      if (run.chatSessionId == null && detail.chatSessionId != null) {
        run.chatSessionId = detail.chatSessionId;
      }
      run.sessionCode = detail.sessionCode
        ? String(detail.sessionCode)
        : run.sessionCode;
      run.botMsgId = detail.botMsgId ? String(detail.botMsgId) : run.botMsgId;
      run.requestText = detail.requestText || run.requestText;
      run.status = normalizedStatus;
      run.sceneType = "agent";
      if (detail.sourceType) {
        run.sourceType = String(detail.sourceType).toLowerCase();
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
        run.steps = steps;
      } else if (hasServerTrace) {
        run.steps = [];
      }

      this.applyServerSessionMeta(detail, "agent");
      this.snapshotRunState(run.runId);
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
      sourceType,
      run
    ) {
      if (!payload || payload.runId == null) {
        return false;
      }
      if (!run) {
        run = this.resolveRunForEvent(payload, { createIfMissing: true });
      }
      if (!run) return false;
      // 用 ack 字段补全 run entry（chatSessionId 若 run 已有则不覆盖）
      if (run.chatSessionId == null && payload.chatSessionId != null) {
        run.chatSessionId = payload.chatSessionId;
        // 若与当前 activeChatSessionId 不一致，以 ack 为准
        if (this.activeChatSessionId !== run.chatSessionId) {
          this.activeChatSessionId = run.chatSessionId;
        }
      }
      run.sessionCode = payload.sessionCode
        ? String(payload.sessionCode)
        : run.sessionCode;
      run.botMsgId = payload.botMsgId
        ? String(payload.botMsgId)
        : requestPayload && requestPayload.botMsgId
        ? String(requestPayload.botMsgId)
        : run.botMsgId;
      run.status = "running";
      run.sceneType = expectedSceneType || "agent";
      run.sourceType = sourceType || "chat";
      run.requestText =
        requestPayload && requestPayload.message
          ? requestPayload.message
          : run.requestText;
      this.applyServerSessionMeta(payload, expectedSceneType || "agent");
      this.snapshotRunState(run.runId);
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
      const run = this.resolveRunForEvent(payload);
      if (!run) return;
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
      run.thinkingContent = (run.thinkingContent || "") + thinkingText;
      // 同步追加到 thinkingSegments：找到当前 active 段追加；若无 active 段则兜底新建
      run.thinkingSegments = this.appendThinkingChunkToSegments(
        (run.thinkingSegments || []).slice(),
        thinkingText
      );
      this.isThinking = true;
      if (payload.botMsgId) {
        run.botMsgId = payload.botMsgId;
      }

      var botMsgId =
        payload.botMsgId || run.botMsgId || this.botMsgId;
      if (botMsgId) {
        var targetMsg = this.findOrCreateBotMessage(botMsgId, payload);
        if (targetMsg) {
          targetMsg.thinkingContent =
            (targetMsg.thinkingContent || "") + thinkingText;
          // 直接同步 run.thinkingSegments 到 targetMsg（深拷贝避免引用共享）
          // 注意：不要给 targetMsg 独立调用 appendThinkingChunkToSegments，
          // 否则 applyAgentStepEvent 处理 step 事件时会覆盖 targetMsg 的 thinking 数据
          targetMsg.thinkingSegments = this.cloneThinkingSegments(
            run.thinkingSegments
          );
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
      this.snapshotRunState(run.runId);
      this.scrollToBottom();
    },
    clearThinkingState() {
      this.isThinking = false;
      this.thinkingExpanded = true;
    },
    // 找到当前正在流式输出（isStreaming=true）的最新 thinking segment
    // 用于把 thinking chunk 追加到正确的轮次桶里
    findActiveThinkingSegment(segments) {
      if (!Array.isArray(segments) || segments.length === 0) {
        return -1;
      }
      for (var i = segments.length - 1; i >= 0; i--) {
        if (segments[i] && segments[i].isStreaming) {
          return i;
        }
      }
      return -1;
    },
    // 把 thinking chunk 追加到 segments 的最新 active 段；
    // 若没有 active 段（异常情况或历史消息恢复后），创建一个 iteration=0 的兜底段
    appendThinkingChunkToSegments(segments, text, iteration) {
      if (!Array.isArray(segments)) {
        return [{ iteration: 0, content: text, isStreaming: true }];
      }
      var activeIdx = this.findActiveThinkingSegment(segments);
      if (activeIdx >= 0) {
        // 不可变更新：创建新 segment 对象，避免与其它引用共享对象时重复追加
        var oldSeg = segments[activeIdx];
        var newSeg = {
          iteration: oldSeg.iteration,
          content: (oldSeg.content || "") + text,
          isStreaming: true,
        };
        var newSegments = segments.slice();
        newSegments[activeIdx] = newSeg;
        return newSegments;
      }
      var newSegments2 = segments.slice();
      newSegments2.push({
        iteration: iteration != null ? iteration : 0,
        content: text,
        isStreaming: true,
      });
      return newSegments2;
    },
    // 深拷贝 thinkingSegments 数组（含 segment 对象），避免引用共享导致重复追加
    cloneThinkingSegments(segments) {
      if (!Array.isArray(segments)) {
        return [];
      }
      return segments.map(function (s) {
        return {
          iteration: s.iteration,
          content: s.content,
          isStreaming: s.isStreaming,
        };
      });
    },
    // 历史消息 thinking 整段切分：按 steps 里 llm_started 的数量均匀切分成多个 segment
    // 这样 mergedTimeline 能按顺序匹配（第 N 个 llm_started → 第 N 个 segment）产生交错效果
    // 若没有 steps 或没有 llm_started，降级为单个 segment
    splitThinkingIntoSegments(thinkingText, steps) {
      var text = thinkingText || "";
      if (!text) {
        return [];
      }
      // 统计 steps 里 LLM 轮次锚点的数量
      // 实时流式：llm_started
      // 历史消息：DB 里 markStepSuccess 用 llm_finished 覆盖了 eventType
      var llmAnchorCount = 0;
      if (Array.isArray(steps)) {
        for (var i = 0; i < steps.length; i++) {
          var step = steps[i];
          if (step && step.eventType) {
            var et = String(step.eventType).toLowerCase();
            if (et === "llm_started" || et === "llm_finished") {
              llmAnchorCount++;
            }
          }
        }
      }
      // 没有 LLM 锚点或只有 1 个，不切分
      if (llmAnchorCount <= 1) {
        return [{ iteration: 1, content: text, isStreaming: false }];
      }
      // 按段数均匀切分（按字符数等分），iteration 从 1 开始（与实时流式一致）
      var segments = [];
      var totalLen = text.length;
      var segLen = Math.floor(totalLen / llmAnchorCount);
      for (var j = 0; j < llmAnchorCount; j++) {
        var start = j * segLen;
        var end = j === llmAnchorCount - 1 ? totalLen : (j + 1) * segLen;
        segments.push({
          iteration: j + 1,
          content: text.substring(start, end),
          isStreaming: false,
        });
      }
      return segments;
    },
    // 历史消息：从 steps 的 LLM 锚点 outputJson 直接提取每轮 thinking，构造 segments
    // 与实时流式保持一致（第 N 个 LLM 锚点 → 第 N 个 segment），不再按字符切分
    // 返回 null 表示无法从 steps 提取（调用方降级到 splitThinkingIntoSegments）
    buildThinkingSegmentsFromSteps(steps) {
      if (!Array.isArray(steps) || steps.length === 0) {
        return null;
      }
      var segments = [];
      var anchorCount = 0;
      for (var i = 0; i < steps.length; i++) {
        var step = steps[i];
        if (!isLlmAnchorStep(step)) {
          continue;
        }
        anchorCount++;
        var content = extractThinkingFromStepOutput(step);
        segments.push({
          iteration: anchorCount,
          content: content || "",
          isStreaming: false,
        });
      }
      if (anchorCount === 0) {
        return null;
      }
      // 所有锚点都提取不到 reasoningContent（旧数据/异常）→ 返回 null 让调用方降级
      var hasAnyContent = false;
      for (var j = 0; j < segments.length; j++) {
        if (segments[j].content) {
          hasAnyContent = true;
          break;
        }
      }
      return hasAnyContent ? segments : null;
    },
    startThinkingSegment(iteration, run) {
      if (!run) return;
      var iterNo =
        iteration != null && !isNaN(Number(iteration))
          ? Number(iteration)
          : 0;
      var segments = run.thinkingSegments || [];
      // 若最新 segment 已是该 iteration 且仍是 streaming，直接复用，避免重复开段
      var lastSeg = segments[segments.length - 1];
      if (
        lastSeg &&
        lastSeg.iteration === iterNo &&
        lastSeg.isStreaming
      ) {
        return;
      }
      // 不可变更新：创建新数组，确保 Vue 响应式触发
      run.thinkingSegments = segments.concat([
        {
          iteration: iterNo,
          content: "",
          isStreaming: true,
        },
      ]);
    },
    // llm_finished 事件：结束对应 iteration 的 thinking segment
    finishThinkingSegment(iteration, run) {
      if (!run) return;
      var segments = run.thinkingSegments || [];
      var iterNo =
        iteration != null && !isNaN(Number(iteration))
          ? Number(iteration)
          : null;
      // 不可变更新：创建新数组和新 segment 对象
      run.thinkingSegments = segments.map(function (seg) {
        if (seg && seg.isStreaming) {
          if (iterNo == null || seg.iteration === iterNo) {
            return {
              iteration: seg.iteration,
              content: seg.content,
              isStreaming: false,
            };
          }
        }
        return seg;
      });
    },
    async toggleThinkingExpanded(message) {
      // 全局思考卡片（无 message 对象）切换
      if (!message || typeof message !== "object" || !message.botMsgId) {
        this.thinkingExpanded = !this.thinkingExpanded;
        return;
      }

      // 折叠 → 直接切换
      if (message.thinkingExpanded) {
        message.thinkingExpanded = false;
        return;
      }

      // 展开：历史消息且 thinking 未加载过，先调接口加载
      if (
        !message.thinkingLoaded &&
        !message.thinkingLoading &&
        message.messageId &&
        !message.thinkingContent
      ) {
        message.thinkingLoading = true;
        try {
          // 分别加载 thinking 和 steps，任一失败不影响另一个
          var thinkingText = "";
          var normalizedSteps = [];

          // 1. 加载执行轨迹 steps（按 botMsgId）—— 主路径：从 step.outputJson 提取每轮 thinking
          if (message.botMsgId) {
            try {
              var runRes = await doctorApi.getAgentRunDetailByBotMsgId(
                message.botMsgId
              );
              if (runRes && runRes.data) {
                var rawSteps = collectAgentTraceItems(runRes.data);
                for (var i = 0; i < rawSteps.length; i++) {
                  var node = normalizeAgentTraceNode(rawSteps[i], i);
                  if (node) {
                    normalizedSteps.push(node);
                  }
                }
                message.agentSteps = normalizedSteps;
              }
            } catch (se) {
              console.warn("加载执行轨迹失败:", se);
            }
          }

          // 2. 加载思考内容（按 messageId）—— 用于折叠态摘要 + 降级兜底
          try {
            var thinkingRes = await doctorApi.getThinkingByMessageId(
              message.messageId
            );
            thinkingText =
              (thinkingRes && thinkingRes.data != null ? thinkingRes.data : "") ||
              "";
          } catch (te) {
            console.warn("加载思考内容失败:", te);
            thinkingText = "";
          }

          message.thinkingContent = String(thinkingText);
          // 主路径：从 steps 的 LLM 锚点 outputJson 直接提取每轮 reasoningContent，
          // 与实时流式"第 N 个 llm_started → 第 N 个 segment"完全对齐
          var segmentsFromSteps = this.buildThinkingSegmentsFromSteps(normalizedSteps);
          if (segmentsFromSteps) {
            message.thinkingSegments = segmentsFromSteps;
          } else {
            // 降级：steps 无 LLM 锚点或 outputJson 无 reasoningContent（旧数据），
            // 用整段 thinkingText 按字符均匀切分
            message.thinkingSegments = this.splitThinkingIntoSegments(
              String(thinkingText),
              normalizedSteps
            );
          }
          message.thinkingLoaded = true;
        } finally {
          message.thinkingLoading = false;
        }
      }
      message.thinkingExpanded = true;
    },
    findOrCreateBotMessage(botMsgId, payload) {
      var payloadRunId =
        payload && payload.runId != null ? String(payload.runId) : null;
      if (!botMsgId) {
        var currentRun = this.currentAgentRun;
        botMsgId =
          (currentRun && currentRun.botMsgId) || this.botMsgId;
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
        thinkingSegments: [],
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
      const run = this.resolveRunForEvent(payload);
      if (!run) return;
      var receiveMsg =
        payload.chunkText == null ? "" : String(payload.chunkText);
      if (!receiveMsg) {
        return;
      }
      var botMsgId =
        payload.botMsgId ||
        run.botMsgId ||
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
            : run.chatSessionId != null
            ? run.chatSessionId
            : null,
        sessionCode:
          payload.sessionCode ||
          run.sessionCode ||
          this.currentSessionCode ||
          null,
        sceneType:
          payload.sceneType ||
          this.currentSessionSceneType ||
          this.resolveExpectedSessionSceneType(),
      };
      this.applyServerSessionMeta(sessionMeta, sessionMeta.sceneType);

      run.botMsgId = botMsgId;
      if (payload.sessionCode) {
        run.sessionCode = payload.sessionCode;
      }
      if (payload.runId != null) {
        run.runId = payload.runId;
      }
      if (!this.isTerminalAgentRunStatus(run.status)) {
        run.status = "running";
      }
      this.snapshotRunState(run.runId);

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
              : run.runId != null
              ? run.runId
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
            : run.runId != null
            ? run.runId
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
          const compressRun = this.resolveRunForEvent(payload);
          const prevUsage = compressRun
            ? compressRun.tokenUsage
            : this.tokenUsage;
          const nextUsage = {
            promptTokens: payload.tokenAfter,
            completionTokens: 0,
            totalTokens: payload.tokenAfter,
            cumulativeTotalTokens:
              prevUsage && prevUsage.cumulativeTotalTokens != null
                ? prevUsage.cumulativeTotalTokens
                : payload.tokenAfter,
            contextWindow:
              payload.contextWindow != null
                ? payload.contextWindow
                : (prevUsage && prevUsage.contextWindow) ||
                  llmConfig.getConfig().maxInputContextTokens ||
                  DEFAULT_LLM_MAX_INPUT_CONTEXT_TOKENS,
            cacheHitTokens: null,
            cacheMissTokens: null,
            reasoningTokens: null,
          };
          if (compressRun) {
            compressRun.tokenUsage = nextUsage;
          }
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
      const run = this.resolveRunForEvent(event);
      if (!run) return;
      var normalizedStep = this.normalizeAgentStepItem(
        event,
        (run.steps || []).length
      );
      if (!normalizedStep) {
        return;
      }
      this.applyAgentStepEvent(normalizedStep, event, run);
    },
    applyAgentStepEvent(stepEvent, eventPayload, run) {
      if (!stepEvent) {
        return;
      }
      if (!run) {
        run = this.resolveRunForEvent(eventPayload || {}, {
          createIfMissing: true,
        });
      }
      if (!run) return;
      // 补全 run 字段（不覆盖已有值）
      if (run.botMsgId == null && eventPayload && eventPayload.botMsgId) {
        run.botMsgId = String(eventPayload.botMsgId);
      }
      if (run.sessionCode == null && eventPayload && eventPayload.sessionCode) {
        run.sessionCode = String(eventPayload.sessionCode);
      }
      var matchedIndex = -1;
      for (var i = 0; i < run.steps.length; i++) {
        var currentStep = run.steps[i];
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
        run.steps[matchedIndex] = Object.assign(
          {},
          run.steps[matchedIndex],
          stepEvent
        );
      } else {
        run.steps.push(stepEvent);
      }
      run.steps = run.steps
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

      var agentBotMsgId =
        (eventPayload && eventPayload.botMsgId) ||
        (run && run.botMsgId) ||
        this.botMsgId;
      // 预解析 eventType / iterationNo：用于驱动 thinking segment 生命周期
      var preNormalizedEvent = normalizeAgentTraceEvent(eventPayload || stepEvent);
      var preEventType =
        preNormalizedEvent && preNormalizedEvent.eventType
          ? String(preNormalizedEvent.eventType).toLowerCase()
          : "";
      var preIteration =
        stepEvent && stepEvent.iterationNo != null
          ? stepEvent.iterationNo
          : preNormalizedEvent && preNormalizedEvent.iterationNo != null
          ? preNormalizedEvent.iterationNo
          : null;
      // llm_started → 开启新 thinking segment（这一轮的思考内容将追加到此段）
      if (preEventType === "llm_started") {
        this.startThinkingSegment(preIteration, run);
      }
      // llm_finished → 结束对应 iteration 的 thinking segment
      if (preEventType === "llm_finished") {
        this.finishThinkingSegment(preIteration, run);
      }
      if (agentBotMsgId) {
        var targetMsg = this.findOrCreateBotMessage(
          agentBotMsgId,
          eventPayload
        );
        if (targetMsg) {
          targetMsg.agentSteps = run.steps.slice();
          // 同步 thinkingSegments 到 bot 消息对象（直接引用最新值，保持响应式）
          targetMsg.thinkingSegments = this.cloneThinkingSegments(
            run.thinkingSegments || this.thinkingSegments
          );
        }
      }
      var normalizedEvent = preNormalizedEvent;
      var eventType = preEventType;
      var runStatus =
        normalizedEvent && normalizedEvent.runStatus
          ? this.normalizeAgentRunStatus(normalizedEvent.runStatus)
          : null;
      if (
        eventType === "run_failed" ||
        runStatus === "failed" ||
        (!eventType && stepEvent.status === "failed")
      ) {
        run.status = "failed";
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
        run.status = "success";
        this.guidanceMessage = "本轮任务已完成，可继续发起新任务。";
        this.isSending = false;
        this.isStreaming = false;
        this.isThinking = false;
      } else if (!this.isTerminalAgentRunStatus(run.status)) {
        run.status = "running";
      }
      this.snapshotRunState(run.runId);
      // 决策轨迹新增/更新时也触发自动滚动，与思考内容流式输出一致
      this.scrollToBottom();
    },
    applyFinishPayload(chatResponse, run) {
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
      // run 解析（run 可能为 null：finish 来自非追踪 run）
      if (!run) {
        run = this.resolveRunForEvent(payload);
      }
      var runId = run
        ? run.runId
        : payload.runId != null
        ? String(payload.runId)
        : null;
      var botMsgId =
        payload.botMsgId ||
        (run && run.botMsgId) ||
        this.botMsgId;
      var normalizedSources = this.extractSourcesFromResponse(payload);

      // 解析 token 用量快照（Agent FINISH 载荷携带）——写入 run.tokenUsage
      if (payload.usage && typeof payload.usage === "object" && run) {
        run.tokenUsage = payload.usage;
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

      // 补全 run 字段并设置终态（仅 run 非空时）
      if (run) {
        if (run.botMsgId == null && payload.botMsgId) {
          run.botMsgId = String(payload.botMsgId);
        }
        if (run.sessionCode == null && payload.sessionCode) {
          run.sessionCode = String(payload.sessionCode);
        }
        if (payload.chatSessionId != null && run.chatSessionId == null) {
          run.chatSessionId = payload.chatSessionId;
        }
        // 修复 interrupted/cancelled 状态识别（原逻辑仅判 failed 否则一律 success）
        var normalized = this.normalizeAgentRunStatus(payload.status);
        if (normalized === "failed") {
          run.status = "failed";
          this.guidanceMessage = "任务执行失败，请稍后重试。";
        } else if (normalized === "interrupted" || normalized === "cancelled") {
          run.status = normalized;
          this.guidanceMessage = "任务已中断，可继续发起新任务。";
        } else {
          run.status = "success";
          this.guidanceMessage = "本轮任务已完成，可继续发起新任务。";
        }
        run.finishReceived = true;
        // snapshot 须在 removeRunEntry 之前调用（仍读 this.activeAgentRuns[runId]）
        this.snapshotRunState(run.runId);
        this.silentFetchAgentRunDetail(
          payload.runId != null ? payload.runId : run.runId
        );
        // 终态后移除 entry
        if (runId && this.isTerminalAgentRunStatus(run.status)) {
          this.removeRunEntry(runId);
        }
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
                me.activeAgentRuns &&
                typeof me.activeAgentRuns === "object"
              ) {
                var failingRunIds = Object.keys(me.activeAgentRuns);
                for (var fri = 0; fri < failingRunIds.length; fri++) {
                  var failingRun = me.activeAgentRuns[failingRunIds[fri]];
                  if (
                    failingRun &&
                    !me.isTerminalAgentRunStatus(failingRun.status)
                  ) {
                    failingRun.status = "failed";
                    me.snapshotRunState(failingRun.runId);
                  }
                }
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
        messageId: message.messageId != null ? message.messageId : null,
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
        // 历史消息思考内容状态：默认空 + 折叠 + 未加载
        thinkingContent:
          role === "assistant" ? "" : null,
        thinkingSegments: [],
        thinkingExpanded: false,
        thinkingLoaded: false,
        thinkingLoading: false,
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

      // 程序触发的滚动不更新用户标志（避免 scrollToBottom 自身触发 scroll 事件误判）
      if (this.isProgrammaticScroll) {
        this.showBackToBottom = distanceFromBottom > 200;
        return;
      }

      // 用户主动滚动：远离底部时标记停止跟随，靠近底部时恢复跟随
      // 阈值 200px，避免内容布局抖动导致的误判
      if (distanceFromBottom > 200) {
        this.isUserScrolledUp = true;
        this.showBackToBottom = true;
      } else {
        this.isUserScrolledUp = false;
        this.showBackToBottom = false;
      }
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
      var currentStopRun = this.currentAgentRun;
      var runId = (currentStopRun && currentStopRun.runId) || null;
      if (!runId) {
        // 没有 runId 时仅做前端状态重置
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
      // 二次确认（与回滚/中断一致的 confirmBar）
      var confirmed = await this.showConfirmBar(
        "确定压缩当前会话上下文？压缩后早期对话将被摘要替代。",
        { confirmText: "压缩", cancelText: "取消" }
      );
      if (!confirmed) {
        return;
      }
      this.isCompressing = true;
      try {
        await doctorApi.compressContext(sessionId);
        // 结果由 SSE handler 统一处理（context_compressing / context_compressed / context_compress_failed）
      } catch (e) {
        this.isCompressing = false;
        this.showUiMessage("error", "触发压缩失败：" + (e && e.message ? e.message : "未知错误"));
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
      this.taskStartTime = Date.now();
      this.lastTtft = null;

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
      this.scrollToBottom(true);
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

        // 流式输出（无 force）：只要用户没主动向上滚就跟随
        // 强制滚动（force=true）：发送消息/回到底部按钮，重置用户标志
        var shouldScroll = force === true || !me.isUserScrolledUp;

        if (shouldScroll) {
          // 标记程序滚动，避免触发的 scroll 事件被 handleChatScroll 误判为用户操作
          me.isProgrammaticScroll = true;
          chatMessages.scrollTop = chatMessages.scrollHeight;
          if (force === true) {
            me.isUserScrolledUp = false;
            me.showBackToBottom = false;
          }
          // 用 rAF 保持程序滚动标志到下一帧绘制后，避免布局抖动期被误判
          requestAnimationFrame(function () {
            requestAnimationFrame(function () {
              me.isProgrammaticScroll = false;
            });
          });
        }

        me.handleChatScroll();
      });
    },
    doInternetSearch(internetSearchSelected) {
      this.internetSearchSelected = !internetSearchSelected;

      if (this.internetSearchSelected) {
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
