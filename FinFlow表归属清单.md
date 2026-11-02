# FinFlow 表归属与跨模块访问清单（M05）

> 执行日期：2026-10-31 ｜ 范围：数据归属 ｜ M05 只做盘点与标注，**未迁移任何表**；M06 完成后两张渠道表的实体与 Mapper 已随能力迁入 `channel`；M07 已把这两张表迁入独立 schema `finflow_channel`（同实例，V35）；M10 已把风控三表迁入独立 schema `finflow_risk` 并新增规则版本快照表（同实例，V36）。
>
> 判定口径：表归属 = 该表实体（`@TableName`）与 Mapper 所在模块；运行时访问 = Java / MyBatis XML 中实际执行的 SQL；迁移期访问 = Flyway 脚本里的 DDL 与种子数据。当前 38 张表位于 schema `finflow`，`payment_channel` / `payment_transaction` 位于 `finflow_channel`，`risk_rule` / `risk_event` / `risk_blacklist` / `risk_rule_version` 位于 `finflow_risk`，**全库无外键约束**，关联全靠业务字段与索引。

## 一、结论速览

| 检查项 | 结果 |
| --- | --- |
| 表总数（`CREATE TABLE`） | 44 |
| 有唯一实体的表（`@TableName`） | 44，无孤儿表 |
| 归属不明的表 | **0** |
| `channel` 相关表的归属 | **明确**：`payment_channel`、`payment_transaction` 归 `channel` 模块（M06 随能力迁入，M07 已迁 schema `finflow_channel`）|
| `risk` 相关表的归属 | **明确**：`risk_rule`、`risk_event`、`risk_blacklist`、`risk_rule_version` 归 risk（M10 已迁 schema `finflow_risk`）|
| 运行时跨模块直接读表 | **2 处 Mapper**，涉及 7 张外部表；渠道跨 schema 读已于 M07 收口，风控 `risk_event` 报表读按 schema 限定 |
| 运行时跨模块写表 | 0（模块间写操作均走对方 api 或事件）|
| 迁移期跨模块触表 | 20 个业务脚本写 `sys_permission` / `sys_menu`；4 处跨模块 `ALTER`；V35 / V36 属各自模块整表迁库 |

## 二、表 → 模块清单（44 张）

| 表 | 归属模块 | 实体类 | 迁移脚本 | 说明 |
| --- | --- | --- | --- | --- |
| `sys_user` | auth | `auth.entity.SysUser` | V2 | 登录用户 |
| `sys_refresh_token` | auth | `auth.entity.SysRefreshToken` | V3 | 刷新令牌 |
| `sys_login_log` | auth | `auth.entity.SysLoginLog` | V5 | 登录日志 |
| `sys_role` | system | `system.entity.SysRole` | V4 | 角色 |
| `sys_permission` | system | `system.entity.SysPermission` | V4 | 权限点（各业务脚本按 id 顺延追加）|
| `sys_menu` | system | `system.entity.SysMenu` | V4 | 菜单（各业务脚本追加）|
| `sys_user_role` | system | `system.entity.SysUserRole` | V4 | 用户-角色 |
| `sys_role_permission` | system | `system.entity.SysRolePermission` | V4 | 角色-权限 |
| `system_config` | system | `system.entity.SystemConfig` | V33 | 系统配置 |
| `file_object` | system | `system.file.FileObject` | V34 | 文件对象（MinIO）|
| `audit_log` | audit | `audit.entity.AuditLog` | V32 | 操作审计（只增不改）|
| `merchant` | merchant | `merchant.entity.Merchant` | V7 | 商户主体 |
| `merchant_status_log` | merchant | `merchant.entity.MerchantStatusLog` | V7 | 商户状态变更 |
| `merchant_api_key` | merchant | `merchant.entity.MerchantApiKey` | V8 | API 密钥（V29 补密文列）|
| `merchant_rate` | merchant | `merchant.entity.MerchantRate` | V9 | 商户费率 |
| `merchant_rate_log` | merchant | `merchant.entity.MerchantRateLog` | V9 | 费率变更日志 |
| `merchant_settle_account` | merchant | `merchant.entity.MerchantSettleAccount` | V9 | 结算账户 |
| `merchant_tier_rate` | merchant | `merchant.entity.MerchantTierRate` | V20 | 阶梯费率（**脚本放在清分迁移里，表归 merchant**）|
| `payment_order` | payment | `payment.entity.PaymentOrder` | V11 | 支付订单（V18 补可退额度列）|
| `payment_order_state_log` | payment | `payment.entity.PaymentOrderStateLog` | V11 | 支付状态流水 |
| `payment_channel` | channel | `channel.entity.PaymentChannel` | V12 | 渠道配置（V20 补渠道成本费率；M07/V35 迁入 `finflow_channel`）|
| `payment_transaction` | channel | `channel.entity.PaymentTransaction` | V12 | 渠道交互记录（只增不改；M07/V35 迁入 `finflow_channel`）|
| `payment_callback` | payment | `payment.entity.PaymentCallback` | V13 | 渠道回调记录（只增不改）|
| `account` | account | `account.entity.Account` | V15 | 商户账户与余额 |
| `account_ledger` | ledger | `ledger.entity.AccountLedger` | V16 | 资金流水（只增不改；V17/V18 补列）|
| `refund_order` | refund | `refund.entity.RefundOrder` | V18 | 退款单 |
| `refund_order_state_log` | refund | `refund.entity.RefundOrderStateLog` | V18 | 退款状态流水 |
| `clearing_order` | clearing | `clearing.entity.ClearingOrder` | V20 | 清分单 |
| `clearing_detail` | clearing | `clearing.entity.ClearingDetail` | V20 | 清分明细 |
| `settlement_order` | settlement | `settlement.entity.SettlementOrder` | V21 | 结算单 |
| `settlement_detail` | settlement | `settlement.entity.SettlementDetail` | V21 | 结算明细 |
| `settlement_order_state_log` | settlement | `settlement.entity.SettlementOrderStateLog` | V22 | 结算状态流水 |
| `reconciliation_task` | reconciliation | `reconciliation.entity.ReconciliationTask` | V24 | 对账任务 |
| `reconciliation_bill_item` | reconciliation | `reconciliation.entity.ReconciliationBillItem` | V24 | 渠道账单暂存（只增不改）|
| `reconciliation_record` | reconciliation | `reconciliation.entity.ReconciliationRecord` | V25 | 匹配结果 |
| `reconciliation_difference` | reconciliation | `reconciliation.entity.ReconciliationDifference` | V26 | 差异处理 |
| `risk_rule` | risk | `risk.entity.RiskRule` | V27 | 风控规则（V36 迁入 `finflow_risk` 并补 `version` 列）|
| `risk_event` | risk | `risk.entity.RiskEvent` | V27 | 风控决策日志（V36 迁入 `finflow_risk`）|
| `risk_blacklist` | risk | `risk.entity.RiskBlacklist` | V27 | 名单（V36 迁入 `finflow_risk`）|
| `risk_rule_version` | risk | `risk.entity.RiskRuleVersion` | V36 | 规则历史版本快照（只增不改，唯一键 `(rule_id, version)`）|
| `webhook_event` | webhook | `webhook.entity.WebhookEvent` | V28 | 商户通知事件 |
| `webhook_delivery` | webhook | `webhook.entity.WebhookDelivery` | V28 | 投递记录与重试 |
| `notification_template` | notification | `notification.entity.NotificationTemplate` | V30 | 通知模板 |
| `notification` | notification | `notification.entity.Notification` | V30 | 站内信（V31 补已读）|

模块表数：auth 3、system 7、audit 1、merchant 7、payment 3、channel 2、refund 2、account 1、ledger 1、clearing 2、settlement 3、reconciliation 4、risk 4、webhook 2、notification 2。`contract` / `common` 不持有任何表。

## 三、运行时跨模块直接读表 / join（M07 / M10 后剩 2 处 Mapper，渠道跨 schema 读已收口，风控报表读限定 schema）

### 3.1 reconciliation 读 payment / refund 的 3 张表（channel 已改走 api）

位置：[ReconciliationMatchMapper.xml](finflow-backend/src/main/resources/mapper/reconciliation/ReconciliationMatchMapper.xml#L19-L138)，`ReconciliationMatchMapper.insertMatchResults`（S28 批量匹配，`INSERT ... SELECT`）。

| 被读表 | 归属 | 形式 |
| --- | --- | --- |
| `payment_order` | payment | 只读 join / EXISTS 判断 |
| `refund_order` | refund | 只读聚合子查询（成功退款合计）|
| `payment_order_state_log` | payment | 只读 EXISTS（当日进 PAID 的判定）|

M07 起不再读 `payment_transaction`：`MISSING_IN_CHANNEL` 改为「主库候选 SQL（`selectMissingInChannelCandidates`）→ `channel.api.PaymentTransactionApi.findChannelOrderNos` 批量判定渠道归属并取首条渠道单号 → 回填后批量写入 `reconciliation_record`」，避免跨 schema join。剩余 payment / refund 读仍在同一 schema，按演进约定「资金主链路不拆」暂留；对账独立部署或 payment 移库时再改 api 快照 / 事件投影。

### 3.2 system 运营报表读 6 张外部表

位置：[DashboardStatsMapper.xml](finflow-backend/src/main/resources/mapper/system/DashboardStatsMapper.xml#L9-L84)，10 个只读 `SELECT`，无写入。

| 查询方法 | 被读表 | 归属 | 行号 |
| --- | --- | --- | --- |
| `selectPaymentTodaySummary` | `payment_order` | payment | L14 |
| `selectTodayRefundAmount` | `refund_order` | refund | L20 |
| `selectTodaySettlementAmount` | `settlement_order` | settlement | L25 |
| `selectTodayPlatformFee` | `clearing_order` | clearing | L32 |
| `selectTodayRiskRejectCount` | `finflow_risk.risk_event` | risk | L39 |
| `selectTradeAmountTrend` | `payment_order` | payment | L44 |
| `selectSuccessRateTrend` | `payment_order` | payment | L54 |
| `selectRefundTrend` | `refund_order` | refund | L60 |
| `selectChannelShare` | `clearing_order` | clearing | L69 |
| `selectMerchantTop` | `clearing_order` + `merchant`（join）| clearing / merchant | L78-L79 |

`DashboardStatsMapper` 是运营后台聚合报表的既有特例（接口 Javadoc 已注明「只读、不写任何数据」）。归属上仍是 system 的 Mapper 读他模块表，**不新增**；后续若要拆库，改为各模块 api 聚合或预聚合指标表。M10 起 `risk_event` 已不在 `finflow`，该查询在 SQL 内写死 `finflow_risk.`，不做跨 schema join，也不新增 system → risk 的 Java 依赖。

## 四、迁移期跨模块触表（附注，非运行时）

M05 只盘点，以下两类不阻塞本步骤，但属于拆分时需要一并处理的点。

### 4.1 业务迁移脚本写 system 的权限 / 菜单表

20 个业务脚本向 `sys_permission` / `sys_menu` 追加权限点与菜单：V7、V8、V9、V10、V11、V14、V16、V17、V18、V19、V22、V23、V24、V25、V26、V27、V28、V30、V31、V32（V33、V34 属 system 模块自身脚本，不计）。属单向 seed 数据，无读取、无运行时依赖。

### 4.2 跨模块 `ALTER`（脚本位置与表归属不一致）

| 脚本 | 被改表 | 归属 | 改动 |
| --- | --- | --- | --- |
| V17（资金中心菜单）| `account_ledger` | ledger | 加 `remark` 列 |
| V18（退款建表）| `payment_order` | payment | 加 `refunded_amount` 列 |
| V18（退款建表）| `account_ledger` | ledger | 改 `biz_type` 注释补 REFUND |
| V20（清分建表）| `payment_channel` | channel | 加 `cost_rate_bps` 列并回填 |
| V20（清分建表）| `merchant_tier_rate` | merchant | 表本身在此脚本创建 |

## 五、归属判定中的几个取舍

- `payment_channel` / `payment_transaction`：M05 盘点时归 payment 的「渠道域」；M06 把实体、Mapper、查询服务一并迁入顶层 `channel` 模块，归属改为 `channel`；M07 再经 V35 把两张表迁到 `finflow_channel`，实体以 `@TableName(schema = "finflow_channel")` 映射。
- `payment_callback`：归 payment。渠道回调记录是支付入账凭证，M07 不迁；M06 只迁回调解析能力，不迁这张表。
- `merchant_tier_rate`：归 merchant（实体、Mapper、读写均在 merchant）。脚本落在 V20 只是历史书写位置，不影响归属。
- `file_object`：归 system（实体在 `system.file`，业务模块只经 `FileObjectApi` 访问）。
- `risk_rule` / `risk_event` / `risk_blacklist` / `risk_rule_version`：归 risk，M10/V36 迁入 `finflow_risk`；`risk_rule_version` 是规则的从属快照表，与规则同模块、同事务写入。
- `contract` / `common`：无表。

## 六、复现与验证

- 表清单：`db/migration` + `db/channel` + `db/risk` 中 `CREATE TABLE` 共 44 条，与全工程 `@TableName` 44 处一一对应；无实体缺口、无多余实体。
- 跨模块读表：对 `src/main/resources/mapper/**/*.xml` 逐个核对 `FROM` / `JOIN` / `INSERT` / `UPDATE` 的表名，仅命中第三节 2 处。
- 运行时 Java 层跨模块 `mapper` / `entity` 引用：`ModuleBoundaryTest` 规则一冻结基线为 0 字节，规则一~规则四基线全部 0 字节，规则五通过。
- 数据库无外键：迁移脚本中 `FOREIGN KEY` / `REFERENCES` 零命中；关联字段仅建普通索引。
- schema 分布：应用仍连 `jdbc:mysql://localhost:3306/finflow`；`payment_channel` / `payment_transaction` 自 V35 起位于同实例 `finflow_channel`，`risk_rule` / `risk_event` / `risk_blacklist` / `risk_rule_version` 自 V36 起位于同实例 `finflow_risk`，应用用户需具备这两个库的读写权限（跨库 `RENAME` 还需目标库 CREATE / INSERT 权限）。

## 七、遗留（超出 M05 范围，不在本步骤处理）

1. 对账仍直接只读 payment / refund 的同 schema 表（渠道读已收口）；对账与 payment / refund 同库同事务，按演进约定「资金主链路不拆」，待对账独立部署时再改。
2. Dashboard 跨模块聚合维持现状，归属 system，不随渠道 / 风控拆分而迁表（风控部分按 schema 限定只读）。
3. Flyway 已按模块分目录（`db/migration` + `db/channel` + `db/risk`），`db/channel` 与 `db/risk` 后续脚本必须显式限定 schema。
4. 迁移脚本的跨模块 `ALTER` 与菜单 seed 暂不重排；V35 / V36 均采用整表 `RENAME`，未重建表、未复制数据。
