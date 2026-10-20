<script setup lang="ts">
import { computed, ref, markRaw } from 'vue'
import JobListPage from './JobListPage.vue'
import JobLogPage from './JobLogPage.vue'

const tab = ref<'jobs' | 'logs'>('jobs')
const current = computed(() => markRaw(tab.value === 'jobs' ? JobListPage : JobLogPage))

function switchView(view: string) {
  tab.value = view === 'logs' ? 'logs' : 'jobs'
}
</script>

<template>
  <component :is="current" @switch="switchView" />
</template>
