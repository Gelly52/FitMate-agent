import { API_BASE } from "../config/runtime";

export const SSE_CONNECT_PATH = "/sse/connect";
export const SSE_EVENT_NAMES = Object.freeze({
  OPEN: "open",
  MESSAGE: "message",
  ADD: "add",
  THINKING: "thinking",
  FINISH: "finish",
  ERROR: "error",
  CUSTOM_EVENT: "customEvent",
  CUSTOM_EVENT_SNAKE: "custom_event",
  AGENT_EVENT: "agent_event",
  TRACE_EVENT: "trace_event",
  AGENT_STEP: "agentStep",
});

function getEventSourceClass(customEventSourceClass) {
  if (typeof customEventSourceClass === "function") {
    return customEventSourceClass;
  }

  if (
    typeof window !== "undefined" &&
    typeof window.EventSource === "function"
  ) {
    return window.EventSource;
  }

  return undefined;
}

function normalizeHandlers(options) {
  const handlers = Object.assign({}, options.handlers || {});
  const handlerKeys = [
    "onOpen",
    "onMessage",
    "onAdd",
    "onThinking",
    "onFinish",
    "onError",
    "onCustomEvent",
    "onCustomEventSnake",
    "onCustom_event",
    "onAgentEvent",
    "onTraceEvent",
  ];

  handlerKeys.forEach((handlerKey) => {
    if (typeof options[handlerKey] === "function") {
      handlers[handlerKey] = options[handlerKey];
    }
  });

  return handlers;
}

export function buildSseConnectPath(ticket) {
  return SSE_CONNECT_PATH + "?ticket=" + encodeURIComponent(ticket);
}

export function buildSseConnectUrl(ticket, apiBase = API_BASE) {
  return (apiBase || "") + buildSseConnectPath(ticket);
}

export function bindSseListeners(source, handlers = {}) {
  if (!source || typeof source.addEventListener !== "function") {
    return function noop() {};
  }

  const listeners = [];
  const register = (eventName, handler) => {
    if (typeof handler !== "function") {
      return;
    }

    const wrappedHandler = (event) => handler(event, { eventName, source });
    source.addEventListener(eventName, wrappedHandler, false);
    listeners.push({ eventName, wrappedHandler });
  };

  register(SSE_EVENT_NAMES.OPEN, handlers.onOpen);
  register(SSE_EVENT_NAMES.MESSAGE, handlers.onMessage);
  register(SSE_EVENT_NAMES.ADD, handlers.onAdd);
  register(SSE_EVENT_NAMES.THINKING, handlers.onThinking);
  register(SSE_EVENT_NAMES.FINISH, handlers.onFinish);
  register(SSE_EVENT_NAMES.ERROR, handlers.onError);

  const customEventHandler =
    handlers.onCustomEvent ||
    handlers.onCustomEventSnake ||
    handlers.onCustom_event;

  register(SSE_EVENT_NAMES.CUSTOM_EVENT, customEventHandler);
  register(SSE_EVENT_NAMES.CUSTOM_EVENT_SNAKE, customEventHandler);

  register(SSE_EVENT_NAMES.AGENT_EVENT, handlers.onAgentEvent || customEventHandler);
  register(SSE_EVENT_NAMES.TRACE_EVENT, handlers.onTraceEvent || customEventHandler);
  register(SSE_EVENT_NAMES.AGENT_STEP, handlers.onAgentEvent || customEventHandler);

  return function unbindListeners() {
    if (typeof source.removeEventListener !== "function") {
      return;
    }

    listeners.forEach(({ eventName, wrappedHandler }) => {
      source.removeEventListener(eventName, wrappedHandler, false);
    });
  };
}

export function connectSse(options = {}) {
  if (
    options.ticket === undefined ||
    options.ticket === null ||
    options.ticket === ""
  ) {
    throw new Error("ticket is required to connect SSE");
  }

  const EventSourceClass = getEventSourceClass(options.EventSourceClass);
  const url = buildSseConnectUrl(options.ticket, options.apiBase);

  if (typeof EventSourceClass !== "function") {
    return {
      isSupported: false,
      url,
      source: null,
      close() {},
      unbind() {},
    };
  }

  const source = new EventSourceClass(url, options.eventSourceOptions);
  const handlers = normalizeHandlers(options);
  const unbind = bindSseListeners(source, handlers);

  return {
    isSupported: true,
    url,
    source,
    unbind,
    close() {
      unbind();
      if (typeof source.close === "function") {
        source.close();
      }
    },
  };
}

export function closeSse(connection) {
  if (!connection) {
    return;
  }

  if (typeof connection.close === "function") {
    connection.close();
    return;
  }

  if (connection.source && typeof connection.source.close === "function") {
    connection.source.close();
  }
}

const sseService = {
  API_BASE,
  SSE_CONNECT_PATH,
  SSE_EVENT_NAMES,
  buildSseConnectPath,
  buildSseConnectUrl,
  bindSseListeners,
  connectSse,
  closeSse,
};

if (typeof window !== "undefined") {
  window.sseService = sseService;
}

export default sseService;
