<script setup lang="ts">
import { nextTick, onMounted, reactive, ref, watch } from 'vue'
import DataTable, { type Column } from '@/components/ui/DataTable.vue'
import Pagination from '@/components/ui/Pagination.vue'
import { Drawer, Field, Segmented, Select, Tag, TextInput } from '@/components/ui'
import { ListPage, PermissionButton } from '@/components/business'
import { gsap, EASE } from '@/composables/motion'
import { systemApi, type SysJobRow, type SysJobLogRow, type SysJobPayload } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'

/** 视图记忆（localStorage）+ 双列表 v-show 常驻 + 切换时轻量 fade 入场（不用全页 gsap） */
const view = ref<'table' | 'logs'>((localStorage.getItem('monitor-job-view') as 'table' | 'logs') || 'table')
const VIEWS = [
  { label: '任务', value: 'table' },
  { label: '执行日志', value: 'logs' },
]

const tableView = ref()
const logView = ref()

watch(view, async v => {
  localStorage.setItem('monitor-job-view', v)
  await nextTick()
  const el = (v === 'table' ? tableView : logView).value?.$el
  if (!el) return
  const items = el.querySelectorAll('.reveal')
  if (items.length) gsap.fromTo(items, { opacity: 0, y: 12 }, { opacity: 1, y: 0, duration: 0.35, ease: EASE.reveal, stagger: 0.05, clearProps: 'transform' })
})

/** 容器挂载后补 entrance-ready（reveal 依赖该标记显示） */
const hostEl = ref<HTMLElement | null>(null)
onMounted(() => {
  if (hostEl.value) hostEl.value.dataset.entranceReady = 'true'
})

/* ---------------- 任务列表 ---------------- */

interface JobQuery extends Record<string, unknown> { jobName: string }
const jobList = useListPage<SysJobRow, JobQuery>({
  query: { jobName: '' },
  fetch: params => systemApi.jobs.list(params) as Promise<any>,
})

const JOB_COLUMNS: Column[] = [
  { key: 'jobId', title: 'ID', width: 60 },
  { key: 'jobName', title: '任务名称' },
  { key: 'invokeTarget', title: '调用目标' },
  { key: 'cronExpression', title: 'cron 表达式', width: 150 },
  { key: 'status', title: '状态', width: 90 },
  { key: 'createTime', title: '创建时间', width: 168 },
  { key: 'actions', title: '', width: 230, className: 'col-actions' },
]

async function toggleStatus(row: SysJobRow) {
  const next = row.status === '0' ? '1' : '0'
  const ok = await confirm({
    title: next === '1' ? '暂停任务' : '启动任务',
    message: `确认${next === '1' ? '暂停' : '启动'}「${row.jobName}」吗？`,
    confirmText: next === '1' ? '暂停' : '启动',
  })
  if (!ok) return
  try {
    await systemApi.jobs.changeStatus(row.jobId, next)
    pushToast(next === '1' ? '任务已暂停' : '任务已启动')
    await jobList.load()
  } catch (error) {
    toastError(error, '状态切换失败')
  }
}

async function runOnce(row: SysJobRow) {
  const ok = await confirm({ title: '立即执行', message: `立即执行一次「${row.jobName}」？`, confirmText: '执行' })
  if (!ok) return
  try {
    await systemApi.jobs.run(row.jobId)
    pushToast('已触发执行，结果见执行日志')
  } catch (error) {
    toastError(error, '触发失败')
  }
}

async function removeJobs(ids: (string | number)[]) {
  const ok = await confirm({ title: '删除任务', message: `确认删除选中的 ${ids.length} 个任务吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await systemApi.jobs.remove(ids.join(','))
    pushToast(`已删除 ${ids.length} 个任务`)
    await jobList.load()
  } catch (error) {
    toastError(error, '删除失败')
  }
}

/* ---------------- 执行日志 ---------------- */

interface LogQuery extends Record<string, unknown> { jobName: string; status: string }
const logList = useListPage<SysJobLogRow, LogQuery>({
  query: { jobName: '', status: '' },
  fetch: params => systemApi.jobs.logs.list(params) as Promise<any>,
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

/* ---------------- 新增 / 编辑表单 ---------------- */

const drawerOpen = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<SysJobPayload>({
  jobName: '',
  jobGroup: 'DEFAULT',
  invokeTarget: '',
  cronExpression: '',
  misfirePolicy: '3',
  concurrent: '1',
  remark: '',
})
const cronState = ref<'idle' | 'ok' | 'bad'>('idle')

const MISFIRE_OPTIONS = [
  { label: '默认（放弃错过的执行）', value: '3' },
  { label: '立即触发执行', value: '1' },
  { label: '触发一次执行', value: '2' },
  { label: '不触发立即执行', value: '0' },
]
const CONCURRENT_OPTIONS = [
  { label: '禁止并发', value: '1' },
  { label: '允许并发', value: '0' },
]

function openCreate() {
  editingId.value = null
  Object.assign(form, { jobName: '', jobGroup: 'DEFAULT', invokeTarget: '', cronExpression: '', misfirePolicy: '3', concurrent: '1', remark: '' })
  cronState.value = 'idle'
  drawerOpen.value = true
}

function openEdit(row: SysJobRow) {
  editingId.value = row.jobId
  Object.assign(form, {
    jobId: row.jobId,
    jobName: row.jobName,
    jobGroup: row.jobGroup || 'DEFAULT',
    invokeTarget: row.invokeTarget,
    cronExpression: row.cronExpression,
    misfirePolicy: row.misfirePolicy || '3',
    concurrent: row.concurrent || '1',
    remark: row.remark || '',
  })
  cronState.value = 'idle'
  drawerOpen.value = true
}

async function checkCron() {
  if (!form.cronExpression) return
  try {
    const ok = await systemApi.jobs.cronCheck(form.cronExpression)
    cronState.value = ok ? 'ok' : 'bad'
  } catch {
    cronState.value = 'bad'
  }
}

async function saveJob() {
  if (!form.jobName || !form.invokeTarget || !form.cronExpression) {
    pushToast('任务名称、调用目标、cron 表达式均必填')
    return
  }
  try {
    if (editingId.value) {
      await systemApi.jobs.edit({ ...form, jobId: editingId.value })
      pushToast('任务已更新')
    } else {
      await systemApi.jobs.add({ ...form })
      pushToast('任务已创建（默认暂停，可在列表启动）')
    }
    drawerOpen.value = false
    await jobList.load()
  } catch (error) {
    toastError(error, '保存失败')
  }
}
</script>

<template>
  <div ref="hostEl" class="page-host" data-entrance-ready="true">
    <ListPage v-show="view === 'table'" ref="tableView" title="定时任务" desc="基于 Quartz 的任务调度管理，支持 cron 表达式、暂停/恢复与立即执行。" table-title="任务列表" :list="jobList" :columns="JOB_COLUMNS" fit-viewport :min-width="980">
      <template #actions>
        <Segmented v-model="view" label="视图" :options="VIEWS" />
        <PermissionButton perm="monitor:job:add" variant="primary" icon="plus" @click="openCreate">新增任务</PermissionButton>
      </template>
      <template #search>
        <Field label="任务名称" wide>
          <TextInput v-model="jobList.query.jobName" icon="search" placeholder="按任务名称模糊搜索" clearable @enter="jobList.search()" />
        </Field>
      </template>
      <template #batch="{ ids }">
        <PermissionButton perm="monitor:job:remove" variant="text" size="sm" danger @click="removeJobs(ids)">删除</PermissionButton>
      </template>

      <template #cell-jobName="{ row }">
        <div>
          <div class="cell-title">{{ row.jobName }}</div>
          <div v-if="row.remark" class="cell-sub">{{ row.remark }}</div>
        </div>
      </template>
      <template #cell-invokeTarget="{ row }">
        <span class="mono ellipsis" style="display: inline-block; max-width: 240px">{{ row.invokeTarget }}</span>
      </template>
      <template #cell-cronExpression="{ row }">
        <span class="mono tnum" style="font-size: 12.5px">{{ row.cronExpression }}</span>
      </template>
      <template #cell-status="{ row }">
        <Tag :tone="row.status === '0' ? 'ok' : 'neutral'" dot>{{ row.status === '0' ? '正常' : '暂停' }}</Tag>
      </template>
      <template #cell-actions="{ row }">
        <div class="row-actions">
          <PermissionButton v-if="row.status === '0'" perm="monitor:job:run" variant="text" size="sm" @click="runOnce(row)">执行</PermissionButton>
          <PermissionButton v-else perm="monitor:job:changeStatus" variant="text" size="sm" @click="toggleStatus(row)">启动</PermissionButton>
          <PermissionButton v-if="row.status === '0'" perm="monitor:job:changeStatus" variant="text" size="sm" @click="toggleStatus(row)">暂停</PermissionButton>
          <PermissionButton perm="monitor:job:edit" variant="text" size="sm" @click="openEdit(row)">编辑</PermissionButton>
          <PermissionButton perm="monitor:job:remove" variant="text" size="sm" danger @click="removeJobs([row.jobId])">删除</PermissionButton>
        </div>
      </template>
    </ListPage>

    <ListPage v-show="view === 'logs'" ref="logView" title="执行日志" desc="定时任务的每次触发都会在此留痕：成功与失败、异常摘要与起止时间。" table-title="执行记录" :list="logList" :columns="LOG_COLUMNS" fit-viewport :min-width="980">
      <template #actions>
        <Segmented v-model="view" label="视图" :options="VIEWS" />
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

    <Drawer :open="drawerOpen" :title="editingId ? '编辑定时任务' : '新增定时任务'" :sub="editingId ? `任务 ID ${editingId}` : '创建后默认为暂停状态'" :width="520" @close="drawerOpen = false">
      <div class="form-stack">
        <Field label="任务名称" required>
          <TextInput v-model="form.jobName" placeholder="如：AI对话清理（90天前）" />
        </Field>
        <Field label="调用目标" required>
          <TextInput v-model="form.invokeTarget" placeholder="jobTasks.aiChatClean" />
          <div class="field-hint">格式：bean名.方法名(参数)，仅允许白名单包内的入口</div>
        </Field>
        <Field label="cron 表达式" required>
          <div style="display: flex; gap: 8px; align-items: center">
            <TextInput v-model="form.cronExpression" placeholder="0 0 2 * * ?" style="flex: 1" />
            <button type="button" class="drawer-btn-check" @click="checkCron">校验</button>
          </div>
          <div class="field-hint">
            <Tag v-if="cronState === 'ok'" tone="ok" dot>表达式有效</Tag>
            <Tag v-else-if="cronState === 'bad'" tone="danger" dot>表达式无效</Tag>
            <span v-else class="soft">每天 02:00 示例：0 0 2 * * ?</span>
          </div>
        </Field>
        <Field label="misfire 策略">
          <Select v-model="form.misfirePolicy" :options="MISFIRE_OPTIONS" />
        </Field>
        <Field label="并发执行">
          <Select v-model="form.concurrent" :options="CONCURRENT_OPTIONS" />
        </Field>
        <Field label="备注">
          <TextInput v-model="form.remark" placeholder="选填" />
        </Field>
      </div>
      <template #footer>
        <div style="display: flex; justify-content: flex-end; gap: 10px">
          <button type="button" class="drawer-btn-ghost" @click="drawerOpen = false">取消</button>
          <button type="button" class="drawer-btn-primary" @click="saveJob">保存</button>
        </div>
      </template>
    </Drawer>
  </div>
</template>

<style scoped>
.page-host {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.page-host > .list-page {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.form-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.field-hint {
  margin-top: 5px;
  font-size: 12px;
  color: var(--text-sub, #6e7480);
}
.drawer-btn-check {
  border: 1px solid var(--hairline, rgba(0, 0, 0, 0.1));
  background: var(--card-bg, #fff);
  border-radius: 8px;
  padding: 8px 14px;
  font: inherit;
  font-size: 12.5px;
  cursor: pointer;
}
.drawer-btn-ghost,
.drawer-btn-primary {
  border-radius: 9px;
  padding: 8px 18px;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.drawer-btn-ghost {
  border: 1px solid var(--hairline, rgba(0, 0, 0, 0.1));
  background: none;
  color: var(--text, #1a1c20);
}
.drawer-btn-primary {
  border: 0;
  background: rgb(65, 118, 230);
  color: #fff;
}
.row-actions {
  display: inline-flex;
  gap: 4px;
}
</style>
