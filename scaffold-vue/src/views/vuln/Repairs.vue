<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Field, Segmented, Select, TextInput, type Column } from '@/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton, UserSelect } from '@/components/business'
import DictTag from '@/components/business/DictTag.vue'
import { vulnApi, type TaskRow, type VulnRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { required, validate } from '@/utils/validate'

const list = useListPage<TaskRow, { vulnTitle: string; taskStatus: string; priority: string }>({
  query: { vulnTitle: '', taskStatus: '', priority: '' },
  fetch: params => vulnApi.repairTasks.list(params) as Promise<any>,
  exporter: params => vulnApi.repairTasks.export(params),
  exportName: '修复工单.csv',
})

const columns: Column[] = [
  { key: 'vulnTitle', title: '漏洞标题', minWidth: 190 },
  { key: 'assetName', title: '所属资产', minWidth: 140 },
  { key: 'vulnRiskLevel', title: '风险', width: 80 },
  { key: 'taskStatus', title: '工单状态', width: 92 },
  { key: 'priority', title: '优先级', width: 84 },
  { key: 'handlerName', title: '负责人', width: 96 },
  { key: 'planTime', title: '计划完成', width: 106 },
  { key: 'verifierName', title: '验证人', width: 96 },
  { key: 'actions', title: '操作', width: 128, className: 'col-actions' },
]

/* 漏洞下拉：新建/编辑工单选择关联漏洞 */
const vulnOptions = ref<Array<{ label: string; value: number }>>([])
onMounted(async () => {
  try {
    const page = await vulnApi.vulnerabilities.list({ pageNum: 1, pageSize: 200 })
    vulnOptions.value = (page.rows as unknown as VulnRow[]).map(v => ({ label: v.vulnTitle, value: v.id }))
  } catch {
    vulnOptions.value = []
  }
})

interface TaskForm { id?: number; vulnId: number | null; handlerId: number | null; priority: string; progressDesc: string; planTime: string; remark: string }
const emptyForm = (): TaskForm => ({ vulnId: null, handlerId: null, priority: '1', progressDesc: '', planTime: '', remark: '' })
const dialog = ref(false)
const saving = ref(false)
const form = reactive<TaskForm>(emptyForm())
const errors = ref<Partial<Record<keyof TaskForm, string>>>({})

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: TaskRow) {
  Object.assign(form, {
    id: row.id, vulnId: row.vulnId, handlerId: row.handlerId, priority: row.priority,
    progressDesc: row.progressDesc ?? '', planTime: row.planTime ?? '', remark: row.remark ?? '',
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    vulnId: [required('请选择关联漏洞')],
    handlerId: [required('请选择负责人')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = {
    vulnId: form.vulnId!, handlerId: form.handlerId!, priority: form.priority,
    progressDesc: form.progressDesc, planTime: form.planTime || undefined, remark: form.remark,
  }
  try {
    if (form.id) {
      await vulnApi.repairTasks.update(form.id, payload)
      pushToast('工单已更新')
    } else {
      await vulnApi.repairTasks.create(payload)
      pushToast('工单已创建')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '工单保存失败')
  } finally {
    saving.value = false
  }
}

/* ── 状态机动作：接单 0→1 / 提交修复 1→2 / 复测 2→{3|1} / 关闭 3→4 ── */
async function accept(row: TaskRow) {
  const ok = await confirm({ title: '接单', message: `确认接下「${row.vulnTitle}」的修复工单吗？`, confirmText: '接单' })
  if (!ok) return
  try {
    await vulnApi.repairTasks.accept(row.id)
    pushToast('已接单')
    await list.load()
  } catch (error) {
    toastError(error, '接单失败')
  }
}

async function close(row: TaskRow) {
  const ok = await confirm({ title: '关闭工单', message: `确认关闭「${row.vulnTitle}」的工单吗？关闭后流程结束。`, confirmText: '关闭', danger: true })
  if (!ok) return
  try {
    await vulnApi.repairTasks.close(row.id)
    pushToast('工单已关闭')
    await list.load()
  } catch (error) {
    toastError(error, '关闭失败')
  }
}

interface ActionForm { id: number; title: string; finishTime: string; progressDesc: string; verifyRemark: string }
const actionDialog = ref(false)
const actionSaving = ref(false)
const actionMode = ref<'fix' | 'verify'>('fix')
const actionPass = ref<'Y' | 'N'>('Y')
const action = reactive<ActionForm>({ id: 0, title: '', finishTime: '', progressDesc: '', verifyRemark: '' })
const actionErrors = ref<Partial<Record<'finishTime' | 'verifyRemark', string>>>({})

function openFix(row: TaskRow) {
  actionMode.value = 'fix'
  Object.assign(action, { id: row.id, title: row.vulnTitle, finishTime: new Date().toISOString().slice(0, 16), progressDesc: row.progressDesc ?? '', verifyRemark: '' })
  actionErrors.value = {}
  actionDialog.value = true
}

function openVerify(row: TaskRow) {
  actionMode.value = 'verify'
  actionPass.value = 'Y'
  Object.assign(action, { id: row.id, title: row.vulnTitle, finishTime: new Date().toISOString().slice(0, 16), progressDesc: '', verifyRemark: '' })
  actionErrors.value = {}
  actionDialog.value = true
}

async function submitAction() {
  if (actionMode.value === 'fix') {
    actionErrors.value = { finishTime: action.finishTime ? '' : '请选择实际完成时间' }
    if (actionErrors.value.finishTime) return
    actionSaving.value = true
    try {
      await vulnApi.repairTasks.fix(action.id, { finishTime: action.finishTime, progressDesc: action.progressDesc })
      pushToast('已提交修复，等待复测')
      actionDialog.value = false
      await list.load()
    } catch (error) {
      toastError(error, '提交修复失败')
    } finally {
      actionSaving.value = false
    }
    return
  }
  actionErrors.value = { verifyRemark: action.verifyRemark.trim() ? '' : '请填写验证意见' }
  if (actionErrors.value.verifyRemark) return
  actionSaving.value = true
  try {
    await vulnApi.repairTasks.verify(action.id, { pass: actionPass.value === 'Y', verifyRemark: action.verifyRemark.trim(), finishTime: action.finishTime })
    pushToast(actionPass.value === 'Y' ? '复测通过' : '已打回，工单回到处理中')
    actionDialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '复测提交失败')
  } finally {
    actionSaving.value = false
  }
}

async function remove(row: TaskRow) {
  const ok = await confirm({ title: '删除工单', message: `确认删除「${row.vulnTitle}」的工单吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await vulnApi.repairTasks.remove(row.id)
    pushToast('工单已删除')
    await list.load()
  } catch (error) {
    toastError(error, '工单删除失败')
  }
}

async function batchRemove(ids: (string | number)[]) {
  const ok = await confirm({ title: '批量删除', message: `确认删除选中的 ${ids.length} 个工单吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await Promise.all(ids.map(id => vulnApi.repairTasks.remove(Number(id))))
    pushToast(`已删除 ${ids.length} 个工单`)
    await list.load()
  } catch (error) {
    toastError(error, '批量删除失败')
  }
}
</script>

<template>
  <ListPage title="修复工单" desc="漏洞修复流转：接单 → 提交修复 → 复测（通过/打回）→ 关闭，漏洞状态随之自动计算。" table-title="工单列表" :list="list" :columns="columns" selectable fit-viewport>
    <template #actions>
      <PermissionButton perm="vuln:repair:add" variant="primary" icon="plus" @click="openCreate">新建工单</PermissionButton>
    </template>

    <template #search>
      <Field label="漏洞标题" wide>
        <TextInput v-model="list.query.vulnTitle" icon="search" placeholder="漏洞标题，模糊匹配" clearable @enter="list.search()" />
      </Field>
      <Field label="工单状态">
        <DictSelect v-model="list.query.taskStatus" type="vuln_task_status" all-label="全部状态" />
      </Field>
      <Field label="优先级">
        <DictSelect v-model="list.query.priority" type="vuln_task_priority" all-label="全部" />
      </Field>
    </template>

    <template #batch="{ ids }">
      <PermissionButton perm="vuln:repair:remove" variant="text" size="sm" danger @click="batchRemove(ids)">删除</PermissionButton>
    </template>

    <template #cell-vulnTitle="{ row }"><span class="cell-title">{{ row.vulnTitle }}</span></template>
    <template #cell-assetName="{ row }"><span class="muted">{{ row.assetName }}<template v-if="row.assetIp"> · {{ row.assetIp }}</template></span></template>
    <template #cell-vulnRiskLevel="{ row }"><DictTag type="vuln_risk_level" :value="row.vulnRiskLevel" /></template>
    <template #cell-taskStatus="{ row }"><DictTag type="vuln_task_status" :value="row.taskStatus" /></template>
    <template #cell-priority="{ row }"><DictTag type="vuln_task_priority" :value="row.priority" /></template>
    <template #cell-handlerName="{ row }"><span>{{ row.handlerName || '—' }}</span></template>
    <template #cell-planTime="{ row }"><span class="tnum soft">{{ row.planTime || '—' }}</span></template>
    <template #cell-verifierName="{ row }"><span>{{ row.verifierName || '—' }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton v-if="row.taskStatus === '0'" perm="vuln:repair:edit" variant="text" size="sm" @click="accept(row)">接单</PermissionButton>
        <PermissionButton v-else-if="row.taskStatus === '1'" perm="vuln:repair:edit" variant="text" size="sm" @click="openFix(row)">修复</PermissionButton>
        <PermissionButton v-else-if="row.taskStatus === '2'" perm="vuln:repair:edit" variant="text" size="sm" @click="openVerify(row)">复测</PermissionButton>
        <PermissionButton v-else-if="row.taskStatus === '3'" perm="vuln:repair:edit" variant="text" size="sm" @click="close(row)">关闭</PermissionButton>
        <PermissionButton perm="vuln:repair:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="vuln:repair:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" :title="form.id ? '编辑工单' : '新建工单'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="关联漏洞" required :error="errors.vulnId" span>
        <Select v-model="form.vulnId" :options="vulnOptions" placeholder="选择漏洞" filterable :invalid="!!errors.vulnId" />
      </Field>
      <Field label="负责人" required :error="errors.handlerId">
        <UserSelect v-model="form.handlerId" value-key="id" placeholder="选择负责人" />
      </Field>
      <Field label="优先级">
        <DictSelect v-model="form.priority" type="vuln_task_priority" />
      </Field>
      <Field label="计划完成时间">
        <TextInput v-model="form.planTime" type="date" />
      </Field>
      <Field label="修复进度说明" span>
        <TextInput v-model="form.progressDesc" multiline :rows="2" placeholder="选填" :maxlength="500" />
      </Field>
      <Field label="备注" span>
        <TextInput v-model="form.remark" multiline :rows="2" placeholder="选填" :maxlength="500" />
      </Field>
    </FormDialog>

    <FormDialog :open="actionDialog" :title="actionMode === 'fix' ? '提交修复' : '漏洞复测'" :sub="action.title" :saving="actionSaving" :submit-text="actionMode === 'fix' ? '提交修复' : '提交复测'" @close="actionDialog = false" @submit="submitAction">
      <template v-if="actionMode === 'fix'">
        <Field label="实际完成时间" required :error="actionErrors.finishTime" span>
          <TextInput v-model="action.finishTime" type="datetime-local" :invalid="!!actionErrors.finishTime" />
        </Field>
        <Field label="修复进度说明" span>
          <TextInput v-model="action.progressDesc" multiline :rows="3" placeholder="本轮修复说明" :maxlength="500" />
        </Field>
      </template>
      <template v-else>
        <Field label="复测结论" group>
          <Segmented v-model="actionPass" :options="[{ label: '复测通过', value: 'Y' }, { label: '打回', value: 'N' }]" />
        </Field>
        <Field label="验证意见" required :error="actionErrors.verifyRemark" span>
          <TextInput v-model="action.verifyRemark" multiline :rows="3" :placeholder="actionPass === 'Y' ? '通过原因与复测说明' : '打回原因'" :maxlength="500" :invalid="!!actionErrors.verifyRemark" />
        </Field>
      </template>
    </FormDialog>
  </ListPage>
</template>
