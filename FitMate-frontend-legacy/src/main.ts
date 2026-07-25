import { createApp } from "vue";
import App from "./App.vue";
import router from "./router";
import toast from "./services/toast";
import { initTheme } from "./services/theme";
import "./styles/base.css";
import "./config/runtime";
import "./services/http";
import "./services/doctorApi";
import "./services/sseService";

initTheme();

const app = createApp(App);

app.config.globalProperties.$message = toast;
app.use(router);

app.mount("#app");
