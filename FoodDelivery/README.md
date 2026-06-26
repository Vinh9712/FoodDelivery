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
│   └── common-web/
└── services/
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

### `customer-service`
Chịu trách nhiệm `User`, `Customer`, `Address`, `RefreshToken`, auth JWT, login/register/logout, profile, address management, phân quyền role.

### Các service khác
- `restaurant-service`: quản lý restaurant, menu, menu item.
- `order-service`: đặt món, trạng thái đơn.
- `payment-service`: thanh toán.
- `delivery-service`: giao hàng, driver assignment.
- `notification-service`: gửi thông báo.

## Customer/Auth Service scope

Service này chạy độc lập và có database riêng.

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

Compose dựng PostgreSQL + Kafka + Kafka-UI + Keycloak + Eureka + Config Server + API
Gateway + 6 service, đúng thứ tự khởi động qua healthcheck. Credential DB lấy từ `.env`
(không hardcode). Chi tiết: [docs/PLATFORM.md](docs/PLATFORM.md#run-the-whole-stack-with-docker-compose).

## Ghi chú

- Root module hiện tại đã khai báo `infra`, `shared`, và `services` trong `pom.xml`.
- Nếu cần đổi convention folder, giữ nguyên 5 layer chính để không lệch structure chung.
