<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { merchantApi } from '@/api/merchant'
import { paymentApi } from '@/api/payment'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { PaymentOrderDetail } from '@/types/api'
import {
  PAYMENT_METHOD_LABEL,
  PAYMENT_STATUS_LABEL,
  PAYMENT_STATUS_TAG,
  TXN_TYPE_LABEL,
  prettyPayload,
} from '../paymentDisplay'
import RefundCreateDialog from '@/views/refund/components/RefundCreateDialog.vue'

/**
 * 支付订单详情抽屉：订单信息（含发起退款入口）/ 状态时间线 / 渠道交互记录 三个 Tab。
 * 从列表点「详情」可追一单从创建到终态的完整生命周期。
 */
const visible = ref(false)
const detail = ref<PaymentOrderDetail | null>(null)
const merchantName = ref('')
const activeTab = ref('basic')

async function open(id: number, tab = 'basic'): Promise<void> {
  activeTab.value = tab
  visible.value = true
  detail.value = await paymentApi.detail(id)
  // 商户名不在订单接口里，用商户详情补齐（仅展示用，失败不阻塞详情）
  merchantName.value = await merchantApi
    .detail(detail.value.order.merchantId)
    .then((resp) => resp.merchant.merchantName)
    .catch(() => '')
}

defineExpose({ open })

watch(visible, (open) => {
  if (!open) {
    detail.value = null
    merchantName.value = ''
  }
})

/** 剩余可退（分）；无数据时视为已退完，隐藏入口。 */
const remainingFen = computed(() =>
  detail.value ? detail.value.order.amount - (detail.value.order.refundedAmount ?? 0) : 0,
)

const refundDialog = ref<InstanceType<typeof RefundCreateDialog>>()

/** 发起退款成功后刷新详情（剩余可退、状态可能变化）。 */
async function onRefundCreated(): Promise<void> {
  if (detail.value) {
    detail.value = await paymentApi.detail(detail.value.order.id)
  }
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? `支付订单：${detail.order.platformOrderNo}` : '支付订单详情'" size="62%">
    <el-tabs v-if="detail" v-model="activeTab">
      <!-- 订单信息 -->
      <el-tab-pane label="订单信息" name="basic">
        <div class="tab-toolbar">
          <el-tooltip
            v-if="remainingFen > 0 && detail.order.status === 'PAID'"
            :content="`剩余可退 ${fenToYuan(remainingFen)} 元`"
            placement="top"
          >
            <el-button type="warning" plain v-permission="'refund:order:create'" @click="refundDialog?.open(detail.order)">
              发起退款
            </el-button>
          </el-tooltip>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="平台订单号">{{ detail.order.platformOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="商户订单号">{{ detail.order.merchantOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="商户">
            {{ merchantName || `商户 #${detail.order.merchantId}` }}
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="PAYMENT_STATUS_TAG[detail.order.status] ?? 'info'">
              {{ PAYMENT_STATUS_LABEL[detail.order.status] ?? detail.order.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="金额">
            {{ fenToYuan(detail.order.amount) }} {{ detail.order.currency }}
          </el-descriptions-item>
          <el-descriptions-item label="支付方式">
            {{ PAYMENT_METHOD_LABEL[detail.order.paymentMethod] ?? detail.order.paymentMethod }}
          </el-descriptions-item>
          <el-descriptions-item label="商品标题" :span="2">{{ detail.order.subject }}</el-descriptions-item>
          <el-descriptions-item label="商品描述" :span="2">{{ detail.order.body ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="回调地址" :span="2">{{ detail.order.callbackUrl ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">
            {{ dateTimeFormatter(null, null, detail.order.createdAt) }}
          </el-descriptions-item>
          <el-descriptions-item label="过期时间">
            {{ dateTimeFormatter(null, null, detail.order.expiredAt) }}
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- 状态时间线（读状态留痕） -->
      <el-tab-pane label="状态时间线" name="timeline">
        <el-timeline v-if="detail.stateLogs.length" class="timeline">
          <el-timeline-item
            v-for="log in detail.stateLogs"
            :key="log.id"
            :timestamp="dateTimeFormatter(null, null, log.createdAt)"
            :type="PAYMENT_STATUS_TAG[log.toStatus] ?? 'info'"
          >
            <div class="timeline-title">
              <el-tag size="small" :type="PAYMENT_STATUS_TAG[log.fromStatus] ?? 'info'">
                {{ PAYMENT_STATUS_LABEL[log.fromStatus] ?? log.fromStatus }}
              </el-tag>
              <span class="arrow">→</span>
              <el-tag size="small" :type="PAYMENT_STATUS_TAG[log.toStatus] ?? 'info'">
                {{ PAYMENT_STATUS_LABEL[log.toStatus] ?? log.toStatus }}
              </el-tag>
            </div>
            <div class="timeline-meta">
              {{ log.reason ?? '—' }}（操作：{{ log.operator }}）
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无状态留痕" />
      </el-tab-pane>

      <!-- 渠道交互记录 -->
      <el-tab-pane label="渠道交互" name="transactions">
        <el-table v-if="detail.transactions.length" :data="detail.transactions" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="payload-block">
                <h5>请求报文</h5>
                <pre>{{ prettyPayload(row.requestPayload) }}</pre>
                <h5>响应报文</h5>
                <pre>{{ prettyPayload(row.responsePayload) }}</pre>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="channelCode" label="渠道" width="130" />
          <el-table-column label="类型" width="100" align="center">
            <template #default="{ row }">{{ TXN_TYPE_LABEL[row.txnType] ?? row.txnType }}</template>
          </el-table-column>
          <el-table-column prop="channelOrderNo" label="渠道单号" min-width="170">
            <template #default="{ row }">{{ row.channelOrderNo ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="170" :formatter="dateTimeFormatter" />
        </el-table>
        <el-empty v-else description="暂无渠道交互记录" />
      </el-tab-pane>
    </el-tabs>

    <RefundCreateDialog ref="refundDialog" @created="onRefundCreated" />
  </el-drawer>
</template>

<style scoped>
.tab-toolbar {
  display: flex;
  margin-bottom: 12px;
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

.payload-block h5 {
  margin: 8px 0 4px;
}

.payload-block pre {
  margin: 0;
  padding: 8px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
