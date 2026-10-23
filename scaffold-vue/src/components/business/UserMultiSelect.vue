<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Button, Checkbox, Field, Modal, TextInput } from '@/components/ui'
import { systemApi } from '@/api'

/* 用户多选：空数组 = 不指定（调用方按业务解释为全员）。
   选择在弹窗里完成，避免下拉层级被表单容器裁切。 */
const props = withDefaults(defineProps<{
  modelValue: number[] | null | undefined
  placeholder?: string
}>(), {
  placeholder: '未指定接收人',
})
const emit = defineEmits<{ (e: 'update:modelValue', value: number[]): void }>()

const users = ref<Array<{ id: number; nickname: string; username: string }>>([])
const open = ref(false)
const keyword = ref('')
const draft = ref<number[]>([])

onMounted(async () => {
  try {
    const page = await systemApi.users.list({ pageNum: 1, pageSize: 200, status: 1 })
    users.value = page.rows.map(u => ({ id: u.id, nickname: u.nickname, username: u.username }))
  } catch {
    users.value = []
  }
})

const selected = computed(() => {
  const ids = props.modelValue ?? []
  return ids.map(id => users.value.find(u => u.id === id) ?? { id, nickname: `用户 ${id}`, username: '' })
})

const filtered = computed(() => {
  const q = keyword.value.trim().toLowerCase()
  if (!q) return users.value
  return users.value.filter(u => u.nickname.toLowerCase().includes(q) || u.username.toLowerCase().includes(q))
})

function openPicker() {
  draft.value = [...(props.modelValue ?? [])]
  keyword.value = ''
  open.value = true
}

function toggle(id: number, checked: boolean) {
  const next = new Set(draft.value)
  if (checked) next.add(id)
  else next.delete(id)
  draft.value = [...next]
}

function confirm() {
  emit('update:modelValue', draft.value)
  open.value = false
}

function clearAll() {
  draft.value = []
}
</script>

<template>
  <div class="ums">
    <div class="ums-control" role="button" tabindex="0" @click="openPicker" @keydown.enter.prevent="openPicker">
      <template v-if="selected.length">
        <span v-for="user in selected" :key="user.id" class="ums-chip">{{ user.nickname }}</span>
      </template>
      <span v-else class="soft">{{ placeholder }}</span>
    </div>
    <Button variant="secondary" size="sm" @click="openPicker">选择</Button>
  </div>

  <Modal :open="open" title="选择接收人" sub="不选任何人 = 全员可见" :width="520" @close="open = false">
    <div class="ums-picker">
      <Field label="搜索用户" wide>
        <TextInput v-model="keyword" icon="search" placeholder="按昵称或账号筛选" clearable />
      </Field>
      <div class="ums-list" role="listbox" aria-multiselectable="true">
        <label v-for="user in filtered" :key="user.id" class="ums-item">
          <Checkbox :model-value="draft.includes(user.id)" @update:model-value="toggle(user.id, $event)" />
          <span class="ums-name">{{ user.nickname }}</span>
          <span class="soft">{{ user.username }}</span>
        </label>
        <div v-if="!filtered.length" class="ums-empty soft">没有匹配的用户</div>
      </div>
    </div>
    <template #footer>
      <div class="ums-actions">
        <Button variant="text" size="sm" @click="clearAll">清空选择</Button>
        <span class="spacer" />
        <Button variant="ghost" @click="open = false">取消</Button>
        <Button variant="primary" @click="confirm">确定</Button>
      </div>
    </template>
  </Modal>
</template>

<style scoped>
.ums { display: flex; align-items: center; gap: 8px; width: 100%; min-width: 0; }
.ums-control {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
  min-width: 0;
  min-height: var(--control-h);
  padding: 4px 10px;
  border: 1px solid var(--line-strong);
  border-radius: var(--r-sm);
  background: var(--input-bg);
  box-shadow: var(--edge);
  cursor: pointer;
  transition: border-color .2s ease, box-shadow .2s ease;
}
.ums-control:hover { border-color: rgba(var(--shade), .18); }
.ums-control:focus-visible { outline: 2px solid var(--focus); outline-offset: 2px; }
.ums-chip {
  display: inline-flex;
  align-items: center;
  height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  background: var(--blue-soft);
  color: var(--blue);
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.ums-picker { display: flex; flex-direction: column; gap: 12px; }
.ums-list {
  max-height: 280px;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--line);
  border-radius: var(--r-md);
  padding: 6px;
}
.ums-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--r-xs);
  cursor: pointer;
  transition: background .15s ease;
}
.ums-item:hover { background: var(--hover-bg); }
.ums-name { font-size: 13px; }
.ums-empty { padding: 22px 10px; text-align: center; font-size: 12.5px; }
.ums-actions { display: flex; align-items: center; gap: 8px; width: 100%; }
.ums-actions .spacer { flex: 1; }
</style>
