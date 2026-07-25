import { createRouter, createWebHashHistory } from "vue-router";
import { getToken, getUserInfo } from "../services/http";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("../pages/login/LoginPage.vue"),
    meta: { public: true, title: "登录" },
  },
  {
    path: "/",
    component: () => import("../layouts/AppLayout.vue"),
    children: [
      {
        path: "",
        redirect: "/chat",
      },
      {
        path: "chat/:sessionId?",
        name: "chat",
        component: () => import("../pages/chat/ChatPage.vue"),
        meta: { title: "对话" },
      },
      {
        path: "training",
        name: "training",
        component: () => import("../pages/training/TrainingPage.vue"),
        meta: { title: "训练记录", forceView: "training-log" },
      },
      {
        path: "body-metrics",
        name: "body-metrics",
        component: () => import("../pages/metrics/MetricsPage.vue"),
        meta: { title: "身体指标", forceView: "body-metrics" },
      },
      {
        path: "wiki",
        name: "wiki",
        component: () => import("../pages/wiki/WikiPage.vue"),
        meta: { title: "Wiki" },  // 保持英文，作为专有名词
      },
      {
        path: "upload",
        name: "upload",
        component: () => import("../pages/knowledge/KnowledgePage.vue"),
        meta: { title: "知识库" },
      },
      {
        path: "dashboard",
        name: "dashboard",
        component: () => import("../pages/dashboard/DashboardPage.vue"),
        meta: { title: "仪表盘" },
      },
      {
        path: "settings",
        name: "settings",
        component: () => import("../pages/settings/SettingsPage.vue"),
        meta: { title: "设置" },
      },
    ],
  },
  {
    path: "/:pathMatch(.*)*",
    redirect: "/chat",
  },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const hasSession = !!(getToken() && getUserInfo());

  if (to.meta.public) {
    if (hasSession && to.name === "login") {
      return next({ path: "/chat" });
    }
    return next();
  }

  if (!hasSession) {
    return next({ path: "/login" });
  }

  next();
});

router.afterEach((to) => {
  const title = to.meta?.title;
  if (title) {
    document.title = `FitMate / ${title}`;
  } else {
    document.title = "FitMate";
  }
});

/**
 * 动态更新标签页标题。
 * - 聊天页面：根据会话标题 + 生成状态动态显示
 *   生成中: "生成中… FitMate / 会话标题"
 *   正常:   "FitMate / 会话标题"
 * - 其他页面：保持路由标题不变
 */
export function setDocumentTitle(title: string, suffix?: string) {
  const parts: string[] = [];
  if (suffix) parts.push(suffix);
  if (title) parts.push("FitMate", title);
  else parts.push("FitMate");
  document.title = parts.join(" / ");
}

export function resetDocumentTitle() {
  const current = router.currentRoute.value;
  const title = current.meta?.title;
  if (title) {
    document.title = `FitMate / ${title}`;
  } else {
    document.title = "FitMate";
  }
}

export default router;
