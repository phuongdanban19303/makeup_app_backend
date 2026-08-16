/* =================================================================================
   MASTER DATABASE SCHEMA - HỆ THỐNG ĐẶT THỢ TRANG ĐIỂM (MICROSERVICES)
   RBAC 5-TABLE ARCHITECTURE INTEGRATION
================================================================================= */

-- 1. Create Databases
CREATE DATABASE booking_db;
CREATE DATABASE payment_db;
CREATE DATABASE location_db;

/* =================================================================================
   PHẦN 1: USER SERVICE (PostgreSQL) - Quản lý tài khoản, RBAC, Hồ sơ & Dịch vụ
================================================================================= */
\c user_db;

CREATE SCHEMA IF NOT EXISTS user_service;
SET search_path TO user_service, public;

-- 1. BẢNG USERS (Lưu thông tin định danh)
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);

-- 2. BẢNG ROLES (Lưu các Vai trò chính)
CREATE TABLE IF NOT EXISTS roles (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) UNIQUE NOT NULL, -- 'ROLE_ADMIN', 'ROLE_MUA', 'ROLE_CUSTOMER'
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. BẢNG PERMISSIONS (Lưu các Quyền hạn chi tiết)
CREATE TABLE IF NOT EXISTS permissions (
    id SERIAL PRIMARY KEY,
    name VARCHAR(100) UNIQUE NOT NULL, -- 'booking:create', 'mua:update_status', 'system:view_dashboard'
    description VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. BẢNG USER_ROLES (Map N-N: User có những Role gì?)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- 5. BẢNG ROLE_PERMISSIONS (Map N-N: Role có những Permission gì?)
CREATE TABLE IF NOT EXISTS role_permissions (
    role_id INT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id INT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ==========================================
-- SEED DEFAULT ROLES & PERMISSIONS DATA
-- ==========================================
INSERT INTO roles (name, description) VALUES
    ('ROLE_CUSTOMER', 'Khách hàng đặt lịch trang điểm'),
    ('ROLE_MUA', 'Thợ trang điểm cung cấp dịch vụ'),
    ('ROLE_ADMIN', 'Quản trị viên hệ thống')
ON CONFLICT (name) DO NOTHING;

INSERT INTO permissions (name, description) VALUES
    ('booking:create', 'Tạo đơn đặt lịch trang điểm'),
    ('booking:cancel', 'Hủy đơn đặt lịch trang điểm'),
    ('mua:update_status', 'Cập nhật trạng thái hoạt động MUA'),
    ('mua:manage_services', 'Quản lý danh mục gói dịch vụ makeup'),
    ('system:view_dashboard', 'Xem báo cáo quản trị hệ thống')
ON CONFLICT (name) DO NOTHING;

-- Map permissions for ROLE_CUSTOMER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_CUSTOMER' AND p.name IN ('booking:create', 'booking:cancel')
ON CONFLICT DO NOTHING;

-- Map permissions for ROLE_MUA
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_MUA' AND p.name IN ('mua:update_status', 'mua:manage_services', 'booking:cancel')
ON CONFLICT DO NOTHING;

-- Map permissions for ROLE_ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT DO NOTHING;

-- ==========================================
-- MUA EXTENDED PROFILES & SERVICES TABLES
-- ==========================================
CREATE TABLE IF NOT EXISTS mua_profiles (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    avatar_url VARCHAR(255),
    bio TEXT,
    identity_card_url VARCHAR(255),
    is_verified BOOLEAN DEFAULT FALSE,
    rating DECIMAL(3,2) DEFAULT 5.00 CHECK (rating >= 1.0 AND rating <= 5.0),
    total_reviews INT DEFAULT 0,
    total_completed_jobs INT DEFAULT 0,
    current_status VARCHAR(20) DEFAULT 'OFFLINE' CHECK (current_status IN ('ONLINE', 'OFFLINE', 'BUSY'))
);
CREATE INDEX IF NOT EXISTS idx_mua_status ON mua_profiles(current_status);

CREATE TABLE IF NOT EXISTS makeup_services (
    id BIGSERIAL PRIMARY KEY,
    mua_id BIGINT NOT NULL REFERENCES mua_profiles(user_id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    description TEXT,
    base_price DECIMAL(12,2) NOT NULL CHECK (base_price >= 0),
    estimated_duration_minutes INT NOT NULL CHECK (estimated_duration_minutes > 0),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_services_mua_id ON makeup_services(mua_id);

CREATE TABLE IF NOT EXISTS mua_portfolios (
    id BIGSERIAL PRIMARY KEY,
    mua_id BIGINT NOT NULL REFERENCES mua_profiles(user_id) ON DELETE CASCADE,
    image_url VARCHAR(255) NOT NULL,
    caption VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

/* =================================================================================
   PHẦN 2: PAYMENT SERVICE (PostgreSQL v2.0.0) - Quản lý ví điện tử, Sổ cái & Ví tạm giữ
================================================================================= */
\c payment_db;

CREATE SCHEMA IF NOT EXISTS payment_service;
SET search_path TO payment_service, public;

-- 1. BẢNG WALLETS: Quản lý danh mục 4 loại ví (Ví Khách, Ví Thợ, Ví Tạm Giữ Sàn, Ví Doanh Thu Sàn)
CREATE TABLE IF NOT EXISTS wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,                                              -- ID người dùng hoặc ID ví hệ thống
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('CUSTOMER', 'WORKER', 'SYSTEM_ESCROW', 'SYSTEM_REVENUE')),
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),            -- Số dư khả dụng (Bảo vệ chống âm tiền ở tầng CSDL)
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'LOCKED')),
    version INT NOT NULL DEFAULT 0,                                             -- Dùng cho Optimistic Locking
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_user_type_wallet UNIQUE (user_id, user_type)
);

-- 2. BẢNG TRANSACTIONS: Header lưu vết thông tin tổng quan của từng giao dịch tài chính
CREATE TABLE IF NOT EXISTS transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(100) NOT NULL,                                         -- Mã đơn hàng (booking_id) hoặc mã nạp/rút tiền
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN ('TOPUP', 'ESCROW_HOLD', 'ESCROW_RELEASE', 'ESCROW_REFUND', 'WITHDRAWAL')),
    total_amount DECIMAL(15, 2) NOT NULL CHECK (total_amount > 0),
    fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,                            -- Phí chiết khấu sàn (15%)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,                               -- Khoá duy nhất chống lặp giao dịch khi retry
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. BẢNG LEDGER_ENTRIES: Sổ cái nhật ký bút toán (Append-Only - Cấm UPDATE / DELETE)
-- Luôn đảm bảo Tổng Nợ (DEBIT) = Tổng Có (CREDIT) trong cùng 1 transaction_id
CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),   -- DEBIT (Trừ/Nợ) hoặc CREDIT (Cộng/Có)
    amount DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(15, 2) NOT NULL CHECK (balance_after >= 0),           -- Số dư ví ngay sau khi thực hiện bút toán
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. BẢNG USER_BANK_ACCOUNTS: Lưu thông tin tài khoản ngân hàng liên kết chính chủ để rút tiền
CREATE TABLE IF NOT EXISTS user_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id VARCHAR(100) NOT NULL,
    bank_code VARCHAR(20) NOT NULL,                                             -- VCB, MB, TCB, ICB, VPB...
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 5. BẢNG WITHDRAWAL_REQUESTS: Quản lý yêu cầu rút tiền Payout về tài khoản ngân hàng
CREATE TABLE IF NOT EXISTS withdrawal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    user_bank_account_id UUID NOT NULL REFERENCES user_bank_accounts(id),
    amount DECIMAL(15, 2) NOT NULL CHECK (amount >= 50000),                      -- Mức tối thiểu rút 50.000 VNĐ
    fee DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'PROCESSING', 'SUCCESS', 'REJECTED', 'FAILED')),
    payout_ref_id VARCHAR(100),                                                 -- Mã giao dịch phía ngân hàng / cổng chi hộ
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_transactions_ref_type ON transactions(reference_id, transaction_type);
CREATE INDEX IF NOT EXISTS idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);

/* =================================================================================
   PHẦN 3: LOCATION SERVICE (PostgreSQL + PostGIS) - Lưu vết lịch sử di chuyển
================================================================================= */
\c location_db;

CREATE SCHEMA IF NOT EXISTS location_service;
SET search_path TO location_service, public;
CREATE EXTENSION IF NOT EXISTS postgis;

CREATE TABLE IF NOT EXISTS mua_location_history (
    id BIGSERIAL PRIMARY KEY,
    mua_id BIGINT NOT NULL,
    booking_id VARCHAR(50),
    location GEOMETRY(Point, 4326) NOT NULL,
    recorded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_location_history_mua ON mua_location_history(mua_id, recorded_at);
CREATE INDEX IF NOT EXISTS idx_location_spatial ON mua_location_history USING GIST(location);
