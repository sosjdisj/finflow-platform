<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import {
  markAllNotificationsRead,
  markNotificationRead,
  pageNotifications,
} from '@/api/notification'
import { usePaging } from '@/composables/usePaging'
import { useUnreadCount } from '@/composables/useUnreadCount'
import { dateTimeFormatter } from '@/utils/format'
import type { Merchant, NotificationItem } from '@/types/api'
import {
  EMAIL_STATUS_TEXT,
  NOTIFICATION_TEMPLATE_TEXT,
  emailStatusTag,
  emailStatusText,
  notificationTemplateText,
} from '@/utils/messageDisplay'

/**
 * 站内信：模板类型 / 邮件状态 / 商户筛选 + 分页。
 * 未读行高亮，可单条标记已读或全部已读（管理端统一已读，标记后同步顶栏角标）。
 */
const templateCode = ref('')
const emailStatus = ref('')
const merchantId = ref<number | null>(null)

const merchantOptions = ref<Merchant[]>([])
const merchantNameMap = reactive<Record<number, string>>({})

const { refresh: refreshUnread } = useUnreadCount()

const paging = usePaging<NotificationItem>((page, size) =>
  pageNotifications({
    page,
    size,
    templateCode: templateCode.value || undefined,
    emailStatus: emailStatus.value || undefined,
    merchantId: merchantId.value ?? undefined,
  }),
)
onMounted(paging.load)

async function searchMerchants(query: string): Promise<void> {
  const resp = await merchantApi.page(1, 20, { keyword: query || undefined })
  merchantOptions.value = resp.records
  for (const merchant of resp.records) {
    merchantNameMap[merchant.id] = merchant.merchantName
  }
}

function merchantName(id: number): string {
  return merchantNameMap[id] ?? `商户 #${id}`
}

function resetFilters(): void {
  templateCode.value = ''
  emailStatus.value = ''
  merchantId.value = null
  void paging.reload()
}

async function markRead(row: NotificationItem): Promise<void> {
  await markNotificationRead(row.id)
  ElMessage.success('已标记已读')
  await refreshUnread()
  void paging.load()
}

async function markAll(): Promise<void> {
  await ElMessageBox.confirm('确认将全部未读站内信标记为已读？', '全部已读', {
    confirmButtonText: '确认', cancelButtonText: '取消', type: 'warning',
  })
  const marked = await markAllNotificationsRead()
  ElMessage.success(`已标记 ${marked} 条`)
  await refreshUnread()
  void paging.reload()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select
        v-model="templateCode"
        class="template"
        placeholder="全部类型"
        clearable
        @change="paging.reload()"
      >
        <el-option
          v-for="(label, value) in NOTIFICATION_TEMPLATE_TEXT"
          :key="value"
          :label="label"
          :value="value"
        />
      </el-select>
      <el-select
        v-model="emailStatus"
        class="email"
        placeholder="邮件状态"
        clearable
        @change="paging.reload()"
      >
        <el-option v-for="(label, value) in EMAIL_STATUS_TEXT" :key="value" :label="label" :value="value" />
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
      <el-button type="warning" plain class="read-all" v-permission="'notification:read'" @click="markAll">
        全部已读
      </el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border :row-class-name="({ row }: { row: NotificationItem }) => (row.readAt ? '' : 'unread-row')">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="expand">
            <p class="content">{{ row.content }}</p>
            <p v-if="row.emailError" class="email-error">邮件失败原因：{{ row.emailError }}</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">{{ notificationTemplateText(row.templateCode) }}</template>
      </el-table-column>
      <el-table-column label="收件商户" min-width="140">
        <template #default="{ row }">{{ merchantName(row.receiverId) }}</template>
      </el-table-column>
      <el-table-column label="邮件" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="emailStatusTag(row.emailStatus)" size="small">
            {{ emailStatusText(row.emailStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已读状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.readAt ? 'info' : 'danger'" size="small">
            {{ row.readAt ? '已读' : '未读' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="readAt" label="已读时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column prop="createdAt" label="发送时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="!row.readAt"
            link
            type="primary"
            v-permission="'notification:read'"
            @click="markRead(row)"
          >
            标记已读
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

.template {
  width: 130px;
}

.email {
  width: 160px;
}

.merchant {
  width: 180px;
}

.read-all {
  margin-left: auto;
}

.expand {
  padding: 8px 24px;
}

.content {
  margin: 0 0 4px;
}

.email-error {
  color: var(--el-color-danger);
  margin: 0;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

:deep(.unread-row) {
  font-weight: 600;
}
</style>
