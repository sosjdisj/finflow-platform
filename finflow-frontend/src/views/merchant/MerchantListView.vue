<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { merchantApi } from '@/api/merchant'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter } from '@/utils/format'
import type { Merchant } from '@/types/api'
import { MERCHANT_STATUS_LABEL, MERCHANT_STATUS_TAG } from './merchantDisplay'
import MerchantDetailDrawer from './components/MerchantDetailDrawer.vue'

/**
 * 商户管理：列表（状态/关键词筛选+分页）+ 新增/编辑 + 状态变更 + 详情抽屉。
 * 详情（凭证/费率/结算账户）在抽屉组件里。
 */
const status = ref('')
const keyword = ref('')

const paging = usePaging<Merchant>((page, size) =>
  merchantApi.page(page, size, {
    status: status.value || undefined,
    keyword: keyword.value || undefined,
  }),
)
onMounted(paging.load)

const detailDrawer = ref<InstanceType<typeof MerchantDetailDrawer>>()

function openDetail(row: Merchant, tab?: string): void {
  void detailDrawer.value?.open(row.id, tab)
}

/** ---------- 新增 / 编辑 ---------- */
const editVisible = ref(false)
const editingId = ref<number | null>(null)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  merchantName: '',
  category: '',
  contactName: '',
  contactPhone: '',
  contactEmail: '',
  businessLicenseNo: '',
  businessAddress: '',
  settleCycle: 'T1',
  webhookUrl: '',
})
/** 统一社会信用代码字符集（剔除易混淆的 I/O/S/V/Z）与前 17 位加权因子（GB 32100-2015）。 */
const CREDIT_CODE_CHARSET = '0123456789ABCDEFGHJKLMNPQRTUWXY'
const CREDIT_CODE_WEIGHTS = [1, 3, 9, 27, 19, 26, 16, 17, 20, 29, 25, 13, 8, 24, 10, 30, 28]

/** 统一社会信用代码：18 位、字符集合法、末位校验码正确（忽略大小写）。 */
function isValidCreditCode(code: string): boolean {
  const value = code.trim().toUpperCase()
  if (value.length !== 18) {
    return false
  }
  let sum = 0
  for (const [position, weight] of CREDIT_CODE_WEIGHTS.entries()) {
    const index = CREDIT_CODE_CHARSET.indexOf(value.charAt(position))
    if (index < 0) {
      return false
    }
    sum += index * weight
  }
  return CREDIT_CODE_CHARSET.charAt((31 - (sum % 31)) % 31) === value.charAt(17)
}

const editRules = {
  merchantName: [{ required: true, message: '请输入商户名称', trigger: 'blur' }],
  contactName: [{ required: true, message: '请输入联系人', trigger: 'blur' }],
  contactPhone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
  businessLicenseNo: [
    { required: true, message: '请输入统一社会信用代码', trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (error?: Error) => void) => {
        if (!value || isValidCreditCode(value)) {
          callback()
        } else {
          callback(new Error('统一社会信用代码格式不正确'))
        }
      },
      trigger: 'blur',
    },
  ],
}

function openCreate(): void {
  editingId.value = null
  Object.assign(editForm, {
    merchantName: '',
    category: '',
    contactName: '',
    contactPhone: '',
    contactEmail: '',
    businessLicenseNo: '',
    businessAddress: '',
    settleCycle: 'T1',
    webhookUrl: '',
  })
  editVisible.value = true
}

function openEdit(row: Merchant): void {
  editingId.value = row.id
  Object.assign(editForm, {
    merchantName: row.merchantName,
    category: row.category ?? '',
    contactName: row.contactName,
    contactPhone: row.contactPhone,
    contactEmail: row.contactEmail ?? '',
    businessLicenseNo: row.businessLicenseNo,
    businessAddress: row.businessAddress ?? '',
    settleCycle: row.settleCycle,
    webhookUrl: row.webhookUrl ?? '',
  })
  editVisible.value = true
}

async function submitEdit(): Promise<void> {
  const valid = await editFormRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  const payload = {
    merchantName: editForm.merchantName.trim(),
    category: editForm.category || undefined,
    contactName: editForm.contactName.trim(),
    contactPhone: editForm.contactPhone.trim(),
    contactEmail: editForm.contactEmail || undefined,
    businessLicenseNo: editForm.businessLicenseNo.trim(),
    businessAddress: editForm.businessAddress || undefined,
    settleCycle: editForm.settleCycle,
    webhookUrl: editForm.webhookUrl || undefined,
  }
  if (editingId.value === null) {
    await merchantApi.create(payload)
    ElMessage.success('商户已创建，状态为待审核')
  } else {
    await merchantApi.update(editingId.value, payload)
    ElMessage.success('保存成功')
  }
  editVisible.value = false
  await paging.load()
}

/** ---------- 状态变更（暂停 / 恢复 / 关闭） ---------- */
async function changeStatus(row: Merchant, target: 'SUSPENDED' | 'ACTIVE' | 'CLOSED'): Promise<void> {
  const tips: Record<string, string> = {
    SUSPENDED: `确定暂停商户「${row.merchantName}」吗？暂停期间将无法发起交易。`,
    ACTIVE: `确定恢复商户「${row.merchantName}」为生效状态吗？`,
    CLOSED: `确定关闭商户「${row.merchantName}」吗？关闭后不可恢复。`,
  }
  await ElMessageBox.confirm(tips[target], '提示', { type: 'warning' })
  await merchantApi.changeStatus(row.id, target, `后台操作：${MERCHANT_STATUS_LABEL[target]}`)
  ElMessage.success('状态已变更')
  await paging.load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="status" class="status" placeholder="全部状态" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in MERCHANT_STATUS_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
      <el-input
        v-model="keyword"
        class="search"
        placeholder="商户名称 / 商户编号"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button v-permission="'merchant:merchant:create'" type="primary" @click="openCreate">新增商户</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="merchantNo" label="商户编号" width="180" />
      <el-table-column prop="merchantName" label="商户名称" min-width="160" />
      <el-table-column prop="contactName" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="联系电话" width="130" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="MERCHANT_STATUS_TAG[row.status] ?? 'info'">
            {{ MERCHANT_STATUS_LABEL[row.status] ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="settleCycle" label="结算周期" width="90" align="center" />
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button v-permission="'merchant:merchant:update'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            v-permission="'merchant:merchant:status'"
            link
            type="warning"
            @click="changeStatus(row, 'SUSPENDED')"
          >
            暂停
          </el-button>
          <el-button
            v-if="row.status === 'SUSPENDED'"
            v-permission="'merchant:merchant:status'"
            link
            type="success"
            @click="changeStatus(row, 'ACTIVE')"
          >
            恢复
          </el-button>
          <el-button
            v-if="row.status !== 'CLOSED'"
            v-permission="'merchant:merchant:status'"
            link
            type="danger"
            @click="changeStatus(row, 'CLOSED')"
          >
            关闭
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

  <el-dialog
    v-model="editVisible"
    :title="editingId === null ? '新增商户' : `编辑商户：${editForm.merchantName}`"
    width="560px"
  >
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="130px">
      <el-form-item label="商户名称" prop="merchantName">
        <el-input v-model="editForm.merchantName" maxlength="128" />
      </el-form-item>
      <el-form-item label="所属行业">
        <el-input v-model="editForm.category" maxlength="32" />
      </el-form-item>
      <el-form-item label="联系人" prop="contactName">
        <el-input v-model="editForm.contactName" maxlength="64" />
      </el-form-item>
      <el-form-item label="联系电话" prop="contactPhone">
        <el-input v-model="editForm.contactPhone" maxlength="32" />
      </el-form-item>
      <el-form-item label="联系邮箱">
        <el-input v-model="editForm.contactEmail" maxlength="128" />
      </el-form-item>
      <el-form-item label="统一社会信用代码" prop="businessLicenseNo">
        <el-input v-model="editForm.businessLicenseNo" maxlength="64" />
      </el-form-item>
      <el-form-item label="经营地址">
        <el-input v-model="editForm.businessAddress" maxlength="255" />
      </el-form-item>
      <el-form-item label="结算周期">
        <el-select v-model="editForm.settleCycle">
          <el-option label="T+1" value="T1" />
          <el-option label="D+1（自然日）" value="D1" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="editingId !== null" label="Webhook 地址">
        <el-input v-model="editForm.webhookUrl" maxlength="255" placeholder="https://" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="submitEdit">确定</el-button>
    </template>
  </el-dialog>

  <MerchantDetailDrawer ref="detailDrawer" />
</template>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.status {
  width: 130px;
}

.search {
  width: 240px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
