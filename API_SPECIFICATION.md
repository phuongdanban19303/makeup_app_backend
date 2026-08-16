# ĐẶC TẢ API CHÍNH THỨC & BẢN ĐỒ TÍCH HỢP FRONTEND DỰ ÁN MAKEUP APP (MONOREPO)

Tài liệu đặc tả chi tiết toàn bộ các API RESTful, STOMP WebSocket, Cấu trúc Dữ liệu (DTO), Query Parameters, và Luồng xử lý giao diện (Frontend Customer App & Worker MUA App) đối chiếu chính xác 100% theo mã nguồn các Microservices.

---

## I. KIẾN TRÚC HỆ THỐNG & ĐỊNH DẠNG CHUẨN

- **API Gateway Base URL**: `http://localhost:8080`
  - Location Service trực tiếp: `http://localhost:8082`
  - User Service trực tiếp: `http://localhost:8081`
  - Booking Service trực tiếp: `http://localhost:8083`
  - Pricing Service trực tiếp: `http://localhost:8084`
  - Payment Service trực tiếp: `http://localhost:8085`
- **STOMP WebSocket Broker**: `http://localhost:8080/ws-location`
- **Header Xác thực**: `Authorization: Bearer <accessToken>`
- **Phân quyền Role**:
  - `ROLE_CUSTOMER`: Tính năng Khách hàng đặt lịch làm đẹp.
  - `ROLE_MUA`: Tính năng Thợ Makeup quản lý nhận ca & bảng giá dịch vụ.
  - `ROLE_ADMIN`: Quyền quản trị hệ thống & giải quyết tranh chấp.

### Structural Response Envelope (`ApiResponse<T>`)
Mọi API trả về cấu trúc chuẩn JSON chuẩn hóa:

```json
{
  "success": true,
  "status": 200,
  "code": "SUCCESS",
  "message": "Thông báo kết quả xử lý thành công hoặc thất bại",
  "data": { ... },
  "timestamp": "2026-08-13T10:00:00Z",
  "path": "/api/v1/...",
  "trace_id": "d8f4e2a1-7c9b-4b1a-8e2d-3f5a1c9b2e4f"
}
```

---

## II. DANH SÁCH CHI TIẾT CÁC REST API THEO DỊCH VỤ

---

### 1. DỊCH VỤ XÁC THỰC & NGƯỜI DÙNG (`user-service`)

#### 1.1. Gửi mã OTP xác thực SĐT
- **Method & URL**: `POST /api/v1/auth/send-otp`
- **Auth**: Public
- **Request Body**:
```json
{
  "phone": "0901234567"
}
```
- **Response Data (`data`)**:
```json
{
  "phone": "0901234567",
  "otp": "123456"
}
```

#### 1.2. Đăng ký tài khoản (Customer hoặc MUA Worker)
- **Method & URL**: `POST /api/v1/auth/register`
- **Auth**: Public
- **Request Body**:
```json
{
  "phone": "0901234567",
  "email": "user@example.com",
  "fullName": "Nguyễn Văn A",
  "password": "Password123!",
  "otpCode": "123456",
  "role": "CUSTOMER" // Hoặc "MUA"
}
```
- **Response Data (`data`)**:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1Ni...",
  "refreshToken": "eyJhbGciOiJIUzI1Ni...",
  "tokenType": "Bearer",
  "expiresInMs": 86400000,
  "userId": 105,
  "phone": "0901234567",
  "fullName": "Nguyễn Văn A",
  "roles": ["ROLE_CUSTOMER"]
}
```

#### 1.3. Đăng nhập
- **Method & URL**: `POST /api/v1/auth/login`
- **Auth**: Public
- **Request Body**:
```json
{
  "phone": "0901234567",
  "password": "Password123!"
}
```
- **Response Data (`data`)**: Cấu trúc `AuthResponseDto` tương tự Đăng ký.

#### 1.4. Làm mới Token (Refresh Token)
- **Method & URL**: `POST /api/v1/auth/refresh`
- **Auth**: Public
- **Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1Ni..."
}
```
- **Response Data (`data`)**: Cấu trúc `AuthResponseDto` chứa cặp token mới.

#### 1.5. Đăng xuất (Logout)
- **Method & URL**: `POST /api/v1/auth/logout`
- **Auth**: Bearer Token
- **Request Body**:
```json
{
  "refreshToken": "eyJhbGciOiJIUzI1Ni..."
}
```
- **Response Data (`data`)**: `null`

#### 1.6. Lấy thông tin cá nhân của người dùng hiện tại
- **Method & URL**: `GET /api/v1/users/me`
- **Auth**: Bearer Token (`ROLE_CUSTOMER`, `ROLE_MUA`, `ROLE_ADMIN`)
- **Response Data (`data`)**:
```json
{
  "id": "105",
  "fullName": "Nguyễn Văn A",
  "phoneNumber": "0901234567",
  "email": "user@example.com",
  "userRole": "CUSTOMER",
  "status": "ACTIVE"
}
```

#### 1.7. Lấy chi tiết thông tin người dùng theo ID
- **Method & URL**: `GET /api/v1/users/{id}`
- **Auth**: Bearer Token
- **Response Data (`data`)**: Cấu trúc `UserProfileDto`.

#### 1.8. Xem chi tiết Hồ sơ Thợ Makeup (Full MUA Profile)
- **Method & URL**: `GET /api/v1/mua/{muaId}/profile`
- **Auth**: Public / Customer / MUA
- **Response Data (`data`)**:
```json
{
  "userId": 7,
  "fullName": "Nguyễn Văn A",
  "phone": "0901234567",
  "avatarUrl": "https://cdn.makeupapp.com/avatar/img7.jpg",
  "bio": "Chuyên gia trang điểm cô dâu 5 năm kinh nghiệm",
  "isVerified": true,
  "rating": 4.9,
  "totalCompletedJobs": 45,
  "currentStatus": "ONLINE",
  "services": [
    {
      "id": 1,
      "category": "BRIDAL",
      "serviceName": "Makeup Cô Dâu Tiệc Đêm",
      "description": "Bao gồm làm tóc và trang điểm phong cách Hàn Quốc",
      "basePrice": 1500000.0,
      "estimatedDurationMinutes": 90,
      "isActive": true
    }
  ],
  "portfolios": [
    {
      "portfolioId": 10,
      "imageUrl": "https://cdn.makeupapp.com/portfolio/img1.jpg",
      "caption": "Makeup cô dâu phong cách Glowy"
    }
  ]
}
```

#### 1.9. Cập nhật hồ sơ Thợ Makeup (Bio, Trạng thái ONLINE/OFFLINE)
- **Method & URL**: `PUT /api/v1/mua/{muaId}/profile`
- **Auth**: Bearer Token (`ROLE_MUA`, `ROLE_ADMIN`)
- **Request Body**:
```json
{
  "bio": "Chuyên gia trang điểm cô dâu cao cấp",
  "currentStatus": "ONLINE", // "ONLINE" hoặc "OFFLINE"
  "fullName": "Nguyễn Văn A",
  "avatarUrl": "https://cdn.makeupapp.com/avatar/img7.jpg",
  "phone": "0901234567",
  "address": "Quận 1, TP.HCM"
}
```
- **Response Data (`data`)**: Cấu trúc `MuaFullProfileResponseDto` sau khi cập nhật.

#### 1.10. Upload Căn Cước Công Dân (CCCD) Xác Thực Thợ
- **Method & URL**: `POST /api/v1/mua/{muaId}/upload-identity-card`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Headers**: `Content-Type: multipart/form-data`
- **Form Data**: `file` (File ảnh binary CCCD)
- **Response Data (`data`)**:
```json
{
  "muaId": "7",
  "identityCardUrl": "https://cdn.makeupapp.com/cccd/img7.jpg"
}
```

#### 1.11. Quản lý Gói dịch vụ Lắp ghép Động 3 Tầng (Dynamic Bundle Services & Row-based Options)

##### 1.11.1. Tạo mới Gói dịch vụ Lắp ghép Động (Create Bundle)
- **Method & URL**: `POST /api/v1/mua/{muaId}/bundle-services`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Request Body**:
```json
{
  "masterServiceId": 1,
  "category": "BRIDAL", // "BRIDAL", "EVENT", "BASIC", "COMBO"
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "description": "Sử dụng mỹ phẩm cao cấp MAC, Dior",
  "basePrice": 200000.0, // Tiền công cốt lõi không thể bỏ
  "estimatedDurationMinutes": 90,
  "options": [
    { "optionType": "COMPONENT", "optionName": "Đánh Kem Nền", "price": 100000.0, "isDefault": true, "isRemovable": true },
    { "optionType": "COMPONENT", "optionName": "Che Khuyết Điểm", "price": 100000.0, "isDefault": true, "isRemovable": false },
    { "optionType": "COMPONENT", "optionName": "Phấn Phủ", "price": 100000.0, "isDefault": true, "isRemovable": true },
    { "optionType": "ADD_ON", "optionName": "Làm Tóc Cô Dâu", "price": 200000.0, "isDefault": false, "isRemovable": true }
  ]
}
```
- **Response Data (`data`)**:
```json
{
  "id": 1,
  "providerId": 7,
  "masterServiceId": 1,
  "category": "BRIDAL",
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "description": "Sử dụng mỹ phẩm cao cấp MAC, Dior",
  "basePrice": 200000.0,
  "defaultTotalPrice": 500000.0, // basePrice + SUM(COMPONENTS isDefault)
  "estimatedDurationMinutes": 90,
  "isActive": true,
  "options": [
    { "id": 10, "optionType": "COMPONENT", "optionName": "Đánh Kem Nền", "price": 100000.0, "isDefault": true, "isRemovable": true },
    { "id": 11, "optionType": "COMPONENT", "optionName": "Che Khuyết Điểm", "price": 100000.0, "isDefault": true, "isRemovable": false }
  ],
  "createdAt": "2026-08-13T10:00:00Z"
}
```

##### 1.11.2. Lấy danh sách Gói dịch vụ Lắp ghép của Thợ (Read Bundles)
- **Method & URL**: `GET /api/v1/mua/{muaId}/bundle-services`
- **Auth**: Public / Bearer Token
- **Query Params**: `includeInactive=true` (Thợ xem cả gói đã ẩn) hoặc `includeInactive=false` (Mặc định cho Khách)
- **Response Data (`data`)**: `List<ProviderServiceResponseDto>`

##### 1.11.3. Chỉnh sửa Gói dịch vụ Lắp ghép (Update Bundle)
- **Method & URL**: `PUT /api/v1/mua/{muaId}/bundle-services/{serviceId}`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Request Body**: Tương tự API `POST /bundle-services`.

#### 1.12. Quản lý Dịch vụ Đơn Lẻ (Legacy MUA Services)

##### 1.12.1. Tạo mới Dịch vụ đơn lẻ
- **Method & URL**: `POST /api/v1/mua/{muaId}/services`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Request Body**:
```json
{
  "category": "BRIDAL",
  "serviceName": "Makeup Cô Dâu Tiệc Đêm",
  "description": "Trang điểm tự nhiên đi tiệc sinh nhật, sự kiện",
  "basePrice": 1500000.0,
  "estimatedDurationMinutes": 90
}
```

##### 1.12.2. Lấy danh sách Dịch vụ đơn lẻ của Thợ
- **Method & URL**: `GET /api/v1/mua/{muaId}/services`
- **Query Params**: `includeInactive` (boolean, default `false`)

##### 1.12.3. Chỉnh sửa Dịch vụ đơn lẻ
- **Method & URL**: `PUT /api/v1/mua/{muaId}/services/{serviceId}`

##### 1.12.4. Tạm ẩn / Bật lại Dịch vụ (Toggle Status / Soft Delete)
- **Method & URL**: `PATCH /api/v1/mua/{muaId}/services/{serviceId}/toggle-status`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Query Params**: `isActive=false` (Tạm ẩn khỏi giao diện khách) hoặc `isActive=true` (Hiện lại)

##### 1.12.5. Xóa Dịch vụ (Delete)
- **Method & URL**: `DELETE /api/v1/mua/{muaId}/services/{serviceId}`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Query Params**: `permanent=true` (Xóa vĩnh viễn khỏi Database) hoặc `permanent=false` (Tạm ẩn)

#### 1.13. Quản lý Danh mục Chuẩn Sàn (Master Services - Admin Rules)
- **Method & URL**: `GET /api/v1/master-services`
- **Query Params**: `categoryName` (Optional)
- **Response Data (`data`)**:
```json
[
  {
    "id": 1,
    "categoryName": "Makeup",
    "serviceName": "Makeup Cô Dâu",
    "code": "BRIDAL_MAKEUP",
    "description": "Dịch vụ trang điểm cô dâu chuẩn sàn",
    "suggestedBasePrice": 200000.0,
    "standardDurationMinutes": 90
  }
]
```

#### 1.14. Quản lý Bộ Sưu Tập Ảnh Portfolio

##### 1.14.1. Upload Ảnh Portfolio mới
- **Method & URL**: `POST /api/v1/mua/{muaId}/portfolio`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Headers**: `Content-Type: multipart/form-data`
- **Form Data**: `file` (File ảnh), `caption` (Text - Chú thích ảnh)
- **Response Data (`data`)**:
```json
{
  "portfolioId": 10,
  "imageUrl": "https://cdn.makeupapp.com/portfolio/img1.jpg",
  "caption": "Makeup cô dâu tone cam đào",
  "createdAt": "2026-08-13T10:00:00Z"
}
```

##### 1.14.2. Lấy danh sách Ảnh Portfolio của Thợ
- **Method & URL**: `GET /api/v1/mua/{muaId}/portfolio`

##### 1.14.3. Xóa Ảnh Portfolio
- **Method & URL**: `DELETE /api/v1/mua/{muaId}/portfolio/{portfolioId}`

#### 1.15. Truy vấn danh sách thông tin tóm tắt Thợ (Batch Summaries)
- **Method & URL**: `POST /api/v1/mua/summaries`
- **Auth**: Internal Service / Bearer Token
- **Request Body**: `[7, 12, 15]` (Danh sách IDs thợ cần lấy thông tin)
- **Response Data (`data`)**: `List<MuaSummaryDto>`

---

### 2. DỊCH VỤ ĐỊNH VỊ & BẢN ĐỒ (`location-service`)

#### 2.1. Quét tìm danh sách Thợ lân cận xung quanh vị trí Khách hàng (GEOSEARCH)
- **Method & URL**: `GET /api/v1/workers/nearby` (hoặc `/api/v1/location/nearby`)
- **Auth**: Bearer Token (`ROLE_CUSTOMER`, `ROLE_ADMIN`)
- **Query Params**:
  - `latitude`: `10.776889` (Vĩ độ vị trí khách)
  - `longitude`: `106.700806` (Kinh độ vị trí khách)
  - `radiusKm`: `5.0` (Bán kính tìm kiếm - Mặc định 5.0 km)
  - `category`: `BRIDAL` (Lọc theo danh mục - Optional)
  - `requiredSubServices`: `["MAKEUP_FACE", "HAIR_STYLING"]` (Optional)
- **Response Data (`data`)**:
```json
[
  {
    "workerId": 7,
    "fullName": "Nguyễn Văn A",
    "avatarUrl": "https://cdn.makeupapp.com/avatar/img7.jpg",
    "rating": 4.9,
    "totalCompletedJobs": 45,
    "currentStatus": "ONLINE",
    "latitude": 20.9904454,
    "longitude": 105.8018347,
    "distanceKm": 0.01,
    "services": [
      {
        "id": 1,
        "category": "BRIDAL",
        "serviceName": "Makeup Cô Dâu Tiệc Đêm",
        "description": "Trang điểm tự nhiên đi tiệc sinh nhật, sự kiện",
        "basePrice": 1500000.0,
        "estimatedDurationMinutes": 90,
        "isActive": true
      }
    ]
  }
]
```

#### 2.2. Push stream tọa độ GPS thời gian thực (REST Fallback khi Bật Hoạt Động)
- **Method & URL**: `POST /api/v1/location/stream` (hoặc `/api/v1/workers/location/stream`)
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Request Body**:
```json
{
  "workerId": 7, // Nếu có token sẽ tự động lấy từ Token Claim
  "latitude": 20.990445,
  "longitude": 105.801834,
  "bookingId": "bk-uuid-8899-1234", // null nếu rảnh, điền bookingId nếu đang di chuyển
  "timestamp": 1770870000000
}
```
- **Response Data (`data`)**: `null` (Message: "GPS location stream updated in Redis & PostgreSQL")

#### 2.3. Thợ bấm "Tắt hoạt động" -> Xóa định vị GPS khỏi Redis GEO
- **Method & URL**: `DELETE /api/v1/workers/location` (hoặc `/api/v1/location/offline` / `/api/v1/location/worker/{workerId}`)
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Query Params**: `workerId=7` (Optional: Tự động lấy từ Token nếu bỏ trống)
- **Response Data (`data`)**: `null` (Message: "Xóa vị trí GPS của thợ thành công")

---

### 3. DỊCH VỤ TÍNH GIÁ (`pricing-service`)

#### 3.1. Tính toán chi phí gói dịch vụ, phụ phí options & Phí di chuyển thời gian thực
- **Method & URL**: `POST /api/v1/pricing/calculate`
- **Auth**: Bearer Token (`ROLE_CUSTOMER`, `ROLE_MUA`)
- **Request Body**:
```json
{
  "servicePackageId": "1",
  "basePackageFee": 200000.0, // Tiền công cốt lõi không thể bỏ
  "optionsFee": 300000.0,     // Phụ phí từ các options / add-ons được khách chọn
  "distanceInKm": 4.5,        // Quãng đường di chuyển (km)
  "customerLat": 10.776889,
  "customerLng": 106.700806
}
```
- **Response Data (`data`)**:
```json
{
  "servicePackageId": "1",
  "basePackageFee": 200000.0,
  "optionsFee": 300000.0,
  "packageSubtotal": 500000.0, // basePackageFee + optionsFee
  "travelDistanceFee": 45000.0,
  "surgeMultiplier": 1.0,
  "totalFee": 545000.0,
  "currency": "VND"
}
```

---

### 4. DỊCH VỤ ĐẶT LỊCH & VÒNG ĐỜI ĐƠN HÀNG (`booking-service`)

#### 4.1. Khách hàng tạo yêu cầu đặt ca trang điểm (Create & Match Booking)
- **Method & URL**: `POST /api/v1/bookings/request`
- **Auth**: Bearer Token (`ROLE_CUSTOMER`)
- **Request Body**:
```json
{
  "customerId": 105,
  "customerName": "Nguyễn Văn A",
  "customerPhone": "0901234567",
  "muaId": 7, // ID thợ chỉ định
  "muaName": "Trần Nhã Phương",
  "servicePackageId": 1,
  "serviceCategory": "BRIDAL",
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "basePackageFee": 200000.0,
  "customerLat": 20.990445,
  "customerLng": 105.801834,
  "address": "123 Đường ABC, Quận 1, TP.HCM",
  "notes": "Trang điểm tone Hàn Quốc nhẹ nhàng"
}
```
- **Response Data (`data`)**:
```json
{
  "id": "bk-uuid-8899-1234",
  "bookingCode": "BK-1770871234",
  "customerId": 105,
  "customerName": "Nguyễn Văn A",
  "muaId": 7,
  "muaName": "Trần Nhã Phương",
  "servicePackageId": 1,
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "customerLat": 20.990445,
  "customerLng": 105.801834,
  "address": "123 Đường ABC, Quận 1, TP.HCM",
  "basePrice": 200000.0,
  "movingDistanceKm": 4.5,
  "movingFee": 45000.0,
  "surgeMultiplier": 1.0,
  "totalFee": 545000.0,
  "status": "MATCHING", // Đang chờ Thợ nhận ca
  "createdAt": "2026-08-13T10:00:00Z"
}
```

#### 4.2. Thợ Makeup chấp nhận ca (Accept Booking)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/accept`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` với status = `"ACCEPTED"`.

#### 4.3. Thợ Makeup từ chối ca (Reject Booking)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/reject`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` (Tìm ứng viên thợ tiếp theo hoặc hủy đơn).

#### 4.4. Thợ bấm "Bắt đầu di chuyển" (Start Moving)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/start-moving`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` với status = `"MUA_MOVING"`.

#### 4.5. Thợ bấm "Đã đến nhà khách" (Arrived)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/arrived`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` với status = `"ARRIVED"`.

#### 4.6. Thợ bấm "Bắt đầu trang điểm" (Start Makeup)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/start-makeup`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` với status = `"MAKING_UP"`.

#### 4.7. Thợ bấm "Hoàn thành ca trang điểm" (Complete)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/complete`
- **Auth**: Bearer Token (`ROLE_MUA`)
- **Response Data (`data`)**: `BookingResponseDto` với status = `"COMPLETED"`.

#### 4.8. Hủy đơn đặt lịch (Cancel Booking)
- **Method & URL**: `POST /api/v1/bookings/{bookingId}/cancel`
- **Auth**: Bearer Token (`ROLE_CUSTOMER`, `ROLE_MUA`)
- **Query Params**: `reason` (Lý do hủy đơn - Mặc định: "User requested cancellation")
- **Response Data (`data`)**: `BookingResponseDto` với status = `"CANCELLED"`.

#### 4.9. Admin Override chuyển trạng thái đơn (State Machine Override)
- **Method & URL**: `PUT /api/v1/bookings/{bookingId}/status`
- **Auth**: Bearer Token (`ROLE_ADMIN`)
- **Query Params**: `targetStatus` (Ví dụ: `COMPLETED`, `CANCELLED`, `REFUNDED`)

---

### 5. DỊCH VỤ THANH TOÁN & VÍ TIỀN (`payment-service`)

#### 5.1. Xem số dư ví tiền hiện tại
- **Method & URL**: `GET /api/v1/wallets/me/balance` (hoặc `GET /api/v1/wallets/{userId}/balance`)
- **Auth**: Bearer Token (`ROLE_CUSTOMER`, `ROLE_MUA`)
- **Response Data (`data`)**:
```json
{
  "userId": "105",
  "balance": 1250000.0,
  "currency": "VND"
}
```

#### 5.2. Thực hiện thanh toán đơn hàng (E-Wallet / VNPay / Cash)
- **Method & URL**: `POST /api/v1/payments/process`
- **Auth**: Bearer Token (`ROLE_CUSTOMER`)
- **Request Body**:
```json
{
  "bookingId": "bk-uuid-8899-1234",
  "customerId": "105",
  "muaId": "7",
  "amount": 545000.0,
  "paymentMethod": "E_WALLET" // "E_WALLET", "CASH", "VNPAY"
}
```
- **Response Data (`data`)**:
```json
{
  "transactionId": "d8f4e2a1-7c9b-4b1a-8e2d-3f5a1c9b2e4f",
  "bookingId": "bk-uuid-8899-1234",
  "customerId": "105",
  "muaId": "7",
  "amount": 545000.0,
  "paymentMethod": "E_WALLET",
  "status": "SUCCESS",
  "timestamp": 1770871200000
}
```

#### 5.3. Xem chi tiết giao dịch thanh toán
- **Method & URL**: `GET /api/v1/payments/{transactionId}`

#### 5.4. Trừ phí hoa hồng nền tảng từ ví Thợ (Platform Commission Deduction)
- **Method & URL**: `POST /api/v1/wallets/{userId}/deduct-commission`
- **Auth**: Internal Service / Bearer Token (`ROLE_ADMIN`)
- **Query Params**:
  - `bookingAmount`: `545000.0`
  - `commissionRate`: `0.15` (15% chiết khấu sàn)
- **Response Data (`data`)**:
```json
{
  "userId": "7",
  "bookingAmount": 545000.0,
  "commissionFee": 81750.0,
  "netAmountCredited": 463250.0,
  "currency": "VND",
  "status": "PROCESSED"
}
```

---

## III. DỊCH VỤ REAL-TIME WEBSOCKET STOMP BROKER (`/ws-location`)

- **WebSocket Connection Point**: `http://localhost:8080/ws-location` (SockJS + STOMP Client)
- **Header Auth**: `Authorization: Bearer <accessToken>`

### 1. Thợ Push Stream GPS Tọa Độ Ngầm (Client -> Broker)
- **Destination**: `/app/location/stream`
- **Payload Structure**:
```json
{
  "workerId": 7,
  "latitude": 20.990445,
  "longitude": 105.801834,
  "status": "ONLINE",
  "bookingId": null,
  "timestamp": 1770870000000
}
```

### 2. Thợ Lắng Nghe Ca Đặt Mới Khẩn Cấp (Broker -> Thợ Client)
- **Subscription Topic**: `/topic/worker/{workerId}` hoặc `/topic/bookings/{workerId}`
- **Notification Payload**:
```json
{
  "bookingId": "bk-uuid-8899-1234",
  "bookingCode": "BK-1770871234",
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "customerName": "Nguyễn Văn A",
  "customerPhone": "0901234567",
  "address": "123 Đường ABC, Quận 1, TP.HCM",
  "totalPrice": 545000.0,
  "notes": "Trang điểm tone Hàn Quốc nhẹ nhàng"
}
```

---

## IV. BẢN ĐỒ TÍCH HỢP MÀN HÌNH FRONTEND (UI SPECIFICATIONS)

---

### 📱 NHÓM MÀN HÌNH KHÁCH HÀNG (CUSTOMER APP)

#### Màn hình C1: Đăng nhập & Đăng ký Auth
- **Giao diện**: Form chọn SĐT, Mật khẩu, OTP, Vai trò (`Khách hàng` / `Thợ Makeup`).
- **APIs**:
  - Gửi OTP: `POST /api/v1/auth/send-otp`
  - Đăng ký: `POST /api/v1/auth/register`
  - Đăng nhập: `POST /api/v1/auth/login`

#### Màn hình C2: Bản đồ Quét Thợ Lân Cận (Nearby Map View)
- **Giao diện**: Bản đồ vị trí GPS + Render Marker thợ rảnh xung quanh bán kính X km + Card thông tin thợ (Tên, Ảnh, Rating, Số ca, Khoảng cách).
- **API**: `GET /api/v1/workers/nearby?latitude=...&longitude=...&radiusKm=5.0`

#### Màn hình C3: Hồ sơ Chi tiết Thợ & Tùy chỉnh Options (MUA Profile Detail & Options Modal)
- **Giao diện**: Banner Bio thợ, Rating, Tab Portfolio và Tab Danh sách Gói dịch vụ. Bấm vào gói bật **Modal Tùy Chỉnh Options (Checkboxes)**.
- **APIs**:
  - Lấy Profile: `GET /api/v1/mua/{muaId}/profile`
  - Lấy Gói Lắp Ghép: `GET /api/v1/mua/{muaId}/bundle-services`

#### Màn hình C4: Xác nhận Đặt lịch & Thanh toán (Booking Checkout)
- **Giao diện**: Nhập địa chỉ, ngày giờ hẹn, ghi chú tone makeup, chọn Ví/Tiền mặt, bảng Phân rã giá (Tiền công + Phụ phí options + Phí di chuyển).
- **APIs**:
  - Tính giá: `POST /api/v1/pricing/calculate`
  - Tạo đơn: `POST /api/v1/bookings/request`
  - Thanh toán: `POST /api/v1/payments/process`

#### Màn hình C5: Theo dõi Tiến độ Ca làm đẹp (Order Live Tracking)
- **Giao diện**: Stepper vòng đời ca (`MATCHING` ➔ `ACCEPTED` ➔ `MUA_MOVING` ➔ `ARRIVED` ➔ `MAKING_UP` ➔ `COMPLETED`).
- **APIs**:
  - Hủy đơn: `POST /api/v1/bookings/{id}/cancel`

---

### 💄 NHÓM MÀN HÌNH THỢ MAKEUP (WORKER MUA APP)

#### Màn hình M1: Bảng Điều Khiển Công Việc (MUA Workbench Dashboard)
- **Giao diện**: Công tắc **ONLINE/OFFLINE**. Bật ONLINE: stream GPS ngầm và lắng nghe ca mới qua STOMP WebSocket `/topic/worker/{id}`. Popup nhận ca khẩn cấp 30s.
- **APIs**:
  - Tắt ONLINE (Xóa GPS Redis GEO): `DELETE /api/v1/workers/location?workerId=7`
  - Cập nhật trạng thái Profile: `PUT /api/v1/mua/{muaId}/profile`
  - Chấp nhận ca: `POST /api/v1/bookings/{id}/accept`
  - Từ chối ca: `POST /api/v1/bookings/{id}/reject`

#### Màn hình M2: Điều hành Tiến độ Ca làm việc (Job Lifecycle Stepper)
- **Giao diện**: 4 Nút chuyển trạng thái theo đúng quy trình:
  1. `[Bắt đầu di chuyển]` ➔ `POST /api/v1/bookings/{id}/start-moving`
  2. `[Đã đến nhà khách]` ➔ `POST /api/v1/bookings/{id}/arrived`
  3. `[Bắt đầu trang điểm]` ➔ `POST /api/v1/bookings/{id}/start-makeup`
  4. `[Hoàn thành ca]` ➔ `POST /api/v1/bookings/{id}/complete`
- **Stream vị trí ngầm**: `POST /api/v1/location/stream` hoặc `/app/location/stream`

#### Màn hình M3: Quản lý Gói Dịch Vụ Lắp Ghép & Portfolio (Bundle Services & Portfolio CRUD)
- **Giao diện**:
  - Form Tạo mới/Chỉnh sửa Gói Lắp Ghép Động 3 Tầng (Master Category, Tiền công `basePrice`, Components, Add-ons) + Hộp Preview giá mặc định.
  - Công tắc Bật/Tắt tạm ẩn gói dịch vụ (`PATCH /toggle-status`).
  - Icon Xóa vĩnh viễn (`DELETE ?permanent=true`).
  - Thư viện ảnh Portfolio (Upload & Xóa ảnh).
- **APIs**:
  - Tạo gói dynamic: `POST /api/v1/mua/{muaId}/bundle-services`
  - Sửa gói dynamic: `PUT /api/v1/mua/{muaId}/bundle-services/{serviceId}`
  - Đổi trạng thái: `PATCH /api/v1/mua/{muaId}/services/{serviceId}/toggle-status`
  - Xóa vĩnh viễn: `DELETE /api/v1/mua/{muaId}/services/{serviceId}?permanent=true`
  - Upload Portfolio: `POST /api/v1/mua/{muaId}/portfolio`
