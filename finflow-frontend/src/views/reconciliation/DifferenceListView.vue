<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { reconciliationApi } from '@/api/reconciliation'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import { reconDiffStatusTag, reconDiffStatusText, reconHandleTypeText, reconResultText } from '@/utils/reconciliationDisplay'
import type { ReconciliationDifference } from '@/types/api'
import DifferenceHandleDrawer from './components/DifferenceHandleDrawer.vue'

/**
 * 差异订单页：对账差异单列表（人工闭环入口）。
 * 流：待审核 → 确认（调账/补单）→ 处理并关闭；误报可忽略。操作在处理抽屉内完成。
 */
const diffNo = ref('')
const merchantOrderNo = ref('')
const diffType = ref('')
const status = ref('')

const paging = usePaging<ReconciliationDifference>((page, size) =>
  reconciliationApi.differencePage(page, size, {
    diffNo: diffNo.value || undefined,
    merchantOrderNo: merchantOrderNo.value || undefined,
    diffType: diffType.value || undefined,
    status: status.value || undefined,
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  diffNo.value = ''
  merchantOrderNo.value = ''
  diffType.value = ''
  status.value = ''
  void paging.reload()
}

const drawer = ref<InstanceType<typeof DifferenceHandleDrawer>>()

function openHandle(row: ReconciliationDifference): void {
  void drawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="diffNo" class="w200" placeholder="差异单号" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-input v-model="merchantOrderNo" class="w200" placeholder="商户单号" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-select v-model="diffType" class="w160" placeholder="差异类型" clearable @change="paging.reload()">
        <el-option label="金额不一致" value="AMOUNT_MISMATCH" />
        <el-option label="状态不一致" value="STATUS_MISMATCH" />
        <el-option label="渠道有平台无" value="MISSING_IN_PLATFORM" />
        <el-option label="平台有渠道无" value="MISSING_IN_CHANNEL" />
      </el-select>
      <el-select v-model="status" class="w140" placeholder="状态" clearable @change="paging.reload()">
        <el-option label="待审核" value="PENDING" />
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="已关闭" value="CLOSED" />
        <el-option label="已忽略" value="IGNORED" />
      </el-select>
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="diffNo" label="差异单号" width="180" />
      <el-table-column label="差异类型" width="130" align="center">
        <template #default="{ row }">{{ reconResultText(row.diffType) }}</template>
      </el-table-column>
      <el-table-column prop="merchantOrderNo" label="商户单号" min-width="160" />
      <el-table-column label="渠道侧(元)" width="110" align="right">
        <template #default="{ row }">{{ fenToYuan(row.billAmount) }}</template>
      </el-table-column>
      <el-table-column label="平台侧(元)" width="110" align="right">
        <template #default="{ row }">{{ fenToYuan(row.platformAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="reconDiffStatusTag(row.status)" size="small">{{ reconDiffStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处理方式" width="90" align="center">
        <template #default="{ row }">{{ row.handleType ? reconHandleTypeText(row.handleType) : '—' }}</template>
      </el-table-column>
      <el-table-column prop="ledgerNo" label="调账流水号" min-width="150">
        <template #default="{ row }">{{ row.ledgerNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="createdBy" label="发起人" width="100" />
      <el-table-column label="发起时间" width="170" :formatter="dateTimeFormatter" prop="createdAt" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'reconciliation:diff:query'" @click="openHandle(row)">处理</el-button>
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

  <DifferenceHandleDrawer ref="drawer" @processed="paging.load()" />
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

.w160 {
  width: 160px;
}

.w140 {
  width: 140px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
