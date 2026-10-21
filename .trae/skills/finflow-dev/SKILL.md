---
name: finflow-dev
description: FinFlow 支付平台项目的分步开发约定。开发 FinFlow 项目代码、执行开发步骤 S01–S42、或用户提到 FinFlow 的某个模块（支付/清分/结算/对账/风控/退款等）时使用 — 先读本约定与步骤报告再动手。
---

# FinFlow 开发约定

开发 FinFlow（企业级支付与资金结算平台）任何代码前，按本约定执行。

## 步骤纪律

1. 步骤清单在仓库根目录 `FinFlow分步开发报告.md`。执行任何步骤前，先读取该文件中对应步骤的「范围 / 做什么 / 不做 / 验收」四段内容。
2. 一次只做一个步骤（S01–S42），不跳步、不合并、不顺手做相邻步骤的内容。
3. 涉及建表、状态机、接口定义时，先输出表结构或类设计请用户确认，确认后再写代码。
4. 验收不过不前进：当前步骤有问题，先修复当前步骤，不带病进入下一步。
5. 每步完成后必须能编译、能启动、核心逻辑有单测，才允许进入下一步。

## 防屎山六条铁律

| # | 铁律 |
|---|------|
| 1 | 金额一律用「分」（bigint）存储，禁止 float/double/decimal 参与计算，程序内计算用 BigDecimal |
| 2 | 状态机集中在一个独立组件里（合法转换表 + 校验方法），禁止在业务代码里散写 if-else 判断状态 |
| 3 | 资金流水只增不改；任何余额变动必须与流水写入在同一个数据库事务内 |
| 4 | 模块之间只能通过对方暴露的 Service 接口调用，禁止跨模块直接读别人的表 |
| 5 | 幂等、异常处理、统一响应等做成公共组件，禁止每个接口各写一套 |
| 6 | 每步完成后必须能编译、能启动、核心逻辑有单测 |

## MyBatis-Plus 使用约定

- 简单 CRUD 用 BaseMapper / ServiceImpl；复杂 SQL（行锁、批量流水、对账匹配、统计报表）手写 XML，不硬凑 Wrapper
- 乐观锁用官方 `@Version` 插件；行级锁 `select ... for update` 手写 XML
- 分页统一用 MP 分页插件
- 流水表、审计日志表禁用逻辑删除、自动更新填充等花活，保持只增不改的朴素写法
- 所有 DDL 走 Flyway 迁移脚本（MySQL 语法），禁止直接改库

## 技术基线

Java 25 + Spring Boot 4.1 + Spring Security + MyBatis-Plus + MySQL 8.0 + Redis + Kafka + Flyway；前端 Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts + vue-tsc。架构为模块化单体（包结构按 auth / merchant / payment / refund / account / ledger / clearing / settlement / reconciliation / risk / webhook / notification / audit / system / common 划分），不引入微服务。

## 任务接收方式

- 用户给出步骤编号（如「做 S12」）时，按报告里该步骤的四段内容执行，完成后按「验收」标准自测并汇报。
- 用户描述模糊时，先对照报告确认是哪一步、范围边界是什么，与用户确认后再开始。
