<!-- FitMate-frontend/src/pages/settings/SettingsPage.vue -->
<template>
  <div class="settings-page px-lg pb-lg overflow-y-auto">
    <header class="mb-xl mt-lg">
      <h1 class="font-headline-md text-headline-md text-on-surface tracking-tight">设置</h1>
      <p class="font-inter text-label-xs text-on-surface-variant uppercase tracking-widest opacity-70 mt-xs">
        Manage your account &amp; appearance
      </p>
    </header>

    <SettingsSectionNav :active="activeSection" @navigate="selectSection" />

    <div class="settings-content">
      <ProfileSection v-if="activeSection === 'profile'" :profile="profile" @updated="onProfileUpdated" />
      <AppearanceSection v-else-if="activeSection === 'appearance'" />
      <LlmConfigSection v-else-if="activeSection === 'llm'" />
      <McpConfigSection v-else-if="activeSection === 'mcp'" />
      <SkillsSection v-else-if="activeSection === 'skills'" />
      <MemorySection v-else-if="activeSection === 'memory'" />
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
import LlmConfigSection from "./components/LlmConfigSection.vue";
import McpConfigSection from "./components/McpConfigSection.vue";
import SkillsSection from "./components/SkillsSection.vue";
import MemorySection from "./components/MemorySection.vue";
import type { UserProfile } from "../../types/settings";

export default {
  name: "SettingsPage",
  components: { SettingsSectionNav, ProfileSection, AppearanceSection, AboutSection, LlmConfigSection, McpConfigSection, SkillsSection, MemorySection },
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
      if (hash && ["profile", "appearance", "llm", "mcp", "skills", "memory", "about"].includes(hash)) {
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

.settings-content {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

.settings-content > :deep(*) {
  width: 100%;
  max-width: 640px;
}
</style>
