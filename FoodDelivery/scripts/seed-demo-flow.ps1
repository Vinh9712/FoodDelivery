$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$ComposeFile = Join-Path $Root 'docker-compose.yml'
$EnvFile = Join-Path $Root '.env'
if (-not (Test-Path $EnvFile)) { throw "Missing $EnvFile" }

Get-Content $EnvFile | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim().Trim('"'), 'Process')
    }
}

$PostgresUser = if ($env:DB_USER) { $env:DB_USER } else { 'postgres' }
$postgres = docker compose -f $ComposeFile ps --status running postgres
if ($LASTEXITCODE -ne 0 -or -not ($postgres -match 'postgres')) { throw 'PostgreSQL container is not running' }

function Invoke-PostgresSql([string]$Database, [string]$Sql) {
    $Sql | docker compose -f $ComposeFile exec -T postgres psql -v ON_ERROR_STOP=1 -U $PostgresUser -d $Database
    if ($LASTEXITCODE -ne 0) { throw "SQL failed for $Database" }
}

function Assert-PostgresTable([string]$Database, [string]$Table) {
    $result = Invoke-PostgresSql $Database "SELECT to_regclass('public.$Table');"
    if (-not (($result | Out-String).Trim() -match [regex]::Escape($Table))) {
        throw "Missing required table public.$Table in $Database"
    }
}

foreach ($entry in @(
    @('auth_db', 'users'),
    @('restaurant_db', 'restaurants'),
    @('restaurant_db', 'menu_categories'),
    @('restaurant_db', 'menu_items'),
    @('food_delivery_db', 'drivers')
)) { Assert-PostgresTable $entry[0] $entry[1] }

# Local Docker demo only. BCrypt hash below matches Demo@12345; never print it.
$AdminUserId = '10000000-0000-0000-0000-000000000001'
$OwnerUserId = '10000000-0000-0000-0000-000000000002'
$CustomerUserId = '10000000-0000-0000-0000-000000000003'
$DriverUserId = '10000000-0000-0000-0000-000000000004'
$RestaurantId = '20000000-0000-0000-0000-000000000001'
$CategoryId = '20000000-0000-0000-0000-000000000002'
$MenuItemId = '20000000-0000-0000-0000-000000000003'
$DriverId = '30000000-0000-0000-0000-000000000001'
$PasswordHash = '$2a$10$pDXI9rkLQLs2cwuE/G7Uzea0YiAuY.8hdXKTATjC.rDvguhSVaIJ6'

Invoke-PostgresSql 'auth_db' @"
BEGIN;
INSERT INTO users (id, email, phone, password_hash, role, is_active, email_verified, is_deleted, created_at, updated_at)
VALUES
  ('$AdminUserId', 'admin.demo@food.local', '0900000001', '$PasswordHash', 'ADMIN', TRUE, TRUE, FALSE, NOW(), NOW()),
  ('$OwnerUserId', 'owner.demo@food.local', '0900000002', '$PasswordHash', 'RESTAURANT_OWNER', TRUE, TRUE, FALSE, NOW(), NOW()),
  ('$CustomerUserId', 'customer.demo@food.local', '0900000003', '$PasswordHash', 'CUSTOMER', TRUE, TRUE, FALSE, NOW(), NOW()),
  ('$DriverUserId', 'driver.demo@food.local', '0900000004', '$PasswordHash', 'DRIVER', TRUE, TRUE, FALSE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  email = EXCLUDED.email, phone = EXCLUDED.phone, password_hash = EXCLUDED.password_hash,
  role = EXCLUDED.role, is_active = EXCLUDED.is_active, email_verified = EXCLUDED.email_verified,
  is_deleted = EXCLUDED.is_deleted, deleted_at = NULL, updated_at = NOW();
COMMIT;
"@

Invoke-PostgresSql 'restaurant_db' @"
BEGIN;
INSERT INTO restaurants (id, owner_id, name, description, phone, address_line, district, city, status, open_time, close_time, avg_rating, total_reviews, min_order_amount, estimated_delivery_time_min, is_accepting_orders, created_at, updated_at)
VALUES ('$RestaurantId', '$OwnerUserId', 'Demo Pho Kitchen', 'Local demo restaurant', '0900000010', '123 Nguyen Trai', 'District 5', 'Ho Chi Minh City', 'ACTIVE', '08:00', '22:00', 0.00, 0, 0.00, 30, TRUE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  owner_id = EXCLUDED.owner_id, name = EXCLUDED.name, description = EXCLUDED.description,
  phone = EXCLUDED.phone, address_line = EXCLUDED.address_line, district = EXCLUDED.district,
  city = EXCLUDED.city, status = EXCLUDED.status, open_time = EXCLUDED.open_time,
  close_time = EXCLUDED.close_time, avg_rating = EXCLUDED.avg_rating, total_reviews = EXCLUDED.total_reviews,
  min_order_amount = EXCLUDED.min_order_amount, estimated_delivery_time_min = EXCLUDED.estimated_delivery_time_min,
  is_accepting_orders = EXCLUDED.is_accepting_orders, updated_at = NOW();
INSERT INTO menu_categories (id, restaurant_id, name, description, sort_order, is_available, display_order, is_active, created_at, updated_at)
VALUES ('$CategoryId', '$RestaurantId', 'Pho', 'Demo menu category', 1, TRUE, 1, TRUE, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  restaurant_id = EXCLUDED.restaurant_id, name = EXCLUDED.name, description = EXCLUDED.description,
  sort_order = EXCLUDED.sort_order, is_available = EXCLUDED.is_available,
  display_order = EXCLUDED.display_order, is_active = EXCLUDED.is_active, updated_at = NOW();
INSERT INTO menu_items (id, restaurant_id, category_id, name, description, price, is_available, is_vegetarian, is_spicy, prep_time_minutes, preparation_time_min, display_order, created_at, updated_at)
VALUES ('$MenuItemId', '$RestaurantId', '$CategoryId', 'Demo Beef Pho', 'Demo order item', 65000.00, TRUE, FALSE, FALSE, 15, 15, 1, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
  restaurant_id = EXCLUDED.restaurant_id, category_id = EXCLUDED.category_id, name = EXCLUDED.name,
  description = EXCLUDED.description, price = EXCLUDED.price, is_available = EXCLUDED.is_available,
  is_vegetarian = EXCLUDED.is_vegetarian, is_spicy = EXCLUDED.is_spicy,
  prep_time_minutes = EXCLUDED.prep_time_minutes, preparation_time_min = EXCLUDED.preparation_time_min,
  display_order = EXCLUDED.display_order, updated_at = NOW();
COMMIT;
"@

Invoke-PostgresSql 'food_delivery_db' @"
BEGIN;
INSERT INTO drivers (id, user_id, full_name, phone, vehicle_type, license_plate, avg_rating, available, status, is_online, total_reviews)
VALUES ('$DriverId', '$DriverUserId', 'Demo Driver', '0900000004', 'MOTORBIKE', 'DEMO-001', 5.00, TRUE, 'ACTIVE', TRUE, 0)
ON CONFLICT (id) DO UPDATE SET
  user_id = EXCLUDED.user_id, full_name = EXCLUDED.full_name, phone = EXCLUDED.phone,
  vehicle_type = EXCLUDED.vehicle_type, license_plate = EXCLUDED.license_plate, avg_rating = EXCLUDED.avg_rating,
  available = EXCLUDED.available, status = EXCLUDED.status, is_online = EXCLUDED.is_online,
  total_reviews = EXCLUDED.total_reviews;
COMMIT;
"@

Write-Host 'Demo data seeded.'
Write-Host "Users: admin.demo@food.local, owner.demo@food.local, customer.demo@food.local, driver.demo@food.local"
Write-Host "Restaurant: $RestaurantId  Category: $CategoryId  Menu item: $MenuItemId  Driver: $DriverId"
Write-Host 'Next: import and run FoodDelivery-Demo-Order-Flow.postman_collection.json.'
