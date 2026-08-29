# AGENTS.md

order-svc 是订单微服务（bounded context）。技术设计见 `../.agents/designs/2026-08-28-order-management.md`。

## 分层架构

依赖方向：`interfaces.http → application.port.in → application.service → domain`；`infra.client → application.port.out`（依赖倒置）；`domain` 不依赖其他层。

已确认的妥协：JPA 与 Spring Data 注解直接标注在领域对象上，仓储端口直接继承 `JpaRepository`，不引入独立的持久化 Row 模型。

## 项目结构

```text
org.acm.os
├── OrderSvcApplication              # Spring Boot 启动入口（默认扫描 org.acm.os）
│
├── domain                            # 领域层：纯业务，不依赖其他层
│   ├── order/                        # Order 聚合根、OrderItem、OrderStatus、OrderRepository、
│   │                                 # DuplicateSkuException、CurrencyMismatchException
│   ├── payment/                      # Payment 与 PaymentStatus
│   ├── refund/                       # Refund、RefundType 与 RefundStatus
│   ├── shipment/                     # Shipment、ShipmentItem 与 ShipmentStatus
│   └── shared/                       # AuditMetadata（JPA @MappedSuperclass）、BusinessException 基类、
│                                     # InvalidRequestException
│
├── application                       # 应用层：用例编排 + 端口定义
│   ├── port/                         # 端口：驱动端 in + 被驱动端 out
│   │   ├── in/                       # 入站用例端口（adapter 依赖其抽象）
│   │   │   ├── OrderUseCase          # 创建/查询订单用例端口；Payment/Refund/ShipmentUseCase
│   │   │   ├── command/              # CreateOrderCommand 等入站命令
│   │   │   └── query/               # SearchOrderQuery 等入站查询
│   │   └── out/                      # 出站端口与端口契约异常 co-locate
│   │       ├── ProductCatalogClient + ProductNotFoundException、PaymentClient
│   │       └── InventoryClient + InsufficientInventoryException、LogisticsClient
│   ├── service/                      # 应用服务（实现入站端口）
│   │   ├── OrderService              # 实现 OrderUseCase；create 内部编排幂等 + 订单创建
│   │   ├── IdempotencyService        # 幂等守卫：check→reserve→execute→complete（含可重试失败协议）
│   │   └── OrderLifecycleService     # 支付、取消/退款、拆分发货与确认收货编排
│   ├── idempotency/                  # 幂等记录实体与仓储（技术缓存表，非领域概念）
│   │   ├── IdempotencyRecord
│   │   └── IdempotencyRecordRepository
│   └── exception/                    # 应用层异常
│       ├── IdempotencyKeyReuseException
│       ├── ReservedByConcurrentWriterException
│       └── RetryableOperationException
│
├── infra                             # 基础设施层：出站适配器 + 配置
│   ├── client/                       # 商品、库存、支付、物流 Mock 与失败注入
│   └── AuditingConfig                # JPA 审计配置
│
└── interfaces/                       # 适配器层：入站 REST
    └── http/
        ├── controller/               # 订单、支付、退款审核、发货及 demo Mock Controller
        ├── mapper/                   # OrderRequestMapper、OrderResponseMapper（MapStruct）
        ├── request/                  # 订单、取消/退款、审核、发货及 Mock 配置请求
        ├── response/                 # 订单详情/摘要、支付、退款与物流响应
        ├── exception/                # UnsupportedApiVersionException
        └── GlobalExceptionHandler    # 统一 Problem Details (RFC 9457) 映射
```
