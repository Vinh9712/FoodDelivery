-- Clean old data to avoid duplicates
DELETE FROM menu_items;
DELETE FROM menu_categories;
DELETE FROM restaurant_reviews;
DELETE FROM restaurants;

-- Insert Restaurants
INSERT INTO restaurants (
    id, owner_id, name, description, phone, address_line, district, city, 
    status, open_time, close_time, avg_rating, total_reviews, min_order_amount, 
    estimated_delivery_time_min, logo_url, banner_url, is_accepting_orders
) VALUES 
(
    '019f7567-133e-7bfa-bd16-e788321cec11', 
    '019f7567-133e-7bfa-bd16-e788321cec22',
    'Phở Việt Nam - Lý Quốc Sư',
    'Thương hiệu phở gia truyền nổi tiếng Hà Nội và Sài Gòn với nước dùng ngọt thanh xương hầm 24h và bò ta tươi ngon.',
    '0901234567',
    '120 Lý Tự Trọng',
    'Quận 1',
    'Thành phố Hồ Chí Minh',
    'ACTIVE',
    '06:00:00',
    '22:00:00',
    4.8,
    350,
    30000.00,
    20,
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=500&auto=format&fit=crop&q=60',
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=1200&auto=format&fit=crop&q=80',
    TRUE
),
(
    '019f7567-133e-7bfa-bd16-e788321cec12', 
    '019f7567-133e-7bfa-bd16-e788321cec22',
    'Cơm Tấm Sài Gòn - Nguyễn Tri Phương',
    'Cơm tấm sườn bì chả đặc sản Sài Gòn. Sườn nướng mật ong thơm phức cực phẩm.',
    '0902345678',
    '258 Nguyễn Tri Phương',
    'Quận 10',
    'Thành phố Hồ Chí Minh',
    'ACTIVE',
    '07:00:00',
    '21:30:00',
    4.7,
    512,
    25000.00,
    25,
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500&auto=format&fit=crop&q=60',
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=1200&auto=format&fit=crop&q=80',
    TRUE
),
(
    '019f7567-133e-7bfa-bd16-e788321cec13', 
    '019f7567-133e-7bfa-bd16-e788321cec22',
    'Trà Sữa Gong Cha - Hồ Tùng Mậu',
    'Gong Cha nổi tiếng với trà sữa Đài Loan hảo hạng, lớp kem sữa milk foam béo ngậy đặc trưng.',
    '0903456789',
    '83 Hồ Tùng Mậu',
    'Quận 1',
    'Thành phố Hồ Chí Minh',
    'ACTIVE',
    '09:00:00',
    '22:30:00',
    4.6,
    1240,
    20000.00,
    15,
    'https://images.unsplash.com/photo-1541658016709-82535e94bc69?w=500&auto=format&fit=crop&q=60',
    'https://images.unsplash.com/photo-1541658016709-82535e94bc69?w=1200&auto=format&fit=crop&q=80',
    TRUE
),
(
    '019f7567-133e-7bfa-bd16-e788321cec14', 
    '019f7567-133e-7bfa-bd16-e788321cec22',
    'Bánh Mì Huỳnh Hoa - Lê Thị Riêng',
    'Ổ bánh mì đắt nhất nhưng đáng tiền nhất Sài Gòn, đầy ắp pa-tê siêu thơm, bơ vàng, và các loại thịt nguội chả lụa.',
    '0904567890',
    '26 Lê Thị Riêng',
    'Quận 1',
    'Thành phố Hồ Chí Minh',
    'ACTIVE',
    '14:00:00',
    '23:00:00',
    4.9,
    2890,
    50000.00,
    30,
    'https://images.unsplash.com/photo-1600454641350-f9824e868367?w=500&auto=format&fit=crop&q=60',
    'https://images.unsplash.com/photo-1600454641350-f9824e868367?w=1200&auto=format&fit=crop&q=80',
    TRUE
),
(
    '019f7567-133e-7bfa-bd16-e788321cec15', 
    '019f7567-133e-7bfa-bd16-e788321cec22',
    'Burger & Pizza Corner',
    'Đồ ăn nhanh chuẩn vị Mỹ. Pizza đế giòn phô mai ngập tràn, Burger bò Wagyu siêu mọng nước.',
    '0905678901',
    '45 Nguyễn Huệ',
    'Quận 1',
    'Thành phố Hồ Chí Minh',
    'ACTIVE',
    '10:00:00',
    '22:00:00',
    4.5,
    189,
    45000.00,
    20,
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=500&auto=format&fit=crop&q=60',
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=1200&auto=format&fit=crop&q=80',
    TRUE
);

-- Insert Menu Categories (supplying both sort_order and display_order, is_available and is_active)
INSERT INTO menu_categories (id, restaurant_id, name, description, sort_order, display_order, is_available, is_active) VALUES
-- Phở Việt Nam (11)
('019f7567-133e-7bfa-bd16-e788321cec31', '019f7567-133e-7bfa-bd16-e788321cec11', 'Phở Bò Gia Truyền', 'Phở bò truyền thống bò tái, chín, nạm, gầu, gân...', 1, 1, TRUE, TRUE),
('019f7567-133e-7bfa-bd16-e788321cec32', '019f7567-133e-7bfa-bd16-e788321cec11', 'Phở Gà Ta', 'Phở gà ta thơm ngọt, thịt dai giòn sần sật.', 2, 2, TRUE, TRUE),
('019f7567-133e-7bfa-bd16-e788321cec33', '019f7567-133e-7bfa-bd16-e788321cec11', 'Nước Giải Khát', 'Trà đá, nước ngọt lạnh buốt.', 3, 3, TRUE, TRUE),

-- Cơm Tấm Sài Gòn (12)
('019f7567-133e-7bfa-bd16-e788321cec34', '019f7567-133e-7bfa-bd16-e788321cec12', 'Món Cơm Tấm', 'Các đĩa cơm tấm đầy đặn ăn kèm sườn, bì, chả, trứng ốp la.', 1, 1, TRUE, TRUE),
('019f7567-133e-7bfa-bd16-e788321cec35', '019f7567-133e-7bfa-bd16-e788321cec12', 'Canh & Món Kèm', 'Canh khổ qua rừng, chả chưng thêm...', 2, 2, TRUE, TRUE),

-- Trà Sữa Gong Cha (13)
('019f7567-133e-7bfa-bd16-e788321cec36', '019f7567-133e-7bfa-bd16-e788321cec13', 'Trà Sữa Kem Sữa (Milk Foam)', 'Dòng trà kết hợp lớp kem mặn béo ngậy trứ danh.', 1, 1, TRUE, TRUE),
('019f7567-133e-7bfa-bd16-e788321cec37', '019f7567-133e-7bfa-bd16-e788321cec13', 'Trà Trái Cây Thanh Nhiệt', 'Trà thanh lọc mát lạnh kết hợp trái cây tươi.', 2, 2, TRUE, TRUE),

-- Bánh Mì Huỳnh Hoa (14)
('019f7567-133e-7bfa-bd16-e788321cec38', '019f7567-133e-7bfa-bd16-e788321cec14', 'Bánh Mì Siêu Cấp', 'Các loại bánh mì giòn rụm nhân ngập tràn.', 1, 1, TRUE, TRUE),

-- Burger & Pizza (15)
('019f7567-133e-7bfa-bd16-e788321cec39', '019f7567-133e-7bfa-bd16-e788321cec15', 'Pizza Đặc Sắc', 'Pizza nướng lò củi hảo hạng.', 1, 1, TRUE, TRUE),
('019f7567-133e-7bfa-bd16-e788321cec40', '019f7567-133e-7bfa-bd16-e788321cec15', 'Burger Wagyu Mỹ', 'Burger bò nướng lò.', 2, 2, TRUE, TRUE);

-- Insert Menu Items (supplying both prep_time_minutes and preparation_time_min, is_available and display_order)
INSERT INTO menu_items (
    id, category_id, restaurant_id, name, description, price, discount_price, 
    is_available, image_url, prep_time_minutes, preparation_time_min, is_vegetarian, is_spicy, display_order
) VALUES
-- Phở Bò Gia Truyền (31)
(
    '019f7567-133e-7bfa-bd16-e788321cec51', '019f7567-133e-7bfa-bd16-e788321cec31', '019f7567-133e-7bfa-bd16-e788321cec11',
    'Phở Tái Lăn', 'Thịt bò xào lăn tỏi tái sơ trên lửa lớn, nước dùng béo ngậy thơm nức.',
    65000.00, 60000.00, TRUE, 
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=300&auto=format&fit=crop',
    10, 10, FALSE, FALSE, 1
),
(
    '019f7567-133e-7bfa-bd16-e788321cec52', '019f7567-133e-7bfa-bd16-e788321cec31', '019f7567-133e-7bfa-bd16-e788321cec11',
    'Phở Nạm Gầu Bò', 'Thịt nạm giòn ngọt kết hợp gầu bò giòn sần sật béo ngậy.',
    70000.00, NULL, TRUE,
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=300&auto=format&fit=crop',
    10, 10, FALSE, FALSE, 2
),
(
    '019f7567-133e-7bfa-bd16-e788321cec53', '019f7567-133e-7bfa-bd16-e788321cec31', '019f7567-133e-7bfa-bd16-e788321cec11',
    'Phở Đặc Biệt (Đủ Thứ)', 'Bao gồm tái, nạm, gầu, gân, bò viên, trứng trần ăn siêu no.',
    85000.00, 80000.00, TRUE,
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=300&auto=format&fit=crop',
    12, 12, FALSE, FALSE, 3
),

-- Phở Gà Ta (32)
(
    '019f7567-133e-7bfa-bd16-e788321cec54', '019f7567-133e-7bfa-bd16-e788321cec32', '019f7567-133e-7bfa-bd16-e788321cec11',
    'Phở Gà Đùi Xé', 'Phở gà ta sử dụng 100% thịt đùi giòn dai kèm lá chanh thơm nồng.',
    60000.00, NULL, TRUE,
    'https://images.unsplash.com/photo-1582878826629-29b7ad1cdc43?w=300&auto=format&fit=crop',
    10, 10, FALSE, FALSE, 1
),

-- Nước Giải Khát (33)
(
    '019f7567-133e-7bfa-bd16-e788321cec55', '019f7567-133e-7bfa-bd16-e788321cec33', '019f7567-133e-7bfa-bd16-e788321cec11',
    'Trà Đá Lá Dứa', 'Ly trà đá mát lạnh thơm hương lá dứa giải nhiệt tốt.',
    5000.00, NULL, TRUE,
    'https://images.unsplash.com/photo-1541658016709-82535e94bc69?w=300&auto=format&fit=crop',
    2, 2, TRUE, FALSE, 1
),

-- Món Cơm Tấm (34)
(
    '019f7567-133e-7bfa-bd16-e788321cec56', '019f7567-133e-7bfa-bd16-e788321cec34', '019f7567-133e-7bfa-bd16-e788321cec12',
    'Cơm Tấm Sườn Bì Chả Trứng', 'Đĩa cơm tấm đầy đủ cực phẩm thơm ngon sườn miếng to chà bá.',
    55000.00, 50000.00, TRUE,
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=300&auto=format&fit=crop',
    15, 15, FALSE, FALSE, 1
),
(
    '019f7567-133e-7bfa-bd16-e788321cec57', '019f7567-133e-7bfa-bd16-e788321cec34', '019f7567-133e-7bfa-bd16-e788321cec12',
    'Cơm Tấm Sườn Nướng Mật Ong', 'Sườn cốt lết dày miếng được ướp mật ong nướng vàng ươm mọng nước.',
    45000.00, NULL, TRUE,
    'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=300&auto=format&fit=crop',
    12, 12, FALSE, FALSE, 2
),

-- Trà Sữa Gong Cha (36)
(
    '019f7567-133e-7bfa-bd16-e788321cec58', '019f7567-133e-7bfa-bd16-e788321cec36', '019f7567-133e-7bfa-bd16-e788321cec13',
    'Trà Bí Đao Milk Foam', 'Trà bí đao ngọt thanh kết hợp lớp kem sữa mặn béo ngậy Gong Cha độc quyền.',
    48000.00, NULL, TRUE,
    'https://images.unsplash.com/photo-1541658016709-82535e94bc69?w=300&auto=format&fit=crop',
    5, 5, TRUE, FALSE, 1
),
(
    '019f7567-133e-7bfa-bd16-e788321cec59', '019f7567-133e-7bfa-bd16-e788321cec36', '019f7567-133e-7bfa-bd16-e788321cec13',
    'Trà Sữa Trân Châu Đen', 'Trà sữa đen truyền thống thơm béo kèm trân châu đen dai giòn ngọt lịm.',
    52000.00, 47000.00, TRUE,
    'https://images.unsplash.com/photo-1541658016709-82535e94bc69?w=300&auto=format&fit=crop',
    5, 5, FALSE, FALSE, 2
),

-- Bánh Mì Huỳnh Hoa (38)
(
    '019f7567-133e-7bfa-bd16-e788321cec60', '019f7567-133e-7bfa-bd16-e788321cec38', '019f7567-133e-7bfa-bd16-e788321cec14',
    'Bánh Mì Ổ Đầy Đủ (Thịt Nguội, Pate, Bơ)', 'Ổ bánh nặng gần 500g đầy ắp nhân chả lụa, dăm bông, bơ tươi và pate.',
    65000.00, 62000.00, TRUE,
    'https://images.unsplash.com/photo-1600454641350-f9824e868367?w=300&auto=format&fit=crop',
    8, 8, FALSE, FALSE, 1
),

-- Burger & Pizza (39)
(
    '019f7567-133e-7bfa-bd16-e788321cec61', '019f7567-133e-7bfa-bd16-e788321cec39', '019f7567-133e-7bfa-bd16-e788321cec15',
    'Pizza Phô Mai X2 Double Cheese', 'Pizza sốt cà chua và gấp đôi lượng phô mai Mozzarella chảy béo ngậy.',
    120000.00, 99000.00, TRUE,
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=300&auto=format&fit=crop',
    18, 18, TRUE, FALSE, 1
),
-- Burger Wagyu (40)
(
    '019f7567-133e-7bfa-bd16-e788321cec62', '019f7567-133e-7bfa-bd16-e788321cec40', '019f7567-133e-7bfa-bd16-e788321cec15',
    'Burger Bò Wagyu Phô Mai Cheddar', 'Thịt bò Wagyu thượng hạng 150g nướng chín tái vừa kèm phô mai chảy.',
    95000.00, 89000.00, TRUE,
    'https://images.unsplash.com/photo-1513104890138-7c749659a591?w=300&auto=format&fit=crop',
    15, 15, FALSE, FALSE, 1
);
