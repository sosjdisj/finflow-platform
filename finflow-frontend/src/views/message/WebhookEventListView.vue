<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { pageWebhookEvents, retryWebhookEvent } from '@/api/webhook'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import type { Merchant, WebhookEvent } from '@/types/api'
import {
  WEBHOOK_EVENT_TYPE_TEXT,
  WEBHOOK_STATUS_TEXT,
  isWebhookRetryable,
  webhookEventTypeText,
  webhookStatusTag,
  webhookStatusText,
} from '@/utils/messageDisplay'
import WebhookEventDetailDrawer from './components/WebhookEventDetailDrawer.vue'

/**
 * Webhook 投递日志：状态 / 商户 / 事件类型筛选 + 分页。
 * 待投递与重试耗尽的事件可手动重试（立即投递一次）；详情抽屉看报文全文与投递尝试记录。
 */
const status = ref('')
const eventType = ref('')
const merchantId = ref<number | null>(null)

const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const paging = usePaging<WebhookEvent>((page, size) =>
  pageWebhookEvents({
    page,
    size,
    status: status.value || undefined,
    eventType: eventType.value || undefined,
    merchantId: merchantId.value ?? undefined,
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
  eventType.value = ''
  merchantId.value = null
  void paging.reload()
}

async function retry(row: WebhookEvent): Promise<void> {
  await ElMessageBox.confirm(
    `确认手动重试事件 ${row.eventId}？将重置重试计数并立即投递一次。`,
    '手动重试',
    { confirmButtonText: '重试', cancelButtonText: '取消', type: 'warning' },
  )
  const latest = await retryWebhookEvent(row.id)
  ElMessage.success(
    latest.status === 'SUCCESS'
      ? '重试投递成功'
      : `本次投递失败（当前状态：${webhookStatusText(latest.status)}），可稍后再次重试`,
  )
  void paging.reload()
}

const detailDrawer = ref<InstanceType<typeof WebhookEventDetailDrawer>>()

function openDetail(row: WebhookEvent): void {
  void detailDrawer.value?.open(row.id)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="status" class="status" placeholder="全部状态" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in WEBHOOK_STATUS_TEXT" :key="value" :label="label" :value="value" />
      </el-select>
      <el-select v-model="eventType" class="event-type" placeholder="全部事件类型" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in WEBHOOK_EVENT_TYPE_TEXT" :key="value" :label="label" :value="value" />
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
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column label="事件类型" width="110">
        <template #default="{ row }">{{ webhookEventTypeText(row.eventType) }}</template>
      </el-table-column>
      <el-table-column label="商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.merchantId) }}</template>
      </el-table-column>
      <el-table-column prop="bizOrderNo" label="业务单号" width="190" />
      <el-table-column prop="eventId" label="事件 ID" width="280" show-overflow-tooltip />
      <el-table-column label="状态" width="120" align="center">
        <template #default="{ row }">
          <el-tag :type="webhookStatusTag(row.status)">{{ webhookStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="retryCount" label="已重试" width="80" align="center" />
      <el-table-column prop="nextRetryAt" label="下次重试时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="isWebhookRetryable(row.status)"
            link
            type="warning"
            v-permission="'webhook:event:retry'"
            @click="retry(row)"
          >
            重试
          </el-button>
          <el-button link type="primary" v-permission="'webhook:event:list'" @click="openDetail(row)">
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

  <WebhookEventDetailDrawer ref="detailDrawer" />
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.status {
  width: 150px;
}

.event-type {
  width: 140px;
}

.merchant {
  width: 180px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
