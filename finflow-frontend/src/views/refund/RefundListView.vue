<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { merchantApi } from '@/api/merchant'
import { refundApi } from '@/api/refund'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { Merchant, RefundOrder } from '@/types/api'
import { REFUND_STATUS_LABEL, REFUND_STATUS_TAG } from './refundDisplay'
import RefundCreateDialog from './components/RefundCreateDialog.vue'
import RefundOrderDetailDrawer from './components/RefundOrderDetailDrawer.vue'

/**
 * 退款管理列表：状态 / 商户 / 退款单号 / 时间筛选 + 分页。
 * 发起退款与详情分别走共用弹窗与详情抽屉；退款结果由渠道异步回调驱动，列表可刷新跟进。
 */
const status = ref('')
const merchantId = ref<number | null>(null)
const refundNo = ref('')
const timeRange = ref<[string, string] | null>(null)

const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const paging = usePaging<RefundOrder>((page, size) =>
  refundApi.page(page, size, {
    status: status.value || undefined,
    merchantId: merchantId.value ?? undefined,
    refundNo: refundNo.value || undefined,
    createdFrom: timeRange.value?.[0],
    createdTo: timeRange.value?.[1],
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
  refundNo.value = ''
  timeRange.value = null
  void paging.reload()
}

const createDialog = ref<InstanceType<typeof RefundCreateDialog>>()
const detailDrawer = ref<InstanceType<typeof RefundOrderDetailDrawer>>()

function openDetail(row: RefundOrder): void {
  void detailDrawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="status" class="status" placeholder="全部状态" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in REFUND_STATUS_LABEL" :key="value" :label="label" :value="value" />
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
      <el-input
        v-model="refundNo"
        class="refund-no"
        placeholder="退款单号"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="创建开始"
        end-placeholder="创建结束"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="warning" v-permission="'refund:order:create'" @click="createDialog?.open()">
        发起退款
      </el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="refundNo" label="退款单号" width="180" />
      <el-table-column prop="platformOrderNo" label="平台订单号" width="180" />
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
      </el-table-column>
      <el-table-column label="退款金额" width="130" align="right">
        <template #default="{ row }">{{ fenToYuan(row.refundAmount) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="REFUND_STATUS_TAG[row.status] ?? 'info'">
            {{ REFUND_STATUS_LABEL[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="发起人" width="110" />
      <el-table-column prop="createdAt" label="申请时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column prop="refundedAt" label="成功时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" v-permission="'refund:order:query'" @click="openDetail(row)">
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

  <RefundCreateDialog ref="createDialog" @created="paging.reload()" />
  <RefundOrderDetailDrawer ref="detailDrawer" />
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.status {
  width: 130px;
}

.merchant {
  width: 180px;
}

.refund-no {
  width: 180px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
