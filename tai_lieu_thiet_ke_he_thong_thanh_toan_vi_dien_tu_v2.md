# TÀI LIỆU THIẾT KẾ HỆ THỐNG THANH TOÁN, VÍ ĐIỆN TỬ & VÍ TẠM GIỮ (ESCROW & PAYOUT)

> **Phiên bản:** 2.0.0  
> **Trạng thái:** Chính thức  
> **Phạm vi áp dụng:** Module Thanh toán, Ví điện tử, Ví Tạm giữ (Escrow/Holding), Sổ cái đối soát (Ledger), Rút tiền (Payout), Xử lý sự kiện phân tán.

---

## MỤC LỤC
1. [Mô hình 4 Loại Ví trong hệ thống](#1-mô-hình-4-loại-ví-trong-hệ-thống)
2. [Kiến trúc luồng nghiệp vụ tổng quan](#2-kiến-trúc-luồng-nghiệp-vụ-tổng-quan)
3. [Nguyên lý Kế toán kép & Thiết kế CSDL (Database Schema)](#3-nguyên-lý-kế-toán-kép--thiết-kế-csdl-database-schema)
4. [Luồng xử lý chi tiết từng kịch bản](#4-luồng-xử-lý-chi-tiết-từng-kịch-bản)
   - [Kịch bản 1: Nạp tiền vào ví (Top-up)](#kịch-bản-1-nạp-tiền-vào-ví-top-up)
   - [Kịch bản 2: Đặt đơn & Tạm giữ tiền (Order Created & Escrow Hold)](#kịch-bản-2-đặt-đơn--tạm-giữ-tiền-order-created--escrow-hold)
   - [Kịch bản 3: Đơn COMPLETED & Giải phóng tiền (Escrow Release)](#kịch-bản-3-đơn-completed--giải-phóng-tiền-escrow-release)
   - [Kịch bản 4: Hủy đơn & Hoàn tiền (Order Canceled & Escrow Refund)](#kịch-bản-4-hủy-đơn--hoàn-tiền-order-canceled--escrow-refund)
   - [Kịch bản 5: Rút tiền về tài khoản ngân hàng (Payout / Withdrawal)](#kịch-bản-5-rút-tiền-về-tài-khoản-ngân-hàng-payout--withdrawal)
5. [Các lưu ý kỹ thuật quan trọng bất biến](#5-các-lưu-ý-kỹ-thuật-quan-trọng-bất-biến)
6. [Các vấn đề / Sự cố thực tế & Cách xử lý](#6-các-vấn-đề--sự-cố-thực-tế--cách-xử-lý)
7. [Mã nguồn mẫu SQL Transaction chuẩn (Escrow & Release)](#7-mã-nguồn-mẫu-sql-transaction-chuẩn-escrow--release)

---

## 1. Mô hình 4 Loại Ví trong hệ thống

Hệ thống quản lý tài chính chia làm 4 loại ví logic nhằm tách bạch quyền sở hữu và luồng tiền:

| Loại ví | Mã định danh (`user_type`) | Mục đích & Đặc điểm |
| :--- | :--- | :--- |
| **1. Ví Khách (Customer Wallet)** | `CUSTOMER` | Chứa tiền khả dụng của khách hàng nạp vào để thanh toán dịch vụ. Có thể rút tiền. |
| **2. Ví Thợ (Worker Wallet)** | `WORKER` | Nhận tiền công sau khi hoàn thành đơn (đã trừ phí sàn). Có thể rút tiền về ngân hàng. |
| **3. Ví Tạm giữ (System Escrow / Holding)** | `SYSTEM_ESCROW` | **Ví trung gian của hệ thống.** Giữ tiền của khách ngay khi tạo đơn để đảm bảo khách không tiêu lạm trước khi hoàn thành dịch vụ. |
| **4. Ví Doanh thu Sàn (System Revenue)** | `SYSTEM_REVENUE` | Thu tiền chiết khấu (hoa hồng sàn) từ mỗi đơn hàng hoàn tất. |

---

## 2. Kiến trúc luồng nghiệp vụ tổng quan

```
                      [ KHÁCH NẠP TIỀN ]
                              │
                              ▼
                     ┌─────────────────┐
                     │   1. Ví Khách   │
                     └────────┬────────┘
                              │
               (Khách đặt đơn ──> TẠM GIỮ TIỀN)
                              │
                              ▼
                     ┌─────────────────┐
                     │ 3. Ví Tạm Giữ   │ (System Escrow)
                     └────────┬────────┘
                              │
         ┌────────────────────┴────────────────────┐
         │ (Đơn COMPLETED)                         │ (Đơn CANCEL / Hủy)
         ▼                                         ▼
┌─────────────────┬─────────────────┐     ┌─────────────────┐
│   2. Ví Thợ     │ 4. Ví Doanh Thu │     │   1. Ví Khách   │ (Hoàn trả lại)
│ (80% tiền công) │ (20% phí sàn)   │     └─────────────────┘
└────────┬────────┴─────────────────┘
         │
 [ RÚT TIỀN VỀ BANK ]
         │
         ▼
[ Tài khoản Ngân hàng ]
```

---

## 3. Nguyên lý Kế toán kép & Thiết kế CSDL (Database Schema)

### 3.1. Quy tắc Sổ cái (Ledger Rules)
1. **Append-Only:** Bảng `ledger_entries` không cho phép `UPDATE` hay `DELETE`.
2. **Zero-Sum Balance:** Trong cùng một `transaction_id`, tổng phát sinh Nợ (DEBIT) luôn bằng tổng phát sinh Có (CREDIT):
   $$\sum 	ext{Amount}_{	ext{DEBIT}} = \sum 	ext{Amount}_{	ext{CREDIT}}$$
3. **Truy vết biến động:** Lưu trữ `balance_after` của ví sau mỗi bút toán để phục vụ đối soát.

### 3.2. CSDL chuẩn (PostgreSQL)

```sql
-- 1. Bảng danh mục ví (Wallets)
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,               -- ID người dùng hoặc ID hệ thống
    user_type VARCHAR(20) NOT NULL CHECK (user_type IN ('CUSTOMER', 'WORKER', 'SYSTEM_ESCROW', 'SYSTEM_REVENUE')),
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00 CHECK (balance >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'FROZEN', 'LOCKED')),
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_user_type_wallet UNIQUE (user_id, user_type)
);

-- 2. Bảng giao dịch tài chính (Transactions Header)
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_id VARCHAR(100) NOT NULL, -- Mã đơn hàng (order_id) hoặc mã nạp/rút tiền
    transaction_type VARCHAR(50) NOT NULL CHECK (transaction_type IN (
        'TOPUP',             -- Nạp tiền
        'ESCROW_HOLD',       -- Tạm giữ tiền đơn hàng
        'ESCROW_RELEASE',    -- Giải phóng tiền chia cho Thợ + Sàn
        'ESCROW_REFUND',     -- Hoàn trả tiền tạm giữ khi hủy đơn
        'WITHDRAWAL'         -- Rút tiền về ngân hàng
    )),
    total_amount DECIMAL(15, 2) NOT NULL CHECK (total_amount > 0),
    fee_amount DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 3. Bảng sổ cái chi tiết (Ledger Entries)
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    entry_type VARCHAR(10) NOT NULL CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    amount DECIMAL(15, 2) NOT NULL CHECK (amount > 0),
    balance_after DECIMAL(15, 2) NOT NULL CHECK (balance_after >= 0),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. Bảng tài khoản ngân hàng liên kết (Dùng để Rút tiền)
CREATE TABLE user_bank_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    bank_code VARCHAR(20) NOT NULL,       -- 'VCB', 'MB', 'TCB', 'ICB',...
    account_number VARCHAR(50) NOT NULL,
    account_name VARCHAR(100) NOT NULL,   -- 'NGUYEN VAN A' (Chính chủ)
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 5. Bảng quản lý yêu cầu rút tiền (Withdrawal Requests)
CREATE TABLE withdrawal_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id UUID NOT NULL REFERENCES wallets(id),
    user_bank_account_id UUID NOT NULL REFERENCES user_bank_accounts(id),
    amount DECIMAL(15, 2) NOT NULL CHECK (amount >= 50000), -- Mức tối thiểu rút
    fee DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' 
        CHECK (status IN ('PENDING', 'APPROVED', 'PROCESSING', 'SUCCESS', 'REJECTED', 'FAILED')),
    payout_ref_id VARCHAR(100),                            -- Mã tham chiếu ngân hàng/cổng chi hộ
    rejection_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_transactions_ref_type ON transactions(reference_id, transaction_type);
CREATE INDEX idx_ledger_wallet_created ON ledger_entries(wallet_id, created_at);
```

---

## 4. Luồng xử lý chi tiết từng kịch bản

### Kịch bản 1: Nạp tiền vào ví (Top-up)
1. Khách gửi yêu cầu nạp $X$ VNĐ qua Cổng thanh toán (VNPay / MoMo / PayOS).
2. Hệ thống ghi `transactions` trạng thái `PENDING` với `idempotency_key = "TOPUP_" + gateway_order_id`.
3. Webhook thanh toán thành công:
   * Mở DB Transaction: Khóa Ví Khách (`SELECT ... FOR UPDATE`).
   * Cộng tiền Ví Khách: `balance = balance + X`.
   * Ghi Sổ cái: `CREDIT` Ví Khách $X$ VNĐ.
   * Chuyển `transactions.status = SUCCESS`. Commit DB.

---

### Kịch bản 2: Đặt đơn & Tạm giữ tiền (Order Created & Escrow Hold)
> **Mục tiêu:** Tránh trường hợp đơn hoàn thành nhưng khách không còn tiền trong ví.

1. Khách bấm đặt đơn trị giá **100.000đ**.
2. Hệ thống thực hiện DB Transaction:
   * Khóa **Ví Khách** và **Ví Tạm Giữ (System Escrow)** theo thứ tự UUID (`ORDER BY id ASC FOR UPDATE`).
   * Kiểm tra Ví Khách: `balance >= 100.000đ`. (Nếu không đủ $ightarrow$ Báo lỗi yêu cầu nạp thêm).
   * **Trừ Ví Khách:** `balance = balance - 100.000đ`.
   * **Cộng Ví Tạm Giữ:** `balance = balance + 100.000đ`.
   * Ghi 2 dòng Sổ cái:
     * Dòng 1: Ví Khách - `DEBIT` 100.000đ
     * Dòng 2: Ví Tạm Giữ - `CREDIT` 100.000đ
   * Tạo bản ghi `transactions` (`type = ESCROW_HOLD`, `idempotency_key = "ESCROW_HOLD_" + order_id`, `status = SUCCESS`).
3. Đơn hàng chuyển sang trạng thái chờ thợ nhận / đang thực hiện.

---

### Kịch bản 3: Đơn COMPLETED & Giải phóng tiền (Escrow Release)
Giả sử đơn **100.000đ**, hoa hồng sàn **20% = 20.000đ**, Thợ thực nhận **80.000đ**:

```
[Order COMPLETED] ──> Bắn Event: order.completed ──> [Payment Consumer]
                                                              │
                                              ┌───────────────┴───────────────┐
                                              ▼                               ▼
                                    (Kiểm tra Idempotency)          (Khóa 3 Ví theo ID ASC)
                                                                              │
                                                                              ▼
                                                                [Thực hiện DB Transaction:]
                                                                1. Trừ Ví Tạm Giữ:    -100.000đ (DEBIT)
                                                                2. Cộng Doanh Thu Sàn: +20.000đ (CREDIT)
                                                                3. Cộng Ví Thợ:        +80.000đ (CREDIT)
                                                                4. Ghi 3 dòng Sổ cái
                                                                5. Tạo Transaction (ESCROW_RELEASE)
```

1. Payment Consumer nhận Event `order.completed`.
2. Kiểm tra `idempotency_key = "ESCROW_RELEASE_" + order_id`. Nếu đã xử lý $ightarrow$ Bỏ qua (ACK).
3. Mở DB Transaction:
   * Khóa **Ví Tạm Giữ**, **Ví Thợ**, **Ví Doanh Thu Sàn** (`ORDER BY id ASC FOR UPDATE`).
   * **Trừ Ví Tạm Giữ:** `balance = balance - 100.000đ`.
   * **Cộng Doanh Thu Sàn:** `balance = balance + 20.000đ`.
   * **Cộng Ví Thợ:** `balance = balance + 80.000đ`.
   * Ghi 3 dòng Sổ cái:
     * `DEBIT` Ví Tạm Giữ: 100.000đ
     * `CREDIT` Ví Doanh Thu Sàn: 20.000đ
     * `CREDIT` Ví Thợ: 80.000đ
   * Lưu `transactions` (`ESCROW_RELEASE`, `status = SUCCESS`).
4. Commit DB & Phản hồi ACK cho Message Broker.

---

### Kịch bản 4: Hủy đơn & Hoàn tiền (Order Canceled & Escrow Refund)
Nếu khách hủy đơn hợp lệ hoặc không tìm được thợ:
1. Mở DB Transaction:
   * Khóa **Ví Tạm Giữ** và **Ví Khách** (`ORDER BY id ASC FOR UPDATE`).
   * **Trừ Ví Tạm Giữ:** `balance = balance - 100.000đ`.
   * **Cộng trả lại Ví Khách:** `balance = balance + 100.000đ`.
   * Ghi Sổ cái:
     * `DEBIT` Ví Tạm Giữ: 100.000đ
     * `CREDIT` Ví Khách: 100.000đ
   * Tạo bản ghi `transactions` (`type = ESCROW_REFUND`, `status = SUCCESS`).
2. Commit DB và thông báo tiền đã hoàn lại ví khả dụng của khách.

---

### Kịch bản 5: Rút tiền về tài khoản ngân hàng (Payout / Withdrawal)

```
[1. Khách/Thợ tạo lệnh rút 500k] ──> [2. Trừ tiền Ví Khả dụng ngay trong DB Transaction]
                                                        │
                                     [3. Chờ Admin duyệt / Auto Payout Cổng thanh toán]
                                                        │
                                    ┌───────────────────┴───────────────────┐
                                    ▼                                       ▼
                             (THÀNH CÔNG)                               (THẤT BÀI)
                                    │                                       │
                         [Ghi nhận Sổ cái & Xong]              [Hoàn trả tiền vào Ví]
```

1. **Tạo lệnh rút & Giữ tiền:**
   * Khách nhập mã PIN/OTP xác thực rút 500.000đ.
   * Khóa Ví $ightarrow$ Kiểm tra `balance >= 500.000đ`.
   * Trừ tiền ví: `balance = balance - 500.000đ`.
   * Tạo bản ghi trong `withdrawal_requests` với trạng thái `PROCESSING`.
2. **Chi trả (Payout):**
   * Hệ thống tự động gọi API Chi hộ (MoMo/PayOS/Ngân hàng) hoặc Kế toán chuyển khoản thủ công.
3. **Xử lý kết quả:**
   * **Nếu Thành công:**
     * Chuyển trạng thái `withdrawal_requests.status = SUCCESS`.
     * Ghi Sổ cái: `DEBIT` Ví người dùng 500.000đ.
   * **Nếu Thất bại:**
     * Chuyển `withdrawal_requests.status = FAILED`.
     * Thực hiện DB Transaction hoàn trả: `balance = balance + 500.000đ`.
     * Ghi Sổ cái dòng hoàn tiền `CREDIT` vào ví người dùng.

---

## 5. Các lưu ý kỹ thuật quan trọng bất biến

| Tiêu chí | Quy định kỹ thuật bắt buộc |
| :--- | :--- |
| **Kiểu số thực tài chính** | Dùng `DECIMAL(15, 2)` hoặc `NUMERIC(15, 2)` (hoặc `BIGINT` đơn vị đồng/cent). Tuyệt đối cấm dùng `FLOAT`/`DOUBLE`. |
| **Bảo vệ chống Race Condition** | Mọi biến động số dư phải nằm trong DB Transaction và có `SELECT ... FOR UPDATE`. |
| **Triệt tiêu Deadlock** | Khi thao tác từ 2 ví trở lên (Ví Khách, Tạm Giữ, Thợ, Sàn), bắt buộc sắp xếp khóa tăng dần theo ID: `ORDER BY id ASC`. |
| **Bảo vệ chống âm tiền** | Khai báo `CHECK (balance >= 0)` trên bảng `wallets` ở tầng CSDL. |
| **Idempotency bắt buộc** | Mọi Event/Request tài chính đều phải có `idempotency_key` duy nhất để ngăn chặn nhân đôi giao dịch khi retry. |

---

## 6. Các vấn đề / Sự cố thực tế & Cách xử lý

### Vấn đề 1: Đơn hàng hoàn tất nhưng tiền trong Ví Tạm Giữ không đủ
* **Nguyên nhân:** Lỗi logic hệ thống bỏ qua bước Tạm giữ (Hold) lúc tạo đơn.
* **Cách xử lý:** Luồng `ESCROW_RELEASE` bắt buộc kiểm tra số dư Ví Tạm Giữ. Nếu không đủ, chặn giao dịch, chuyển trạng thái đơn sang `PAYMENT_DISPUTE` và kích hoạt báo động khẩn cho Admin.

### Vấn đề 2: Duplicate Event từ RabbitMQ/Kafka
* **Nguyên nhân:** Mạng chập chờn khiến tín hiệu ACK bị mất, Broker gửi lại tin nhắn `order.completed`.
* **Cách xử lý:** Kiểm tra `idempotency_key = "ESCROW_RELEASE_" + order_id` trong bảng `transactions`. Nếu đã có bản ghi `SUCCESS`, lập tức gửi lại ACK cho Broker và dừng xử lý.

### Vấn đề 3: Lỗi khi gọi API Chi hộ ngân hàng (Timeout / Mất mạng)
* **Nguyên nhân:** Backend gửi yêu cầu rút tiền sang Ngân hàng nhưng bị Timeout không nhận được kết quả (không rõ thành công hay thất bại).
* **Cách xử lý:** **Không được tự ý hoàn tiền lại cho khách ngay lập tức.** Giữ trạng thái giao dịch ở `PROCESSING`. Chạy Job kiểm tra trạng thái giao dịch (Query Transaction Status API) sang Cổng thanh toán sau mỗi 5 phút để lấy kết quả chính thức.

### Vấn đề 4: Đối soát sai lệch số dư (Daily Reconciliation Batch)
* **Cách xử lý:** Chạy Cronjob 02:00 sáng đối soát công thức cân bằng:
  $$	ext{Tổng tiền Nạp} - 	ext{Tổng tiền Rút} \stackrel{?}{=} 	ext{Tổng số dư 4 loại ví hiện tại}$$
  Nếu phát hiện lệch dù chỉ 1 đồng $ightarrow$ Tự động gửi cảnh báo khẩn cấp (Telegram/Slack) và đóng băng các ví có sai sót.

---

## 7. Mã nguồn mẫu SQL Transaction chuẩn (Escrow & Release)

### A. Đoạn SQL Tạm Giữ Tiền (Escrow Hold khi tạo đơn):
```sql
DO $$
DECLARE
    v_order_id VARCHAR := 'ORD-999';
    v_idempotency_key VARCHAR := 'ESCROW_HOLD_ORD-999';
    v_order_amount DECIMAL(15, 2) := 100000.00;
    v_cust_wallet_id UUID := '11111111-1111-1111-1111-111111111111';
    v_escrow_wallet_id UUID := '44444444-4444-4444-4444-444444444444';
    v_cust_bal DECIMAL(15, 2);
    v_escrow_bal DECIMAL(15, 2);
    v_tx_id UUID;
BEGIN
    -- 1. Chống lặp
    IF EXISTS (SELECT 1 FROM transactions WHERE idempotency_key = v_idempotency_key AND status = 'SUCCESS') THEN
        RETURN;
    END IF;

    -- 2. Khóa 2 ví theo thứ tự UUID tăng dần chống Deadlock
    PERFORM id FROM wallets WHERE id IN (v_cust_wallet_id, v_escrow_wallet_id) ORDER BY id ASC FOR UPDATE;

    -- 3. Kiểm tra số dư ví khách
    SELECT balance INTO v_cust_bal FROM wallets WHERE id = v_cust_wallet_id;
    IF v_cust_bal < v_order_amount THEN
        RAISE EXCEPTION 'Ví khách không đủ số dư để đặt đơn!';
    END IF;

    -- 4. Tạo Transaction
    INSERT INTO transactions (reference_id, transaction_type, total_amount, status, idempotency_key)
    VALUES (v_order_id, 'ESCROW_HOLD', v_order_amount, 'SUCCESS', v_idempotency_key)
    RETURNING id INTO v_tx_id;

    -- 5. Trừ Ví Khách & Ghi Sổ cái (DEBIT)
    UPDATE wallets SET balance = balance - v_order_amount, updated_at = NOW()
    WHERE id = v_cust_wallet_id RETURNING balance INTO v_cust_bal;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, balance_after, description)
    VALUES (v_tx_id, v_cust_wallet_id, 'DEBIT', v_order_amount, v_cust_bal, 'Tam giu tien don #' || v_order_id);

    -- 6. Cộng Ví Tạm Giữ & Ghi Sổ cái (CREDIT)
    UPDATE wallets SET balance = balance + v_order_amount, updated_at = NOW()
    WHERE id = v_escrow_wallet_id RETURNING balance INTO v_escrow_bal;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, balance_after, description)
    VALUES (v_tx_id, v_escrow_wallet_id, 'CREDIT', v_order_amount, v_escrow_bal, 'Nhan tam giu tien don #' || v_order_id);
END $$;
```

---

### B. Đoạn SQL Giải Phóng Tiền (Escrow Release khi đơn COMPLETED):
```sql
DO $$
DECLARE
    v_order_id VARCHAR := 'ORD-999';
    v_idempotency_key VARCHAR := 'ESCROW_RELEASE_ORD-999';
    v_order_amount DECIMAL(15, 2) := 100000.00;
    v_platform_fee DECIMAL(15, 2) := 20000.00; -- 20%
    v_worker_amount DECIMAL(15, 2) := 80000.00;  -- 80%
    
    v_escrow_wallet_id UUID := '44444444-4444-4444-4444-444444444444';
    v_worker_wallet_id UUID := '22222222-2222-2222-2222-222222222222';
    v_revenue_wallet_id UUID := '33333333-3333-3333-3333-333333333333';
    
    v_escrow_bal DECIMAL(15, 2);
    v_worker_bal DECIMAL(15, 2);
    v_revenue_bal DECIMAL(15, 2);
    v_tx_id UUID;
BEGIN
    -- 1. Chống lặp
    IF EXISTS (SELECT 1 FROM transactions WHERE idempotency_key = v_idempotency_key AND status = 'SUCCESS') THEN
        RETURN;
    END IF;

    -- 2. Khóa 3 ví theo thứ tự UUID tăng dần
    PERFORM id FROM wallets WHERE id IN (v_escrow_wallet_id, v_worker_wallet_id, v_revenue_wallet_id) ORDER BY id ASC FOR UPDATE;

    -- 3. Tạo Transaction Release
    INSERT INTO transactions (reference_id, transaction_type, total_amount, fee_amount, status, idempotency_key)
    VALUES (v_order_id, 'ESCROW_RELEASE', v_order_amount, v_platform_fee, 'SUCCESS', v_idempotency_key)
    RETURNING id INTO v_tx_id;

    -- 4. Trừ Ví Tạm Giữ & Ghi Sổ cái (DEBIT 100k)
    UPDATE wallets SET balance = balance - v_order_amount, updated_at = NOW()
    WHERE id = v_escrow_wallet_id RETURNING balance INTO v_escrow_bal;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, balance_after, description)
    VALUES (v_tx_id, v_escrow_wallet_id, 'DEBIT', v_order_amount, v_escrow_bal, 'Giai phong tien tam giu don #' || v_order_id);

    -- 5. Cộng Doanh Thu Sàn & Ghi Sổ cái (CREDIT 20k)
    UPDATE wallets SET balance = balance + v_platform_fee, updated_at = NOW()
    WHERE id = v_revenue_wallet_id RETURNING balance INTO v_revenue_bal;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, balance_after, description)
    VALUES (v_tx_id, v_revenue_wallet_id, 'CREDIT', v_platform_fee, v_revenue_bal, 'Doanh thu phi san don #' || v_order_id);

    -- 6. Cộng Ví Thợ & Ghi Sổ cái (CREDIT 80k)
    UPDATE wallets SET balance = balance + v_worker_amount, updated_at = NOW()
    WHERE id = v_worker_wallet_id RETURNING balance INTO v_worker_bal;

    INSERT INTO ledger_entries (transaction_id, wallet_id, entry_type, amount, balance_after, description)
    VALUES (v_tx_id, v_worker_wallet_id, 'CREDIT', v_worker_amount, v_worker_bal, 'Tien cong thuc nhan don #' || v_order_id);
END $$;
```
