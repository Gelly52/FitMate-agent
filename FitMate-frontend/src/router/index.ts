import { createRouter, createWebHashHistory } from "vue-router";
import { getToken, getUserInfo } from "../services/http";

const routes = [
  {
    path: "/login",
    name: "login",
    component: () => import("../pages/login/LoginPage.vue"),
    meta: { public: true, title: "Authenticate" },
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
        meta: { title: "Agent Chat" },
      },
      {
        path: "training",
        name: "training",
        component: () => import("../pages/training/TrainingPage.vue"),
        meta: { title: "Training Log", forceView: "training-log" },
      },
      {
        path: "body-metrics",
        name: "body-metrics",
        component: () => import("../pages/metrics/MetricsPage.vue"),
        meta: { title: "Body Metrics", forceView: "body-metrics" },
      },
      {
        path: "wiki",
        name: "wiki",
        component: () => import("../pages/wiki/WikiPage.vue"),
        meta: { title: "Wiki" },
      },
      {
        path: "upload",
        name: "upload",
        component: () => import("../pages/knowledge/KnowledgePage.vue"),
        meta: { title: "Knowledge Base" },
      },
      {
        path: "dashboard",
        name: "dashboard",
        component: () => import("../pages/dashboard/DashboardPage.vue"),
        meta: { title: "Dashboard" },
      },
      {
        path: "settings",
        name: "settings",
        component: () => import("../pages/settings/SettingsPage.vue"),
        meta: { title: "Settings" },
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

export default router;
