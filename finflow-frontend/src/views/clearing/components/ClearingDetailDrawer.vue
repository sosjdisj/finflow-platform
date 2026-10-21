<template>
  <el-drawer v-model="visible" :title="detail ? `清分单：${detail.clearing.platformOrderNo}` : '清分单详情'" size="560px">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="平台订单号" :span="2">{{ detail.clearing.platformOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="商户 ID">{{ detail.clearing.merchantId }}</el-descriptions-item>
        <el-descriptions-item label="渠道">{{ detail.clearing.channelCode }}</el-descriptions-item>
        <el-descriptions-item label="支付金额">{{ fenToYuan(detail.clearing.payAmount) }} {{ detail.clearing.currency }}</el-descriptions-item>
        <el-descriptions-item label="结算日">{{ detail.clearing.settleDate }}</el-descriptions-item>
        <el-descriptions-item label="商户应得">{{ fenToYuan(detail.clearing.merchantIncome) }}</el-descriptions-item>
        <el-descriptions-item label="平台手续费">{{ fenToYuan(detail.clearing.platformFee) }}</el-descriptions-item>
        <el-descriptions-item label="渠道成本">{{ fenToYuan(detail.clearing.channelCost) }}</el-descriptions-item>
        <el-descriptions-item label="清分时间">
          {{ dateTimeFormatter(null, null, detail.clearing.clearedAt) }}
        </el-descriptions-item>
      </el-descriptions>

      <h4 class="section-title">分账明细</h4>
      <el-table :data="detail.details" size="small" border>
        <el-table-column label="方向" width="110">
          <template #default="{ row }">
            <el-tag :type="partyTypeTag(row.partyType)" size="small">{{ partyTypeText(row.partyType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="主体" min-width="120">
          <template #default="{ row }">{{ partyLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ fenToYuan(row.amount) }}</template>
        </el-table-column>
      </el-table>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { getClearingDetail } from '@/api/clearing'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { ClearingOrderDetail } from '@/types/api'
import { partyLabel, partyTypeTag, partyTypeText } from '@/utils/clearingDisplay'

/**
 * 清分详情抽屉：清分单三向金额 + 固定 3 行分账明细。
 * 从清分记录列表跳入，核对一笔钱怎么分。
 */
const visible = ref(false)
const detail = ref<ClearingOrderDetail | null>(null)

async function open(id: number): Promise<void> {
  visible.value = true
  detail.value = await getClearingDetail(id)
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
</style>
