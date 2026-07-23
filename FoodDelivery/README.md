# FoodDelivery

Monorepo Spring Boot 3 + Spring Cloud cho food delivery platform. Repository bám DDD/Hexagonal, chia theo layer rõ ràng để mỗi service độc lập, dễ test, dễ mở rộng.

## Cấu trúc tổng

```text
FoodDelivery/
├── pom.xml
├── infra/
│   ├── api-gateway/
│   ├── config-server/
│   └── eureka-server/
├── shared/
│   ├── common-events/
│   ├── common-repo/
│   ├── common-security/
│   └── common-web/
└── services/
    ├── authentication-service/
    ├── customer-service/
    ├── delivery-service/
    ├── notification-service/
    ├── order-service/
    ├── payment-service/
    └── restaurant-service/
```

## Chuẩn folder cho từng service

Mỗi service chính nên chia theo pattern dưới đây, đúng với ảnh bạn gửi:

```text
src/main/java/com/fooddelivery/<service>/
├── api/
│   ├── controller/
│   ├── dto/
│   └── exception/
├── application/
│   ├── command/
│   └── usecase/
├── domain/
│   ├── exception/
│   ├── model/
│   └── repository/
├── infrastructure/
│   ├── feign/
│   └── persistence/
└── utils/
```

### Ý nghĩa từng folder

- `api`: controller, request/response DTO, exception cho layer HTTP.
- `application`: use case và command object, chứa luồng nghiệp vụ.
- `domain`: model, repository contract, business exception.
- `infrastructure`: JPA, Feign, messaging, adapter, technical implementation.
- `utils`: helper dùng nội bộ service.

## Quy ước layer

- `api` chỉ nhận request và trả response.
- `application/usecase` định nghĩa interface nghiệp vụ.
- `application` implementation xử lý business flow.
- `domain/repository` là contract, không dính framework.
- `infrastructure` implement persistence, client, messaging.

## Service chính

### `authentication-service`
Chịu trách nhiệm `User`, JWT, login/register/logout, refresh token, session và phân quyền role.

### `customer-service`
Chịu trách nhiệm `Customer`, `Address`, profile và address management.

### Các service khác
- `restaurant-service`: quản lý restaurant, menu, menu item.
- `order-service`: đặt món, trạng thái đơn.
- `payment-service`: thanh toán.
- `delivery-service`: giao hàng, driver assignment.
- `notification-service`: gửi thông báo.

## Customer/Auth Service scope

Auth và customer chạy độc lập, mỗi service có database riêng.

API chính:
- `POST /auth/register`
- `POST /auth/login`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /customers/me`
- `PUT /customers/me`
- `GET /customers/me/addresses`
- `POST /customers/me/addresses`
- `PUT /customers/me/addresses/{id}`
- `DELETE /customers/me/addresses/{id}`

## Authentication và Authorization

- `authentication-service` phát access token `RS256` có TTL 15 phút và public key tại `/.well-known/jwks.json`.
- API Gateway và từng business service tự kiểm tra signature, `iss`, `aud`, `exp`; không tin `X-User-Id` do client gửi.
- `CUSTOMER` chỉ thao tác profile/order của chính mình; `RESTAURANT_OWNER` chỉ sửa restaurant/menu mình sở hữu; `ADMIN` có quyền quản trị.
- Payment, delivery scheduling và notification side effects chỉ mở dưới `/internal/**`, xác thực bằng `X-Internal-Service-Secret`.
- Public create-order chỉ nhận `restaurantId`, địa chỉ và `menuItemId`/số lượng. `customerId` lấy từ JWT; tên món, giá, giảm giá và minimum order do restaurant-service báo giá qua API nội bộ; phí giao hàng do order-service cấu hình.
- Order-service kiểm tra tính nhất quán của báo giá, lưu snapshot giá server-side và gửi đúng tổng tiền đã lưu sang payment-service; client không thể tự đặt `unitPrice`, `discountAmount`, `deliveryFee` hoặc `totalAmount`.
- Tạo order yêu cầu `Idempotency-Key`; retry cùng customer/key trả lại order cũ và không thanh toán/giao vận lần hai.
- Payment được lưu bền vững theo unique `orderId`, kiểm tra request hash/idempotency key, refund idempotent và phát outbox event. Order PENDING được reconciliation thay vì hủy ngay khi mất response thanh toán.
- Review chỉ dành cho CUSTOMER và chỉ được tạo khi order-service xác nhận đơn thuộc đúng customer, đúng restaurant và đã `DELIVERED`; rating có constraint `1..5` ở entity và database.
- Refresh token được rotation; logout, đổi role, deactivate hoặc phát hiện reuse sẽ revoke refresh token/session ngay. Access token đã phát có thể còn hiệu lực tối đa đến hết TTL.

Biến môi trường bảo mật chính:

```text
JWT_ISSUER=food-delivery-auth
JWT_AUDIENCE=food-delivery-api
JWT_KEY_ID=food-delivery-auth-1
JWT_PRIVATE_KEY_BASE64=<PKCS#8 DER, base64>
JWT_PUBLIC_KEY_BASE64=<X.509 DER, base64>
INTERNAL_SERVICE_SECRET=<random secret>
ORDER_DELIVERY_FEE=15000
```

Nếu không cấu hình RSA key pair, auth service tạo key tạm thời để chạy local single-replica; không dùng chế độ này cho production hoặc nhiều replica.

Khi chạy với PostgreSQL volume cũ còn database `user_db`, đặt `CUSTOMER_DB_NAME=user_db` trong `.env`. Migration mới sẽ rename `customers.user_id` thành `auth_user_id` mà không sửa checksum migration V1 lịch sử.

## Order fulfillment core flow

Happy path state sequence (order-service is source of truth):

```text
PENDING → PAID → CONFIRMED → PREPARING → READY_FOR_PICKUP
        → PICKED_UP → DELIVERING → DELIVERED
```

- Payment success stops at **PAID** (not CONFIRMED). Delivery is **not** scheduled at payment time.
- Restaurant owner/admin advances kitchen states after payment:
  - `CONFIRMED` = restaurant accepted
  - `PREPARING` = kitchen started
  - `READY_FOR_PICKUP` = food ready; **then** order-service schedules delivery (after-commit, idempotent key `delivery-schedule:{orderId}`)
- Delivery lifecycle (driver assign → pick up → in transit → completed) is published by **delivery-service only** and consumed by order-service to advance `PICKED_UP` / `DELIVERING` / `DELIVERED`.

### Restaurant-order API (gateway → order-service)

Role: `RESTAURANT_OWNER` or `ADMIN`. Path prefix routed only to order-service; `/internal/**` is never exposed via the gateway.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/v1/restaurant-orders?restaurantId=&status=&page=&size=` | List restaurant orders |
| `POST` | `/api/v1/restaurant-orders/{orderId}/accept` | PAID → CONFIRMED |
| `POST` | `/api/v1/restaurant-orders/{orderId}/start-preparing` | CONFIRMED → PREPARING |
| `POST` | `/api/v1/restaurant-orders/{orderId}/ready` | PREPARING → READY_FOR_PICKUP (+ schedule delivery) |
| `POST` | `/api/v1/restaurant-orders/{orderId}/reject` | body `{"reason":"..."}` → cancellation path |

### Customer / Admin order API (gateway → order-service)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/orders` | `CUSTOMER` | Create order (Saga) |
| `GET` | `/api/v1/orders?page=&size=&status=&userId=` | `CUSTOMER` / `ADMIN` | List orders (customer = own; admin = system-wide, optional `userId`) |
| `GET` | `/api/v1/orders/{id}` | owner `CUSTOMER` or `ADMIN` | Order detail (items, address, history, payment/refund/cancel fields) |
| `POST` | `/api/v1/orders/{id}/cancel` | owner `CUSTOMER` or `ADMIN` | body `{"reason":"..."}` — PENDING→CANCELLED; paid→compensation/refund |

### Kafka family topics (v1)

| Topic | Typical producers | Consumers |
| --- | --- | --- |
| `order.events.v1` | order-service outbox | downstream (e.g. notification) |
| `delivery.events.v1` | delivery-service outbox | order-service lifecycle listener |
| `payment.events.v1` | payment-service | order reconciliation (when enabled) |

Payloads are **raw JSON** `IntegrationEventEnvelope` (no Java type headers). Kafka key is `orderId` for order/delivery family events. Consumers use `StringDeserializer` and parse the envelope explicitly.

**Coordinated cutover:** deploy order-service and delivery-service together when switching to these v1 topics so producers and consumers agree on topic names and envelope shape. Legacy per-event topic names are not used by the core fulfillment path.

Notifications are **in-app / internal** side effects only; this platform path does not claim external email or SMS delivery.

### Auth password recovery

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/forgot-password` | public | body `{"email"}` — issues reset token (logged in dev; generic response) |
| `POST` | `/api/v1/auth/reset-password` | public | body `{"token","newPassword"}` — sets password, revokes all sessions |
| `POST` | `/api/v1/auth/change-password` | authenticated | body `{"oldPassword","newPassword"}` — revokes all sessions |

### Driver API (gateway → delivery-service)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `POST` | `/api/v1/auth/register-driver` | public | Create DRIVER account |
| `GET` | `/api/v1/drivers/me` | `DRIVER` | Get profile |
| `PUT` | `/api/v1/drivers/me` | `DRIVER` | Create/update profile (vehicle, phone…) |
| `GET` | `/api/v1/drivers/me/deliveries?status=&page=&size=` | `DRIVER` | Job history / filter by status |
| `GET` | `/api/v1/drivers/me/deliveries/current` | `DRIVER` | Active job or 204 |
| `POST` | `/api/v1/drivers/me/online` / `offline` | `DRIVER` | Availability |
| `PUT` | `/api/v1/drivers/me/location` | `DRIVER` | GPS update |

### Notification API (gateway → notification-service)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| `GET` | `/api/v1/notifications/me?page=&size=&unreadOnly=` | authenticated | Customer inbox |
| `POST` | `/api/v1/notifications/me/{id}/read` | owner | Mark one read |
| `POST` | `/api/v1/notifications/me/read-all` | authenticated | Mark all read |
| `GET` | `/api/v1/notifications` | `ADMIN` | Last 200 jobs (ops) |

Consumes family topics: `order.events.v1`, `delivery.events.v1`, `payment.events.v1`.

## Event model

Theo structure hiện tại, service dùng event contract chung từ `shared/common-events` và publish qua Kafka.

Family topics (fulfillment core):
- `order.events.v1`
- `delivery.events.v1`
- `payment.events.v1`

Legacy / adjacent examples:
- `customer.registered`
- `customer.profile.updated`

## Database

Customer/Auth Service cần migration riêng trong `src/main/resources/db/migration`.

Bảng chính:
- `users`
- `customers`
- `customer_addresses`
- `refresh_tokens`

## Test

Nên có:
- unit test cho domain và application
- integration/API test cho controller
- Postman collection cho toàn bộ API của service

## Build and run

Build root project:

```bash
mvn clean install
```

Chạy riêng customer-service:

```bash
mvn -pl services/customer-service spring-boot:run
```

### Chạy toàn bộ stack bằng Docker Compose

```bash
cp .env.example .env          # set DB_USER / DB_PASSWORD
docker compose up --build
```

Compose dựng PostgreSQL + Kafka + Kafka-UI + Keycloak legacy (optional) + Eureka + Config Server + API
Gateway + 7 service, đúng thứ tự khởi động qua healthcheck. Credential DB và security lấy từ `.env`
(không hardcode). Chi tiết: [docs/PLATFORM.md](docs/PLATFORM.md#run-the-whole-stack-with-docker-compose).

## Order reliability (fulfillment v1)

Canonical Kafka topics only (partition key = `orderId`):

| Topic | Role |
|---|---|
| `order.events.v1` | Order lifecycle + refund status + cancelled |
| `delivery.events.v1` | Driver assigned + delivery lifecycle |
| `payment.events.v1` | Payment succeeded/failed/refunded |

Reliability building blocks:

- **Transactional outbox** with per-aggregate monotonic sequence and head-of-line blocking relay (`acks=all`, idempotent producer).
- **Sequenced inbox** (dedupe / stale / gap-defer / drain) in order-service (`order-delivery-v1`, `order-payment-v1`) and delivery-service (`delivery-order-v1`).
- **Delivery reconciliation** for lost schedule responses (lookup before POST; never refund on timeout alone).
- **Idempotent refunds** + order stays `CANCELLATION_PENDING` until payment confirms refund.
- **Restaurant acceptance timeout** (default 10m window, 15s scan) races accept via optimistic locking exactly once.
- **OrderCancelled → pre-pickup delivery cancel** and driver release (post-pickup alerts only).

Ops cutover runbook: [docs/operations/fulfillment-v1-cutover.md](docs/operations/fulfillment-v1-cutover.md).

Key Micrometer series: `outbox_pending`, `outbox_oldest_unpublished_seconds`, `outbox_publish_retry_total`, `outbox_dead_letter_total`, `integration_event_deferred`, `integration_event_gap_total`, `order_delivery_reconciliation_total`, `order_refund_reconciliation_total`, `restaurant_acceptance_timeout_total`, `delivery_cancellation_after_pickup_total`.

## Ghi chú

- Root module hiện tại đã khai báo `infra`, `shared`, và `services` trong `pom.xml`.
- Nếu cần đổi convention folder, giữ nguyên 5 layer chính để không lệch structure chung.
