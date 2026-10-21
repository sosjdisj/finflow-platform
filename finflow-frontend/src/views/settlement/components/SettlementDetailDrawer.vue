<template>
  <el-drawer v-model="visible" :title="detail ? `结算单：${detail.settlement.settleNo}` : '结算详情'" size="60%">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="结算单号">{{ detail.settlement.settleNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="settlementStatusTag(detail.settlement.status)">
            {{ settlementStatusText(detail.settlement.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="商户 ID">{{ detail.settlement.merchantId }}</el-descriptions-item>
        <el-descriptions-item label="结算日">{{ detail.settlement.settleDate }}</el-descriptions-item>
        <el-descriptions-item label="订单笔数">{{ detail.settlement.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="结算金额">
          {{ fenToYuan(detail.settlement.totalAmount) }} {{ detail.settlement.currency }}
        </el-descriptions-item>
        <el-descriptions-item label="审核人">{{ detail.settlement.approvedBy ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="审核时间">
          {{ detail.settlement.approvedAt ? dateTimeFormatter(null, null, detail.settlement.approvedAt) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="入账时间">
          {{ detail.settlement.executedAt ? dateTimeFormatter(null, null, detail.settlement.executedAt) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="失败原因" v-if="detail.settlement.failReason">
          {{ detail.settlement.failReason }}
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="section-title">逐笔明细（{{ detail.details.length }}）</h4>
      <el-table :data="detail.details" size="small" border>
        <el-table-column label="清分单 ID" prop="clearingId" width="110" />
        <el-table-column label="订单 ID" prop="orderId" width="160" />
        <el-table-column label="支付金额" align="right">
          <template #default="{ row }">{{ fenToYuan(row.payAmount) }}</template>
        </el-table-column>
        <el-table-column label="商户应得" align="right">
          <template #default="{ row }">{{ fenToYuan(row.merchantIncome) }}</template>
        </el-table-column>
      </el-table>

      <h4 class="section-title">状态时间线</h4>
      <el-timeline v-if="detail.stateLogs.length" class="timeline">
        <el-timeline-item
          v-for="(log, index) in detail.stateLogs"
          :key="index"
          :timestamp="dateTimeFormatter(null, null, log.createdAt)"
          :type="SETTLEMENT_STATUS_TAG[log.toStatus] ?? 'info'"
        >
          <div class="timeline-title">
            <el-tag size="small" :type="SETTLEMENT_STATUS_TAG[log.fromStatus] ?? 'info'">
              {{ settlementStatusText(log.fromStatus) }}
            </el-tag>
            <span class="arrow">→</span>
            <el-tag size="small" :type="SETTLEMENT_STATUS_TAG[log.toStatus] ?? 'info'">
              {{ settlementStatusText(log.toStatus) }}
            </el-tag>
          </div>
          <div class="timeline-meta">{{ log.reason ?? '—' }}（操作：{{ log.operator }}）</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无状态留痕" />
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getSettlementDetail } from '@/api/settlement'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { SettlementOrderDetail } from '@/types/api'
import { SETTLEMENT_STATUS_TAG, settlementStatusTag, settlementStatusText } from '@/utils/settlementDisplay'

/**
 * 结算详情抽屉：结算单信息 + 逐笔清分引用 + 状态时间线（读 settlement_order_state_log）。
 * 从结算管理列表跳入，回答这笔钱何时给、给没给。
 */
const visible = ref(false)
const detail = ref<SettlementOrderDetail | null>(null)

async function open(id: number): Promise<void> {
  visible.value = true
  detail.value = await getSettlementDetail(id)
}

defineExpose({ open })

watch(visible, (open) => {
  if (!open) {
    detail.value = null
  }
})
</script>

<style scoped>
.section-title {
  margin: 20px 0 12px;
}

.timeline {
  padding-left: 8px;
}

.timeline-title {
  display: flex;
  align-items: center;
  gap: 6px;
}

.arrow {
  color: var(--el-text-color-secondary);
}

.timeline-meta {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
