# Postman Order Demo Design

## Goal

Provide one repeatable local-Docker demo covering Admin, Restaurant Owner, Customer, and Driver order flow.

## Artifacts

- `FoodDelivery/docs/FoodDelivery-Demo-Order-Flow.postman_collection.json`
- `FoodDelivery/scripts/seed-demo-flow.ps1`
- One read-only Delivery API lookup by order ID, plus focused test.

Existing Postman collection remains unchanged.

## Seed Script

PowerShell script runs SQL through `docker compose exec -T postgres psql`. It reads `.env`, verifies `fd-postgres`, databases, and required tables, then seeds fixed UUID records with idempotent `INSERT ... ON CONFLICT` statements.

Seeded data:

- Authentication: admin, owner, customer, driver accounts sharing a documented demo-only password hash.
- Restaurant: restaurant, category, menu item.
- Delivery: driver profile linked to driver auth user ID.

Script never drops tables or deletes user data. Missing infrastructure or schema stops execution with a clear error.

## Delivery Lookup API

Add:

```http
GET /api/v1/deliveries/order/{orderId}
```

Service resolves Delivery through existing `DeliveryRepository.findByOrderId`. Existing `DeliveryAuthorizationService.canRead` protects response: admin/service, owning customer, or assigned driver. Missing delivery returns current `DeliveryNotFoundException` mapping.

This endpoint avoids DB queries from Postman and makes asynchronous order-to-delivery flow observable.

## Collection Flow

Folders run in order:

1. `00 Health`: gateway and required service health.
2. `01 Admin Setup`: admin login and admin verification APIs.
3. `02 Owner Restaurant`: owner login, retrieve seeded restaurant/menu, capture IDs.
4. `03 Customer Order`: customer login, create order, save `orderId`, retry same idempotency key, poll order and delivery lookup.
5. `04 Driver Delivery`: driver login, online, accept, pickup, start, complete.
6. `05 Verification`: customer fetches final order/delivery; admin fetches notifications.

Separate collection variables store `adminToken`, `ownerToken`, `customerToken`, `driverToken`, IDs, base URLs, credentials, and polling counters. Tokens and passwords are never printed.

## Postman Assertions

- Expected HTTP status for each request.
- Login responses contain access tokens.
- Restaurant, menu item, order, delivery IDs are captured.
- Reusing one idempotency key returns the same order ID.
- Polling stops when Delivery exists or fails after a bounded retry count.
- Lifecycle response status matches `DRIVER_ASSIGNED`, `PICKED_UP`, `DELIVERING`, `DELIVERED`.
- Final customer/admin reads succeed.

## Constraints

- Local Docker demo only.
- PostgreSQL migrations must finish before seeding.
- No Flyway seed migration.
- No production credentials or secret handling.
- No broad delivery API/refactor beyond order lookup.
- Fixed IDs make reruns deterministic; idempotent SQL prevents duplicates.

## Verification

- PowerShell parser accepts script.
- Seed script reruns without duplicate rows against running Docker stack.
- Delivery lookup controller/service tests pass.
- Postman collection JSON validates and imports.
- Collection Runner completes all folders against seeded stack.
