<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ledgerApi } from '@/api/ledger'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan, signedYuan } from '@/utils/format'
import type { LedgerRecord } from '@/types/api'
import { BIZ_TYPE_LABEL, SOURCE_LABEL } from './financeDisplay'

/**
 * 资金流水：账户ID / 业务类型 / 业务单号 / 时间筛选 + 分页。
 * amount 带符号（正=入账绿，负=出账红）；从账户列表「查流水」跳入时带 accountId 初始筛选。
 */
const route = useRoute()

const accountId = ref<number | null>(
  route.query.accountId ? Number(route.query.accountId) : null,
)
const bizType = ref('')
const bizNo = ref('')
const timeRange = ref<[string, string] | null>(null)

const paging = usePaging<LedgerRecord>((page, size) =>
  ledgerApi.recordPage(page, size, {
    accountId: accountId.value ?? undefined,
    bizType: bizType.value || undefined,
    bizNo: bizNo.value || undefined,
    createdFrom: timeRange.value?.[0],
    createdTo: timeRange.value?.[1],
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  accountId.value = null
  bizType.value = ''
  bizNo.value = ''
  timeRange.value = null
  void paging.reload()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input-number
        v-model="accountId"
        class="account-id"
        :min="1"
        :controls="false"
        placeholder="账户ID"
        @keyup.enter="paging.reload()"
        @change="paging.reload()"
      />
      <el-select v-model="bizType" class="biz-type" placeholder="全部业务类型" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in BIZ_TYPE_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
      <el-input
        v-model="bizNo"
        class="biz-no"
        placeholder="业务单号"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="id" label="流水ID" width="90" />
      <el-table-column prop="accountId" label="账户ID" width="90" />
      <el-table-column label="业务类型" width="110" align="center">
        <template #default="{ row }">{{ BIZ_TYPE_LABEL[row.bizType] ?? row.bizType }}</template>
      </el-table-column>
      <el-table-column prop="bizNo" label="业务单号" min-width="200" show-overflow-tooltip />
      <el-table-column label="变动金额" width="130" align="right">
        <template #default="{ row }">
          <span :class="row.amount > 0 ? 'amount-in' : 'amount-out'">
            {{ signedYuan(row.amount) }} {{ row.currency }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="变动前余额" width="120" align="right">
        <template #default="{ row }">{{ fenToYuan(row.balanceBefore) }}</template>
      </el-table-column>
      <el-table-column label="变动后余额" width="120" align="right">
        <template #default="{ row }">{{ fenToYuan(row.balanceAfter) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="100" align="center">
        <template #default="{ row }">{{ SOURCE_LABEL[row.source] ?? row.source }}</template>
      </el-table-column>
      <el-table-column label="备注" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.remark || '—' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" width="170" :formatter="dateTimeFormatter" />
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

.account-id :deep(.el-input__inner) {
  text-align: left;
}

.account-id {
  width: 120px;
}

.biz-type {
  width: 140px;
}

.biz-no {
  width: 200px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.amount-in {
  color: var(--el-color-success);
}

.amount-out {
  color: var(--el-color-danger);
}
</style>
