<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { paymentApi } from '@/api/payment'
import type { Merchant } from '@/types/api'
import { PAYMENT_METHOD_LABEL } from '@/views/payment/paymentDisplay'

/**
 * 代客下单弹窗（运营侧测试/补单用，复用商户侧同一下单接口）。
 * 币种与过期时间不传，由后端取默认值（CNY / 系统配置的默认过期小时数）。
 * 创建后订单先 PENDING，约 5 秒由 Mock 渠道回调推进到已支付。
 */
const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

/** 商户下拉（远程搜索，仅生效中商户）。 */
const merchantOptions = ref<Merchant[]>([])

const form = reactive({
  merchantId: null as number | null,
  merchantOrderNo: '',
  amountYuan: null as number | null,
  subject: '',
  paymentMethod: 'ALIPAY',
})

const emit = defineEmits<{ created: [] }>()

const rules: FormRules = {
  merchantId: [{ required: true, message: '请选择商户', trigger: 'change' }],
  merchantOrderNo: [{ required: true, message: '请输入商户订单号', trigger: 'blur' }],
  amountYuan: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (_rule, value: number | null, callback) => {
        if (value == null || value <= 0) {
          callback(new Error('金额必须大于 0'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  subject: [{ required: true, message: '请输入商品标题', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择支付方式', trigger: 'change' }],
}

async function open(): Promise<void> {
  form.merchantId = null
  form.merchantOrderNo = `UI${Date.now()}`
  form.amountYuan = null
  form.subject = ''
  form.paymentMethod = 'ALIPAY'
  visible.value = true
}

defineExpose({ open })

async function searchMerchants(query: string): Promise<void> {
  const resp = await merchantApi.page(1, 20, { status: 'ACTIVE', keyword: query || undefined })
  merchantOptions.value = resp.records
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || form.merchantId == null || form.amountYuan == null) {
    return
  }
  submitting.value = true
  try {
    const created = await paymentApi.create({
      merchantId: form.merchantId,
      merchantOrderNo: form.merchantOrderNo.trim(),
      // 元转分提交（铁律 1：金额一律以分传输与存储）
      amount: Math.round(form.amountYuan * 100),
      subject: form.subject.trim(),
      paymentMethod: form.paymentMethod,
    })
    ElMessage.success(`订单已创建：${created.platformOrderNo}，约 5 秒后变为已支付`)
    visible.value = false
    emit('created')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="创建支付订单" width="560px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="商户" prop="merchantId">
        <el-select
          v-model="form.merchantId"
          filterable
          remote
          :remote-method="searchMerchants"
          :loading="false"
          placeholder="输入商户名称搜索（仅生效中商户）"
          style="width: 100%"
          @focus="searchMerchants('')"
        >
          <el-option
            v-for="m in merchantOptions"
            :key="m.id"
            :label="`${m.merchantName}（${m.merchantNo}）`"
            :value="m.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="商户订单号" prop="merchantOrderNo">
        <el-input v-model="form.merchantOrderNo" maxlength="64" placeholder="商户侧唯一单号" />
      </el-form-item>
      <el-form-item label="金额(元)" prop="amountYuan">
        <el-input-number
          v-model="form.amountYuan"
          :min="0.01"
          :precision="2"
          :controls="false"
          placeholder="单位：元"
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="商品标题" prop="subject">
        <el-input v-model="form.subject" maxlength="128" placeholder="如：测试商品" />
      </el-form-item>
      <el-form-item label="支付方式" prop="paymentMethod">
        <el-select v-model="form.paymentMethod" style="width: 200px">
          <el-option
            v-for="(label, value) in PAYMENT_METHOD_LABEL"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="币种默认 CNY，过期时间取系统配置；创建后约 5 秒由 Mock 渠道回调推进为已支付。"
      />
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建订单</el-button>
    </template>
  </el-dialog>
</template>
