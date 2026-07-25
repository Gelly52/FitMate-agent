import type { AxiosInstance } from "axios";
import type doctorApi from "../services/doctorApi";
import type sseService from "../services/sseService";
import type toast from "../services/toast";

declare module "@vue/runtime-core" {
  interface ComponentCustomProperties {
    $message: typeof toast;
  }
}

declare module "vue-router" {
  interface RouteMeta {
    public?: boolean;
    title?: string;
    forceView?: string;
  }
}

declare global {
  interface Window {
    API_BASE?: string;
    doctorApi?: typeof doctorApi;
    instance?: AxiosInstance;
    sseService?: typeof sseService;
  }
}

export {};
