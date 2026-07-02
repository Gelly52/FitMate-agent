<!-- FitMate-frontend/src/pages/settings/SettingsPage.vue -->
<template>
  <div class="settings-page px-lg py-lg overflow-y-auto">
    <header class="mb-xl">
      <h1 class="font-headline-md text-headline-md text-on-surface tracking-tight">设置</h1>
      <p class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-70 mt-xs">
        Manage your account &amp; appearance
      </p>
    </header>

    <SettingsSectionNav :active="activeSection" @navigate="selectSection" />

    <div class="settings-content max-w-3xl">
      <ProfileSection v-if="activeSection === 'profile'" :profile="profile" @updated="onProfileUpdated" />
      <AppearanceSection v-else-if="activeSection === 'appearance'" />
      <!-- LLM 配置区块（Task 17 将替换为 LlmConfigSection 组件） -->
      <section v-else-if="activeSection === 'llm'" class="settings-llm-placeholder">
        <h2 class="font-headline-sm text-headline-sm text-on-surface">配置</h2>
        <p class="text-on-surface-variant opacity-70 mt-sm">LLM 配置区块加载中...</p>
      </section>
      <AboutSection v-else-if="activeSection === 'about'" />
    </div>
  </div>
</template>

<script lang="ts">
import doctorApi from "../../services/doctorApi";
import SettingsSectionNav from "./components/SettingsSectionNav.vue";
import ProfileSection from "./components/ProfileSection.vue";
import AppearanceSection from "./components/AppearanceSection.vue";
import AboutSection from "./components/AboutSection.vue";
import type { UserProfile } from "../../types/settings";

export default {
  name: "SettingsPage",
  components: { SettingsSectionNav, ProfileSection, AppearanceSection, AboutSection },
  data() {
    return {
      activeSection: "profile",
      profile: null as UserProfile | null,
    };
  },
  mounted() {
    this.loadProfile();
    this.applyHash();
  },
  methods: {
    async loadProfile() {
      try {
        const res = await doctorApi.getUserProfile();
        if (res && res.status === 200) {
          this.profile = res.data as UserProfile;
        }
      } catch (e) {
        console.error("加载用户资料失败", e);
      }
    },
    applyHash() {
      const hash = (this.$route && this.$route.hash || "").replace("#", "");
      if (hash && ["profile", "appearance", "llm", "about"].includes(hash)) {
        this.activeSection = hash;
      }
    },
    selectSection(id: string) {
      this.activeSection = id;
    },
    onProfileUpdated(updated: UserProfile) {
      this.profile = updated;
    },
  },
};
</script>

<style scoped>
.settings-page {
  height: 100%;
}
</style>
