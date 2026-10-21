<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile, UploadRawFile } from 'element-plus'
import { reconciliationApi } from '@/api/reconciliation'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import { reconResultText, reconTaskStatusText } from '@/utils/reconciliationDisplay'
import type { ReconciliationTask } from '@/types/api'

/**
 * 对账任务列表：上传渠道账单 → 同步解析 → 执行匹配 → 钻入对账结果页。
 * 五类结果汇总在任务行内展示；解析失败原因用 tooltip 提示。
 */
const router = useRouter()

const taskNo = ref('')
const channelCode = ref('')
const billDate = ref<string | null>(null)
const status = ref('')

const paging = usePaging<ReconciliationTask>((page, size) =>
  reconciliationApi.taskPage(page, size, {
    taskNo: taskNo.value || undefined,
    channelCode: channelCode.value || undefined,
    billDate: billDate.value ?? undefined,
    status: status.value || undefined,
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  taskNo.value = ''
  channelCode.value = ''
  billDate.value = null
  status.value = ''
  void paging.reload()
}

// ---------- 上传账单 ----------
const uploadVisible = ref(false)
const uploadForm = ref({ channelCode: '', billDate: '' })
const uploadFile = ref<File | null>(null)
const uploading = ref(false)

function openUpload(): void {
  uploadForm.value = { channelCode: '', billDate: '' }
  uploadFile.value = null
  uploadVisible.value = true
}

function onFileChange(file: UploadFile): void {
  uploadFile.value = (file.raw as UploadRawFile) ?? null
}

async function submitUpload(): Promise<void> {
  if (!uploadForm.value.channelCode || !uploadForm.value.billDate || !uploadFile.value) {
    ElMessage.warning('请填写渠道编码、账单日期并选择 CSV 文件')
    return
  }
  uploading.value = true
  try {
    const task = await reconciliationApi.upload(
      uploadFile.value,
      uploadForm.value.channelCode,
      uploadForm.value.billDate,
    )
    ElMessage.success(`账单解析完成：${task.validRows}/${task.totalRows} 行入库`)
    uploadVisible.value = false
    await paging.reload()
  } finally {
    uploading.value = false
  }
}

// ---------- 匹配 / 钻入结果 ----------
const matchingId = ref<number | null>(null)

async function runMatch(row: ReconciliationTask): Promise<void> {
  matchingId.value = row.id
  try {
    const summary = await reconciliationApi.match(row.id)
    ElMessage.success(
      `匹配完成：一致 ${summary.matchedCount}，金额不一致 ${summary.amountMismatchCount}，` +
        `状态不一致 ${summary.statusMismatchCount}，渠道有平台无 ${summary.missingInPlatformCount}，` +
        `平台有渠道无 ${summary.missingInChannelCount}`,
    )
    await paging.reload()
  } finally {
    matchingId.value = null
  }
}

function openResult(row: ReconciliationTask): void {
  void router.push(`/finance/reconciliations/${row.id}`)
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-input v-model="taskNo" class="w200" placeholder="任务号" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-input v-model="channelCode" class="w160" placeholder="渠道编码" clearable @keyup.enter="paging.reload()" @clear="paging.reload()" />
      <el-date-picker v-model="billDate" type="date" placeholder="账单日期" value-format="YYYY-MM-DD" @change="paging.reload()" />
      <el-select v-model="status" class="w140" placeholder="状态" clearable @change="paging.reload()">
        <el-option label="待解析" value="PENDING" />
        <el-option label="解析中" value="PARSING" />
        <el-option label="解析完成" value="COMPLETED" />
        <el-option label="解析失败" value="FAILED" />
      </el-select>
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
      <el-button type="primary" v-permission="'reconciliation:task:upload'" @click="openUpload">上传账单</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="taskNo" label="任务号" width="180" />
      <el-table-column prop="channelCode" label="渠道" width="120" />
      <el-table-column prop="billDate" label="账单日期" width="110" />
      <el-table-column label="解析" width="100" align="center">
        <template #default="{ row }">
          <el-tooltip :disabled="!row.errorMessage" :content="row.errorMessage" placement="top">
            <span>{{ row.validRows }}/{{ row.totalRows }} 行</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'" size="small">
            {{ reconTaskStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="匹配汇总（一致/金额/状态/缺平台/缺渠道）" min-width="220">
        <template #default="{ row }">
          <span v-if="row.matchedAt">
            {{ row.matchedCount }} / {{ row.amountMismatchCount }} / {{ row.statusMismatchCount }} /
            {{ row.missingInPlatformCount }} / {{ row.missingInChannelCount }}
          </span>
          <span v-else class="muted">未匹配</span>
        </template>
      </el-table-column>
      <el-table-column prop="uploadedBy" label="上传人" width="100" />
      <el-table-column label="匹配时间" width="170" :formatter="dateTimeFormatter" prop="matchedAt" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'COMPLETED'"
            link
            type="primary"
            :loading="matchingId === row.id"
            v-permission="'reconciliation:task:match'"
            @click="runMatch(row)"
          >
            {{ row.matchedAt ? '重新匹配' : '执行匹配' }}
          </el-button>
          <el-button v-if="row.matchedAt" link type="primary" @click="openResult(row)">查看结果</el-button>
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

  <el-dialog v-model="uploadVisible" title="上传渠道账单" width="440px">
    <el-form label-width="90px">
      <el-form-item label="渠道编码" required>
        <el-input v-model="uploadForm.channelCode" placeholder="如 MOCK_WALLET" />
      </el-form-item>
      <el-form-item label="账单日期" required>
        <el-date-picker v-model="uploadForm.billDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>
      <el-form-item label="账单文件" required>
        <el-upload
          :auto-upload="false"
          :limit="1"
          accept=".csv"
          :on-change="onFileChange"
          :on-remove="() => (uploadFile = null)"
        >
          <el-button>选择 CSV 文件</el-button>
          <template #tip>
            <div class="upload-tip">表头：channel_order_no,merchant_order_no,amount,status,bill_date；金额单位为分</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="uploadVisible = false">取消</el-button>
      <el-button type="primary" :loading="uploading" @click="submitUpload">上传并解析</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.w200 {
  width: 200px;
}

.w160 {
  width: 160px;
}

.w140 {
  width: 140px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.muted {
  color: var(--el-text-color-secondary);
}

.upload-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
