<script setup lang="ts">
import { ref } from 'vue'
import { getWebhookEventDetail } from '@/api/webhook'
import { dateTimeFormatter } from '@/utils/format'
import type { WebhookEventDetail } from '@/types/api'

/**
 * Webhook 事件详情抽屉：投递报文全文 + 投递尝试记录（一次一行）。
 */
defineOptions({ name: 'WebhookEventDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const detail = ref<WebhookEventDetail | null>(null)

async function open(id: number): Promise<void> {
  visible.value = true
  loading.value = true
  try {
    detail.value = await getWebhookEventDetail(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })

function prettyPayload(payload: string): string {
  try {
    return JSON.stringify(JSON.parse(payload), null, 2)
  } catch {
    return payload
  }
}
</script>

<template>
  <el-drawer v-model="visible" title="Webhook 事件详情" size="640px">
    <div v-loading="loading">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="事件类型">{{ detail.event.eventType }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ detail.event.status }}</el-descriptions-item>
          <el-descriptions-item label="商户 ID">{{ detail.event.merchantId }}</el-descriptions-item>
          <el-descriptions-item label="业务单号">{{ detail.event.bizOrderNo }}</el-descriptions-item>
          <el-descriptions-item label="事件 ID" :span="2">{{ detail.event.eventId }}</el-descriptions-item>
          <el-descriptions-item label="已重试次数">{{ detail.event.retryCount }}</el-descriptions-item>
          <el-descriptions-item label="下次重试时间">
            {{ detail.event.nextRetryAt ?? '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <h4 class="section">投递报文（与实际 POST 内容一致）</h4>
        <pre class="payload">{{ prettyPayload(detail.payload) }}</pre>

        <h4 class="section">投递尝试记录</h4>
        <el-table :data="detail.deliveries" border size="small">
          <el-table-column prop="attemptNo" label="次序" width="60" align="center" />
          <el-table-column label="结果" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="响应码" width="80" align="center">
            <template #default="{ row }">{{ row.responseCode ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="耗时" width="90" align="right">
            <template #default="{ row }">{{ row.durationMs != null ? `${row.durationMs}ms` : '—' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="投递时间" width="170" :formatter="dateTimeFormatter" />
          <el-table-column label="响应体" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">{{ row.responseBody || '—' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
  </el-drawer>
</template>

<style scoped>
.section {
  margin: 16px 0 8px;
}

.payload {
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
  max-height: 260px;
  overflow: auto;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
