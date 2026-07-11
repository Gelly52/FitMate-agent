import type {
  AgentRunStatus,
  AgentTraceEvent,
  AgentTraceNode,
  AgentTraceStatus,
} from "../types/agent";

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

function readString(value: unknown): string | undefined {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  return String(value);
}

function readNumber(value: unknown): number | undefined {
  if (value === undefined || value === null || value === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isNaN(parsed) ? undefined : parsed;
}

export function safeParseAgentPayload(value: unknown): unknown {
  if (typeof value !== "string") {
    return value;
  }
  try {
    return JSON.parse(value);
  } catch (_error) {
    return value;
  }
}

export function normalizeAgentRunStatus(status: unknown): AgentRunStatus {
  const normalized = status == null ? "pending" : String(status).toLowerCase();
  if (normalized === "completed" || normalized === "success") {
    return "success";
  }
  if (
    normalized === "error" ||
    normalized === "failed" ||
    normalized === "timeout"
  ) {
    return "failed";
  }
  if (normalized === "cancelled") {
    return "cancelled";
  }
  if (normalized === "interrupted") {
    return "interrupted";
  }
  if (normalized === "running") {
    return "running";
  }
  return "pending";
}

export function normalizeAgentTraceStatus(status: unknown): AgentTraceStatus {
  const normalized = status == null ? "pending" : String(status).toLowerCase();
  if (normalized === "success" || normalized === "completed" || normalized === "finished") {
    return "completed";
  }
  if (normalized === "failed" || normalized === "error" || normalized === "timeout") {
    return "failed";
  }
  if (normalized === "running" || normalized === "started") {
    return "running";
  }
  if (normalized === "skipped") {
    return "skipped";
  }
  return "pending";
}

function inferTraceStatus(event: AgentTraceEvent): AgentTraceStatus {
  const explicitStatus = normalizeAgentTraceStatus(event.stepStatus ?? event.status);
  if (explicitStatus !== "pending") {
    return explicitStatus;
  }
  const eventType = (event.eventType || "").toLowerCase();
  if (eventType.endsWith("_started") || eventType === "run_started") {
    return "running";
  }
  if (
    eventType.endsWith("_finished") ||
    eventType === "final_answer" ||
    eventType === "run_finished"
  ) {
    return "completed";
  }
  if (eventType.endsWith("_failed") || eventType === "run_failed") {
    return "failed";
  }
  return explicitStatus;
}

export function isTerminalAgentEvent(event: AgentTraceEvent | null): boolean {
  if (!event) {
    return false;
  }
  const eventType = (event.eventType || "").toLowerCase();
  if (["final_answer", "run_finished", "run_failed"].includes(eventType)) {
    return true;
  }
  return (
    !!event.runStatus &&
    ["success", "failed", "cancelled", "interrupted"].includes(
      normalizeAgentRunStatus(event.runStatus)
    )
  );
}

export function normalizeAgentTraceEvent(payload: unknown): AgentTraceEvent | null {
  const parsed = safeParseAgentPayload(payload);
  if (!isRecord(parsed)) {
    return null;
  }

  const eventType = readString(
    parsed.eventType ?? parsed.type ?? parsed.kind ?? parsed.messageType
  );
  const stepStatus = readString(parsed.stepStatus ?? parsed.status);
  const stepName = readString(
    parsed.stepName ?? parsed.label ?? parsed.name ?? parsed.title
  );

  return {
    eventId: readString(parsed.eventId ?? parsed.id ?? parsed.traceId),
    runId: (parsed.runId as number | string | undefined) ?? undefined,
    sequence: readNumber(parsed.sequence ?? parsed.seq ?? parsed.stepNo),
    eventType: eventType ? eventType.toLowerCase() : undefined,
    stepId: (parsed.stepId as number | string | undefined) ?? undefined,
    nodeId: readString(parsed.nodeId),
    parentId: readString(parsed.parentId),
    stepNo: readNumber(parsed.stepNo),
    stepName,
    stepStatus,
    status: readString(parsed.status),
    runStatus: readString(parsed.runStatus),
    toolName: readString(parsed.toolName),
    toolCallId: readString(parsed.toolCallId),
    subagentRunId: (parsed.subagentRunId as number | string | undefined) ?? undefined,
    iterationNo: readNumber(parsed.iterationNo ?? parsed.iteration),
    durationMs: readNumber(parsed.durationMs),
    message: readString(parsed.message ?? parsed.description),
    inputJson: readString(parsed.inputJson),
    outputJson: readString(parsed.outputJson),
    errorMessage: readString(parsed.errorMessage ?? parsed.error),
    createdAt: readString(parsed.createdAt ?? parsed.timestamp),
    raw: parsed,
  };
}

function resolveTraceLabel(event: AgentTraceEvent, fallbackIndex: number): string {
  const eventType = (event.eventType || "").toLowerCase();
  if (eventType === "tool_call_started" && event.toolName) {
    return "Calling tool: " + event.toolName;
  }
  if (eventType === "tool_call_finished" && event.toolName) {
    return "Tool finished: " + event.toolName;
  }
  if (eventType === "tool_call_failed" && event.toolName) {
    return "Tool failed: " + event.toolName;
  }
  if (eventType === "llm_started") {
    return "Agent: Let me think...";
  }
  if (eventType === "llm_finished") {
    return "Done thinking";
  }
  if (eventType === "subagent_started") {
    return "Sub-agent spawned";
  }
  if (eventType === "subagent_finished") {
    return "Sub-agent completed";
  }
  if (eventType === "final_answer" || eventType === "run_finished") {
    return "Ready to respond";
  }
  if (eventType === "run_started") {
    return "Agent started";
  }
  if (eventType === "run_failed") {
    return "Task failed";
  }
  if (event.stepName) {
    return event.stepName;
  }
  if (event.toolName) {
    return event.toolName;
  }
  if (event.message) {
    return event.message;
  }
  return "Event " + (fallbackIndex + 1);
}

export function normalizeAgentTraceNode(value: unknown, index = 0): AgentTraceNode | null {
  const event = normalizeAgentTraceEvent(value);
  if (!event) {
    return null;
  }
  const id =
    event.stepId ??
    event.nodeId ??
    event.toolCallId ??
    event.eventId ??
    (event.stepNo != null ? "agent-step-" + event.stepNo : undefined) ??
    "agent-trace-" + index;
  return {
    ...event,
    id: String(id),
    label: resolveTraceLabel(event, index),
    status: inferTraceStatus(event),
  };
}

export function collectAgentTraceItems(detail: Record<string, unknown>): unknown[] {
  const sources = [detail.steps, detail.trace, detail.events, detail.nodes, detail.timeline];
  let emptySource: unknown[] | null = null;
  for (const source of sources) {
    if (Array.isArray(source)) {
      if (source.length > 0) {
        return source;
      }
      if (!emptySource) {
        emptySource = source;
      }
    }
  }
  return emptySource || [];
}

/**
 * 判断 step 是否为 LLM 轮次锚点。
 * 实时流式：llm_started；历史消息：DB 里 markStepSuccess 用 llm_finished 覆盖了 eventType。
 */
export function isLlmAnchorStep(step: { eventType?: string } | null | undefined): boolean {
  if (!step || !step.eventType) return false;
  const et = String(step.eventType).toLowerCase();
  return et === "llm_started" || et === "llm_finished";
}

/**
 * 从 LLM 锚点 step 的 outputJson 中提取该轮思考内容（reasoningContent）。
 * 后端在 llm_finished 时把 {"decision":"...","reasoningContent":"<本轮思考>"} 序列化为 JSON 存入 output_json。
 * 兼容 outputJson 为字符串或对象；解析失败或字段缺失返回空字符串。
 */
export function extractThinkingFromStepOutput(step: { outputJson?: unknown } | null | undefined): string {
  if (!step || step.outputJson == null || step.outputJson === "") {
    return "";
  }
  let parsed: unknown = step.outputJson;
  if (typeof parsed === "string") {
    try {
      parsed = JSON.parse(parsed);
    } catch {
      return "";
    }
  }
  if (!isRecord(parsed)) {
    return "";
  }
  const reasoning = parsed.reasoningContent ?? parsed.reasoning ?? parsed.thinking;
  return reasoning == null ? "" : String(reasoning);
}
