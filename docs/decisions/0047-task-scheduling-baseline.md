# ADR-0047：任务调度模块基线

- 状态：Proposed → Accepted（实现授权）
- 日期：2026-08-21
- 决策者：Ainer 项目维护者
- 取代：无
- 被取代：无

## 背景

ADR-0040 将任务调度（P4）列为 1.0 非目标（未建设），ADR-0038 将其定义为 P4 范围
「任务调度管理面闭环」。通知模块已验证 PostgreSQL SKIP LOCKED 队列 + 指数退避 + virtual
thread 并发的完整模式；本 ADR 将该模式泛化为独立模块，让产品用统一的任务基础设施替代
`@Scheduled`、手写轮询或外部调度器。

## 决策

### 1. 模块定位与边界

- `ainer-module-task`：任务类型注册 + 延迟/周期执行 + SKIP LOCKED 队列领取 + 指数退避
  重试 + 管理面（查询/重试/取消）。
- 产品实现 `TaskHandler` 端口（Spring-free 接口），模块负责调度与执行保障。
- **不做**：cron 表达式解析（用 interval + initialDelay）、分布式 leader 选举（SKIP LOCKED
  天然多实例安全）、工作流编排（产品域）、UI。

### 2. 数据模型

```text
ainer_task_definition(
  id UUID PK v7,
  task_type VARCHAR(128) UNIQUE NOT NULL,      -- 产品注册的任务类型标识
  display_name VARCHAR(256) NOT NULL,
  handler_ref VARCHAR(256) NOT NULL,            -- 产品 handler 的 Spring bean 名
  max_attempts INT NOT NULL DEFAULT 3,
  timeout_seconds INT NOT NULL DEFAULT 300,
  status VARCHAR(16) CHECK IN ('ACTIVE','PAUSED'),
  created_at/updated_at TIMESTAMPTZ
)

ainer_task_job(
  id UUID PK v7,
  task_type VARCHAR(128) FK → ainer_task_definition,
  payload JSONB NOT NULL DEFAULT '{}',          -- 产品定义的输入参数
  status VARCHAR(16) CHECK IN ('PENDING','RUNNING','SUCCEEDED','FAILED','EXHAUSTED','CANCELLED'),
  attempt_count INT NOT NULL DEFAULT 0,
  max_attempts INT NOT NULL,                    -- 从定义复制（创建时快照）
  next_run_at TIMESTAMPTZ NOT NULL,             -- 延迟执行时间 / 重试退避时间
  interval_seconds BIGINT NULL,                 -- 非空=周期任务（完成后重置 next_run_at）
  locked_by VARCHAR(128) NULL,                  -- SKIP LOCKED 领取者标识
  locked_at TIMESTAMPTZ NULL,
  last_error VARCHAR(512) NULL,                 -- 最近失败原因（不保存 payload 正文）
  created_by_issuer/type/id VARCHAR,            -- 提交者三元组
  created_at/updated_at/completed_at TIMESTAMPTZ
)

ainer_task_audit(                               -- append-only
  id UUID PK v7,
  job_id UUID FK → ainer_task_job,
  event VARCHAR(32) CHECK IN ('SUBMITTED','CLAIMED','SUCCEEDED','FAILED','RETRY_SCHEDULED','EXHAUSTED','CANCELLED','PAUSED','RESUMED'),
  attempt INT,
  actor_issuer/type/id VARCHAR,
  detail VARCHAR(512) NULL,
  occurred_at TIMESTAMPTZ
)
```

### 3. 执行引擎

- `TaskExecutionEngine`：固定间隔轮询（默认 5s，可配）`SELECT ... WHERE status='PENDING'
  AND next_run_at <= now() FOR UPDATE SKIP LOCKED LIMIT N`，virtual thread 提交执行。
- 领取：`UPDATE SET status='RUNNING', locked_by=?, locked_at=now(), attempt_count=attempt_count+1`。
- 超时：`timeout_seconds` 后引擎视为 FAILED（不杀线程——虚拟线程等待自然结束，但不再等
  其结果）。防僵尸：启动时把 `locked_at < now() - timeout*2` 的 RUNNING 重置为 PENDING。
- 重试：FAILED 且 `attempt_count < max_attempts` → `next_run_at = now() + backoff(attempt)`，
  backoff = `min(2^attempt * base, max)`，base 默认 10s、max 默认 1h。
- 周期：SUCCEEDED 且 `interval_seconds` 非空 → 重置 PENDING + `next_run_at = now() + interval`。

### 4. 管理 API

- `POST /api/tasks/definitions`：注册任务类型（task.manage scope）
- `GET /api/tasks/definitions`：分页列出（task.read）
- `POST /api/tasks/jobs`：提交任务（task.submit；payload 为产品定义 JSON）
- `GET /api/tasks/jobs?status=&taskType=`：分页查询（task.read）
- `POST /api/tasks/jobs/{id}/cancellations`：取消未完成任务（task.manage）
- `POST /api/tasks/jobs/{id}/retries`：手动重试 FAILED/EXHAUSTED（task.manage）
- `POST /api/tasks/definitions/{id}/status-changes`：启停任务类型（task.manage）

### 5. 与通知模块的关系

通知投递保留自己的引擎（有特定的渠道分发逻辑）；任务模块是通用基础设施。两者共享
SKIP LOCKED + 退避模式但各自独立——不互相依赖。未来通知可迁移到任务模块之上（非本
切片）。

## 安全

- scope：`task.read` / `task.manage` / `task.submit`（应用服务内强制）
- 错误码：`AINER.TASK.*`（404/409/422）
- 审计同事务 append-only；`last_error` 只存异常摘要（不保存 payload 正文）
- payload 是不可信输入：handler 自行校验
- 提交者三元组持久化（溯源）

## 非目标

- cron 表达式、日历语义、时区感知调度
- 分布式 leader / 单实例互斥（SKIP LOCKED 已保证不重复执行）
- 工作流 DAG / 依赖编排
- 死信队列的外部导出
- 管理面 UI

## 参考

- [ADR-0038（P4 范围）](0038-p4-scope-refinement-and-enterprise-base.md)
- [ADR-0040（1.0 合同；P4 非目标）](0040-p3-enterprise-base-and-1.0-product-contract.md)
- 通知模块 SKIP LOCKED 模式（ainer-module-notification）
