<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ledgerApi } from '@/api/ledger'
import { usePaging } from '@/composables/usePaging'
import { dateTimeFormatter, fenToYuan } from '@/utils/format'
import type { Account } from '@/types/api'
import { OWNER_TYPE_LABEL, OWNER_TYPE_TAG } from './financeDisplay'

/**
 * 账户列表：主体类型 / 币种筛选 + 分页。
 * 行操作：查流水（跳资金流水页带账户ID）、一致性校验、人工调账（仅超管，调账必须走流水）。
 */
const router = useRouter()

const ownerType = ref('')
const currency = ref('')

const paging = usePaging<Account>((page, size) =>
  ledgerApi.accountPage(page, size, {
    ownerType: ownerType.value || undefined,
    currency: currency.value || undefined,
  }),
)
onMounted(paging.load)

function resetFilters(): void {
  ownerType.value = ''
  currency.value = ''
  void paging.reload()
}

function goLedger(row: Account): void {
  void router.push({ path: '/finance/ledgers', query: { accountId: String(row.id) } })
}

async function checkConsistency(row: Account): Promise<void> {
  const result = await ledgerApi.consistency(row.id)
  const verdict = result.consistent ? '账实相符' : '账实不符，请排查！'
  await ElMessageBox.alert(
    `当前可用余额：${fenToYuan(result.currentBalance)} 元\n流水累计净额：${fenToYuan(result.ledgerSum)} 元`,
    `一致性校验：${verdict}`,
    { confirmButtonText: '知道了', type: result.consistent ? 'success' : 'error' },
  )
}

/** 调账弹窗：金额输入元（带符号），提交转分。 */
const adjustVisible = ref(false)
const adjustForm = reactive<{ account: Account | null; amountYuan: number | null; remark: string }>({
  account: null,
  amountYuan: null,
  remark: '',
})

function openAdjust(row: Account): void {
  adjustForm.account = row
  adjustForm.amountYuan = null
  adjustForm.remark = ''
  adjustVisible.value = true
}

async function submitAdjust(): Promise<void> {
  if (!adjustForm.account || adjustForm.amountYuan == null || adjustForm.amountYuan === 0) {
    ElMessage.warning('请输入不为 0 的调账金额')
    return
  }
  await ledgerApi.adjust({
    accountId: adjustForm.account.id,
    amount: Math.round(adjustForm.amountYuan * 100),
    remark: adjustForm.remark || undefined,
  })
  ElMessage.success('调账成功，已留痕资金流水')
  adjustVisible.value = false
  void paging.load()
}
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <el-select v-model="ownerType" class="owner-type" placeholder="全部主体类型" clearable @change="paging.reload()">
        <el-option v-for="(label, value) in OWNER_TYPE_LABEL" :key="value" :label="label" :value="value" />
      </el-select>
      <el-input
        v-model="currency"
        class="currency"
        placeholder="币种（如 CNY）"
        clearable
        @keyup.enter="paging.reload()"
        @clear="paging.reload()"
      />
      <el-button type="primary" plain @click="paging.reload()">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>

    <el-table v-loading="paging.loading" :data="paging.records" border>
      <el-table-column prop="id" label="账户ID" width="90" />
      <el-table-column label="主体类型" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="OWNER_TYPE_TAG[row.ownerType] ?? 'info'">
            {{ OWNER_TYPE_LABEL[row.ownerType] ?? row.ownerType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ownerId" label="主体ID" width="110" />
      <el-table-column prop="currency" label="币种" width="80" align="center" />
      <el-table-column label="可用余额" min-width="130" align="right">
        <template #default="{ row }">{{ fenToYuan(row.availableBalance) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column label="冻结余额" min-width="130" align="right">
        <template #default="{ row }">{{ fenToYuan(row.frozenBalance) }} {{ row.currency }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" :formatter="dateTimeFormatter" />
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goLedger(row)">查流水</el-button>
          <el-button link type="primary" @click="checkConsistency(row)">一致性校验</el-button>
          <el-button v-permission="'ledger:adjust'" link type="warning" @click="openAdjust(row)">调账</el-button>
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

  <el-dialog v-model="adjustVisible" title="人工调账" width="440px">
    <el-alert
      class="adjust-tip"
      type="warning"
      :closable="false"
      title="调账直接变更账户余额并强制留痕资金流水，仅限超管操作"
    />
    <el-form label-width="90px">
      <el-form-item label="账户">
        <span>#{{ adjustForm.account?.id }}（{{ OWNER_TYPE_LABEL[adjustForm.account?.ownerType ?? ''] ?? '' }}
          {{ adjustForm.account?.ownerId }}，{{ adjustForm.account?.currency }}）</span>
      </el-form-item>
      <el-form-item label="调账金额">
        <el-input-number
          v-model="adjustForm.amountYuan"
          :precision="2"
          :controls="false"
          placeholder="正数调增，负数调减（元）"
          class="adjust-amount"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="adjustForm.remark" type="textarea" :rows="2" maxlength="255" placeholder="调账原因（随流水留痕）" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="adjustVisible = false">取消</el-button>
      <el-button type="primary" @click="submitAdjust">确认调账</el-button>
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

.owner-type {
  width: 150px;
}

.currency {
  width: 160px;
}

.pager {
  margin-top: 12px;
  justify-content: flex-end;
}

.adjust-tip {
  margin-bottom: 16px;
}

.adjust-amount {
  width: 100%;
}
</style>
