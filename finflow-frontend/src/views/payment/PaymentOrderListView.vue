<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { merchantApi } from '@/api/merchant'
import { paymentApi } from '@/api/payment'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { Merchant, PaymentOrder } from '@/types/api'
import { PAYMENT_METHOD_LABEL, PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TAG } from './paymentDisplay'
import PaymentOrderCreateDialog from './components/PaymentOrderCreateDialog.vue'
import PaymentOrderDetailDrawer from './components/PaymentOrderDetailDrawer.vue'

/**
 * 支付订单列表：状态 / 商户 / 时间 / 金额 / 订单号筛选 + 分页。
 * 商户列名称来自下拉选项缓存；完整生命周期在详情抽屉里追。
 * 「创建订单」为运营侧代客下单入口（复用商户侧下单接口，便于联调造单）。
 */
const status = ref('')
const merchantId = ref<number | null>(null)
const timeRange = ref<[string, string] | null>(null)
const minAmountYuan = ref<number | null>(null)
const maxAmountYuan = ref<number | null>(null)
const keyword = ref('')

/** 商户下拉（远程搜索）；选项同时充当下单商户 id→名称映射。 */
const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const paging = usePaging<PaymentOrder>((page, size) =>
  paymentApi.page(page, size, {
    status: status.value || undefined,
    merchantId: merchantId.value ?? undefined,
    keyword: keyword.value || undefined,
    createdFrom: timeRange.value?.[0],
    createdTo: timeRange.value?.[1],
    // 金额筛选输入元、提交分（铁律 1）
    minAmount: minAmountYuan.value == null ? undefined : Math.round(minAmountYuan.value * 100),
    maxAmount: maxAmountYuan.value == null ? undefined : Math.round(maxAmountYuan.value * 100),
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
  status.value = ''
  merchantId.value = null
  timeRange.value = null
  minAmountYuan.value = null
  maxAmountYuan.value = null
  keyword.value = ''
  void paging.reload()
}

const detailDrawer = ref<InstanceType<typeof PaymentOrderDetailDrawer>>()
const createDialog = ref<InstanceType<typeof PaymentOrderCreateDialog>>()

function openDetail(row: PaymentOrder): void {
  void detailDrawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="status" class="status" placeholder="全部状态" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in PAYMENT_STATUS_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
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
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="创建开始"
        end-placeholder="创建结束"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <div class="amount-range">
        <el-input-number v-model="minAmountYuan" :min="0" :precision="2" :controls="false" placeholder="最小金额(元)" />
        <span class="separator">-</span>
        <el-input-number v-model="maxAmountYuan" :min="0" :precision="2" :controls="false" placeholder="最大金额(元)" />
      </div>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="商户订单号 / 平台订单号"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button v-permission="'payment:order:create'" type="primary" @click="createDialog?.open()">
        创建订单
      </el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="platformOrderNo" label="平台订单号" width="180" />
      <el-table-column prop="merchantOrderNo" label="商户订单号" width="180" />
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }">{{ fenToYuan(row.amount) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column label="支付方式" width="100" align="center">
        <template #default="{ row }">{{ PAYMENT_METHOD_LABEL[row.paymentMethod] ?? row.paymentMethod }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="PAYMENT_STATUS_TAG[row.status] ?? 'info'">
            {{ PAYMENT_STATUS_LABEL[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
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

  <PaymentOrderCreateDialog ref="createDialog" @created="paging.reload()" />
  <PaymentOrderDetailDrawer ref="detailDrawer" />
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.status {
  width: 120px;
}

.merchant {
  width: 180px;
}

.amount-range {
  display: flex;
  align-items: center;
}

.amount-range .el-input-number {
  width: 120px;
}

.separator {
  margin: 0 4px;
}

.search {
  width: 200px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
