/**
 * Minimal toast service replacing Element Plus ElMessage.
 * Renders a transient, top-centered notification using the Obsidian
 * Precision tokens. Exposes the same `success/error/info/warning` surface
 * the app previously relied on via `this.$message`.
 */

let containerEl = null;

function ensureContainer() {
  if (typeof document === "undefined") {
    return null;
  }
  if (containerEl && document.body.contains(containerEl)) {
    return containerEl;
  }
  containerEl = document.createElement("div");
  containerEl.className = "fa-toast-container";
  document.body.appendChild(containerEl);
  return containerEl;
}

const ICONS = {
  success: "check_circle",
  error: "error",
  warning: "warning",
  info: "info",
};

function show(type, text) {
  const container = ensureContainer();
  if (!container || !text) {
    return;
  }

  const toast = document.createElement("div");
  toast.className = "fa-toast fa-toast-" + type;

  const icon = document.createElement("span");
  icon.className = "material-symbols-outlined fa-toast-icon";
  icon.textContent = ICONS[type] || ICONS.info;

  const message = document.createElement("span");
  message.className = "fa-toast-text";
  message.textContent = String(text);

  toast.appendChild(icon);
  toast.appendChild(message);
  container.appendChild(toast);

  requestAnimationFrame(() => {
    toast.classList.add("fa-toast-visible");
  });

  window.setTimeout(() => {
    toast.classList.remove("fa-toast-visible");
    window.setTimeout(() => {
      if (toast.parentNode) {
        toast.parentNode.removeChild(toast);
      }
    }, 220);
  }, 2600);
}

const toast = {
  success(text) {
    show("success", text);
  },
  error(text) {
    show("error", text);
  },
  warning(text) {
    show("warning", text);
  },
  info(text) {
    show("info", text);
  },
};

export default toast;
