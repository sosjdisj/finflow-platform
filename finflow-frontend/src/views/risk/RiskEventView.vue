<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { riskApi } from '@/api/risk'
import { usePaging } from '@/composables/usePaging'
import { fenToYuan, dateTimeFormatter } from '@/utils/format'
import { riskVerdictTagType, riskVerdictText } from '@/utils/riskDisplay'
import type { RiskEvent } from '@/types/api'

/**
 * 风险事件列表：风控每次检查的留痕（只增不改），命中规则明细为 JSON 快照，点击行查看详情。
 */
const orderId = ref<number | null>(null)
const merchantId = ref<number | null>(null)
const verdict = ref('')
const createdRange = ref<[string, string] | null>(null)

const paging = usePaging<RiskEvent>((page, size) =>
  riskApi.eventPage(page, size, {
    orderId: orderId.value ?? undefined,
    merchantId: merchantId.value ?? undefined,
    verdict: verdict.value || undefined,
    createdFrom: createdRange.value?.[0],
    createdTo: createdRange.value?.[1],
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  orderId.value = null
  merchantId.value = null
  verdict.value = ''
  createdRange.value = null
  void paging.reload()
}

// ---------- 命中明细 ----------
const detailVisible = ref(false)
const detail = ref<RiskEvent | null>(null)
const detailHits = ref<{ ruleName: string; ruleType: string; score: number; detail: string }[]>([])

interface HitRuleRaw {
  ruleName?: unknown
  ruleType?: unknown
  score?: unknown
  detail?: unknown
}

function openDetail(row: RiskEvent): void {
  detail.value = row
  try {
    const parsed = row.hitRules ? (JSON.parse(row.hitRules) as HitRuleRaw[]) : []
    detailHits.value = parsed.map((h) => ({
      ruleName: String(h.ruleName ?? ''),
      ruleType: String(h.ruleType ?? ''),
      score: Number(h.score ?? 0),
      detail: String(h.detail ?? ''),
    }))
  } catch {
    detailHits.value = []
  }
  detailVisible.value = true
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input-number v-model="orderId" class="w180" :controls="false" placeholder="订单 ID" />
      <el-input-number v-model="merchantId" class="w180" :controls="false" placeholder="商户 ID" />
      <el-select v-model="verdict" class="w150" placeholder="判决" clearable @change="paging.reload()">
        <el-option label="放行" value="ALLOW" />
        <el-option label="待人工审核" value="REVIEW" />
        <el-option label="拒绝" value="REJECT" />
      </el-select>
      <el-date-picker
        v-model="createdRange"
        type="datetimerange"
        start-placeholder="创建起"
        end-placeholder="创建止"
        value-format="YYYY-MM-DDTHH:mm:ss"
        @change="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border @row-click="openDetail">
      <el-table-column prop="eventNo" label="事件号" width="190" />
      <el-table-column prop="orderId" label="订单 ID" width="90" />
      <el-table-column prop="merchantId" label="商户 ID" width="90" />
      <el-table-column label="金额" width="120" align="right">
        <template #default="{ row }">￥{{ fenToYuan(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="命中数" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.hitRules">{{ JSON.parse(row.hitRules).length }}</span>
          <span v-else class="muted">0</span>
        </template>
      </el-table-column>
      <el-table-column prop="totalScore" label="总分" width="80" align="center" />
      <el-table-column label="判决" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="riskVerdictTagType(row.verdict)" size="small">{{ riskVerdictText(row.verdict) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="clientIp" label="IP" width="130" show-overflow-tooltip />
      <el-table-column prop="deviceId" label="设备号" width="130" show-overflow-tooltip />
      <el-table-column label="检查时间" width="170" :formatter="dateTimeFormatter" prop="createdAt" />
    </el-table>
    <div class="row-tip">点击行查看命中规则明细</div>

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

  <el-dialog v-model="detailVisible" title="风险事件详情" width="640px">
    <el-descriptions v-if="detail" :column="2" border size="small">
      <el-descriptions-item label="事件号">{{ detail.eventNo }}</el-descriptions-item>
      <el-descriptions-item label="检查时间">{{ detail.createdAt }}</el-descriptions-item>
      <el-descriptions-item label="订单 ID">{{ detail.orderId }}</el-descriptions-item>
      <el-descriptions-item label="商户 ID">{{ detail.merchantId }}</el-descriptions-item>
      <el-descriptions-item label="金额">￥{{ fenToYuan(detail.amount) }}</el-descriptions-item>
      <el-descriptions-item label="总分">{{ detail.totalScore }}</el-descriptions-item>
      <el-descriptions-item label="判决">
        <el-tag :type="riskVerdictTagType(detail.verdict)" size="small">{{ riskVerdictText(detail.verdict) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="IP / 设备">{{ detail.clientIp || '-' }} / {{ detail.deviceId || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="detailHits" border size="small" class="hit-table" empty-text="未命中任何规则">
      <el-table-column prop="ruleName" label="规则名称" min-width="140" />
      <el-table-column prop="ruleType" label="类型" width="170" />
      <el-table-column prop="score" label="分值" width="70" align="center" />
      <el-table-column prop="detail" label="命中说明" min-width="200" show-overflow-tooltip />
    </el-table>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
  align-items: center;
}

.w180 {
  width: 180px;
}

.w150 {
  width: 150px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.row-tip {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.hit-table {
  margin-top: 12px;
}

.muted {
  color: var(--el-text-color-secondary);
}
</style>
