export type AgentRunStatus =
  | "pending"
  | "running"
  | "success"
  | "failed"
  | "cancelled"
  | "interrupted";

export type AgentTraceStatus =
  | "pending"
  | "running"
  | "completed"
  | "failed"
  | "skipped";

export interface AgentTraceEvent {
  eventId?: string;
  runId?: number | string;
  sequence?: number;
  eventType?: string;
  stepId?: number | string;
  nodeId?: string;
  parentId?: string;
  stepNo?: number;
  stepName?: string;
  stepStatus?: string;
  status?: string;
  runStatus?: string;
  toolName?: string;
  toolCallId?: string;
  iterationNo?: number;
  durationMs?: number;
  message?: string;
  inputJson?: string;
  outputJson?: string;
  errorMessage?: string;
  createdAt?: string;
  raw?: Record<string, unknown>;
}

export interface AgentTraceNode extends AgentTraceEvent {
  id: string;
  label: string;
  status: AgentTraceStatus;
}

export interface AgentExecuteAck {
  runId: number | string;
  chatSessionId?: number | string | null;
  sessionCode?: string | null;
  botMsgId?: string | null;
  status?: AgentRunStatus | string;
  idempotent?: boolean;
}

export interface AgentRunDetail {
  runId?: number | string;
  chatSessionId?: number | string | null;
  sessionCode?: string | null;
  botMsgId?: string | null;
  requestText?: string;
  status?: AgentRunStatus | string;
  sourceType?: string | null;
  resultJson?: string | null;
  steps?: unknown[];
  trace?: unknown[];
  events?: unknown[];
  nodes?: unknown[];
  timeline?: unknown[];
}
