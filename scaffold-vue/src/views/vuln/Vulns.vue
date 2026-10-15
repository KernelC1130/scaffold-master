<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { Drawer, Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@/components/business'
import DictTag from '@/components/business/DictTag.vue'
import { vulnApi, type AssetRow, type TaskRow, type VulnRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { pattern, required, validate } from '@/utils/validate'

const list = useListPage<VulnRow, { vulnTitle: string; cveNo: string; riskLevel: string; vulnStatus: string }>({
  query: { vulnTitle: '', cveNo: '', riskLevel: '', vulnStatus: '' },
  fetch: params => vulnApi.vulnerabilities.list(params) as Promise<any>,
  exporter: params => vulnApi.vulnerabilities.export(params),
  exportName: '漏洞列表.csv',
})

const columns: Column[] = [
  { key: 'vulnTitle', title: '漏洞标题', minWidth: 200 },
  { key: 'cveNo', title: 'CVE 编号', width: 140 },
  { key: 'riskLevel', title: '风险等级', width: 96 },
  { key: 'vulnStatus', title: '漏洞状态', width: 96 },
  { key: 'assetName', title: '所属资产', minWidth: 160 },
  { key: 'foundTime', title: '发现日期', width: 110 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

/* 资产下拉：表单选择所属资产 */
const assetOptions = ref<Array<{ label: string; value: number }>>([])
onMounted(async () => {
  try {
    const page = await vulnApi.assets.list({ pageNum: 1, pageSize: 200 })
    assetOptions.value = (page.rows as unknown as AssetRow[]).map(a => ({ label: `${a.assetName}（${a.assetIp}）`, value: a.id }))
  } catch {
    assetOptions.value = []
  }
})
const assetLabelOf = (id: number) => assetOptions.value.find(o => o.value === id)?.label

/* ── 详情抽屉：漏洞信息 + 关联工单子表（三级穿透第二级，工单子表即第三级入口） ── */
const detail = ref<VulnRow | null>(null)
const detailTasks = ref<TaskRow[]>([])
const detailLoading = ref(false)
watch(detail, async (row) => {
  detailTasks.value = []
  if (!row) return
  detailLoading.value = true
  try {
    const page = await vulnApi.repairTasks.list({ vulnId: row.id, pageNum: 1, pageSize: 100 })
    detailTasks.value = page.rows as unknown as TaskRow[]
  } catch {
    detailTasks.value = []
  } finally {
    detailLoading.value = false
  }
})

interface VulnForm { id?: number; assetId: number | null; vulnTitle: string; cveNo: string; riskLevel: string; foundTime: string; remark: string }
const emptyForm = (): VulnForm => ({ assetId: null, vulnTitle: '', cveNo: '', riskLevel: '0', foundTime: new Date().toISOString().slice(0, 10), remark: '' })
const dialog = ref(false)
const saving = ref(false)
const form = reactive<VulnForm>(emptyForm())
const errors = ref<Partial<Record<keyof VulnForm, string>>>({})

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: VulnRow) {
  Object.assign(form, {
    id: row.id, assetId: row.assetId, vulnTitle: row.vulnTitle, cveNo: row.cveNo ?? '',
    riskLevel: row.riskLevel, foundTime: row.foundTime ?? '', remark: row.remark ?? '',
  })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    assetId: [required('请选择所属资产')],
    vulnTitle: [required('请输入漏洞标题')],
    cveNo: [pattern(/^$|^CVE-\d{4}-\d{4,}$/, '格式如 CVE-2024-1234，可留空')],
    foundTime: [required('请选择发现日期')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = {
    assetId: form.assetId!, vulnTitle: form.vulnTitle.trim(), cveNo: form.cveNo.trim(),
    riskLevel: form.riskLevel, foundTime: form.foundTime, remark: form.remark,
  }
  try {
    if (form.id) {
      await vulnApi.vulnerabilities.update(form.id, payload)
      pushToast('漏洞已更新')
    } else {
      await vulnApi.vulnerabilities.create(payload)
      pushToast('漏洞已新增')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '漏洞保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: VulnRow) {
  const ok = await confirm({ title: '删除漏洞', message: `确认删除漏洞「${row.vulnTitle}」吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await vulnApi.vulnerabilities.remove(row.id)
    pushToast('漏洞已删除')
    await list.load()
  } catch (error) {
    toastError(error, '漏洞删除失败')
  }
}

async function batchRemove(ids: (string | number)[]) {
  const ok = await confirm({ title: '批量删除', message: `确认删除选中的 ${ids.length} 个漏洞吗？（存在工单的漏洞会被拒绝）`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await Promise.all(ids.map(id => vulnApi.vulnerabilities.remove(Number(id))))
    pushToast(`已删除 ${ids.length} 个漏洞`)
    await list.load()
  } catch (error) {
    toastError(error, '批量删除失败')
  }
}
</script>

<template>
  <ListPage title="漏洞管理" desc="登记与跟踪安全漏洞，漏洞状态由修复工单自动计算，资产风险随漏洞联动。" table-title="漏洞列表" :list="list" :columns="columns" selectable fit-viewport>
    <template #actions>
      <PermissionButton perm="vuln:vulnerability:add" variant="primary" icon="plus" @click="openCreate">新增漏洞</PermissionButton>
    </template>

    <template #search>
      <Field label="漏洞标题" wide>
        <TextInput v-model="list.query.vulnTitle" icon="search" placeholder="漏洞标题，模糊匹配" clearable @enter="list.search()" />
      </Field>
      <Field label="CVE 编号">
        <TextInput v-model="list.query.cveNo" icon="search" placeholder="CVE 编号" clearable @enter="list.search()" />
      </Field>
      <Field label="风险等级">
        <DictSelect v-model="list.query.riskLevel" type="vuln_risk_level" all-label="全部" />
      </Field>
      <Field label="漏洞状态">
        <DictSelect v-model="list.query.vulnStatus" type="vuln_status" all-label="全部状态" />
      </Field>
    </template>

    <template #batch="{ ids }">
      <PermissionButton perm="vuln:vulnerability:remove" variant="text" size="sm" danger @click="batchRemove(ids)">删除</PermissionButton>
    </template>

    <template #cell-vulnTitle="{ row }"><span class="cell-title">{{ row.vulnTitle }}</span></template>
    <template #cell-cveNo="{ row }"><span class="code-chip">{{ row.cveNo || '—' }}</span></template>
    <template #cell-riskLevel="{ row }"><DictTag type="vuln_risk_level" :value="row.riskLevel" /></template>
    <template #cell-vulnStatus="{ row }"><DictTag type="vuln_status" :value="row.vulnStatus" /></template>
    <template #cell-assetName="{ row }"><span class="muted">{{ assetLabelOf(row.assetId) ?? row.assetName }}</span></template>
    <template #cell-foundTime="{ row }"><span class="tnum soft">{{ row.foundTime }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="vuln:vulnerability:list" icon-only icon="eye" label="查看详情" @click="detail = row" />
        <PermissionButton perm="vuln:vulnerability:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="vuln:vulnerability:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" :title="form.id ? '编辑漏洞' : '新增漏洞'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="所属资产" required :error="errors.assetId" span>
        <Select v-model="form.assetId" :options="assetOptions" placeholder="选择资产" filterable :invalid="!!errors.assetId" />
      </Field>
      <Field label="漏洞标题" required :error="errors.vulnTitle" span>
        <TextInput v-model="form.vulnTitle" placeholder="如：Struts2 远程代码执行" :invalid="!!errors.vulnTitle" />
      </Field>
      <Field label="CVE 编号" :error="errors.cveNo">
        <TextInput v-model="form.cveNo" placeholder="CVE-2024-1234，可留空" :invalid="!!errors.cveNo" />
      </Field>
      <Field label="风险等级">
        <DictSelect v-model="form.riskLevel" type="vuln_risk_level" />
      </Field>
      <Field label="发现日期" required :error="errors.foundTime">
        <TextInput v-model="form.foundTime" type="date" :invalid="!!errors.foundTime" />
      </Field>
      <Field label="备注" span>
        <TextInput v-model="form.remark" multiline :rows="2" placeholder="选填" :maxlength="500" />
      </Field>
    </FormDialog>

    <Drawer :open="Boolean(detail)" origin-from="tr" title="漏洞详情" :sub="detail ? detail.vulnTitle : ''" :width="600" @close="detail = null">
      <template v-if="detail">
        <div class="drawer-stack">
          <div class="kv-grid">
            <div class="kv"><div class="kv-label">风险等级</div><div class="kv-value"><DictTag type="vuln_risk_level" :value="detail.riskLevel" /></div></div>
            <div class="kv"><div class="kv-label">漏洞状态</div><div class="kv-value"><DictTag type="vuln_status" :value="detail.vulnStatus" /><span class="soft"> 由工单自动计算</span></div></div>
            <div class="kv"><div class="kv-label">CVE 编号</div><div class="kv-value mono">{{ detail.cveNo || '—' }}</div></div>
            <div class="kv"><div class="kv-label">发现日期</div><div class="kv-value tnum">{{ detail.foundTime || '—' }}</div></div>
            <div class="kv span-2"><div class="kv-label">所属资产</div><div class="kv-value">{{ detail.assetName }}<span v-if="detail.assetIp" class="soft tnum"> · {{ detail.assetIp }}</span></div></div>
            <div v-if="detail.remark" class="kv span-2"><div class="kv-label">备注</div><div class="kv-value">{{ detail.remark }}</div></div>
          </div>
          <div class="section-label" style="margin: 20px 0 10px">修复工单（{{ detailTasks.length }}）</div>
          <div v-if="detailLoading" class="soft">加载中…</div>
          <div v-else-if="!detailTasks.length" class="soft">暂无修复工单，可在「修复工单」页新建并关联此漏洞</div>
          <div v-else class="drawer-stack" style="display: flex; flex-direction: column; gap: 10px">
            <div v-for="t in detailTasks" :key="t.id" class="kv-grid">
              <div class="kv span-2"><div class="kv-value">
                <DictTag type="vuln_task_status" :value="t.taskStatus" />
                <DictTag type="vuln_task_priority" :value="t.priority" />
                <span class="soft tnum">{{ t.planTime ? `计划 ${t.planTime}` : '' }}</span>
              </div></div>
              <div class="kv"><div class="kv-label">负责人</div><div class="kv-value">{{ t.handlerName || '—' }}</div></div>
              <div class="kv"><div class="kv-label">验证人</div><div class="kv-value">{{ t.verifierName || '—' }}</div></div>
              <div v-if="t.progressDesc" class="kv span-2"><div class="kv-label">进度说明</div><div class="kv-value">{{ t.progressDesc }}</div></div>
              <div v-if="t.verifyRemark" class="kv span-2"><div class="kv-label">验证意见</div><div class="kv-value"><Tag :tone="t.taskStatus === '3' ? 'ok' : 'info'" dot>{{ t.verifyRemark }}</Tag></div></div>
            </div>
          </div>
        </div>
      </template>
    </Drawer>
  </ListPage>
</template>
