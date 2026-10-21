<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import BaseChart from '@/components/BaseChart.vue'
import {
  getChannelShare,
  getDashboardSummary,
  getDashboardTrends,
  getMerchantTop,
  type ChannelItem,
  type DashboardSummary,
  type DashboardTrends,
  type MerchantTopItem,
} from '@/api/dashboard'
import { fenToYuan } from '@/utils/format'

/** 统计窗口天数（交易额/成功率/退款/渠道/TOP 共用），默认近 7 日。 */
const RANGE_OPTIONS = [7, 14, 30]
const days = ref(7)

const summary = ref<DashboardSummary | null>(null)
const trends = ref<DashboardTrends | null>(null)
const channels = ref<ChannelItem[]>([])
const merchantTop = ref<MerchantTopItem[]>([])
const loading = ref(false)

/** 指标卡：金额卡展示元（分→元），计数卡直接展示个数。 */
const statCards = computed(() => {
  const s = summary.value
  return [
    { label: '今日交易额（元）', value: s ? fenToYuan(s.todayTradeAmount) : '—' },
    { label: '今日订单数', value: s ? String(s.todayOrderCount) : '—' },
    { label: '支付成功率', value: s ? `${s.successRate.toFixed(2)}%` : '—' },
    { label: '今日退款金额（元）', value: s ? fenToYuan(s.todayRefundAmount) : '—' },
    { label: '今日结算金额（元）', value: s ? fenToYuan(s.todaySettlementAmount) : '—' },
    { label: '今日手续费收入（元）', value: s ? fenToYuan(s.todayPlatformFee) : '—' },
    { label: '今日风控拦截数', value: s ? String(s.todayRiskRejectCount) : '—' },
    { label: '今日异常订单数', value: s ? String(s.todayAbnormalOrderCount) : '—' },
  ]
})

/** 交易额趋势（柱）+ 支付成功率趋势（折线，右轴）。 */
const trendOption = computed<EChartsOption>(() => {
  const t = trends.value
  const dates = t ? t.tradeAmount.map((p) => p.date.slice(5)) : []
  return {
    tooltip: { trigger: 'axis' },
    legend: { data: ['交易额（元）', '支付成功率（%）'] },
    grid: { left: 60, right: 60, bottom: 30 },
    xAxis: { type: 'category', data: dates },
    yAxis: [
      { type: 'value', name: '元' },
      { type: 'value', name: '%', max: 100, axisLabel: { formatter: '{value}%' } },
    ],
    series: [
      {
        name: '交易额（元）',
        type: 'bar',
        data: t ? t.tradeAmount.map((p) => +(p.amount / 100).toFixed(2)) : [],
      },
      {
        name: '支付成功率（%）',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: t ? t.successRate.map((p) => p.rate) : [],
      },
    ],
  }
})

/** 退款趋势（折线）。 */
const refundOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 60, right: 30, bottom: 30 },
  xAxis: { type: 'category', data: trends.value ? trends.value.refund.map((p) => p.date.slice(5)) : [] },
  yAxis: { type: 'value', name: '元' },
  series: [
    {
      name: '退款金额（元）',
      type: 'line',
      smooth: true,
      areaStyle: {},
      data: trends.value ? trends.value.refund.map((p) => +(p.amount / 100).toFixed(2)) : [],
    },
  ],
}))

/** 渠道占比饼图。 */
const channelOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item', formatter: '{b}: ¥{c}（{d}%）' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [
    {
      name: '渠道占比',
      type: 'pie',
      radius: ['40%', '70%'],
      itemStyle: { borderRadius: 6 },
      label: { show: false },
      data: channels.value.map((c) => ({ name: c.channelCode, value: +(c.amount / 100).toFixed(2) })),
    },
  ],
}))

/** 商户 TOP10 横向条形（按金额降序，ECharts 从下往上画所以反转）。 */
const topOption = computed<EChartsOption>(() => {
  const sorted = [...merchantTop.value].sort((a, b) => a.amount - b.amount)
  return {
    tooltip: { trigger: 'axis', formatter: (params: unknown) => {
      const list = params as { name: string; value: number }[]
      const p = list[0]
      return p ? `${p.name}: ¥${p.value}` : ''
    } },
    grid: { left: 130, right: 40, bottom: 30 },
    xAxis: { type: 'value', name: '元' },
    yAxis: { type: 'category', data: sorted.map((m) => m.merchantName) },
    series: [{ name: '交易额（元）', type: 'bar', data: sorted.map((m) => +(m.amount / 100).toFixed(2)) }],
  }
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const [s, t, c, m] = await Promise.all([
      getDashboardSummary(),
      getDashboardTrends(days.value),
      getChannelShare(days.value),
      getMerchantTop(days.value),
    ])
    summary.value = s
    trends.value = t
    channels.value = c
    merchantTop.value = m
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div v-loading="loading">
    <el-card class="card">
      <div class="stat-grid">
        <div v-for="card in statCards" :key="card.label" class="stat-item">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="card">
      <template #header>
        <div class="card-header">
          <span>交易趋势</span>
          <el-radio-group v-model="days" size="small" @change="load">
            <el-radio-button v-for="d in RANGE_OPTIONS" :key="d" :value="d">近 {{ d }} 日</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <BaseChart :option="trendOption" height="360px" />
    </el-card>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card header="退款趋势（元）" class="card">
          <BaseChart :option="refundOption" height="300px" />
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="渠道占比" class="card">
          <BaseChart :option="channelOption" height="300px" />
        </el-card>
      </el-col>
    </el-row>

    <el-card header="商户交易额 TOP10" class="card">
      <BaseChart :option="topOption" height="360px" />
    </el-card>
  </div>
</template>

<style scoped>
.card {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-item {
  text-align: center;
}

.stat-label {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
}
</style>
