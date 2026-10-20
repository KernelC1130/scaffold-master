<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Field, Segmented, Select, Tag, TextInput } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import type { Column } from '@/components/ui'
import { systemApi, type SysJobLogRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { revealPage } from '@/composables/motion'

const emit = defineEmits<{ switch: [view: string] }>()

/** ListPage 根（.page）——挂载后补一次入场激活（tab 切换重建时 Transition 不触发） */
const rootEl = ref()

interface LogQuery extends Record<string, unknown> { jobName: string; status: string }
const logList = useListPage<SysJobLogRow, LogQuery>({
  query: { jobName: '', status: '' },
  fetch: params => systemApi.jobs.logs.list(params) as Promise<any>,
})

onMounted(() => {
  if (rootEl.value?.$el) revealPage(rootEl.value.$el)
})

const LOG_COLUMNS: Column[] = [
  { key: 'jobLogId', title: '日志ID', width: 80 },
  { key: 'jobName', title: '任务名称' },
  { key: 'invokeTarget', title: '调用目标' },
  { key: 'status', title: '结果', width: 90 },
  { key: 'jobMessage', title: '日志信息' },
  { key: 'startTime', title: '开始时间', width: 168 },
  { key: 'endTime', title: '结束时间', width: 168 },
]

const LOG_STATUS_OPTIONS = [
  { label: '全部结果', value: '' },
  { label: '成功', value: '0' },
  { label: '失败', value: '1' },
]

async function clearLogs() {
  const ok = await confirm({ title: '清空执行日志', message: '确认清空全部执行日志吗？清空后无法恢复。', confirmText: '清空', danger: true })
  if (!ok) return
  try {
    await systemApi.jobs.logs.clear()
    pushToast('执行日志已清空')
    await logList.load()
  } catch (error) {
    toastError(error, '清空失败')
  }
}

const TAB_OPTIONS = [
  { label: '任务', value: 'jobs' },
  { label: '执行日志', value: 'logs' },
]
const tabModel = ref('logs')
</script>

<template>
  <ListPage ref="rootEl" title="执行日志" desc="定时任务的每次触发都会在此留痕：成功与失败、异常摘要与起止时间。" table-title="执行记录" :list="logList" :columns="LOG_COLUMNS" fit-viewport :min-width="980">
    <template #actions>
      <Segmented v-model="tabModel" label="视图" :options="TAB_OPTIONS" @update:model-value="tabModel === 'jobs' && emit('switch', 'jobs')" />
      <PermissionButton perm="monitor:job:remove" variant="secondary" icon="trash" danger @click="clearLogs">清空日志</PermissionButton>
    </template>
    <template #search>
      <Field label="任务名称" wide>
        <TextInput v-model="logList.query.jobName" icon="search" placeholder="按任务名称模糊搜索" clearable @enter="logList.search()" />
      </Field>
      <Field label="结果">
        <Select v-model="logList.query.status" :options="LOG_STATUS_OPTIONS" />
      </Field>
    </template>
    <template #cell-status="{ row }"><Tag :tone="row.status === '0' ? 'ok' : 'danger'" dot>{{ row.status === '0' ? '成功' : '失败' }}</Tag></template>
    <template #cell-jobMessage="{ row }">
      <span class="ellipsis" style="display: inline-block; max-width: 300px">{{ row.jobMessage }}</span>
      <div v-if="row.exceptionInfo" class="cell-sub" style="color: var(--danger, #d33)">{{ row.exceptionInfo }}</div>
    </template>
  </ListPage>
</template>
