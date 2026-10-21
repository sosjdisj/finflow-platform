<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { reconciliationApi } from '@/api/reconciliation'
import { fenToYuan } from '@/utils/format'
import { reconDiffStatusText, reconHandleTypeText, reconResultText } from '@/utils/reconciliationDisplay'
import type { ReconciliationDifference } from '@/types/api'

/**
 * 差异处理抽屉（差异订单页内）：差异单详情 + 按状态流转。
 * PENDING：审核确认（选调账/补单）或忽略；CONFIRMED：执行处理（调账/补单登记），成功即关闭；终态只读。
 */
const emit = defineEmits<{ processed: [] }>()

const visible = ref(false)
const diff = ref<ReconciliationDifference | null>(null)
const submitting = ref(false)

// 审核确认表单
const confirmForm = reactive({ handleType: 'ADJUST', handleRemark: '' })
// 执行处理表单：调账（金额以元输入，提交转分，带符号）
const adjustForm = reactive({ accountId: undefined as number | undefined, amountYuan: 0, handleResult: '' })
// 忽略 / 补单
const ignoreForm = reactive({ remark: '' })
const supplementForm = reactive({ handleResult: '' })

const isOpen = computed(() => diff.value?.status === 'PENDING')
const isConfirmed = computed(() => diff.value?.status === 'CONFIRMED')
const handleType = computed(() => diff.value?.handleType)

async function open(id: number): Promise<void> {
  visible.value = true
  diff.value = await reconciliationApi.differenceDetail(id)
  confirmForm.handleType = diff.value.handleType ?? 'ADJUST'
  confirmForm.handleRemark = ''
  adjustForm.accountId = undefined
  adjustForm.amountYuan = 0
  adjustForm.handleResult = ''
  ignoreForm.remark = ''
  supplementForm.handleResult = ''
}

function changed(): void {
  diff.value = null
  visible.value = false
  emit('processed')
}

async function submitConfirm(): Promise<void> {
  if (!diff.value) return
  submitting.value = true
  try {
    diff.value = await reconciliationApi.confirmDifference(
      diff.value.id,
      confirmForm.handleType,
      confirmForm.handleRemark,
    )
    ElMessage.success('已确认处理方式')
  } finally {
    submitting.value = false
  }
}

async function submitIgnore(): Promise<void> {
  if (!diff.value) return
  submitting.value = true
  try {
    diff.value = await reconciliationApi.ignoreDifference(diff.value.id, ignoreForm.remark)
    ElMessage.success('差异单已忽略')
    changed()
  } finally {
    submitting.value = false
  }
}

async function submitHandle(): Promise<void> {
  if (!diff.value) return
  submitting.value = true
  try {
    if (handleType.value === 'ADJUST') {
      if (!adjustForm.accountId || adjustForm.amountYuan === 0) {
        ElMessage.warning('请填写目标账户与带符号金额（正调增、负调减）')
        return
      }
      diff.value = await reconciliationApi.handleDifference(diff.value.id, {
        accountId: adjustForm.accountId,
        amount: Math.round(adjustForm.amountYuan * 100),
        handleResult: adjustForm.handleResult || undefined,
      })
    } else {
      if (!supplementForm.handleResult) {
        ElMessage.warning('请填写补录凭证（如补录的平台订单号）')
        return
      }
      diff.value = await reconciliationApi.handleDifference(diff.value.id, {
        handleResult: supplementForm.handleResult,
      })
    }
    ElMessage.success('处理完成，差异单已关闭')
    changed()
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>

<template>
  <el-drawer v-model="visible" :title="diff ? `差异单 ${diff.diffNo}` : '差异处理'" size="520px">
    <el-descriptions v-if="diff" :column="1" border>
      <el-descriptions-item label="状态">
        <el-tag size="small">{{ reconDiffStatusText(diff.status) }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="差异类型">{{ reconResultText(diff.diffType) }}</el-descriptions-item>
      <el-descriptions-item label="商户单号">{{ diff.merchantOrderNo }}</el-descriptions-item>
      <el-descriptions-item label="渠道单号">{{ diff.channelOrderNo ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="平台订单号">{{ diff.platformOrderNo ?? '—' }}</el-descriptions-item>
      <el-descriptions-item label="渠道侧">
        {{ fenToYuan(diff.billAmount) }} {{ diff.billStatus ? `(${diff.billStatus})` : '' }}
      </el-descriptions-item>
      <el-descriptions-item label="平台侧">
        {{ fenToYuan(diff.platformAmount) }} {{ diff.platformStatus ? `(${diff.platformStatus})` : '' }}
      </el-descriptions-item>
      <el-descriptions-item label="差异说明">{{ diff.remark ?? '—' }}</el-descriptions-item>
      <el-descriptions-item v-if="diff.handleType" label="处理方式">
        {{ reconHandleTypeText(diff.handleType) }}
      </el-descriptions-item>
      <el-descriptions-item v-if="diff.handleRemark" label="审核说明">{{ diff.handleRemark }}</el-descriptions-item>
      <el-descriptions-item v-if="diff.handleResult" label="处理结果">{{ diff.handleResult }}</el-descriptions-item>
      <el-descriptions-item v-if="diff.ledgerNo" label="调账流水号">{{ diff.ledgerNo }}</el-descriptions-item>
    </el-descriptions>

    <!-- 待审核：确认处理方式 / 忽略 -->
    <template v-if="isOpen">
      <div class="section-title">审核确认</div>
      <el-form label-width="90px">
        <el-form-item label="处理方式">
          <el-radio-group v-model="confirmForm.handleType">
            <el-radio value="ADJUST">调账（走资金流水）</el-radio>
            <el-radio value="SUPPLEMENT">补单（登记补录凭证）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核说明">
          <el-input v-model="confirmForm.handleRemark" type="textarea" :rows="2" placeholder="差异根因、核实结论等" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" v-permission="'reconciliation:diff:handle'" @click="submitConfirm">确认</el-button>
        </el-form-item>
      </el-form>

      <div class="section-title">误报忽略</div>
      <el-form label-width="90px">
        <el-form-item label="忽略说明">
          <el-input v-model="ignoreForm.remark" placeholder="如：渠道侧账务错误，线下催正" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="submitting" v-permission="'reconciliation:diff:handle'" @click="submitIgnore">忽略此差异</el-button>
        </el-form-item>
      </el-form>
    </template>

    <!-- 已确认：执行处理 -->
    <template v-else-if="isConfirmed">
      <div class="section-title">执行处理（成功即关闭）</div>
      <el-form v-if="handleType === 'ADJUST'" label-width="90px">
        <el-form-item label="目标账户">
          <el-input-number v-model="adjustForm.accountId" :min="1" :controls="false" placeholder="账户 ID（见资金流水页）" class="w-full" />
        </el-form-item>
        <el-form-item label="调账金额">
          <el-input-number v-model="adjustForm.amountYuan" :step="0.01" placeholder="元，正调增 / 负调减" class="w-full" />
        </el-form-item>
        <el-form-item label="处理说明">
          <el-input v-model="adjustForm.handleResult" type="textarea" :rows="2" placeholder="调账原因，将随流水留痕" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" v-permission="'reconciliation:diff:handle'" @click="submitHandle">执行调账并关闭</el-button>
        </el-form-item>
      </el-form>
      <el-form v-else label-width="90px">
        <el-form-item label="补录凭证">
          <el-input v-model="supplementForm.handleResult" type="textarea" :rows="2" placeholder="补录的平台订单号或线下补录说明" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" v-permission="'reconciliation:diff:handle'" @click="submitHandle">登记补单并关闭</el-button>
        </el-form-item>
      </el-form>
    </template>

    <el-alert
      v-else-if="diff"
      :title="`差异单已${diff.status === 'CLOSED' ? '关闭' : '忽略'}，仅可查看`"
      type="info"
      :closable="false"
    />
  </el-drawer>
</template>

<style scoped>
.section-title {
  font-weight: 600;
  margin: 20px 0 12px;
}

.w-full {
  width: 100%;
}
</style>
