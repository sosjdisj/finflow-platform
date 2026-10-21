<script setup lang="ts">
import { ref, watch } from 'vue'
import { refundApi } from '@/api/refund'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { RefundOrderDetail } from '@/types/api'
import { PAYMENT_STATUS_TAG } from '@/views/payment/paymentDisplay'
import { REFUND_STATUS_LABEL, REFUND_STATUS_TAG } from '../refundDisplay'

/**
 * 退款详情抽屉：退款单信息 + 状态时间线（读 refund_order_state_log）。
 * 从退款列表或支付订单详情跳入，追踪一笔退款从申请到终态的完整过程。
 */
const visible = ref(false)
const detail = ref<RefundOrderDetail | null>(null)

async function open(id: number): Promise<void> {
  visible.value = true
  detail.value = await refundApi.detail(id)
}

defineExpose({ open })

watch(visible, (open) => {
  if (!open) {
    detail.value = null
  }
})
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? `退款单：${detail.refund.refundNo}` : '退款详情'" size="55%">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="退款单号">{{ detail.refund.refundNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="REFUND_STATUS_TAG[detail.refund.status] ?? 'info'">
            {{ REFUND_STATUS_LABEL[detail.refund.status] ?? detail.refund.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="平台订单号">
          {{ detail.refund.platformOrderNo }}
        </el-descriptions-item>
        <el-descriptions-item label="退款金额">
          {{ fenToYuan(detail.refund.refundAmount) }} {{ detail.refund.currency }}
        </el-descriptions-item>
        <el-descriptions-item label="渠道">{{ detail.refund.channelCode ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="渠道退款单号">{{ detail.refund.channelRefundNo ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="发起人">{{ detail.refund.operator }}</el-descriptions-item>
        <el-descriptions-item label="退款成功时间">
          {{ detail.refund.refundedAt ? dateTimeFormatter(null, null, detail.refund.refundedAt) : '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="退款原因" :span="2">{{ detail.refund.reason ?? '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.refund.failReason" label="失败原因" :span="2">
          {{ detail.refund.failReason }}
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="section-title">状态时间线</h4>
      <el-timeline v-if="detail.stateLogs.length" class="timeline">
        <el-timeline-item
          v-for="log in detail.stateLogs"
          :key="log.id"
          :timestamp="dateTimeFormatter(null, null, log.createdAt)"
          :type="PAYMENT_STATUS_TAG[log.toStatus] ?? 'info'"
        >
          <div class="timeline-title">
            <el-tag size="small" :type="REFUND_STATUS_TAG[log.fromStatus] ?? 'info'">
              {{ REFUND_STATUS_LABEL[log.fromStatus] ?? log.fromStatus }}
            </el-tag>
            <span class="arrow">→</span>
            <el-tag size="small" :type="REFUND_STATUS_TAG[log.toStatus] ?? 'info'">
              {{ REFUND_STATUS_LABEL[log.toStatus] ?? log.toStatus }}
            </el-tag>
          </div>
          <div class="timeline-meta">{{ log.reason ?? '—' }}（操作：{{ log.operator }}）</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无状态留痕" />
    </template>
  </el-drawer>
</template>

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
