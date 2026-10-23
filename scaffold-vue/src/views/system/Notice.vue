<script setup lang="ts">
import { reactive, ref } from 'vue'
import { Button, Drawer, Field, Select, Tag, TextInput, type Column } from '@/components/ui'
import { FormDialog, ListPage, PermissionButton, UserMultiSelect } from '@/components/business'
import { systemApi, type NoticeRow, type NoticeSendPayload } from '@/api'
import { useListPage } from '@/composables/list'
import { confirm } from '@/composables/confirm'
import { pushToast, toastError } from '@/composables/toast'

/* 通知中心：全员公告与点对点消息共用一套列表；未读筛选走 unreadOnly 参数。 */
const list = useListPage<NoticeRow, { unread: string }>({
  query: { unread: '0' },
  fetch: params => systemApi.notices.mine(params) as Promise<any>,
  toParams: query => ({ unreadOnly: query.unread === '1' }),
})

const columns: Column[] = [
  { key: 'title', title: '标题', minWidth: 220 },
  { key: 'category', title: '分类', width: 90 },
  { key: 'level', title: '级别', width: 90 },
  { key: 'createTime', title: '时间', width: 168 },
  { key: 'actions', title: '操作', width: 150, className: 'col-actions' },
]

const UNREAD_OPTIONS = [
  { label: '全部通知', value: '0' },
  { label: '仅未读', value: '1' },
]
const CATEGORY_OPTIONS = [
  { label: '公告', value: 'notice' },
  { label: '告警', value: 'alert' },
  { label: '待办提醒', value: 'task' },
]
const LEVEL_OPTIONS = [
  { label: '普通', value: 'info' },
  { label: '注意', value: 'warn' },
  { label: '紧急', value: 'danger' },
]

const CATEGORY_LABEL: Record<string, string> = { notice: '公告', alert: '告警', task: '待办' }
const CATEGORY_TONE: Record<string, string> = { notice: 'info', alert: 'danger', task: 'ok' }
const LEVEL_LABEL: Record<string, string> = { info: '普通', warn: '注意', danger: '紧急' }
const LEVEL_TONE: Record<string, string> = { info: 'neutral', warn: 'ok', danger: 'danger' }

/* ── 详情：打开即标记已读 ── */
const detail = ref<NoticeRow | null>(null)

async function openDetail(row: NoticeRow) {
  detail.value = row
  if (row.readFlag) return
  try {
    await systemApi.notices.read(row.id)
    row.readFlag = 1
    list.total.value = Math.max(0, list.total.value - 1)
  } catch {
    /* 标记失败不阻断阅读 */
  }
}

async function markRead(row: NoticeRow) {
  if (row.readFlag) return
  try {
    await systemApi.notices.read(row.id)
    row.readFlag = 1
    list.rows.value = [...list.rows.value]
    pushToast('已标记为已读')
  } catch (error) {
    toastError(error, '操作失败')
  }
}

async function readAll() {
  try {
    await systemApi.notices.readAll()
    pushToast('已全部标记为已读')
    await list.load()
  } catch (error) {
    toastError(error, '操作失败')
  }
}

async function remove(row: NoticeRow) {
  const ok = await confirm({ title: '删除通知', message: `确认删除「${row.title}」吗？`, confirmText: '删除', danger: true })
  if (!ok) return
  try {
    await systemApi.notices.remove(row.id)
    pushToast('通知已删除')
    await list.load()
  } catch (error) {
    toastError(error, '删除失败')
  }
}

/* ── 发送通知 ── */
const emptyForm = (): NoticeSendPayload => ({ title: '', content: '', category: 'notice', level: 'info', recipientIds: [] })
const dialog = ref(false)
const saving = ref(false)
const form = reactive<NoticeSendPayload>(emptyForm())

function openSend() {
  Object.assign(form, emptyForm())
  dialog.value = true
}

async function submit() {
  if (!form.title.trim() || !form.content.trim()) {
    pushToast('标题和正文都不能为空')
    return
  }
  saving.value = true
  try {
    await systemApi.notices.send({ ...form, title: form.title.trim(), content: form.content.trim() })
    const count = form.recipientIds?.length ?? 0
    pushToast(count > 0 ? `通知已发送给 ${count} 位用户` : '公告已发送给全员')
    dialog.value = false
    await list.load()
  } catch (error) {
    toastError(error, '发送失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ListPage title="通知中心" desc="全员公告与发给个人的业务提醒共用同一个入口，铃铛按可见范围展示。" table-title="通知列表" :list="list" :columns="columns" fit-viewport>
    <template #actions>
      <Button variant="ghost" @click="readAll">全部已读</Button>
      <PermissionButton perm="system:notice:add" variant="primary" icon="plus" @click="openSend">发送通知</PermissionButton>
    </template>

    <template #search>
      <Field label="状态">
        <Select v-model="list.query.unread" :options="UNREAD_OPTIONS" />
      </Field>
    </template>

    <template #cell-title="{ row }">
      <span class="notice-title" @click="openDetail(row)">
        <span v-if="!row.readFlag" class="unread-dot" aria-hidden="true" />
        <span :class="!row.readFlag ? 'cell-title' : ''">{{ row.title }}</span>
      </span>
    </template>
    <template #cell-category="{ row }">
      <Tag :tone="CATEGORY_TONE[row.category] ?? 'neutral'">{{ CATEGORY_LABEL[row.category] ?? '通知' }}</Tag>
    </template>
    <template #cell-level="{ row }">
      <Tag :tone="LEVEL_TONE[row.level] ?? 'neutral'" dot>{{ LEVEL_LABEL[row.level] ?? row.level }}</Tag>
    </template>
    <template #cell-createTime="{ row }"><span class="tnum soft">{{ row.createTime }}</span></template>
    <template #cell-actions="{ row }">
      <div class="row-actions">
        <Button variant="text" size="sm" @click="openDetail(row)">查看</Button>
        <Button v-if="!row.readFlag" variant="text" size="sm" @click="markRead(row)">已读</Button>
        <PermissionButton perm="system:notice:remove" variant="text" size="sm" danger @click="remove(row)">删除</PermissionButton>
      </div>
    </template>

    <Drawer :open="Boolean(detail)" origin-from="tr" title="通知详情" :sub="detail ? detail.title : ''" :width="560" @close="detail = null">
      <template v-if="detail">
        <div class="drawer-stack">
          <div class="kv-grid">
            <div class="kv"><div class="kv-label">分类</div><div class="kv-value">
              <Tag :tone="CATEGORY_TONE[detail.category] ?? 'neutral'">{{ CATEGORY_LABEL[detail.category] ?? '通知' }}</Tag>
              <Tag :tone="LEVEL_TONE[detail.level] ?? 'neutral'" dot>{{ LEVEL_LABEL[detail.level] ?? detail.level }}</Tag>
            </div></div>
            <div class="kv"><div class="kv-label">范围</div><div class="kv-value">{{ detail.recipientId ? `指定用户（ID ${detail.recipientId}）` : '全员公告' }}</div></div>
            <div class="kv"><div class="kv-label">发送人</div><div class="kv-value">{{ detail.createBy || '—' }}</div></div>
            <div class="kv"><div class="kv-label">时间</div><div class="kv-value tnum">{{ detail.createTime }}</div></div>
          </div>
          <div class="notice-body">{{ detail.content }}</div>
        </div>
      </template>
    </Drawer>

    <FormDialog :open="dialog" title="发送通知" sub="接收人留空即全员公告" :saving="saving" @close="dialog = false" @submit="submit">
      <Field label="标题" required span>
        <TextInput v-model="form.title" placeholder="如：工单即将超期" :maxlength="120" />
      </Field>
      <Field label="正文" required span>
        <TextInput v-model="form.content" multiline :rows="5" placeholder="说明事项、时间与需要处理的动作" :maxlength="2000" />
      </Field>
      <Field label="分类">
        <Select v-model="form.category" :options="CATEGORY_OPTIONS" />
      </Field>
      <Field label="级别">
        <Select v-model="form.level" :options="LEVEL_OPTIONS" />
      </Field>
      <Field label="接收人" span hint="可多选；不选任何人表示全员公告">
        <UserMultiSelect v-model="form.recipientIds" placeholder="未指定 = 全员公告" />
      </Field>
    </FormDialog>
  </ListPage>
</template>

<style scoped>
.notice-title { display: inline-flex; align-items: center; gap: 7px; cursor: pointer; }
.unread-dot { width: 6px; height: 6px; border-radius: 50%; background: var(--blue); flex: none; }
.notice-body {
  margin-top: 16px;
  padding: 16px;
  border-radius: var(--r-md);
  border: 1px solid var(--line);
  background: var(--input-bg);
  font-size: 13px;
  line-height: 1.75;
  white-space: pre-wrap;
  word-break: break-word;
}
.row-actions { display: inline-flex; gap: 4px; }
</style>
