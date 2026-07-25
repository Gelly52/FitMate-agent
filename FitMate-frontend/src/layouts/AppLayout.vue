<template>
  <div class="flex h-screen overflow-hidden bg-background text-on-background">
    <SideNav :expanded="isSideNavExpanded" @toggle="toggleSideNav" />
    <div
      class="flex-1 flex flex-col h-screen transition-all duration-150 ease-out"
      :class="isSideNavExpanded ? 'md:ml-60' : 'md:ml-20'"
    >
      <TopBar :title="currentTitle" @logout="handleLogout" />
      <main class="flex-1 flex flex-col overflow-hidden">
        <router-view />
      </main>
    </div>
    <!-- Mobile sidebar offset spacer -->
    <div
      class="hidden md:block fixed left-0 top-0 h-full z-40 pointer-events-none transition-all duration-150 ease-out"
      :class="isSideNavExpanded ? 'w-60' : 'w-20'"
    ></div>
  </div>
</template>

<script lang="ts">
import { getToken, getUserInfo, clearUserSession } from "../services/http";
import doctorApi from "../services/doctorApi";
import { llmConfig } from "../services/llmConfig";
import SideNav from "../components/SideNav.vue";
import TopBar from "../components/TopBar.vue";

export default {
  name: "AppLayout",
  components: { SideNav, TopBar },
  data() {
    return {
      isSideNavExpanded: false,
      isLoggingOut: false,
    };
  },
  computed: {
    currentTitle() {
      return this.$route?.meta?.title || "FitMate";
    },
  },
  methods: {
    toggleSideNav() {
      this.isSideNavExpanded = !this.isSideNavExpanded;
    },
    async handleLogout() {
      if (this.isLoggingOut) return;
      this.isLoggingOut = true;
      try {
        await doctorApi.userLogout();
      } catch (e) {
        // ignore network errors during logout
      } finally {
        clearUserSession();
        this.isLoggingOut = false;
        this.$router.push("/login");
      }
    },
  },
  mounted() {
    llmConfig.load();
  },
};
</script>
