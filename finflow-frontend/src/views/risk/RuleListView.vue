<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { riskApi } from '@/api/risk'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import { riskRuleParamsText, riskRuleTypeText } from '@/utils/riskDisplay'
import type { RiskRule } from '@/types/api'

/**
 * 风控规则管理：动态增删改 + 启停开关。规则改动下一笔交易即时生效（后端求值实时读库）。
 * 参数按类型给出模板；合法性由后端对应求值器写入时校验，前端只做 JSON 格式预检。
 */
const ruleName = ref('')
const ruleType = ref('')
const enabledFilter = ref<boolean | null>(null)

const paging = usePaging<RiskRule>((page, size) =>
  riskApi.rulePage(page, size, {
    ruleName: ruleName.value || undefined,
    ruleType: ruleType.value || undefined,
    enabled: enabledFilter.value ?? undefined,
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  ruleName.value = ''
  ruleType.value = ''
  enabledFilter.value = null
  void paging.reload()
}

// ---------- 新增 / 编辑 ----------
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = ref({ ruleName: '', ruleType: 'AMOUNT_LIMIT', params: '', score: 0, priority: 100, enabled: true, remark: '' })

/** 各类型的参数模板，切换类型时预填，减少手写 JSON 出错。 */
const PARAM_TEMPLATES = {
  AMOUNT_LIMIT: '{ "amountLimit": 1000000 }',
  FREQUENCY: '{ "windowSeconds": 60, "maxCount": 10 }',
  BLACKLIST: '{ "dimension": "USER" }',
  SAME_IP_MULTI_ACCOUNT: '{ "maxAccounts": 3, "windowSeconds": 3600 }',
}

function templateOf(ruleType: string): string {
  return (PARAM_TEMPLATES as Record<string, string>)[ruleType] ?? '{}'
}

function onTypeChange(): void {
  form.value.params = templateOf(form.value.ruleType)
}

function openCreate(): void {
  editingId.value = null
  form.value = { ruleName: '', ruleType: 'AMOUNT_LIMIT', params: PARAM_TEMPLATES.AMOUNT_LIMIT, score: 0, priority: 100, enabled: true, remark: '' }
  dialogVisible.value = true
}

function openEdit(row: RiskRule): void {
  editingId.value = row.id
  form.value = {
    ruleName: row.ruleName,
    ruleType: row.ruleType,
    params: row.params,
    score: row.score,
    priority: row.priority,
    enabled: row.enabled,
    remark: row.remark ?? '',
  }
  dialogVisible.value = true
}

async function submitForm(): Promise<void> {
  if (!form.value.ruleName.trim()) {
    ElMessage.warning('请填写规则名称')
    return
  }
  try {
    JSON.parse(form.value.params)
  } catch {
    ElMessage.warning('参数不是合法 JSON')
    return
  }
  saving.value = true
  try {
    const data = { ...form.value, ruleName: form.value.ruleName.trim(), remark: form.value.remark.trim() || undefined }
    if (editingId.value == null) {
      await riskApi.ruleCreate(data)
      ElMessage.success('规则已创建，下一笔交易即生效')
    } else {
      await riskApi.ruleUpdate(editingId.value, data)
      ElMessage.success('规则已更新，下一笔交易即生效')
    }
    dialogVisible.value = false
    await paging.reload()
  } finally {
    saving.value = false
  }
}

// ---------- 启停 / 删除 ----------
const togglingId = ref<number | null>(null)

async function toggle(row: RiskRule): Promise<void> {
  togglingId.value = row.id
  try {
    await riskApi.ruleToggle(row.id, !row.enabled)
    ElMessage.success(row.enabled ? '规则已停用' : '规则已启用')
    await paging.reload()
  } finally {
    togglingId.value = null
  }
}

async function remove(row: RiskRule): Promise<void> {
  await ElMessageBox.confirm(`确认删除规则「${row.ruleName}」？删除后立即停止生效。`, '删除确认', { type: 'warning' })
  await riskApi.ruleDelete(row.id)
  ElMessage.success('已删除')
  await paging.reload()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="ruleName" class="w200" placeholder="规则名称" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-select v-model="ruleType" class="w180" placeholder="规则类型" clearable @change="paging.reload()">
        <el-option label="单笔金额限额" value="AMOUNT_LIMIT" />
        <el-option label="时间窗口频次" value="FREQUENCY" />
        <el-option label="黑名单" value="BLACKLIST" />
        <el-option label="同 IP 多账户" value="SAME_IP_MULTI_ACCOUNT" />
      </el-select>
      <el-select v-model="enabledFilter" class="w120" placeholder="启停" clearable @change="paging.reload()">
        <el-option label="已启用" :value="true" />
        <el-option label="已停用" :value="false" />
      </el-select>
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" v-permission="'risk:rule:create'" @click="openCreate">新增规则</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="ruleName" label="规则名称" min-width="160" />
      <el-table-column label="类型" width="140">
        <template #default="{ row }">{{ riskRuleTypeText(row.ruleType) }}</template>
      </el-table-column>
      <el-table-column label="参数" min-width="220">
        <template #default="{ row }">
          <el-tooltip :content="row.params" placement="top">
            <span>{{ riskRuleParamsText(row.ruleType, row.params) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="分值" width="80" align="center" />
      <el-table-column prop="priority" label="优先级" width="90" align="center" />
      <el-table-column label="启停" width="90" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="togglingId === row.id"
            v-permission="'risk:rule:update'"
            @change="toggle(row)"
          />
          <span v-if="togglingId !== row.id" class="switch-text">{{ row.enabled ? '启用' : '停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column label="更新时间" width="170" :formatter="dateTimeFormatter" prop="updatedAt" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'risk:rule:update'" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" v-permission="'risk:rule:delete'" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="paging.page"
      v-model:page-size="paging.size"
      class="pager"
      layout="total, sizes, prev, pager, next"
      :total="paging.total"
      @current-change="paging.load()"
      @size-change="paging.reload()"
    />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingId == null ? '新增风控规则' : '编辑风控规则'" width="560px">
    <el-form label-width="90px">
      <el-form-item label="规则名称" required>
        <el-input v-model="form.ruleName" placeholder="如：单笔超 1 万元加 30 分" />
      </el-form-item>
      <el-form-item label="规则类型" required>
        <el-select v-model="form.ruleType" :disabled="editingId != null" style="width: 100%" @change="onTypeChange">
          <el-option label="单笔金额限额" value="AMOUNT_LIMIT" />
          <el-option label="时间窗口频次" value="FREQUENCY" />
          <el-option label="黑名单" value="BLACKLIST" />
          <el-option label="同 IP 多账户" value="SAME_IP_MULTI_ACCOUNT" />
        </el-select>
      </el-form-item>
      <el-form-item label="参数" required>
        <el-input v-model="form.params" type="textarea" :rows="3" placeholder="JSON 参数，随类型自动预填模板" />
      </el-form-item>
      <el-form-item label="命中分值">
        <el-input-number v-model="form.score" :min="0" :max="1000" />
        <span class="form-tip">黑名单类型命中直接拒绝，分值仅留档</span>
      </el-form-item>
      <el-form-item label="优先级">
        <el-input-number v-model="form.priority" :min="0" :max="9999" />
        <span class="form-tip">小者先执行</span>
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitForm">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.w200 {
  width: 200px;
}

.w180 {
  width: 180px;
}

.w120 {
  width: 120px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.switch-text {
  margin-left: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
