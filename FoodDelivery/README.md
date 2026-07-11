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

## Event model

Theo structure hiện tại, service dùng event contract chung từ `shared/common-events` và publish qua Kafka.

Event chính:
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

## Ghi chú

- Root module hiện tại đã khai báo `infra`, `shared`, và `services` trong `pom.xml`.
- Nếu cần đổi convention folder, giữ nguyên 5 layer chính để không lệch structure chung.
