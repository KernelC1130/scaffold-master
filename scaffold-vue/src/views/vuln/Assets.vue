<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Drawer, Field, TextInput, type Column } from '@/components/ui'
import { DictSelect, FormDialog, ListPage, PermissionButton } from '@/components/business'
import DictTag from '@/components/business/DictTag.vue'
import { vulnApi, type AssetRow, type VulnRow } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'
import { pattern, required, validate } from '@/utils/validate'

const list = useListPage<AssetRow, { assetIp: string; assetName: string; riskLevel: string; status: string }>({
  query: { assetIp: '', assetName: '', riskLevel: '', status: '' },
  fetch: params => vulnApi.assets.list(params) as Promise<any>,
  exporter: params => vulnApi.assets.export(params),
  exportName: '资产列表.csv',
})

const columns: Column[] = [
  { key: 'assetIp', title: 'IP 地址', minWidth: 130 },
  { key: 'assetName', title: '服务 / 应用名', minWidth: 180 },
  { key: 'owner', title: '负责人', width: 110 },
  { key: 'riskLevel', title: '风险等级', width: 96 },
  { key: 'status', title: '状态', width: 88 },
  { key: 'remark', title: '备注' },
  { key: 'createTime', title: '创建时间', width: 160 },
  { key: 'actions', title: '操作', width: 96, className: 'col-actions' },
]

interface AssetForm { id?: number; assetIp: string; assetName: string; owner: string; status: string; remark: string }
const emptyForm = (): AssetForm => ({ assetIp: '', assetName: '', owner: '', status: '0', remark: '' })
const dialog = ref(false)
const saving = ref(false)
const form = reactive<AssetForm>(emptyForm())
const errors = ref<Partial<Record<keyof AssetForm, string>>>({})

function openCreate() {
  Object.assign(form, emptyForm(), { id: undefined })
  errors.value = {}
  dialog.value = true
}

function openEdit(row: AssetRow) {
  Object.assign(form, { id: row.id, assetIp: row.assetIp, assetName: row.assetName, owner: row.owner ?? '', status: row.status, remark: row.remark ?? '' })
  errors.value = {}
  dialog.value = true
}

async function submit() {
  errors.value = validate(form, {
    assetIp: [required('请输入 IP 地址'), pattern(/^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/, '仅支持 IPv4 格式')],
    assetName: [required('请输入服务 / 应用名')],
  })
  if (Object.keys(errors.value).length) return
  saving.value = true
  const payload = { assetIp: form.assetIp.trim(), assetName: form.assetName.trim(), owner: form.owner.trim(), status: form.status, remark: form.remark }
  try {
    if (form.id) {
      await vulnApi.assets.update(form.id, payload)
      pushToast('资产已更新')
    } else {
      await vulnApi.assets.create(payload)
      pushToast('资产已新增')
    }
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '资产保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: AssetRow) {
  const ok = await confirm({ title: '删除资产', message: `确认删除资产「${row.assetName}（${row.assetIp}）」吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await vulnApi.assets.remove(row.id)
    pushToast('资产已删除')
    await list.load()
  } catch (error) {
    toastError(error, '资产删除失败')
  }
}

async function batchRemove(ids: (string | number)[]) {
  const ok = await confirm({ title: '批量删除', message: `确认删除选中的 ${ids.length} 个资产吗？（存在关联漏洞的资产会被拒绝）`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await Promise.all(ids.map(id => vulnApi.assets.remove(Number(id))))
    pushToast(`已删除 ${ids.length} 个资产`)
    await list.load()
  } catch (error) {
    toastError(error, '批量删除失败')
  }
}

/* ── 详情抽屉：资产信息 + 关联漏洞子表（三级穿透第一级） ── */
const detail = ref<AssetRow | null>(null)
const detailVulns = ref<VulnRow[]>([])
const detailLoading = ref(false)
watch(detail, async (row) => {
  detailVulns.value = []
  if (!row) return
  detailLoading.value = true
  try {
    const page = await vulnApi.vulnerabilities.list({ assetId: row.id, pageNum: 1, pageSize: 100 })
    detailVulns.value = page.rows as unknown as VulnRow[]
  } catch {
    detailVulns.value = []
  } finally {
    detailLoading.value = false
  }
})
</script>

<template>
  <ListPage title="资产管理" desc="登记纳管的资产（IP 与服务），风险等级由关联漏洞自动计算。" table-title="资产列表" :list="list" :columns="columns" selectable>
    <template #actions>
      <PermissionButton perm="vuln:asset:add" variant="primary" icon="plus" @click="openCreate">新增资产</PermissionButton>
    </template>

    <template #search>
      <Field label="IP 地址" wide>
        <TextInput v-model="list.query.assetIp" icon="search" placeholder="资产 IP，模糊匹配" clearable @enter="list.search()" />
      </Field>
      <Field label="服务 / 应用名" wide>
        <TextInput v-model="list.query.assetName" icon="search" placeholder="服务 / 应用名，模糊匹配" clearable @enter="list.search()" />
      </Field>
      <Field label="风险等级">
        <DictSelect v-model="list.query.riskLevel" type="vuln_risk_level" all-label="全部" />
      </Field>
      <Field label="状态">
        <DictSelect v-model="list.query.status" type="vuln_asset_status" all-label="全部状态" />
      </Field>
    </template>

    <template #batch="{ ids }">
      <PermissionButton perm="vuln:asset:remove" variant="text" size="sm" danger @click="batchRemove(ids)">删除</PermissionButton>
    </template>

    <template #cell-assetIp="{ row }"><span class="code-chip">{{ row.assetIp }}</span></template>
    <template #cell-assetName="{ row }"><span class="cell-title">{{ row.assetName }}</span></template>
    <template #cell-owner="{ row }"><span>{{ row.owner || '—' }}</span></template>
    <template #cell-riskLevel="{ row }"><DictTag type="vuln_risk_level" :value="row.riskLevel" /></template>
    <template #cell-status="{ row }"><DictTag type="vuln_asset_status" :value="row.status" /></template>
    <template #cell-remark="{ row }"><span class="muted">{{ row.remark || '—' }}</span></template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <PermissionButton perm="vuln:asset:list" icon-only icon="eye" label="查看详情" @click="detail = row" />
        <PermissionButton perm="vuln:asset:edit" icon-only icon="edit" label="编辑" @click="openEdit(row)" />
        <PermissionButton perm="vuln:asset:remove" icon-only icon="trash" label="删除" danger @click="remove(row)" />
      </div>
    </template>

    <FormDialog :open="dialog" :title="form.id ? '编辑资产' : '新增资产'" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="IP 地址" required :error="errors.assetIp">
        <TextInput v-model="form.assetIp" placeholder="如：192.168.1.10" :invalid="!!errors.assetIp" />
      </Field>
      <Field label="服务 / 应用名" required :error="errors.assetName">
        <TextInput v-model="form.assetName" placeholder="如：OA 系统" :invalid="!!errors.assetName" />
      </Field>
      <Field label="负责人">
        <TextInput v-model="form.owner" placeholder="选填" />
      </Field>
      <Field label="状态" group>
        <DictSelect v-model="form.status" type="vuln_asset_status" />
      </Field>
      <Field label="备注" span>
        <TextInput v-model="form.remark" multiline :rows="2" placeholder="选填" :maxlength="500" />
      </Field>
    </FormDialog>

    <Drawer :open="Boolean(detail)" origin-from="tr" title="资产详情" :sub="detail ? `${detail.assetName}（${detail.assetIp}）` : ''" :width="600" @close="detail = null">
      <template v-if="detail">
        <div class="drawer-stack">
          <div class="kv-grid">
            <div class="kv"><div class="kv-label">IP 地址</div><div class="kv-value mono">{{ detail.assetIp }}</div></div>
            <div class="kv"><div class="kv-label">状态</div><div class="kv-value"><DictTag type="vuln_asset_status" :value="detail.status" /></div></div>
            <div class="kv"><div class="kv-label">风险等级</div><div class="kv-value"><DictTag type="vuln_risk_level" :value="detail.riskLevel" /><span class="soft"> 由关联漏洞自动计算</span></div></div>
            <div class="kv"><div class="kv-label">负责人</div><div class="kv-value">{{ detail.owner || '—' }}</div></div>
            <div v-if="detail.remark" class="kv span-2"><div class="kv-label">备注</div><div class="kv-value">{{ detail.remark }}</div></div>
          </div>
          <div class="section-label" style="margin: 20px 0 10px">关联漏洞（{{ detailVulns.length }}）</div>
          <div v-if="detailLoading" class="soft">加载中…</div>
          <div v-else-if="!detailVulns.length" class="soft">暂无关联漏洞，可在「漏洞管理」页登记并选择此资产</div>
          <div v-else class="drawer-stack" style="gap: 10px">
            <div v-for="v in detailVulns" :key="v.id" class="kv-grid" style="border: 1px solid var(--line); border-radius: 10px; padding: 12px 14px">
              <div class="kv span-2"><div class="kv-value">
                <DictTag type="vuln_risk_level" :value="v.riskLevel" />
                <DictTag type="vuln_status" :value="v.vulnStatus" />
                <b>{{ v.vulnTitle }}</b>
              </div></div>
              <div class="kv"><div class="kv-label">CVE 编号</div><div class="kv-value mono">{{ v.cveNo || '—' }}</div></div>
              <div class="kv"><div class="kv-label">发现日期</div><div class="kv-value tnum">{{ v.foundTime || '—' }}</div></div>
            </div>
          </div>
        </div>
      </template>
    </Drawer>
  </ListPage>
</template>
