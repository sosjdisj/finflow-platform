<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { merchantApi } from '@/api/merchant'
import { pageClearings } from '@/api/clearing'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { ClearingOrder, Merchant } from '@/types/api'
import { clearingStatusText } from '@/utils/clearingDisplay'
import ClearingDetailDrawer from './components/ClearingDetailDrawer.vue'

/**
 * 清分记录列表：商户 / 平台订单号 / 结算日筛选 + 分页。
 * 清分为只读数据（消费支付成功事件自动产生），页面回答「这笔钱怎么分」；
 * 汇总入口在结算管理页，此处不做操作。
 */
const merchantId = ref<number | null>(null)
const platformOrderNo = ref('')
const settleDate = ref<string | null>(null)

const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const paging = usePaging<ClearingOrder>((page, size) =>
  pageClearings({
    page,
    size,
    merchantId: merchantId.value ?? undefined,
    platformOrderNo: platformOrderNo.value || undefined,
    settleDate: settleDate.value ?? undefined,
  }),
)
onMounted(paging.load)

async function searchMerchants(query: string): Promise<void> {
  const resp = await merchantApi.page(1, 20, { status: 'ACTIVE', keyword: query || undefined })
  merchantOptions.value = resp.records
  for (const merchant of resp.records) {
    merchantNameMap[merchant.id] = merchant.merchantName
  }
}

function merchantName(id: number): string {
  return merchantNameMap[id] ?? `商户 #${id}`
}

function resetFilters(): void {
  merchantId.value = null
  platformOrderNo.value = ''
  settleDate.value = null
  void paging.reload()
}

const detailDrawer = ref<InstanceType<typeof ClearingDetailDrawer>>()

function openDetail(row: ClearingOrder): void {
  void detailDrawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select
        v-model="merchantId"
        class="merchant"
        placeholder="全部商户"
        clearable
        filterable
        remote
        :remote-method="searchMerchants"
        :loading="false"
        @change="paging.reload()"
        @focus="searchMerchants('')"
      >
        <el-option v-for="m in merchantOptions" :key="m.id" :label="m.merchantName" :value="m.id" />
      </el-select>
      <el-input
        v-model="platformOrderNo"
        class="order-no"
        placeholder="平台订单号"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-date-picker
        v-model="settleDate"
        type="date"
        placeholder="结算日"
        value-format="YYYY-MM-DD"
        @change="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="platformOrderNo" label="平台订单号" width="180" />
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
      </el-table-column>
      <el-table-column label="支付金额" width="130" align="right">
        <template #default="{ row }">{{ fenToYuan(row.payAmount) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column label="商户应得" width="120" align="right">
        <template #default="{ row }">{{ fenToYuan(row.merchantIncome) }}</template>
      </el-table-column>
      <el-table-column label="平台手续费" width="110" align="right">
        <template #default="{ row }">{{ fenToYuan(row.platformFee) }}</template>
      </el-table-column>
      <el-table-column label="渠道成本" width="100" align="right">
        <template #default="{ row }">{{ fenToYuan(row.channelCost) }}</template>
      </el-table-column>
      <el-table-column prop="channelCode" label="渠道" width="120" />
      <el-table-column prop="settleDate" label="结算日" width="110" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag type="success" size="small">{{ clearingStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="clearedAt" label="清分时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'clearing:clearing:query'" @click="openDetail(row)">
            详情
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

  <ClearingDetailDrawer ref="detailDrawer" />
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.merchant {
  width: 180px;
}

.order-no {
  width: 180px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
