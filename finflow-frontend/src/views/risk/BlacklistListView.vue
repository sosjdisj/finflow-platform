<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { riskApi } from '@/api/risk'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import { riskDimensionText } from '@/utils/riskDisplay'
import type { RiskBlacklist } from '@/types/api'

/** 黑名单管理：用户 / IP / 设备三维度，维度 + 值唯一；删除即解除拉黑。 */
const dimension = ref('')
const value = ref('')

const paging = usePaging<RiskBlacklist>((page, size) =>
  riskApi.blacklistPage(page, size, {
    dimension: dimension.value || undefined,
    value: value.value || undefined,
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  dimension.value = ''
  value.value = ''
  void paging.reload()
}

// ---------- 新增 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const form = ref({ dimension: 'USER', value: '', reason: '' })

function openCreate(): void {
  form.value = { dimension: 'USER', value: '', reason: '' }
  dialogVisible.value = true
}

async function submitCreate(): Promise<void> {
  if (!form.value.value.trim()) {
    ElMessage.warning('请填写名单值')
    return
  }
  saving.value = true
  try {
    await riskApi.blacklistCreate({
      dimension: form.value.dimension,
      value: form.value.value.trim(),
      reason: form.value.reason.trim() || undefined,
    })
    ElMessage.success('已加入黑名单，命中订单将被直接拒绝')
    dialogVisible.value = false
    await paging.reload()
  } finally {
    saving.value = false
  }
}

async function remove(row: RiskBlacklist): Promise<void> {
  await ElMessageBox.confirm(
    `确认将「${row.value}」移出黑名单？移出后该${riskDimensionText(row.dimension)}不再被拦截。`,
    '解除拉黑',
    { type: 'warning' },
  )
  await riskApi.blacklistDelete(row.id)
  ElMessage.success('已解除')
  await paging.reload()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="dimension" class="w160" placeholder="维度" clearable @change="paging.reload()">
        <el-option label="用户（商户号）" value="USER" />
        <el-option label="IP 地址" value="IP" />
        <el-option label="设备号" value="DEVICE" />
      </el-select>
      <el-input v-model="value" class="w200" placeholder="名单值" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" v-permission="'risk:blacklist:create'" @click="openCreate">新增黑名单</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column label="维度" width="150">
        <template #default="{ row }">{{ riskDimensionText(row.dimension) }}</template>
      </el-table-column>
      <el-table-column prop="value" label="名单值" min-width="200" show-overflow-tooltip />
      <el-table-column prop="reason" label="拉黑原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="操作人" width="110" />
      <el-table-column label="加入时间" width="170" :formatter="dateTimeFormatter" prop="createdAt" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" v-permission="'risk:blacklist:delete'" @click="remove(row)">解除</el-button>
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

  <el-dialog v-model="dialogVisible" title="新增黑名单" width="480px">
    <el-form label-width="90px">
      <el-form-item label="维度" required>
        <el-select v-model="form.dimension" style="width: 100%">
          <el-option label="用户（商户号）" value="USER" />
          <el-option label="IP 地址" value="IP" />
          <el-option label="设备号" value="DEVICE" />
        </el-select>
      </el-form-item>
      <el-form-item label="名单值" required>
        <el-input v-model="form.value" placeholder="商户号 / IP / 设备号" />
      </el-form-item>
      <el-form-item label="拉黑原因">
        <el-input v-model="form.reason" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitCreate">加入黑名单</el-button>
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

.w160 {
  width: 160px;
}

.w200 {
  width: 200px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
