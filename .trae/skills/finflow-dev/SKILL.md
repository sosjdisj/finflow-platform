---
name: finflow-dev
description: FinFlow 支付平台项目的分步开发约定。开发 FinFlow 项目代码、执行开发步骤 S01–S42 或微服务演进步骤 M01–M17、或用户提到 FinFlow 的某个模块（支付/清分/结算/对账/风控/退款等）、或涉及微服务拆分与 Spring Cloud 引入时使用 — 先读本约定与对应步骤报告再动手。
---

# FinFlow 开发约定

开发 FinFlow（企业级支付与资金结算平台）任何代码前，按本约定执行。

## 步骤纪律

1. 步骤清单在仓库根目录：`FinFlow分步开发报告.md`（S01–S42）与 `FinFlow微服务演进执行报告.md`（M01–M17）。执行任何步骤前，先读取对应文件中该步骤的「范围 / 做什么 / 不做 / 完成标志」内容。
2. 一次只做一个步骤，不跳步、不合并、不顺手做相邻步骤的内容；S 与 M 两套编号互不混用。S42 是可选步骤且已取消，其内容由 M16、M17 覆盖，不要单独执行。
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

Java 25 + Spring Boot 4.1 + Spring Security + MyBatis-Plus + MySQL 8.0 + Redis + Kafka + Flyway；前端 Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts + vue-tsc。当前架构为模块化单体（包结构按 auth / merchant / payment / refund / account / ledger / clearing / settlement / reconciliation / risk / webhook / notification / audit / system / common 划分）。

微服务演进按 `FinFlow微服务演进执行报告.md` 执行（M01–M17）：目标形态为「运营后台单体 + 开放平台网关 + 渠道服务 + 风控服务」四个部署单元，Spring Cloud Alibaba（Nacos 注册配置 + Sentinel 流控熔断）在 M08 之后按业务诉求逐步引入，不得提前引入。

## 微服务演进约定（M01–M17）

1. 资金主链路 payment / refund / account / ledger 不拆，保持同库同事务。
2. 一次只拆一个服务，拆完观察后再动下一个。
3. 每个服务拆出后必须保留回退路径（可切回进程内调用）。
4. Spring Cloud Alibaba 组件只在演进报告「组件与引入时机」一节对应的步骤引入；没有业务诉求的组件（Seata / RocketMQ / Dubbo / Stream / Bus）不引入。
5. 跨 schema 禁止 join，跨库数据只走对方 api 接口或事件投影。
6. 同步调用必须有超时预算与降级策略；能异步的一律走 Kafka 事件。
7. 事件契约集中在 contract 模块，字段与语义只增不改。
8. 资金类一致性不引入分布式事务框架，统一用「本地事务 + 事件 + 对账兜底」。

## 任务接收方式

- 用户给出 S 编号（如「做 S12」）时，按 `FinFlow分步开发报告.md` 该步骤的四段内容执行，完成后按「验收」标准自测并汇报。
- 用户给出 M 编号（如「做 M07」）时，按 `FinFlow微服务演进执行报告.md` 该步骤的四段内容执行，完成后按「完成标志」自测并汇报。
- 用户描述模糊时，先对照两份报告确认是哪一步、范围边界是什么，与用户确认后再开始。
