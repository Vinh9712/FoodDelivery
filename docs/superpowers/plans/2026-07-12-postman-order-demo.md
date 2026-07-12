# Postman Order Demo Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build one repeatable local-Docker Admin, Restaurant Owner, Customer, and Driver order demo with deterministic seed data, automatic Postman variable capture, and observable order-to-delivery progress.

**Architecture:** Keep existing APIs and service boundaries. Add one read-only Delivery lookup by `orderId`, seed only fixed demo rows through PostgreSQL, then orchestrate existing endpoints in a new Postman collection. Delivery authorization remains entity-based: admin/service, owning customer, assigned driver.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security method authorization, Spring Data JPA, JUnit 5, AssertJ, PostgreSQL 16, Docker Compose, PowerShell, Postman Collection v2.1.

## Global Constraints

- Local Docker demo only.
- Existing `FoodDelivery/docs/FoodDelivery.postman_collection.json` remains unchanged.
- No Flyway seed migration.
- Seed script never drops tables or deletes user data.
- Fixed UUIDs and `INSERT ... ON CONFLICT` make reruns deterministic.
- No production credentials or secret handling.
- No broad Delivery API or refactor beyond order lookup.
- Do not add dependencies.
- Do not commit or push.

## File Structure

- Modify `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/application/service/DeliveryLifecycleService.java`: read Delivery by order ID.
- Modify `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/security/DeliveryAuthorizationService.java`: authorize order-keyed reads using existing role/ownership rules.
- Modify `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/api/controller/DeliveryController.java`: expose `GET /api/v1/deliveries/order/{orderId}`.
- Modify `FoodDelivery/services/delivery-service/src/test/java/com/fooddelivery/delivery/application/service/DeliveryLifecycleServiceIntegrationTest.java`: prove repository-backed lookup and missing-delivery behavior.
- Create `FoodDelivery/services/delivery-service/src/test/java/com/fooddelivery/delivery/security/DeliveryAuthorizationServiceTest.java`: prove role and ownership authorization by order ID.
- Create `FoodDelivery/scripts/seed-demo-flow.ps1`: validate local Docker/PostgreSQL state and idempotently seed auth, restaurant/menu, and driver rows.
- Create `FoodDelivery/docs/FoodDelivery-Demo-Order-Flow.postman_collection.json`: execute full role flow and capture tokens/IDs.

---

### Task 1: Delivery Lookup by Order ID

**Files:**
- Modify: `FoodDelivery/services/delivery-service/src/test/java/com/fooddelivery/delivery/application/service/DeliveryLifecycleServiceIntegrationTest.java`
- Modify: `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/application/service/DeliveryLifecycleService.java`

**Interfaces:**
- Consumes: existing `DeliveryRepository.findByOrderId(UUID orderId)`.
- Produces: `DeliveryLifecycleService.getDeliveryByOrderId(UUID orderId): Delivery`.

- [ ] **Step 1: Add failing service tests**

Add imports:

```java
import com.fooddelivery.delivery.domain.exception.DeliveryNotFoundException;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
```

Add tests:

```java
@Test
void getsDeliveryByOrderId() {
    UUID orderId = UUID.randomUUID();
    Delivery saved = deliveryRepository.save(new Delivery(
            orderId, UUID.randomUUID(), null, new Address("123 Nguyen Trai", null, null), null));

    Delivery found = lifecycleService.getDeliveryByOrderId(orderId);

    assertThat(found.getId()).isEqualTo(saved.getId());
    assertThat(found.getOrderId()).isEqualTo(orderId);
}

@Test
void missingOrderDeliveryThrowsNotFound() {
    UUID orderId = UUID.randomUUID();

    assertThatThrownBy(() -> lifecycleService.getDeliveryByOrderId(orderId))
            .isInstanceOf(DeliveryNotFoundException.class);
}
```

- [ ] **Step 2: Run tests and confirm RED**

Run from `FoodDelivery`:

```bash
mvn -pl services/delivery-service -am -Dtest=DeliveryLifecycleServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because `getDeliveryByOrderId(UUID)` does not exist.

- [ ] **Step 3: Add minimum service method**

Insert beside `getDelivery(UUID)`:

```java
@Transactional(readOnly = true)
public Delivery getDeliveryByOrderId(UUID orderId) {
    return deliveryRepository.findByOrderId(orderId)
            .orElseThrow(() -> new DeliveryNotFoundException(orderId));
}
```

Use existing exception mapping. For this demo, exception UUID represents requested order ID when no Delivery exists; no new exception type.

- [ ] **Step 4: Run tests and confirm GREEN**

Run same Maven command. Expected: `BUILD SUCCESS`.

---

### Task 2: Order-Keyed Delivery Authorization

**Files:**
- Create: `FoodDelivery/services/delivery-service/src/test/java/com/fooddelivery/delivery/security/DeliveryAuthorizationServiceTest.java`
- Modify: `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/security/DeliveryAuthorizationService.java`

**Interfaces:**
- Consumes: `DeliveryRepository.findByOrderId(UUID)`, `DriverRepository.findByUserId(UUID)`.
- Produces: `DeliveryAuthorizationService.canReadOrder(UUID orderId, Authentication authentication): boolean`.

- [ ] **Step 1: Create failing authorization test**

Create test using Mockito already supplied by `spring-boot-starter-test`:

```java
package com.fooddelivery.delivery.security;

import com.fooddelivery.delivery.domain.model.Delivery;
import com.fooddelivery.delivery.domain.model.Driver;
import com.fooddelivery.delivery.domain.model.valueobject.Address;
import com.fooddelivery.delivery.infrastructure.repository.DeliveryRepository;
import com.fooddelivery.delivery.infrastructure.repository.DriverRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeliveryAuthorizationServiceTest {

    private DeliveryRepository deliveries;
    private DriverRepository drivers;
    private DeliveryAuthorizationService authorization;

    @BeforeEach
    void setUp() {
        deliveries = mock(DeliveryRepository.class);
        drivers = mock(DriverRepository.class);
        authorization = new DeliveryAuthorizationService(deliveries, drivers);
    }

    @Test
    void owningCustomerCanReadByOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Delivery delivery = new Delivery(orderId, customerId, null, new Address("Address", null, null), null);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));

        assertThat(authorization.canReadOrder(orderId, auth(customerId, "CUSTOMER"))).isTrue();
        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    @Test
    void assignedDriverCanReadByOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID driverUserId = UUID.randomUUID();
        Driver driver = mock(Driver.class);
        Delivery delivery = new Delivery(orderId);
        UUID driverId = UUID.randomUUID();
        delivery.assignDriver(driverId);
        when(deliveries.findByOrderId(orderId)).thenReturn(Optional.of(delivery));
        when(driver.getId()).thenReturn(driverId);
        when(drivers.findByUserId(driverUserId)).thenReturn(Optional.of(driver));

        assertThat(authorization.canReadOrder(orderId, auth(driverUserId, "DRIVER"))).isTrue();
    }

    @Test
    void adminCanReadBeforeLookupAndMissingDeliveryIsDeniedToCustomer() {
        UUID orderId = UUID.randomUUID();

        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "ADMIN"))).isTrue();
        assertThat(authorization.canReadOrder(orderId, auth(UUID.randomUUID(), "CUSTOMER"))).isFalse();
    }

    private Authentication auth(UUID subject, String role) {
        return new UsernamePasswordAuthenticationToken(
                subject.toString(), "", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
```

- [ ] **Step 2: Run test and confirm RED**

```bash
mvn -pl services/delivery-service -am -Dtest=DeliveryAuthorizationServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: compilation failure because `canReadOrder` does not exist.

- [ ] **Step 3: Reuse one authorization helper**

Refactor `canRead` and add `canReadOrder`:

```java
public boolean canRead(UUID deliveryId, Authentication authentication) {
    return canRead(deliveryRepository.findById(deliveryId).orElse(null), authentication);
}

public boolean canReadOrder(UUID orderId, Authentication authentication) {
    return canRead(deliveryRepository.findByOrderId(orderId).orElse(null), authentication);
}

private boolean canRead(Delivery delivery, Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return false;
    }
    if (hasRole(authentication, "ADMIN") || hasRole(authentication, "SERVICE")) {
        return true;
    }
    if (delivery == null) {
        return false;
    }
    UUID subject = subject(authentication);
    if (subject == null) {
        return false;
    }
    if (hasRole(authentication, "CUSTOMER")) {
        return subject.equals(delivery.getCustomerId());
    }
    if (hasRole(authentication, "DRIVER")) {
        return driverRepository.findByUserId(subject)
                .map(Driver::getId)
                .map(id -> id.equals(delivery.getDriverId()))
                .orElse(false);
    }
    return false;
}
```

This preserves current admin/service behavior and removes duplicated role logic.

- [ ] **Step 4: Run authorization and existing Delivery tests**

```bash
mvn -pl services/delivery-service -am -Dtest=DeliveryAuthorizationServiceTest,DeliveryLifecycleServiceIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Expected: `BUILD SUCCESS`.

---

### Task 3: Expose Delivery Lookup Endpoint

**Files:**
- Modify: `FoodDelivery/services/delivery-service/src/main/java/com/fooddelivery/delivery/api/controller/DeliveryController.java`

**Interfaces:**
- Consumes: `DeliveryLifecycleService.getDeliveryByOrderId(UUID)` and `DeliveryAuthorizationService.canReadOrder(UUID, Authentication)` through method security.
- Produces: `GET /api/v1/deliveries/order/{orderId}` returning existing `DeliveryDetailResponse`.

- [ ] **Step 1: Add endpoint**

Insert before generic `@GetMapping("/{id}")`:

```java
@GetMapping("/order/{orderId}")
@PreAuthorize("@deliveryAuthorization.canReadOrder(#orderId, authentication)")
public ResponseEntity<DeliveryDetailResponse> getByOrderId(@PathVariable UUID orderId) {
    return ResponseEntity.ok(DeliveryDetailResponse.from(
            deliveryLifecycleService.getDeliveryByOrderId(orderId)));
}
```

No new DTO. Existing exception handler supplies missing-delivery response.

- [ ] **Step 2: Compile and run Delivery suite**

```bash
mvn -pl services/delivery-service -am test
```

Expected: `BUILD SUCCESS`. If Docker unavailable, `DeliveryFlywayMigrationTest` may report skipped; record that separately, not as PostgreSQL verification.

---

### Task 4: Deterministic Local Seed Script

**Files:**
- Create: `FoodDelivery/scripts/seed-demo-flow.ps1`
- Read while implementing: Authentication, Restaurant, Delivery Flyway migrations and `FoodDelivery/docker-compose.yml`.

**Interfaces:**
- Consumes: `.env`, Docker Compose service `postgres`, databases `auth_db`, `restaurant_db`, `food_delivery_db`.
- Produces fixed auth users, restaurant/category/menu item, and linked driver profile.

- [ ] **Step 1: Lock deterministic values**

Use these values in script and collection:

```text
adminUserId      = 10000000-0000-0000-0000-000000000001
ownerUserId      = 10000000-0000-0000-0000-000000000002
customerUserId   = 10000000-0000-0000-0000-000000000003
driverUserId     = 10000000-0000-0000-0000-000000000004
restaurantId     = 20000000-0000-0000-0000-000000000001
categoryId       = 20000000-0000-0000-0000-000000000002
menuItemId       = 20000000-0000-0000-0000-000000000003
driverId         = 30000000-0000-0000-0000-000000000001
password          = Demo@12345
emails            = admin.demo@food.local, owner.demo@food.local, customer.demo@food.local, driver.demo@food.local
```

Before writing SQL, read exact final table definitions. Use actual column names and enum strings from current migrations/entities. Generate one BCrypt hash once, embed only that demo hash, document local-only intent.

- [ ] **Step 2: Create infrastructure guards**

Script must:

```powershell
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root '.env'
if (-not (Test-Path $EnvFile)) { throw "Missing $EnvFile" }

Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim().Trim('"'), 'Process')
    }
}

$PostgresUser = if ($env:DB_USER) { $env:DB_USER } else { 'postgres' }
docker compose -f (Join-Path $Root 'docker-compose.yml') ps --status running postgres | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL container is not running' }
```

Add helper that executes SQL without shell interpolation:

```powershell
function Invoke-PostgresSql([string]$Database, [string]$Sql) {
    $Sql | docker compose -f (Join-Path $Root 'docker-compose.yml') exec -T postgres `
        psql -v ON_ERROR_STOP=1 -U $PostgresUser -d $Database
    if ($LASTEXITCODE -ne 0) { throw "SQL failed for $Database" }
}
```

Check each database and required table with `SELECT to_regclass('public.<table>')`; throw when result is blank.

- [ ] **Step 3: Add idempotent SQL**

For each service DB, use one transaction:

```sql
BEGIN;
INSERT INTO ... VALUES (...)
ON CONFLICT (id) DO UPDATE SET
    <demo-owned mutable columns> = EXCLUDED.<column>;
COMMIT;
```

Rules:

- Authentication: insert four users with roles `ADMIN`, `RESTAURANT_OWNER`, `CUSTOMER`, `DRIVER`, shared BCrypt demo hash, active and not deleted. Use exact auth schema columns.
- Restaurant: insert restaurant owned by `ownerUserId`, active/accepting orders; category; available menu item with positive price. Use exact V6 schema columns and preserve required legacy columns.
- Delivery: insert driver linked through `user_id = driverUserId`, `ACTIVE`, online, available, fixed vehicle/phone/license values. Use exact latest schema columns.
- Never use `TRUNCATE`, `DELETE`, `DROP`, or broad `UPDATE`.
- Conflict updates target only rows with fixed demo IDs.

- [ ] **Step 4: Add completion output without secrets**

Print seeded IDs/emails and next command. Never print password hash or tokens.

- [ ] **Step 5: Validate PowerShell syntax**

From repository root on Windows:

```powershell
[void][scriptblock]::Create((Get-Content -Raw .\FoodDelivery\scripts\seed-demo-flow.ps1))
```

Expected: no output, exit success.

- [ ] **Step 6: Verify idempotency against running stack**

Run twice:

```powershell
.\FoodDelivery\scripts\seed-demo-flow.ps1
.\FoodDelivery\scripts\seed-demo-flow.ps1
```

Expected: both succeed; SQL count queries return exactly four fixed auth users, one restaurant, one category, one item, one driver.

---

### Task 5: New Postman Collection

**Files:**
- Create: `FoodDelivery/docs/FoodDelivery-Demo-Order-Flow.postman_collection.json`

**Interfaces:**
- Consumes: gateway `http://localhost:8080`, deterministic seed IDs/accounts, existing Auth/Restaurant/Order/Driver/Delivery APIs.
- Produces: importable Postman Collection v2.1 with folder-ordered end-to-end demo.

- [ ] **Step 1: Define collection variables**

Create collection v2.1. Add variables:

```text
baseUrl=http://localhost:8080
adminEmail=admin.demo@food.local
ownerEmail=owner.demo@food.local
customerEmail=customer.demo@food.local
driverEmail=driver.demo@food.local
demoPassword=Demo@12345
restaurantId=20000000-0000-0000-0000-000000000001
categoryId=20000000-0000-0000-0000-000000000002
menuItemId=20000000-0000-0000-0000-000000000003
adminToken=
ownerToken=
customerToken=
driverToken=
orderId=
deliveryId=
orderIdempotencyKey=demo-order-{{$guid}}
deliveryPollCount=0
maxDeliveryPolls=20
```

Never log password/token values in scripts.

- [ ] **Step 2: Add `00 Health`**

Add gateway and required actuator health requests. Tests accept only HTTP 200 and assert JSON health status when present.

```javascript
pm.test("service responds", () => pm.response.to.have.status(200));
```

- [ ] **Step 3: Add `01 Admin Setup`**

Login body:

```json
{"email":"{{adminEmail}}","password":"{{demoPassword}}"}
```

Capture token:

```javascript
pm.test("admin login succeeds", () => pm.response.to.have.status(200));
const body = pm.response.json();
pm.expect(body.data.accessToken).to.be.a("string").and.not.empty;
pm.collectionVariables.set("adminToken", body.data.accessToken);
```

Call existing admin user verification/list endpoint using `Bearer {{adminToken}}`; assert 200. Do not create users because seed owns deterministic accounts.

- [ ] **Step 4: Add `02 Owner Restaurant`**

Login owner, capture `ownerToken` with same pattern. Call actual plural routes:

```http
GET {{baseUrl}}/api/v1/restaurants/{{restaurantId}}
GET {{baseUrl}}/api/v1/restaurants/{{restaurantId}}/menu
```

Assert 200, restaurant ID equality, and seeded menu item presence. Capture returned IDs back into collection variables.

- [ ] **Step 5: Add `03 Customer Order`**

Login customer and capture `customerToken`. Create order:

```http
POST {{baseUrl}}/api/v1/orders
Authorization: Bearer {{customerToken}}
Idempotency-Key: {{orderIdempotencyKey}}
Content-Type: application/json
```

```json
{
  "restaurantId": "{{restaurantId}}",
  "deliveryAddress": "123 Nguyen Trai, District 5, Ho Chi Minh City",
  "items": [{"menuItemId": "{{menuItemId}}", "quantity": 1}]
}
```

Assert 201 and capture `body.data.id` or actual unwrapped `id` after checking current response envelope. Repeat identical request with same key; assert accepted success status and same order ID. Then GET `/api/v1/orders/{{orderId}}` and assert 200.

- [ ] **Step 6: Add bounded Delivery polling**

Request:

```http
GET {{baseUrl}}/api/v1/deliveries/order/{{orderId}}
Authorization: Bearer {{customerToken}}
```

Test script:

```javascript
let count = Number(pm.collectionVariables.get("deliveryPollCount") || 0);
const max = Number(pm.collectionVariables.get("maxDeliveryPolls"));
if (pm.response.code === 200) {
    const raw = pm.response.json();
    const body = raw.data || raw;
    pm.expect(body.id).to.be.a("string").and.not.empty;
    pm.collectionVariables.set("deliveryId", body.id);
    pm.collectionVariables.set("deliveryPollCount", "0");
} else {
    pm.expect(pm.response.code).to.eql(404);
    count += 1;
    pm.collectionVariables.set("deliveryPollCount", String(count));
    if (count >= max) {
        throw new Error(`Delivery not created after ${max} polls`);
    }
    setTimeout(() => {}, 1000);
    pm.execution.setNextRequest(pm.info.requestName);
}
```

Confirm current Postman runtime supports `pm.execution.setNextRequest`; use `postman.setNextRequest` only if project runner is older.

- [ ] **Step 7: Add `04 Driver Delivery`**

Login driver, capture `driverToken`; call:

```http
POST {{baseUrl}}/api/v1/drivers/me/online
POST {{baseUrl}}/api/v1/deliveries/{{deliveryId}}/accept
POST {{baseUrl}}/api/v1/deliveries/{{deliveryId}}/picked-up
POST {{baseUrl}}/api/v1/deliveries/{{deliveryId}}/start-delivery
POST {{baseUrl}}/api/v1/deliveries/{{deliveryId}}/complete
```

Use `Bearer {{driverToken}}`. Assert status sequence from response body, accepting existing response envelope:

```javascript
const raw = pm.response.json();
const body = raw.data || raw;
pm.expect(body.status).to.eql("DRIVER_ASSIGNED");
```

Repeat with `PICKED_UP`, `DELIVERING`, `DELIVERED` for later requests. Online request asserts `online === true`.

- [ ] **Step 8: Add `05 Verification`**

Customer reads final order and Delivery; assert 200 and final Delivery `DELIVERED`. Admin performs existing notification/admin read only if route exists and is gateway-accessible; otherwise omit it rather than inventing an API. This intentional simplification supersedes spec wording if no current notification read endpoint exists.

- [ ] **Step 9: Validate JSON**

```powershell
Get-Content -Raw .\FoodDelivery\docs\FoodDelivery-Demo-Order-Flow.postman_collection.json | ConvertFrom-Json | Out-Null
```

Expected: no output, exit success.

---

### Task 6: End-to-End Verification

**Files:**
- No source changes unless verification exposes a concrete bug; any bug fix starts a new RED/GREEN cycle.

**Interfaces:**
- Consumes all prior deliverables.
- Produces recorded evidence that local demo works.

- [ ] **Step 1: Run affected Maven reactor**

```bash
mvn -pl services/delivery-service -am test
```

Expected: `BUILD SUCCESS`. Record skipped Docker tests explicitly.

- [ ] **Step 2: Validate artifacts statically**

```powershell
[void][scriptblock]::Create((Get-Content -Raw .\FoodDelivery\scripts\seed-demo-flow.ps1))
Get-Content -Raw .\FoodDelivery\docs\FoodDelivery-Demo-Order-Flow.postman_collection.json | ConvertFrom-Json | Out-Null
```

Expected: no parser errors.

- [ ] **Step 3: Start current Docker stack and wait for health**

Use repository's existing documented Docker Compose startup command. Do not rebuild unrelated images unless required by changed Delivery code. Confirm gateway, authentication, restaurant, order, delivery, Kafka, and PostgreSQL healthy before seeding.

- [ ] **Step 4: Run seed twice**

```powershell
.\FoodDelivery\scripts\seed-demo-flow.ps1
.\FoodDelivery\scripts\seed-demo-flow.ps1
```

Expected: both succeed; no duplicate fixed rows.

- [ ] **Step 5: Run collection**

Import collection into Postman Collection Runner, or use already-installed Newman without adding it as project dependency:

```bash
newman run FoodDelivery/docs/FoodDelivery-Demo-Order-Flow.postman_collection.json
```

Expected: all assertions pass; one order ID, one Delivery ID; final Delivery status `DELIVERED`.

- [ ] **Step 6: Report exact result**

Report Maven result, Docker/Testcontainers status, seed first/second-run result, Postman assertion totals, skipped steps, and shortest decisive error for any failure. Do not claim end-to-end success when Docker or Collection Runner was unavailable.
