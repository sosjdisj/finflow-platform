<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reconciliationApi } from '@/api/reconciliation'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import { reconResultTag, reconResultText } from '@/utils/reconciliationDisplay'
import type { ReconciliationRecord, ReconciliationTask } from '@/types/api'

/**
 * 对账结果页（任务列表钻入）：任务五类汇总 + 匹配记录分页（按结果筛选）。
 * 非 MATCHED 记录可发起差异处理，进入差异单人工闭环（S29）。
 */
const route = useRoute()
const router = useRouter()
const taskId = Number(route.params.id)

const task = ref<ReconciliationTask | null>(null)
const resultFilter = ref('')

const paging = usePaging<ReconciliationRecord>((page, size) =>
  reconciliationApi.recordPage(taskId, page, size, resultFilter.value || undefined),
)

onMounted(async () => {
  task.value = await reconciliationApi.taskDetail(taskId)
  await paging.load()
})

const summaryCards = computed(() => {
  const t = task.value
  if (!t || !t.matchedAt) return []
  return [
    { label: '匹配一致', value: t.matchedCount, type: 'success' as const },
    { label: '金额不一致', value: t.amountMismatchCount, type: 'danger' as const },
    { label: '状态不一致', value: t.statusMismatchCount, type: 'warning' as const },
    { label: '渠道有平台无', value: t.missingInPlatformCount, type: 'danger' as const },
    { label: '平台有渠道无', value: t.missingInChannelCount, type: 'warning' as const },
  ]
})

// 发起差异处理：成功后提示差异单号，可在「差异处理」页跟进
const creating = ref(false)

async function createDifference(row: ReconciliationRecord): Promise<void> {
  creating.value = true
  try {
    const diff = await reconciliationApi.createDifference(row.id)
    ElMessage.success(`差异单 ${diff.diffNo} 已发起，请在「差异处理」页跟进`)
  } finally {
    creating.value = false
  }
}

function back(): void {
  void router.push('/finance/reconciliations')
}
</script>

<template>
  <el-card v-if="task" shadow="never">
    <div class="header">
      <el-button link @click="back">← 返回任务列表</el-button>
      <span class="title">{{ task.taskNo }}</span>
      <el-tag size="small" type="info">{{ task.channelCode }}</el-tag>
      <span class="muted">账单日 {{ task.billDate }} · 上传人 {{ task.uploadedBy }}</span>
    </div>

    <div v-if="summaryCards.length" class="summary">
      <div v-for="card in summaryCards" :key="card.label" class="summary-item">
        <span class="summary-value">{{ card.value }}</span>
        <el-tag :type="card.type" size="small" effect="plain">{{ card.label }}</el-tag>
      </div>
    </div>
    <el-alert v-else title="该任务尚未执行匹配，请回任务列表执行" type="info" :closable="false" class="summary" />

    <div class="toolbar">
      <el-radio-group v-model="resultFilter" @change="paging.reload()">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button value="MATCHED">一致</el-radio-button>
        <el-radio-button value="AMOUNT_MISMATCH">金额不一致</el-radio-button>
        <el-radio-button value="STATUS_MISMATCH">状态不一致</el-radio-button>
        <el-radio-button value="MISSING_IN_PLATFORM">渠道有平台无</el-radio-button>
        <el-radio-button value="MISSING_IN_CHANNEL">平台有渠道无</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column label="匹配结果" width="130" align="center">
        <template #default="{ row }">
          <el-tag :type="reconResultTag(row.result)" size="small">{{ reconResultText(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="merchantOrderNo" label="商户单号" min-width="160" />
      <el-table-column prop="channelOrderNo" label="渠道单号" min-width="150">
        <template #default="{ row }">{{ row.channelOrderNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="渠道侧" width="150">
        <template #default="{ row }">
          <span>{{ fenToYuan(row.billAmount) }}</span>
          <el-tag v-if="row.billStatus" size="small" class="side-tag">{{ row.billStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="平台侧" width="150">
        <template #default="{ row }">
          <span>{{ fenToYuan(row.platformAmount) }}</span>
          <el-tag v-if="row.platformStatus" size="small" class="side-tag">{{ row.platformStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="platformOrderNo" label="平台订单号" min-width="150">
        <template #default="{ row }">{{ row.platformOrderNo ?? '—' }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="差异说明" min-width="200" show-overflow-tooltip />
      <el-table-column label="匹配时间" width="170" :formatter="dateTimeFormatter" prop="createdAt" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.result !== 'MATCHED'"
            link
            type="primary"
            :loading="creating"
            v-permission="'reconciliation:diff:create'"
            @click="createDifference(row)"
          >
            发起差异处理
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
.header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary {
  display: flex;
  gap: 24px;
  margin: 16px 0;
}

.summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.summary-value {
  font-size: 22px;
  font-weight: 600;
}

.toolbar {
  margin-bottom: 12px;
}

.side-tag {
  margin-left: 4px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
