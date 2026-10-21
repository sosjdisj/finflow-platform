<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { approveSettlement, executeSettlement, pageSettlements } from '@/api/settlement'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { Merchant, SettlementOrder } from '@/types/api'
import { SETTLEMENT_STATUS_TEXT, isApprovable, isExecutable, settlementStatusTag } from '@/utils/settlementDisplay'
import SettlementDetailDrawer from './components/SettlementDetailDrawer.vue'

/**
 * 结算管理列表：结算单号 / 商户 / 结算日 / 状态筛选 + 分页（兼结算历史）。
 * 审核与执行在此操作：CALCULATED 可审核，APPROVED 可执行，FAILED 可重试；
 * 执行成功即商户入账，详情抽屉看逐笔明细与状态时间线。
 */
const merchantId = ref<number | null>(null)
const settleNo = ref('')
const settleDate = ref<string | null>(null)
const status = ref('')

const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const paging = usePaging<SettlementOrder>((page, size) =>
  pageSettlements({
    page,
    size,
    merchantId: merchantId.value ?? undefined,
    settleNo: settleNo.value || undefined,
    settleDate: settleDate.value ?? undefined,
    status: status.value || undefined,
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
  settleNo.value = ''
  settleDate.value = null
  status.value = ''
  void paging.reload()
}

async function approve(row: SettlementOrder): Promise<void> {
  await ElMessageBox.confirm(
    `确认审核通过结算单 ${row.settleNo}（${fenToYuan(row.totalAmount)} 元）？审核通过后才能执行打款。`,
    '结算审核',
    { confirmButtonText: '通过', cancelButtonText: '取消', type: 'warning' },
  )
  await approveSettlement(row.id)
  ElMessage.success('审核通过')
  void paging.reload()
}

async function execute(row: SettlementOrder): Promise<void> {
  await ElMessageBox.confirm(
    `确认执行打款 ${row.settleNo}？商户账户将入账 ${fenToYuan(row.totalAmount)} 元，执行失败可重试。`,
    '结算执行',
    { confirmButtonText: '执行', cancelButtonText: '取消', type: 'warning' },
  )
  await executeSettlement(row.id)
  ElMessage.success('执行成功，商户已入账')
  void paging.reload()
}

const detailDrawer = ref<InstanceType<typeof SettlementDetailDrawer>>()

function openDetail(row: SettlementOrder): void {
  void detailDrawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="status" class="status" placeholder="全部状态" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in SETTLEMENT_STATUS_TEXT" :key="value" :label="label" :value="value" />
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
        v-model="settleNo"
        class="settle-no"
        placeholder="结算单号"
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
      <el-table-column prop="settleNo" label="结算单号" width="190" />
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
      </el-table-column>
      <el-table-column prop="settleDate" label="结算日" width="110" />
      <el-table-column prop="orderCount" label="订单笔数" width="90" align="right" />
      <el-table-column label="结算金额" width="140" align="right">
        <template #default="{ row }">{{ fenToYuan(row.totalAmount) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="settlementStatusTag(row.status)">
            {{ SETTLEMENT_STATUS_TEXT[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="approvedBy" label="审核人" width="100" />
      <el-table-column prop="executedAt" label="入账时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="isApprovable(row.status)"
            link
            type="warning"
            v-permission="'settlement:settle:approve'"
            @click="approve(row)"
          >
            审核
          </el-button>
          <el-button
            v-if="isExecutable(row.status)"
            link
            :type="row.status === 'FAILED' ? 'danger' : 'success'"
            v-permission="'settlement:settle:execute'"
            @click="execute(row)"
          >
            {{ row.status === 'FAILED' ? '重试' : '执行' }}
          </el-button>
          <el-button link type="primary" v-permission="'settlement:settle:query'" @click="openDetail(row)">
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

  <SettlementDetailDrawer ref="detailDrawer" />
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

.settle-no {
  width: 190px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
