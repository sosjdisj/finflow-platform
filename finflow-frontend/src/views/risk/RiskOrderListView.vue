<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { riskApi, riskOrderApi } from '@/api/risk'
import { usePaging } from '@/composables/usePaging'
import { fenToYuan, dateTimeFormatter } from '@/utils/format'
import type { PaymentOrder } from '@/types/api'

/**
 * 风险订单（风控 REVIEW 待人工审核）：通过 → 订单回 PENDING 并直接渠道路由（人工放行即风控结论，
 * 不重查风控）；拒绝 → 订单置 FAILED。列表复用支付订单分页接口（status=REVIEW）。
 */
const keyword = ref('')

const paging = usePaging<PaymentOrder>((page, size) => riskOrderApi.page(page, size, keyword.value))
onMounted(paging.load)

function resetFilters(): void {
  keyword.value = ''
  void paging.reload()
}

// ---------- 人工通过 / 拒绝 ----------
const handlingId = ref<number | null>(null)

async function approve(row: PaymentOrder): Promise<void> {
  const { value } = await ElMessageBox.prompt(
    `确认放行订单 ${row.platformOrderNo || row.merchantOrderNo}？通过后将直接路由渠道收款（不再重查风控）。`,
    '人工通过',
    { type: 'warning', inputPlaceholder: '处理说明（可选）', inputValue: '' },
  )
  handlingId.value = row.id
  try {
    await riskApi.approveOrder(row.id, value?.trim() || undefined)
    ElMessage.success('已通过，订单继续渠道受理')
    await paging.reload()
  } finally {
    handlingId.value = null
  }
}

async function reject(row: PaymentOrder): Promise<void> {
  const { value } = await ElMessageBox.prompt(
    `确认拒绝订单 ${row.platformOrderNo || row.merchantOrderNo}？拒绝后订单置为失败，不可恢复。`,
    '人工拒绝',
    { type: 'warning', inputPlaceholder: '拒绝原因（可选）', inputValue: '' },
  )
  handlingId.value = row.id
  try {
    await riskApi.rejectOrder(row.id, value?.trim() || undefined)
    ElMessage.success('已拒绝，订单置为失败')
    await paging.reload()
  } finally {
    handlingId.value = null
  }
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="keyword" class="w260" placeholder="商户单号 / 平台单号 / 商户 ID" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-alert class="tip" type="info" :closable="false" title="以下订单命中风控规则进入人工审核（REVIEW），请结合风险事件判断后通过或拒绝。" />

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="id" label="订单 ID" width="90" />
      <el-table-column prop="merchantOrderNo" label="商户单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="platformOrderNo" label="平台单号" min-width="180" show-overflow-tooltip />
      <el-table-column prop="merchantId" label="商户 ID" width="90" />
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">￥{{ fenToYuan(row.amount) }}</template>
      </el-table-column>
      <el-table-column prop="paymentMethod" label="支付方式" width="110" />
      <el-table-column prop="subject" label="商品" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag type="warning" size="small">待人工审核</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" :formatter="dateTimeFormatter" prop="createdAt" />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="success"
            :loading="handlingId === row.id"
            v-permission="'risk:order:handle'"
            @click="approve(row)"
          >
            通过
          </el-button>
          <el-button
            link
            type="danger"
            :loading="handlingId === row.id"
            v-permission="'risk:order:handle'"
            @click="reject(row)"
          >
            拒绝
          </el-button>
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
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.tip {
  margin-bottom: 12px;
}

.w260 {
  width: 260px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
