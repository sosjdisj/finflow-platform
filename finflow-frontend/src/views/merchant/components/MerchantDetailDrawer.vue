<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { dateTimeFormatter } from '@/utils/format'
import type { MerchantApiKey, MerchantDetail, MerchantRate, MerchantRateLog, SettleAccount } from '@/types/api'
import { ACCOUNT_TYPE_LABEL, MERCHANT_STATUS_LABEL, bpsToPercent, feeToYuan } from '../merchantDisplay'

/**
 * 商户详情抽屉：基本信息（含状态留痕）/ 凭证 / 费率 / 结算账户 四个 Tab。
 * Secret 仅创建响应返回一次：弹出专用对话框展示 + 明确提示 + 复制按钮，关闭后无法再取。
 */
const visible = ref(false)
const merchantId = ref<number | null>(null)
const detail = ref<MerchantDetail | null>(null)
const activeTab = ref('basic')

async function open(id: number, tab = 'basic'): Promise<void> {
  merchantId.value = id
  activeTab.value = tab
  visible.value = true
  await loadBasic()
}

defineExpose({ open })

watch(visible, (open) => {
  if (!open) {
    detail.value = null
  }
})

async function loadBasic(): Promise<void> {
  if (merchantId.value === null) {
    return
  }
  detail.value = await merchantApi.detail(merchantId.value)
}

/** ---------- 凭证 Tab ---------- */
const apiKeys = ref<MerchantApiKey[]>([])

async function loadApiKeys(): Promise<void> {
  if (merchantId.value === null) {
    return
  }
  apiKeys.value = await merchantApi.apiKeys(merchantId.value)
}

const secretVisible = ref(false)
const secretOnce = ref<{ apiKey: string; secret: string } | null>(null)

async function generateKey(): Promise<void> {
  await ElMessageBox.confirm('将生成新的 API 密钥对，Secret 仅在本次显示一次，确定继续？', '生成密钥', {
    type: 'warning',
  })
  if (merchantId.value === null) {
    return
  }
  const created = await merchantApi.createApiKey(merchantId.value)
  secretOnce.value = { apiKey: created.apiKey, secret: created.secret }
  secretVisible.value = true
  await loadApiKeys()
}

async function copySecret(): Promise<void> {
  if (secretOnce.value) {
    await navigator.clipboard.writeText(secretOnce.value.secret)
    ElMessage.success('Secret 已复制')
  }
}

/** 白名单编辑 */
const whitelistVisible = ref(false)
const whitelistFormRef = ref<FormInstance>()
const whitelistForm = { keyId: 0, ips: '' }

function openWhitelist(row: MerchantApiKey): void {
  whitelistForm.keyId = row.id
  whitelistForm.ips = row.ipWhitelist ?? ''
  whitelistVisible.value = true
}

async function submitWhitelist(): Promise<void> {
  const valid = await whitelistFormRef.value?.validate().catch(() => false)
  if (!valid || merchantId.value === null) {
    return
  }
  const ips = whitelistForm.ips
    .split(/[,\n]/)
    .map((s) => s.trim())
    .filter(Boolean)
  await merchantApi.updateIpWhitelist(merchantId.value, whitelistForm.keyId, ips)
  ElMessage.success('白名单已更新')
  whitelistVisible.value = false
  await loadApiKeys()
}

/** ---------- 费率 Tab ---------- */
const rate = ref<MerchantRate | null>(null)
const rateLogs = ref<MerchantRateLog[]>([])

async function loadRate(): Promise<void> {
  if (merchantId.value === null) {
    return
  }
  ;[rate.value, rateLogs.value] = await Promise.all([
    merchantApi.rate(merchantId.value),
    merchantApi.rateLogs(merchantId.value),
  ])
}

const rateVisible = ref(false)
const rateFormRef = ref<FormInstance>()
const rateForm = { rateBps: null as number | null, fixedFeeYuan: null as number | null }
const rateRules = {
  rateBps: [{ type: 'number' as const, min: 0, max: 10000, message: '0 ~ 10000 万分比', trigger: 'blur' }],
  fixedFeeYuan: [{ type: 'number' as const, min: 0, message: '不能为负', trigger: 'blur' }],
}

function openRate(): void {
  rateForm.rateBps = rate.value?.rateBps ?? null
  rateForm.fixedFeeYuan = rate.value?.fixedFee == null ? null : rate.value.fixedFee / 100
  rateVisible.value = true
}

async function submitRate(): Promise<void> {
  const valid = await rateFormRef.value?.validate().catch(() => false)
  if (!valid || merchantId.value === null) {
    return
  }
  if ((rateForm.rateBps ?? 0) === 0 && (rateForm.fixedFeeYuan ?? 0) === 0) {
    ElMessage.warning('比例费率与固定手续费至少启用一种')
    return
  }
  await merchantApi.setRate(merchantId.value, {
    rateBps: rateForm.rateBps ?? undefined,
    fixedFee: rateForm.fixedFeeYuan == null ? undefined : Math.round(rateForm.fixedFeeYuan * 100),
  })
  ElMessage.success('费率已保存')
  rateVisible.value = false
  await loadRate()
}

/** ---------- 结算账户 Tab ---------- */
const accounts = ref<SettleAccount[]>([])

async function loadAccounts(): Promise<void> {
  if (merchantId.value === null) {
    return
  }
  accounts.value = await merchantApi.settleAccounts(merchantId.value)
}

const accountVisible = ref(false)
const accountFormRef = ref<FormInstance>()
const accountForm = { accountType: 'BANK_CARD', accountName: '', accountNo: '', bankName: '' }
const accountRules = {
  accountType: [{ required: true, message: '请选择类型', trigger: 'change' }],
  accountName: [{ required: true, message: '请输入户名', trigger: 'blur' }],
  accountNo: [{ required: true, message: '请输入账号', trigger: 'blur' }],
}

function openAccount(): void {
  Object.assign(accountForm, { accountType: 'BANK_CARD', accountName: '', accountNo: '', bankName: '' })
  accountVisible.value = true
}

async function submitAccount(): Promise<void> {
  const valid = await accountFormRef.value?.validate().catch(() => false)
  if (!valid || merchantId.value === null) {
    return
  }
  try {
    await merchantApi.createSettleAccount(merchantId.value, {
      accountType: accountForm.accountType,
      accountName: accountForm.accountName.trim(),
      accountNo: accountForm.accountNo.trim(),
      bankName: accountForm.bankName || undefined,
    })
  } catch {
    return // 银行卡缺开户行等校验错误：request 层已提示
  }
  ElMessage.success('账户已添加')
  accountVisible.value = false
  await loadAccounts()
}

async function markDefault(row: SettleAccount): Promise<void> {
  if (merchantId.value === null) {
    return
  }
  await merchantApi.markDefaultAccount(merchantId.value, row.id)
  ElMessage.success('已设为默认')
  await loadAccounts()
}

async function removeAccount(row: SettleAccount): Promise<void> {
  await ElMessageBox.confirm(`确定删除该结算账户（${row.accountNoMasked}）吗？`, '提示', { type: 'warning' })
  if (merchantId.value === null) {
    return
  }
  await merchantApi.deleteSettleAccount(merchantId.value, row.id)
  ElMessage.success('已删除')
  await loadAccounts()
}

/** Tab 切换时懒加载对应数据。 */
async function onTabChange(name: string | number): Promise<void> {
  if (name === 'keys') {
    await loadApiKeys()
  } else if (name === 'rate') {
    await loadRate()
  } else if (name === 'accounts') {
    await loadAccounts()
  } else if (name === 'basic') {
    await loadBasic()
  }
}
</script>

<template>
  <el-drawer v-model="visible" :title="detail ? `商户：${detail.merchant.merchantName}` : '商户详情'" size="62%">
    <el-tabs v-if="detail" v-model="activeTab" @tab-change="onTabChange">
      <!-- 基本信息 -->
      <el-tab-pane label="基本信息" name="basic">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="商户编号">{{ detail.merchant.merchantNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag>{{ MERCHANT_STATUS_LABEL[detail.merchant.status] ?? detail.merchant.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="商户名称">{{ detail.merchant.merchantName }}</el-descriptions-item>
          <el-descriptions-item label="所属行业">{{ detail.merchant.category ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detail.merchant.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detail.merchant.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="联系邮箱">{{ detail.merchant.contactEmail ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="结算周期">{{ detail.merchant.settleCycle }}</el-descriptions-item>
          <el-descriptions-item label="统一社会信用代码" :span="2">
            {{ detail.merchant.businessLicenseNo }}
          </el-descriptions-item>
          <el-descriptions-item label="经营地址" :span="2">{{ detail.merchant.businessAddress ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="Webhook 地址" :span="2">{{ detail.merchant.webhookUrl ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">状态变更留痕</h4>
        <el-table :data="detail.statusLogs" border size="small">
          <el-table-column prop="fromStatus" label="原状态" width="110" />
          <el-table-column prop="toStatus" label="新状态" width="110" />
          <el-table-column prop="reason" label="原因" min-width="160" />
          <el-table-column prop="operator" label="操作人" width="110" />
          <el-table-column prop="createdAt" label="时间" width="170" :formatter="dateTimeFormatter" />
        </el-table>
      </el-tab-pane>

      <!-- API 凭证 -->
      <el-tab-pane label="API 凭证" name="keys">
        <div class="tab-toolbar">
          <el-button v-permission="'merchant:merchant:api-key'" type="primary" @click="generateKey">
            生成密钥对
          </el-button>
        </div>
        <el-table :data="apiKeys" border>
          <el-table-column prop="apiKey" label="API Key" min-width="220" />
          <el-table-column prop="ipWhitelist" label="IP 白名单" min-width="180">
            <template #default="{ row }">{{ row.ipWhitelist ?? '未设置（不限）' }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
          <el-table-column label="操作" width="110" fixed="right">
            <template #default="{ row }">
              <el-button v-permission="'merchant:merchant:api-key'" link type="primary" @click="openWhitelist(row)">
                白名单
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- 费率 -->
      <el-tab-pane label="费率" name="rate">
        <div class="tab-toolbar">
          <el-button v-permission="'merchant:merchant:rate'" type="primary" @click="openRate">设置费率</el-button>
        </div>
        <el-descriptions :column="3" border class="rate-summary">
          <el-descriptions-item label="比例费率">{{ bpsToPercent(rate?.rateBps ?? null) }}</el-descriptions-item>
          <el-descriptions-item label="固定手续费">{{ feeToYuan(rate?.fixedFee ?? null) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ rate ? dateTimeFormatter(null, null, rate.updatedAt) : '-' }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">变更历史</h4>
        <el-table :data="rateLogs" border size="small">
          <el-table-column label="改前" min-width="150">
            <template #default="{ row }">{{ bpsToPercent(row.oldRateBps) }} / {{ feeToYuan(row.oldFixedFee) }}</template>
          </el-table-column>
          <el-table-column label="改后" min-width="150">
            <template #default="{ row }">{{ bpsToPercent(row.newRateBps) }} / {{ feeToYuan(row.newFixedFee) }}</template>
          </el-table-column>
          <el-table-column prop="operator" label="操作人" width="110" />
          <el-table-column prop="createdAt" label="时间" width="170" :formatter="dateTimeFormatter" />
        </el-table>
      </el-tab-pane>

      <!-- 结算账户 -->
      <el-tab-pane label="结算账户" name="accounts">
        <div class="tab-toolbar">
          <el-button v-permission="'merchant:merchant:settle-account'" type="primary" @click="openAccount">
            新增账户
          </el-button>
        </div>
        <el-table :data="accounts" border>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ ACCOUNT_TYPE_LABEL[row.accountType] ?? row.accountType }}</template>
          </el-table-column>
          <el-table-column prop="accountName" label="户名" min-width="110" />
          <el-table-column prop="accountNoMasked" label="账号" min-width="150" />
          <el-table-column prop="bankName" label="开户行" min-width="130">
            <template #default="{ row }">{{ row.bankName ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="默认" width="80" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.isDefault === 1" type="success" size="small">默认</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <el-button
                v-permission="'merchant:merchant:settle-account'"
                link
                type="primary"
                :disabled="row.isDefault === 1"
                @click="markDefault(row)"
              >
                设默认
              </el-button>
              <el-button v-permission="'merchant:merchant:settle-account'" link type="danger" @click="removeAccount(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- Secret 只展示一次 -->
    <el-dialog v-model="secretVisible" title="请立即保存你的 Secret" width="520px" append-to-body>
      <el-alert type="warning" :closable="false" show-icon class="secret-alert">
        Secret 仅此一次显示，关闭后无法再查看。请立即复制并妥善保存。
      </el-alert>
      <template v-if="secretOnce">
        <el-form label-width="90px">
          <el-form-item label="API Key">
            <el-input :model-value="secretOnce.apiKey" readonly />
          </el-form-item>
          <el-form-item label="Secret">
            <el-input :model-value="secretOnce.secret" readonly>
              <template #append>
                <el-button @click="copySecret">复制</el-button>
              </template>
            </el-input>
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button type="primary" @click="secretVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 白名单编辑 -->
    <el-dialog v-model="whitelistVisible" title="IP 白名单（全量覆盖，逗号或换行分隔，留空清空）" width="520px" append-to-body>
      <el-form ref="whitelistFormRef" :model="whitelistForm" label-width="80px">
        <el-form-item label="IP 列表">
          <el-input v-model="whitelistForm.ips" type="textarea" :rows="4" placeholder="如 10.0.0.1, 10.0.0.0/24" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="whitelistVisible = false">取消</el-button>
        <el-button type="primary" @click="submitWhitelist">保存</el-button>
      </template>
    </el-dialog>

    <!-- 费率设置 -->
    <el-dialog v-model="rateVisible" title="设置费率（至少启用一种）" width="460px" append-to-body>
      <el-form ref="rateFormRef" :model="rateForm" :rules="rateRules" label-width="110px">
        <el-form-item label="比例费率" prop="rateBps">
          <el-input-number v-model="rateForm.rateBps" :min="0" :max="10000" :step="10" />
          <span class="form-hint">万分比（{{ rateForm.rateBps == null ? '-' : (rateForm.rateBps / 100) + '%' }}）</span>
        </el-form-item>
        <el-form-item label="固定手续费" prop="fixedFeeYuan">
          <el-input-number v-model="rateForm.fixedFeeYuan" :min="0" :precision="2" :step="0.1" />
          <span class="form-hint">元（落库存分）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rateVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增结算账户 -->
    <el-dialog v-model="accountVisible" title="新增结算账户" width="480px" append-to-body>
      <el-form ref="accountFormRef" :model="accountForm" :rules="accountRules" label-width="80px">
        <el-form-item label="类型" prop="accountType">
          <el-select v-model="accountForm.accountType">
            <el-option label="银行卡" value="BANK_CARD" />
            <el-option label="支付宝" value="ALIPAY" />
            <el-option label="微信" value="WECHAT" />
          </el-select>
        </el-form-item>
        <el-form-item label="户名" prop="accountName">
          <el-input v-model="accountForm.accountName" />
        </el-form-item>
        <el-form-item label="账号" prop="accountNo">
          <el-input v-model="accountForm.accountNo" />
        </el-form-item>
        <el-form-item v-if="accountForm.accountType === 'BANK_CARD'" label="开户行" required>
          <el-input v-model="accountForm.bankName" placeholder="银行卡必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="accountVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAccount">保存</el-button>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.tab-toolbar {
  display: flex;
  margin-bottom: 12px;
}

.section-title {
  margin: 16px 0 8px;
}

.rate-summary {
  margin-bottom: 8px;
}

.secret-alert {
  margin-bottom: 16px;
}

.form-hint {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
