<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import DataTable, { type Column } from '@/components/ui/DataTable.vue'
import Pagination from '@/components/ui/Pagination.vue'
import Panel from '@/components/ui/Panel.vue'
import { Button, Drawer, Field, IconButton, Segmented, Select, Tag, TextInput } from '@/components/ui'
import { CronBuilder, ListPage, PermissionButton, SearchForm } from '@/components/business'
import { systemApi, type SysJobRow, type SysJobLogRow, type SysJobPayload } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'

/** 视图：table = 任务列表（默认表格视图）；logs = 执行日志（alt 视图，轻量切换无全页动画） */
const view = ref<'table' | 'logs'>((localStorage.getItem('monitor-job-view') as 'table' | 'logs') || 'table')

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

const VIEWS = [
  { label: '任务', value: 'table' },
  { label: '执行日志', value: 'logs' },
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

/* ---------------- 执行日志（alt 视图） ---------------- */

interface LogQuery extends Record<string, unknown> { jobName: string; status: string }
const logList = useListPage<SysJobLogRow, LogQuery>({
  query: { jobName: '', status: '' },
  fetch: params => systemApi.jobs.logs.list(params) as Promise<any>,
})

watch(view, v => localStorage.setItem('monitor-job-view', v))

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

const cronOpen = ref(false)
function applyCron(value: string) {
  form.cronExpression = value
  cronOpen.value = false
  checkCron()
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
  <ListPage title="定时任务" desc="基于 Quartz 的任务调度管理，支持 cron 表达式、暂停/恢复与立即执行。" table-title="任务列表" :list="jobList" :columns="JOB_COLUMNS" fit-viewport alt-fit :alt-view="view === 'logs'" :min-width="980">
    <template #actions>
      <Segmented v-model="view" label="视图" :options="VIEWS" />
      <PermissionButton v-if="view === 'table'" perm="monitor:job:add" variant="primary" icon="plus" @click="openCreate">新增任务</PermissionButton>
      <PermissionButton v-else perm="monitor:job:remove" variant="secondary" icon="trash" danger @click="clearLogs">清空日志</PermissionButton>
    </template>
    <template #search>
      <template v-if="view === 'table'">
        <Field label="任务名称" wide>
          <TextInput v-model="jobList.query.jobName" icon="search" placeholder="按任务名称模糊搜索" clearable @enter="jobList.search()" />
        </Field>
      </template>
      <template v-else>
        <Field label="任务名称" wide>
          <TextInput v-model="logList.query.jobName" icon="search" placeholder="按任务名称模糊搜索" clearable @enter="logList.search()" />
        </Field>
        <Field label="结果">
          <Select v-model="logList.query.status" :options="LOG_STATUS_OPTIONS" />
        </Field>
      </template>
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

    <template #alt>
      <div class="logs-view">
        <SearchForm :loading="logList.loading.value" @search="logList.search()" @reset="logList.reset()">
          <Field label="任务名称" wide>
            <TextInput v-model="logList.query.jobName" icon="search" placeholder="按任务名称模糊搜索" clearable @enter="logList.search()" />
          </Field>
          <Field label="结果">
            <Select v-model="logList.query.status" :options="LOG_STATUS_OPTIONS" />
          </Field>
        </SearchForm>
        <Panel class="list-page-panel" title="执行记录" :meta="`共 ${logList.total.value} 条`">
          <template #tools>
            <IconButton name="refresh" label="刷新" bordered @click="logList.load()" />
          </template>
          <DataTable :columns="LOG_COLUMNS" :data="logList.rows.value" :loading="logList.loading.value" :min-width="900" row-key="jobLogId">
            <template #cell-jobName="{ row }"><span class="cell-title">{{ row.jobName }}</span></template>
            <template #cell-status="{ row }"><Tag :tone="row.status === '0' ? 'ok' : 'danger'" dot>{{ row.status === '0' ? '成功' : '失败' }}</Tag></template>
            <template #cell-jobMessage="{ row }">
              <span class="ellipsis" style="display: inline-block; max-width: 300px">{{ row.jobMessage }}</span>
              <div v-if="row.exceptionInfo" class="cell-sub" style="color: var(--danger, #d33)">{{ row.exceptionInfo }}</div>
            </template>
          </DataTable>
          <Pagination
            :total="logList.total.value"
            :page="logList.page.value"
            :page-size="logList.pageSize.value"
            @update:page="logList.setPage($event)"
            @update:page-size="logList.setPageSize($event)"
          />
        </Panel>
      </div>
    </template>
  <Drawer :open="drawerOpen" :title="editingId ? '编辑定时任务' : '新增定时任务'" :sub="editingId ? `任务 ID ${editingId}` : '创建后默认为暂停状态'" :width="520" @close="drawerOpen = false">
    <div class="form-stack">
      <Field label="任务名称" required>
        <TextInput v-model="form.jobName" placeholder="如：AI对话清理（90天前）" />
      </Field>
      <Field label="调用目标" required>
        <TextInput v-model="form.invokeTarget" placeholder="sampleTask.heartbeat" />
        <div class="field-hint">格式：bean名.方法名(参数)，仅允许白名单包内的入口</div>
      </Field>
      <Field label="cron 表达式" required>
        <div style="display: flex; gap: 8px; align-items: center">
          <TextInput v-model="form.cronExpression" placeholder="0 0 2 * * ?" style="flex: 1" />
          <Button variant="secondary" size="sm" @click="cronOpen = true">向导</Button>
          <Button variant="secondary" size="sm" @click="checkCron">校验</Button>
        </div>
        <!-- 固定高度的提示行：校验结果（Tag）与示例文字同高，切换时不顶开下方内容 -->
        <div class="field-hint cron-hint">
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
      <div style="display: flex; justify-content: flex-end; gap: 8px">
        <Button variant="ghost" @click="drawerOpen = false">取消</Button>
        <Button variant="primary" @click="saveJob">保存</Button>
      </div>
    </template>
  </Drawer>
  <CronBuilder :open="cronOpen" :value="form.cronExpression" @close="cronOpen = false" @confirm="applyCron" />
  </ListPage>
</template>

<style scoped>
.logs-view {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.logs-view > .list-page-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.logs-view > .list-page-panel > .table-wrap {
  flex: 1;
  overscroll-behavior: contain;
}
.form-stack {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.row-actions {
  display: inline-flex;
  gap: 4px;
}
/* 与 Tag 同高（22px）：示例文字和校验结果占用同一行高，状态切换不引起布局跳动 */
.cron-hint {
  min-height: 22px;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
}
</style>
