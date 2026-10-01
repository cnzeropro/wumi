# Wumi · 吾米记账

> 吾之米粟，粒粒分明 —— 本地优先、数据自主、免费无广告的 Android 记账应用。

**当前状态：设计阶段。** 设计文档已定稿并逐节获批（2026-09-14），工程尚未初始化；v0.1 启动时补齐 Gradle 骨架与构建说明。

## 项目身份

| 项 | 值 |
| --- | --- |
| 中文名 / 英文名 | 吾米 / Wumi |
| 应用商店全称 | Wumi · 吾米记账 |
| applicationId | `org.zero.wumi` |
| Slogan | 主：吾米蓄财，智理未来 · 副：Wealth & Me, Hand in Hand |
| 版本基线 | versionName `0.0.1` / versionCode `1`；minSdk 35（Android 15）/ targetSdk 36 / compileSdk 36 |
| 定位 | 自用为主、代码开源；本地优先、数据自主、免费无广告 |
| 商业化 | 无。全功能免费、无广告、无统计上报；不接崩溃上报 SDK |

品牌释义：**Wu** = Wealth / We，**Mi** = Money / Mine → "Wealth & Me" / "We Money" / "Wise Money"。

## 核心原则

**本地 Room 是唯一真相源，云端只是复制物。** 未配置任何同步后端时，App 功能 100% 完整可用；同步是附加能力，不是依赖项。

## 技术栈

| 维度 | 选型 |
| --- | --- |
| 语言 / UI | Kotlin 2.x · Jetpack Compose · Material 3 |
| 架构 | 单 module + 按边界分包；MVVM + StateFlow、Navigation Compose（单 Activity） |
| 本地存储 | Room（唯一真相源，每日滚动备份 7 份） |
| 依赖注入 | Hilt |
| 网络 / 同步 | OkHttp 自封装 WebDAV 客户端（约 200 行，避免依赖停更的第三方库） |
| 图表 | Vico |
| 后台调度 | WorkManager |
| 加密 | AES-256-GCM 信封加密 + PBKDF2-HMAC-SHA256（210,000 迭代）密钥派生 |

## 架构

```
ui/      Compose 界面（明细 · 图表 · 记一笔 · 预算 · 我的）
data/    Room 实体 · DAO · Repository（唯一写入口，带 outbox）
sync/    同步引擎（LWW 合并）· SyncBackend 接口 · webdav/ 实现
crypto/  AES-256-GCM 信封加密 · PBKDF2 密钥派生
di/      Hilt 模块
              │                                │
              ▼                                ▼
      本地 Room（唯一真相源）          SyncBackend 实现（可插拔）
      每日滚动备份 7 份                ├─ WebDAV（坚果云，首发）
                                       ├─ Git / GitHub（路线图）
                                       └─ S3 兼容（路线图）
```

## 同步设计要点

- **可插拔后端**：`SyncBackend` 只暴露 `get` / `put` / `list` / `mkdir`，乐观锁统一用 ETag 表达，不泄漏 WebDAV 特有概念；换后端只新增约 200 行实现类，同步引擎 / 加密 / 合并逻辑零改动
- **合并规则 LWW**：`updatedAt` 新者胜；30 秒时钟模糊区内以 `deviceId` 字典序决胜 —— 任意合并顺序结果一致
- **不丢数据**：Repository 写业务表与写 `outbox` 在同一 Room 事务内完成
- **云端布局**：`manifest.json.enc`（仓库指针 / 设备注册表 / 水位）+ `ops/NNNN.json.enc`（每片 ≤500 条）+ `snapshots/`（全量快照）
- **端到端加密**：口令派生 KEK 包裹随机 DEK（信封加密），云端只见密文，改口令无需重加密全量文件
- **触发时机**：启动 / 回前台（防抖 5s）、本地写入后 30s 防抖、手动下拉刷新、WorkManager 每 6 小时（充电 + 联网约束）
- **迁移性**：密文文件自包含，换云 = 新后端配置 + 从本地写全量快照，账单数据永不搬家

## 数据模型

每个业务实体都带**同步基因**：

| 字段 | 类型 | 作用 |
| --- | --- | --- |
| `id` | UUIDv7 字符串 | 客户端生成、时间有序，离线也能造新记录 |
| `createdAt` / `updatedAt` | Instant | `updatedAt` 是 LWW 合并依据 |
| `deletedAt` | Instant? | 软删除墓碑，跨设备传播删除 |
| `deviceId` | String | 最后修改者、LWW 平局决胜、预留家庭共享 |

实体：`Account`（账户）、`Category`（二级分类，自引用）、`Transaction`（交易）、`Budget`（预算）、`RecurringRule`（周期规则）、`CurrencyInfo`（币种与汇率）、`Setting`（KV 设置）、`OutboxEntry`（待推送队列）。

**金额铁律**：金额一律 `Long` 存最小单位（分 / cent），显示层才做千分位与币种符号格式化，杜绝浮点误差；汇率存字符串（BigDecimal 解析）；多币种记账锁定当日汇率快照，历史账单不随汇率波动重算。

## 功能分期

| 版本 | 交付内容 | 验收标准 |
| --- | --- | --- |
| v0.1 记账闭环 | 账户 / 二级分类 / 三类交易 CRUD、明细页、本地每日滚动备份（7 份）、CSV / JSON 导出 | 完整记一天账；杀进程不丢数据 |
| v0.2 图表 + 多币种 | 统计图表、汇率管理、外币账户与外币记账（快照换算） | 月报数字与手算一致 |
| v0.3 同步 | 同步引擎 + 坚果云 WebDAV 后端 + 端到端加密；多后端配置框架 | 双设备互记，断网离线记、联网自动合，无丢失无重复 |
| v0.4 预算 | 总预算 + 分类预算、超支通知 | 跨月 / 跨设备预算进度正确 |
| v0.5 周期 + 导入 | 周期记账、模板、支付宝 / 微信账单 CSV 导入（智能列映射） | 房租自动月生成；账单一键导入 |
| v1.0 打磨上架 | 应用锁、桌面小组件、空状态与动效打磨 | 上架 Google Play / GitHub Release |

## 信息架构

底部 4 tab + 中央「记一笔」：**明细**（月收支结余、按日分组、下拉刷新即同步）、**图表**（月/年切换、分类占比环形 + 收支趋势折线）、**预算**（总预算进度 + 分类预算，超支变红）、**我的**（账户 / 分类管理、周期记账、模板、同步设置、备份导出、应用锁、外观、诊断包）。

「记一笔」为 ModalBottomSheet：金额大字 + 分类宫格 + 账户 / 日期 / 备注次级行，目标 **3 秒记完一笔**。

## 刻意不做（YAGNI）

投资持仓、股票行情、银行自动同步、报销中心、多人实时协作、AI 记账、无障碍自动记账 —— 均不在 v1 蓝图内。

## 许可

[MIT](LICENSE)
