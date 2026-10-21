<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { paymentApi } from '@/api/payment'
import { refundApi } from '@/api/refund'
import { fenToYuan } from '@/utils/format'
import type { PaymentOrder } from '@/types/api'
import { PAYMENT_STATUS_LABEL, PAYMENT_STATUS_TAG } from '@/views/payment/paymentDisplay'

/**
 * 发起退款弹窗：选择可退支付订单（或由订单详情带入）→ 输入退款金额（元，提交转分）与原因。
 * 剩余可退 = 支付金额 − 已申请退款（后端行锁内二次校验防并发超退，前端校验仅为体验）。
 * 列表页与支付订单详情抽屉共用本组件（铁律 5：公共交互不各写一套）。
 */
const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

/** 弹窗打开时带入的订单（从支付详情进入时非空）；为空时需在弹窗内搜索选择。 */
const presetOrder = ref<PaymentOrder | null>(null)
const selectedOrder = ref<PaymentOrder | null>(null)

const form = reactive({ amountYuan: null as number | null, reason: '' })

const emit = defineEmits<{ created: [] }>()

const order = computed(() => presetOrder.value ?? selectedOrder.value)
const remainingFen = computed(() =>
  order.value ? order.value.amount - (order.value.refundedAmount ?? 0) : 0,
)

const rules: FormRules = {
  amountYuan: [
    { required: true, message: '请输入退款金额', trigger: 'blur' },
    {
      validator: (_rule, value: number | null, callback) => {
        if (value == null || value <= 0) {
          callback(new Error('退款金额必须大于 0'))
        } else if (order.value && Math.round(value * 100) > remainingFen.value) {
          callback(new Error(`超过剩余可退金额 ${fenToYuan(remainingFen.value)} 元`))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function open(preset?: PaymentOrder): Promise<void> {
  presetOrder.value = preset ?? null
  selectedOrder.value = null
  form.amountYuan = null
  form.reason = ''
  visible.value = true
}

defineExpose({ open })

watch(visible, (open) => {
  if (!open) {
    presetOrder.value = null
    selectedOrder.value = null
  }
})

/** 远程搜索可退订单（已支付/退款中）；后端按订单号模糊匹配。 */
async function searchOrders(keyword: string): Promise<void> {
  if (!keyword.trim()) {
    return
  }
  const resp = await paymentApi.page(1, 20, { keyword: keyword.trim() })
  selectedOrderCandidates.value = resp.records.filter(
    (o) => (o.status === 'PAID' || o.status === 'REFUNDING') && o.amount - (o.refundedAmount ?? 0) > 0,
  )
}

const selectedOrderCandidates = ref<PaymentOrder[]>([])

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !order.value || form.amountYuan == null) {
    return
  }
  submitting.value = true
  try {
    await refundApi.create({
      orderId: order.value.id,
      amount: Math.round(form.amountYuan * 100),
      reason: form.reason.trim() || undefined,
    })
    ElMessage.success('退款已受理，结果以渠道回调为准')
    visible.value = false
    emit('created')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="发起退款" width="520px">
    <el-form v-if="!presetOrder" class="order-picker">
      <el-form-item label="支付订单" required>
        <el-select
          v-model="selectedOrder"
          value-key="id"
          filterable
          remote
          :remote-method="searchOrders"
          :loading="false"
          placeholder="输入平台/商户订单号搜索"
          style="width: 100%"
        >
          <el-option
            v-for="o in selectedOrderCandidates"
            :key="o.id"
            :label="`${o.platformOrderNo}（${fenToYuan(o.amount)} 元，可退 ${fenToYuan(o.amount - (o.refundedAmount ?? 0))}）`"
            :value="o"
          >
            <span>{{ o.platformOrderNo }}</span>
            <el-tag size="small" class="opt-tag" :type="PAYMENT_STATUS_TAG[o.status] ?? 'info'">
              {{ PAYMENT_STATUS_LABEL[o.status] ?? o.status }}
            </el-tag>
            <span class="opt-amount">{{ fenToYuan(o.amount - (o.refundedAmount ?? 0)) }} 元可退</span>
          </el-option>
        </el-select>
      </el-form-item>
    </el-form>

    <el-descriptions v-if="order" :column="2" border size="small" class="order-summary">
      <el-descriptions-item label="平台订单号" :span="2">{{ order.platformOrderNo }}</el-descriptions-item>
      <el-descriptions-item label="支付金额">{{ fenToYuan(order.amount) }} {{ order.currency }}</el-descriptions-item>
      <el-descriptions-item label="剩余可退">{{ fenToYuan(remainingFen) }} {{ order.currency }}</el-descriptions-item>
    </el-descriptions>

    <el-form v-if="order" ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="退款金额" prop="amountYuan">
        <el-input-number
          v-model="form.amountYuan"
          :min="0.01"
          :max="remainingFen / 100"
          :precision="2"
          :controls="false"
          placeholder="单位：元"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="退款原因">
        <el-input v-model="form.reason" type="textarea" :rows="2" maxlength="200" placeholder="选填" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!order" @click="submit">提交退款</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.order-picker {
  margin-bottom: 12px;
}

.order-summary {
  margin-bottom: 16px;
}

.opt-tag {
  margin-left: 8px;
}

.opt-amount {
  float: right;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
