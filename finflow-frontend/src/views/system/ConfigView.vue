<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance } from 'element-plus'
import { configApi } from '@/api/system'
import { dateTimeFormatter } from '@/utils/format'
import type { SystemConfigItem } from '@/types/api'

/**
 * 系统配置管理（S37）：key 集合由后端固定（不可增删），仅开放值与描述修改。
 * 编辑控件按值类型渲染：NUMBER → 数字输入，BOOLEAN → 开关，STRING/JSON → 文本。
 * 保存后端落库即删缓存生效，无需重启。
 */
const loading = ref(false)
const records = ref<SystemConfigItem[]>([])

const typeLabel: Record<string, string> = {
  NUMBER: '数字',
  STRING: '文本',
  BOOLEAN: '开关',
  JSON: 'JSON',
}

/** key 分组展示（表单小节标题）。 */
const groupLabel: Record<string, string> = {
  payment: '支付',
  merchant: '商户',
  risk: '风控',
  webhook: 'Webhook',
  settlement: '结算',
  switch: '系统开关',
}

function groupOf(key: string): string {
  const prefix = key.split('.')[0] ?? ''
  return groupLabel[prefix] ?? '其他'
}

const groups = computed(() => {
  const map = new Map<string, SystemConfigItem[]>()
  for (const item of records.value) {
    const group = groupOf(item.configKey)
    if (!map.has(group)) {
      map.set(group, [])
    }
    map.get(group)!.push(item)
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

async function load(): Promise<void> {
  loading.value = true
  try {
    records.value = await configApi.list()
  } finally {
    loading.value = false
  }
}
onMounted(load)

/** ---------- 编辑 ---------- */
const editVisible = ref(false)
const editFormRef = ref<FormInstance>()
const editing = ref<SystemConfigItem | null>(null)
const editForm = reactive({ configValue: '', description: '' })
const editRules = {
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }],
}

/** BOOLEAN 型用开关渲染（表单值仍是字符串 true/false）。 */
const boolValue = computed({
  get: () => editForm.configValue === 'true',
  set: (val: boolean) => {
    editForm.configValue = String(val)
  },
})

/** NUMBER 型用数字输入渲染（表单值仍是字符串，空输入回退空串）。 */
const numberValue = computed({
  get: () => (editForm.configValue === '' ? undefined : Number(editForm.configValue)),
  set: (val: number | undefined) => {
    editForm.configValue = val === undefined || Number.isNaN(val) ? '' : String(val)
  },
})

function openEdit(item: SystemConfigItem): void {
  editing.value = item
  Object.assign(editForm, { configValue: item.configValue, description: item.description })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid || editing.value === null) {
    return
  }
  await configApi.update(editing.value.configKey, {
    configValue: editForm.configValue,
    description: editForm.description || undefined,
  })
  ElMessage.success('已保存，即时生效')
  editVisible.value = false
  await load()
}
</script>

<template>
  <el-card v-loading="loading" shadow="never">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      title="配置保存后即时生效（无需重启）；不当的值可能影响交易、风控与结算，请谨慎修改。"
      class="notice"
    />

    <el-collapse>
      <el-collapse-item v-for="group in groups" :key="group.name" :name="group.name">
        <template #title>
          <span class="group-title">{{ group.name }}</span>
        </template>
        <el-table :data="group.items" border>
          <el-table-column prop="configKey" label="配置键" min-width="230" />
          <el-table-column label="值" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-tag v-if="row.valueType === 'BOOLEAN'" :type="row.configValue === 'true' ? 'success' : 'danger'">
                {{ row.configValue === 'true' ? '开启' : '关闭' }}
              </el-tag>
              <span v-else>{{ row.configValue }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="80" align="center">
            <template #default="{ row }">{{ typeLabel[row.valueType] ?? row.valueType }}</template>
          </el-table-column>
          <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
          <el-table-column label="更新人" width="90" align="center">
            <template #default="{ row }">{{ row.updatedBy === 0 ? '系统' : `用户#${row.updatedBy}` }}</template>
          </el-table-column>
          <el-table-column prop="updatedAt" label="更新时间" width="170" :formatter="dateTimeFormatter" />
          <el-table-column label="操作" width="90" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'system:config:update'" link type="primary" @click="openEdit(row)">
                编辑
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>
  </el-card>

  <el-dialog v-model="editVisible" :title="`编辑配置：${editing?.configKey ?? ''}`" width="520px">
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="80px">
      <el-form-item label="值" prop="configValue">
        <el-input-number
          v-if="editing?.valueType === 'NUMBER'"
          v-model="numberValue"
          :controls="false"
          class="number-input"
        />
        <el-switch v-else-if="editing?.valueType === 'BOOLEAN'" v-model="boolValue" />
        <el-input v-else v-model="editForm.configValue" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="editForm.description" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEdit">确定</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.notice {
  margin-bottom: 12px;
}

.group-title {
  font-weight: 600;
}

/* el-input-number 绑定字符串需宽一点；NUMBER 值均为整数（分/万分比/小时） */
.number-input {
  width: 100%;
}
</style>
