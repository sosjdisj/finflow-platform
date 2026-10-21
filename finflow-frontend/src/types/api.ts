/**
 * 后端接口共享类型：与后端 DTO 一一对应，唯一事实源。
 * 列表/分页等公共结构放这，页面级局部类型就近定义。
 */

/** 统一响应包。traceId 与后端日志同源，排查问题时可据此反查。 */
export interface ApiBody<T> {
  code: number
  message: string
  data: T
  traceId?: string
}

/** 分页数据。 */
export interface PageResponse<T> {
  records: T[]
  total: number
}

/** 双令牌（登录/刷新返回）。 */
export interface TokenPair {
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

/** 图形验证码。 */
export interface CaptchaInfo {
  captchaId: string
  imageBase64: string
}

/** 当前用户信息。 */
export interface UserProfile {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
}

/** 当前用户角色与权限编码。 */
export interface MePerms {
  roles: string[]
  permissions: string[]
}

/** 菜单树节点（sys_menu 展示结构）。 */
export interface MenuNode {
  id: number
  parentId: number
  title: string
  path: string
  icon: string | null
  /** 1 目录 2 页面 */
  menuType: number
  sortNo: number
  permissionId: number | null
  /** 1 显示 0 隐藏 */
  visible: number
  children: MenuNode[]
}

/** 用户管理视图。 */
export interface UserAdmin {
  id: number
  username: string
  nickname: string
  email: string | null
  phone: string | null
  status: number
  lastLoginAt: string | null
  createdAt: string
}

/** 角色视图。 */
export interface Role {
  id: number
  code: string
  name: string
  description: string | null
  /** 1 内置角色，禁改禁删 */
  builtIn: number
  status: number
  createdAt: string
}

/** 权限点视图。 */
export interface Permission {
  id: number
  code: string
  name: string
  /** 1 菜单可见性 2 操作 */
  type: number
  description: string | null
  status: number
  createdAt: string
}

/** 系统配置项（S37）：key 集合固定（后端 ConfigKeys），仅值与描述可改。 */
export interface SystemConfigItem {
  configKey: string
  configValue: string
  /** NUMBER / STRING / BOOLEAN / JSON */
  valueType: string
  description: string
  /** 0 = 系统初始化 */
  updatedBy: number
  updatedAt: string
}

/** 商户视图。 */
export interface Merchant {
  id: number
  merchantNo: string
  merchantName: string
  category: string | null
  contactName: string
  contactPhone: string
  contactEmail: string | null
  businessLicenseNo: string
  businessAddress: string | null
  settleCycle: string
  webhookUrl: string | null
  /** PENDING / REVIEWING / ACTIVE / SUSPENDED / CLOSED */
  status: string
  createdAt: string
}

/** 商户状态变更留痕。 */
export interface MerchantStatusLog {
  id: number
  fromStatus: string
  toStatus: string
  reason: string | null
  operator: string
  createdAt: string
}

/** 商户详情（基本信息 + 状态留痕）。 */
export interface MerchantDetail {
  merchant: Merchant
  statusLogs: MerchantStatusLog[]
}

/** API 凭证（不含 Secret）。 */
export interface MerchantApiKey {
  id: number
  merchantId: number
  apiKey: string
  ipWhitelist: string | null
  createdAt: string
}

/** 凭证创建响应：secret 明文仅此一次。 */
export interface ApiKeyCreated {
  id: number
  merchantId: number
  apiKey: string
  secret: string
  createdAt: string
}

/** 商户费率。 */
export interface MerchantRate {
  merchantId: number
  /** 比例费率（万分比）。 */
  rateBps: number | null
  /** 固定手续费（分）。 */
  fixedFee: number | null
  updatedAt: string
}

/** 费率变更历史。 */
export interface MerchantRateLog {
  id: number
  oldRateBps: number | null
  oldFixedFee: number | null
  newRateBps: number | null
  newFixedFee: number | null
  operator: string
  createdAt: string
}

/** 结算账户（账号脱敏）。 */
export interface SettleAccount {
  id: number
  merchantId: number
  accountType: string
  accountName: string
  accountNoMasked: string
  bankName: string | null
  isDefault: number
  createdAt: string
}

/** 支付订单视图（金额单位：分）。 */
export interface PaymentOrder {
  id: number
  merchantId: number
  merchantOrderNo: string
  platformOrderNo: string
  amount: number
  /** 已申请退款累计金额（分，申请占用、失败释放），剩余可退 = amount - refundedAmount */
  refundedAmount: number
  currency: string
  subject: string
  body: string | null
  paymentMethod: string
  callbackUrl: string | null
  expiredAt: string
  /** CREATED/PENDING/PROCESSING/PAID/SETTLED/FAILED/CLOSED/REFUNDING/REFUNDED */
  status: string
  createdAt: string
}

/** 退款单视图（金额单位：分）。 */
export interface RefundOrder {
  id: number
  refundNo: string
  orderId: number
  merchantId: number
  platformOrderNo: string
  refundAmount: number
  currency: string
  reason: string | null
  channelCode: string | null
  channelRefundNo: string | null
  /** APPLYING/PROCESSING/SUCCESS/FAILED/CLOSED */
  status: string
  failReason: string | null
  operator: string
  refundedAt: string | null
  createdAt: string
}

/** 退款状态变更留痕。 */
export interface RefundStateLog {
  id: number
  refundId: number
  fromStatus: string
  toStatus: string
  reason: string | null
  operator: string
  createdAt: string
}

/** 退款详情（退款单 + 状态留痕）。 */
export interface RefundOrderDetail {
  refund: RefundOrder
  stateLogs: RefundStateLog[]
}

/** 支付订单状态变更留痕。 */
export interface PaymentStateLog {
  id: number
  orderId: number
  fromStatus: string
  toStatus: string
  reason: string | null
  operator: string
  createdAt: string
}

/** 渠道交互留痕（报文为 JSON 字符串）。 */
export interface PaymentTransaction {
  id: number
  orderId: number
  channelCode: string
  /** PAYMENT/REFUND/QUERY/REFUND_QUERY */
  txnType: string
  channelOrderNo: string | null
  requestPayload: string | null
  responsePayload: string | null
  createdAt: string
}

/** 支付订单详情（订单 + 状态留痕 + 渠道交互）。 */
export interface PaymentOrderDetail {
  order: PaymentOrder
  stateLogs: PaymentStateLog[]
  transactions: PaymentTransaction[]
}

/** 账户视图（余额单位：分）。 */
export interface Account {
  id: number
  /** MERCHANT/PLATFORM/CHANNEL */
  ownerType: string
  ownerId: number
  currency: string
  availableBalance: number
  frozenBalance: number
  createdAt: string
}

/** 资金流水视图（amount 带符号：正=入账，负=出账；金额单位：分）。 */
export interface LedgerRecord {
  id: number
  accountId: number
  /** PAYMENT/SETTLEMENT/ADJUSTMENT */
  bizType: string
  bizNo: string
  amount: number
  balanceBefore: number
  balanceAfter: number
  currency: string
  /** PAYMENT_EVENT/CONSOLE/TASK */
  source: string
  remark: string | null
  createdAt: string
}

/** 资金流水一致性校验结果。 */
export interface LedgerConsistency {
  accountId: number
  currentBalance: number
  ledgerSum: number
  consistent: boolean
}

/** 清分单（一笔支付成功订单一张，清分即终态 CLEARED）。 */
export interface ClearingOrder {
  id: number
  orderId: number
  merchantId: number
  platformOrderNo: string
  payAmount: number
  merchantIncome: number
  platformFee: number
  channelCost: number
  currency: string
  channelCode: string
  settleDate: string
  status: string
  clearedAt: string
}

/** 分账明细行（MERCHANT/PLATFORM/CHANNEL，金额均为正数）。 */
export interface ClearingDetail {
  id: number
  partyType: string
  partyId: number | null
  channelCode: string | null
  amount: number
  remark: string | null
}

/** 清分详情（清分单 + 分账明细）。 */
export interface ClearingOrderDetail {
  clearing: ClearingOrder
  details: ClearingDetail[]
}

/** 结算单（同一商户同一结算日一张）。 */
export interface SettlementOrder {
  id: number
  settleNo: string
  merchantId: number
  settleDate: string
  orderCount: number
  totalAmount: number
  currency: string
  /** PENDING/CALCULATED/APPROVED/PROCESSING/SUCCESS/FAILED */
  status: string
  generatedAt: string
  calculatedAt: string | null
  approvedAt: string | null
  approvedBy: string | null
  executedAt: string | null
  failReason: string | null
}

/** 结算单明细（逐笔清分引用快照）。 */
export interface SettlementDetail {
  id: number
  clearingId: number
  orderId: number
  payAmount: number
  merchantIncome: number
}

/** 结算单状态变更留痕。 */
export interface SettlementStateLog {
  fromStatus: string
  toStatus: string
  reason: string | null
  operator: string | null
  createdAt: string
}

/** 结算详情（结算单 + 逐笔明细 + 状态留痕）。 */
export interface SettlementOrderDetail {
  settlement: SettlementOrder
  details: SettlementDetail[]
  stateLogs: SettlementStateLog[]
}

/** 对账任务视图。 */
export interface ReconciliationTask {
  id: number
  taskNo: string
  channelCode: string
  billDate: string
  fileName: string
  /** PENDING/PARSING/COMPLETED/FAILED */
  status: string
  totalRows: number
  validRows: number
  errorMessage: string | null
  uploadedBy: string
  matchedAt: string | null
  /** 匹配完成后的五类结果汇总（未匹配时全 0） */
  matchedCount: number
  amountMismatchCount: number
  statusMismatchCount: number
  missingInPlatformCount: number
  missingInChannelCount: number
  createdAt: string
  updatedAt: string
}

/** 匹配执行汇总（五类结果数量）。 */
export interface MatchSummary {
  taskId: number
  taskNo: string
  matchedCount: number
  amountMismatchCount: number
  statusMismatchCount: number
  missingInPlatformCount: number
  missingInChannelCount: number
  totalRecords: number
  matchedAt: string
}

/** 对账匹配记录（五类结果，金额单位：分）。 */
export interface ReconciliationRecord {
  id: number
  taskId: number
  /** MATCHED/AMOUNT_MISMATCH/STATUS_MISMATCH/MISSING_IN_PLATFORM/MISSING_IN_CHANNEL */
  result: string
  billItemId: number | null
  channelOrderNo: string | null
  merchantOrderNo: string
  billAmount: number | null
  billStatus: string | null
  platformAmount: number | null
  platformStatus: string | null
  platformOrderNo: string | null
  platformRefundNo: string | null
  remark: string | null
  createdAt: string
}

/** 对账差异单（金额单位：分）。 */
export interface ReconciliationDifference {
  id: number
  diffNo: string
  taskId: number
  recordId: number | null
  diffType: string
  /** PENDING/CONFIRMED/CLOSED/IGNORED */
  status: string
  channelOrderNo: string | null
  merchantOrderNo: string
  billAmount: number | null
  platformAmount: number | null
  billStatus: string | null
  platformStatus: string | null
  platformOrderNo: string | null
  remark: string | null
  /** ADJUST/SUPPLEMENT */
  handleType: string | null
  handleRemark: string | null
  handleResult: string | null
  ledgerNo: string | null
  confirmedBy: string | null
  confirmedAt: string | null
  handledBy: string | null
  handledAt: string | null
  createdBy: string
  createdAt: string
  updatedAt: string
}

/** 风控规则（params 为按类型定义的 JSON 字符串）。 */
export interface RiskRule {
  id: number
  ruleName: string
  /** AMOUNT_LIMIT/FREQUENCY/BLACKLIST/SAME_IP_MULTI_ACCOUNT */
  ruleType: string
  params: string
  /** 命中加的风险分（黑名单类型命中直接拒绝） */
  score: number
  /** 执行优先级，小者先执行 */
  priority: number
  enabled: boolean
  remark: string | null
  createdAt: string
  updatedAt: string
}

/** 风险事件（风控检查留痕，只增不改；金额单位：分）。 */
export interface RiskEvent {
  id: number
  eventNo: string
  orderId: number
  merchantId: number
  amount: number
  clientIp: string | null
  deviceId: string | null
  /** 命中规则明细快照（JSON 数组字符串：ruleName/ruleType/score/detail），未命中为 null */
  hitRules: string | null
  totalScore: number
  /** ALLOW/REVIEW/REJECT */
  verdict: string
  createdAt: string
}

/** 风控黑名单条目（用户/IP/设备三维度，维度 + 值唯一）。 */
export interface RiskBlacklist {
  id: number
  /** USER/IP/DEVICE */
  dimension: string
  value: string
  reason: string | null
  createdBy: string
  createdAt: string
}

/** Webhook 事件（一行 = 一个待投递事件）。 */
export interface WebhookEvent {
  id: number
  merchantId: number
  eventId: string
  eventType: string
  bizOrderNo: string
  /** PENDING 待投递或待重试 / SUCCESS 投递成功 / EXHAUSTED 重试耗尽 */
  status: string
  /** 已重试次数（不含首次投递） */
  retryCount: number
  nextRetryAt: string | null
  createdAt: string
}

/** Webhook 投递尝试记录（一次尝试一行，只增不改）。 */
export interface WebhookDelivery {
  id: number
  eventRowId: number
  /** 第几次投递（1=首次，2+ 为重试） */
  attemptNo: number
  success: boolean
  responseCode: number | null
  responseBody: string | null
  durationMs: number | null
  createdAt: string
}

/** Webhook 事件详情（报文全文 + 投递日志）。 */
export interface WebhookEventDetail {
  event: WebhookEvent
  payload: string
  deliveries: WebhookDelivery[]
}

/** 站内信通知记录。 */
export interface NotificationItem {
  id: number
  templateCode: string
  eventId: string
  /** MERCHANT / USER */
  receiverType: string
  receiverId: number
  title: string
  content: string
  /** 站内信已读时间（null=未读） */
  readAt: string | null
  inAppStatus: string
  /** SENT / SKIPPED 未配置SMTP / FAILED 发送失败 */
  emailStatus: string | null
  emailError: string | null
  createdAt: string
}
