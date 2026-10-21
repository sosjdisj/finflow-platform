<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { dateTimeFormatter } from '@/utils/format'
import type { Merchant } from '@/types/api'
import { MERCHANT_STATUS_LABEL, MERCHANT_STATUS_TAG } from './merchantDisplay'

/**
 * 商户审核：待审核 / 审核中两个状态的商户列表，通过 → 生效、驳回 → 关闭（意见必填建议）。
 * 审核后从列表移除（状态已不在筛选范围）。
 */
const activeStatus = ref('PENDING')
const pagingPage = ref(1)
const pagingSize = ref(10)
const pagingTotal = ref(0)
const records = ref<Merchant[]>([])
const loading = ref(false)

async function load(): Promise<void> {
  loading.value = true
  try {
    const resp = await merchantApi.page(pagingPage.value, pagingSize.value, { status: activeStatus.value })
    records.value = resp.records
    pagingTotal.value = resp.total
  } finally {
    loading.value = false
  }
}

onMounted(load)

function switchStatus(status: string): void {
  activeStatus.value = status
  pagingPage.value = 1
  void load()
}

/** ---------- 审核动作（prompt 填意见，意见记入状态留痕） ---------- */
async function quickReview(row: Merchant, approve: boolean): Promise<void> {
  const action = approve ? '通过' : '驳回'
  const { value } = await ElMessageBox.prompt(
    `${action}商户「${row.merchantName}」，请输入审核意见（记入留痕）`,
    `审核${action}`,
    { inputValue: approve ? '资料齐全，审核通过' : '资料不实，审核驳回', type: 'warning' },
  )
  await merchantApi.review(row.id, approve, value?.trim() || undefined)
  ElMessage.success(`已${action}`)
  await load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-radio-group :model-value="activeStatus" @change="switchStatus">
        <el-radio-button value="PENDING">待审核</el-radio-button>
        <el-radio-button value="REVIEWING">审核中</el-radio-button>
      </el-radio-group>
    </div>

    <el-table v-loading="loading" :data="records" border>
      <el-table-column prop="merchantNo" label="商户编号" width="180" />
      <el-table-column prop="merchantName" label="商户名称" min-width="160" />
      <el-table-column prop="contactName" label="联系人" width="100" />
      <el-table-column prop="businessLicenseNo" label="统一社会信用代码" min-width="190" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="MERCHANT_STATUS_TAG[row.status] ?? 'info'">
            {{ MERCHANT_STATUS_LABEL[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="170" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'merchant:merchant:review'" link type="success" @click="quickReview(row, true)">
            通过
          </el-button>
          <el-button v-permission="'merchant:merchant:review'" link type="danger" @click="quickReview(row, false)">
            驳回
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pagingPage"
      v-model:page-size="pagingSize"
      class="pager"
      layout="total, prev, pager, next"
      :total="pagingTotal"
      @current-change="load()"
    />
  </el-card>
</template>

<style scoped>
.toolbar {
  display: flex;
  margin-bottom: 12px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
