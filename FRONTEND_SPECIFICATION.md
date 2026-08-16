# ĐẶC TẢ TÍNH NĂNG & HƯỚNG DẪN TÍCH HỢP FRONTEND (MAKEUP PLATFORM)

Tài liệu này hướng dẫn chi tiết các luồng giao diện (UI/UX), trạng thái màn hình (State), các API cần gọi và Code mẫu (Axios/Fetch) cho Lập trình viên Frontend (Customer App & Worker MUA App).

---

## I. KIẾN TRÚC & HẰNG SỐ CHUNG

- **Base URL API Gateway**: `http://localhost:8080` (hoặc `http://localhost:8082` trực tiếp cho Location Service, `http://localhost:8081` cho User Service, `http://localhost:8083` cho Booking Service).
- **Header Xác thực**: `Authorization: Bearer <accessToken>`
- **Response Structure (Cấu trúc trả về chuẩn)**:
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thông báo kết quả",
  "data": { ... }
}
```

---

## II. MÀN HÌNH & LUỒNG TÍNH NĂNG KHÁCH HÀNG (CUSTOMER APP)

### 1. Màn hình Bản đồ Quét Thợ lân cận (Explore / Nearby Map View)

#### 🎨 Giao diện & Trạng thái (UI State)
- Hiển thị Bản đồ (Mapbox / Google Maps) tại vị trí GPS hiện tại của Khách hàng.
- Render danh sách các Marker thợ trang điểm xung quanh bán kính X km.
- Bên dưới là danh sách Card thông tin thợ (Tên, Ảnh đại diện, Rating, Số ca đã hoàn thành, Trạng thái `ONLINE`/`BUSY`, Khoảng cách `distanceKm`).

#### 📡 API 1.1: Quét danh sách thợ gần nhất
- **Method & Endpoint**: `GET /api/v1/workers/nearby`
- **Params**:
  - `latitude`: Vĩ độ GPS khách (VD: `10.776889`)
  - `longitude`: Kinh độ GPS khách (VD: `106.700806`)
  - `radiusKm`: Bán kính tìm kiếm (Mặc định: `5.0`)

**Response mẫu (`data`)**:
```json
[
  {
    "workerId": 7,
    "fullName": "Nguyễn Văn A",
    "avatarUrl": "https://cdn.makeupapp.com/avatar/img7.jpg",
    "rating": 4.9,
    "totalCompletedJobs": 45,
    "currentStatus": "ONLINE",
    "latitude": 20.990445416837723,
    "longitude": 105.8018347620964,
    "distanceKm": 0.01,
    "services": [
      {
        "id": 1,
        "category": "BRIDAL",
        "serviceName": "Makeup Cô Dâu Tiệc Đêm",
        "description": "Bao gồm trang điểm phong cách Hàn Quốc và làm tóc đi tiệc",
        "basePrice": 1500000.0,
        "estimatedDurationMinutes": 90,
        "isActive": true
      }
    ]
  }
]
```

---

### 2. Màn hình Chi tiết Hồ sơ Thợ (MUA Profile Detail)

#### 🎨 Giao diện (UI)
- Nút "Xem Profile" khi bấm vào 1 Thợ trên bản đồ hoặc danh sách.
- Hiển thị: Avatar, Tên, Rating, Bio mô tả, Các gói dịch vụ làm đẹp (Tên dịch vụ, giá tiền, thời gian ước tính), Bộ sưu tập ảnh sản phẩm (Portfolio).

#### 📡 API 1.2: Lấy thông tin Hồ sơ thợ
- **Method & Endpoint**: `GET /api/v1/mua/{muaId}/profile`
- **Response mẫu (`data`)**:
```json
{
  "userId": 7,
  "fullName": "Nguyễn Văn A",
  "phone": "0901234567",
  "avatarUrl": "https://cdn.makeupapp.com/avatar/img7.jpg",
  "bio": "Chuyên gia trang điểm cô dâu 5 năm kinh nghiệm",
  "rating": 4.9,
  "totalCompletedJobs": 45,
  "currentStatus": "ONLINE",
  "services": [
    {
      "id": 1,
      "category": "WEDDING",
      "serviceName": "Makeup Cô Dâu Tiệc Đêm",
      "basePrice": 1500000.0,
      "estimatedDurationMinutes": 90
    }
  ]
}
```

---

### 3. Màn hình Xác nhận Đặt lịch & Phí dịch vụ (Booking Checkout)

#### 🎨 Giao diện & Luồng
- Chọn gói dịch vụ của thợ -> Nhập địa chỉ makeup -> Gọi API tính giá -> Bấm nút **"Đặt lịch ngay"**.

#### 📡 API 1.3: Tính phí dịch vụ & Phí di chuyển
- **Method & Endpoint**: `POST /api/v1/pricing/calculate`
- **Request Body**:
```json
{
  "servicePackageId": "1",
  "basePackageFee": 200000.0, // Tiền công cốt lõi
  "optionsFee": 300000.0,     // Tổng phụ phí từ các options/add-ons được chọn
  "distanceInKm": 4.5,        // Quãng đường GPS thực tế
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

#### 📡 API 1.4: Khách gửi yêu cầu Đặt ca trang điểm (Chọn đích danh Thợ)
- **Method & Endpoint**: `POST /api/v1/bookings/request`
- **Request Body**:
```json
{
  "muaId": 7, // ID của thợ khách đã chọn
  "servicePackageId": 1,
  "serviceName": "Makeup Cô Dâu Tiệc Đêm",
  "basePackageFee": 1500000.0,
  "customerLat": 20.990445,
  "customerLng": 105.801834,
  "address": "123 Đường ABC, Quận 1, TP.HCM",
  "notes": "Trang điểm tone Hàn Quốc nhẹ nhàng"
}
```
- **Response Data (`data`)**:
```json
{
  "bookingId": "bk-uuid-8899-1234",
  "bookingCode": "BK-1770871234",
  "customerId": 105,
  "muaId": 7,
  "status": "MATCHING", // Đang chờ Thợ phản hồi chấp nhận
  "totalPrice": 1515000.0
}
```

---

## III. MÀN HÌNH & LUỒNG TÍNH NĂNG THỢ MAKEUP (WORKER MUA APP)

### 1. Màn hình Bật / Tắt Hoạt Động & Định vị GPS (Work Status Toggle)

#### 🎨 Giao diện & Hành vi (UI UX Behavior)
- Công tắc **"Đang hoạt động" (Switch ONLINE / OFFLINE)** ở trang chủ Thợ.
- **Khi Bật Hoạt Động (`ONLINE`)**:
  - Gửi vị trí GPS thời gian thực (Mỗi 5-10s/lần) lên hệ thống.
  - Gọi API `PUT /api/v1/mua/{muaId}/profile` cập nhật `currentStatus = 'ONLINE'`.
- **Khi Tắt Hoạt Động (`OFFLINE`)**:
  - Dừng gửi GPS background.
  - **Bắt buộc gọi API xóa định vị khỏi GPS/Redis GEO**: `DELETE /api/v1/workers/location`.
  - Gọi API `PUT /api/v1/mua/{muaId}/profile` cập nhật `currentStatus = 'OFFLINE'`.

#### 📡 API 2.1: Thợ push stream tọa độ GPS (Khi Bật hoạt động)
- **Method & Endpoint**: `POST /api/v1/location/stream`
- **Request Body**:
```json
{
  "latitude": 20.990445,
  "longitude": 105.801834,
  "bookingId": null, // Nếu đang đi làm ca thì truyền bookingId vào đây
  "timestamp": 1770870000000
}
```

#### 📡 API 2.2: Thợ Tắt hoạt động -> Xóa định vị GPS khỏi Redis
- **Method & Endpoint**: `DELETE /api/v1/workers/location`
- **Query Param (Optional)**: `workerId=7`
- **Response Data**: `null` (Message: `"Xóa vị trí GPS của thợ thành công"`)

#### 📡 API 2.3: Cập nhật Trạng thái Thợ (ONLINE / OFFLINE)
- **Method & Endpoint**: `PUT /api/v1/mua/{muaId}/profile`
- **Request Body**:
```json
{
  "currentStatus": "OFFLINE" // Hoặc "ONLINE"
}
```

---

### 2. Màn hình Nhận Ca & Xem Chi Tiết Đơn Đặt Lịch (Incoming Booking Notification)

#### 🎨 Giao diện & Hành vi (UI UX)
- Khi có đơn hàng từ Khách chọn thợ, App Thợ bật Dialog Popup thông báo ca mới kèm Đếm ngược 30 giây:
  - Thông tin Khách hàng, Địa chỉ làm đẹp, Tên dịch vụ.
  - Phí dịch vụ & Phí di chuyển -> **Tổng thu nhập ca (`totalPrice`)**.
  - **Nút "Chấp nhận ca" (Accept)** (Màu xanh).
  - **Nút "Từ chối ca" (Reject)** (Màu đỏ).

#### 📡 API 3.1: Thợ Chấp Nhận Ca (Accept Fee & Request)
- **Method & Endpoint**: `POST /api/v1/bookings/{bookingId}/accept`
- **Response Data (`data`)**:
```json
{
  "bookingId": "bk-uuid-8899-1234",
  "status": "ACCEPTED",
  "message": "Booking accepted by MUA"
}
```

#### 📡 API 3.2: Thợ Từ Chối Ca (Reject Booking)
- **Method & Endpoint**: `POST /api/v1/bookings/{bookingId}/reject`
- **Response Data (`data`)**:
```json
{
  "bookingId": "bk-uuid-8899-1234",
  "status": "CANCELLED", // Hoặc chuyển thợ khác
  "message": "Booking rejected by MUA"
}
```

---

### 3. Màn hình Điều hành & Tiến độ Ca làm đẹp (Order Progress Screen)

Sau khi Thợ Chấp nhận ca, giao diện chuyển sang màn hình Theo dõi tiến độ công việc với các nút bấm chuyển trạng thái:

```
[Bắt đầu di chuyển] ➔ [Đã đến nơi] ➔ [Bắt đầu Makeup] ➔ [Hoàn thành ca]
```

#### 📡 API 3.3: Thợ bấm "Bắt đầu di chuyển"
- `POST /api/v1/bookings/{bookingId}/start-moving` ➔ Status: `MUA_MOVING`

#### 📡 API 3.4: Thợ bấm "Đã đến nhà khách"
- `POST /api/v1/bookings/{bookingId}/arrived` ➔ Status: `ARRIVED`

#### 📡 API 3.5: Thợ bấm "Bắt đầu làm đẹp"
- `POST /api/v1/bookings/{bookingId}/start-makeup` ➔ Status: `MAKING_UP`

#### 📡 API 3.6: Thợ bấm "Hoàn thành ca trang điểm"
- `POST /api/v1/bookings/{bookingId}/complete` ➔ Status: `COMPLETED`

#### 📡 API 3.7: Hủy ca khẩn cấp (Nếu có sự cố)
- `POST /api/v1/bookings/{bookingId}/cancel?reason=Khách không có nhà` ➔ Status: `CANCELLED`

---

## IV. MÀN HÌNH & LUỒNG QUẢN LÝ GÓI DỊCH VỤ LẮP GHÉP ĐỘNG (DYNAMIC BUNDLE & OPTIONS CRUD)

### 1. Phân cấp 3 Tầng & Cấu trúc Dữ liệu Động (Dynamic Schema)

Hệ thống quản lý dịch vụ theo phân cấp 3 tầng rõ ràng:
- **Tầng 1: Master Category** (Admin quy định: VD `Makeup`, `Làm Tóc`, `Nail`).
- **Tầng 2: Base Service** (Dịch vụ chuẩn sàn: VD `Makeup Cô Dâu`, `Makeup Đi Tiệc`).
- **Tầng 3: Options** (Do Thợ tự thiết lập linh hoạt theo từng gói):
  - `COMPONENT` (Thành phần mặc định trong gói. `is_default: true`. Bỏ đi sẽ trừ tiền nếu `is_removable: true`, không cho bỏ nếu `is_removable: false`).
  - `ADD_ON` (Dịch vụ bán thêm. `is_default: false`. Mặc định không chọn. Khách chọn thêm sẽ cộng tiền).

#### 📊 Bảng Cấu trúc Option dữ liệu động (`options`):

| Field Name | Kiểu | Mô tả & Quy tắc UI |
|---|---|---|
| `optionType` | `String` | `'COMPONENT'` (Thành phần) hoặc `'ADD_ON'` (Bán thêm) |
| `optionName` | `String` | Tên bước/dịch vụ (VD: "Đánh Kem Nền", "Che Khuyết Điểm", "Làm Tóc Cô Dâu") |
| `price` | `Number` | Số tiền (VNĐ). Khách chọn sẽ cộng/bỏ chọn sẽ trừ |
| `isDefault` | `Boolean` | `true`: Mặc định được tick / `false`: Mặc định không tick |
| `isRemovable` | `Boolean` | `true`: Cho phép khách bỏ tick / `false`: Bắt buộc, ô checkbox bị mờ đi không cho bỏ |

---

### 2. Luồng Thiết lập Gói phía Thợ (Provider Setup & Preview)

#### 🟢 Nút "+ Tạo gói dịch vụ mới (Bundle Dynamic)"
1. **Chọn dịch vụ chuẩn**: Dropdown gọi `GET /api/v1/master-services` (VD: chọn *Makeup Cô Dâu*).
2. **Nhập Tiền công cốt lõi (`basePrice`)**: Ô nhập số (VD: `200.000đ` - Đây là tiền công không thể bỏ).
3. **Thêm các Bước mặc định (`COMPONENTS`)**:
   - Bấm "+ Thêm bước mặc định":
     - Tên bước: *"Đánh Kem Nền"* | Giá: `100.000đ` | Allow Removable: `YES`
     - Tên bước: *"Che Khuyết Điểm"* | Giá: `100.000đ` | Allow Removable: `NO` (Bắt buộc)
     - Tên bước: *"Phấn Phủ"* | Giá: `100.000đ` | Allow Removable: `YES`
4. **Thêm Dịch vụ bán kèm (`ADD-ONS`)**:
   - Bấm "+ Thêm dịch vụ bán kèm":
     - Tên dịch vụ: *"Làm Tóc Cô Dâu"* | Giá: `200.000đ`
5. **Giao diện App Thợ tự động Preview (Xem trước)**:
   - *"Tổng giá gói hiển thị mặc định cho khách là: **500.000đ**"* (`200k basePrice + 100k + 100k + 100k`).
6. **API Gọi**: `POST /api/v1/mua/{muaId}/bundle-services`

**Request Body Mẫu**:
```json
{
  "masterServiceId": 1,
  "category": "Makeup",
  "serviceName": "Gói Makeup Cô Dâu Cao Cấp",
  "basePrice": 200000.0,
  "estimatedDurationMinutes": 90,
  "description": "Sử dụng mỹ phẩm cao cấp MAC, Dior",
  "options": [
    { "optionType": "COMPONENT", "optionName": "Đánh Kem Nền", "price": 100000.0, "isDefault": true, "isRemovable": true },
    { "optionType": "COMPONENT", "optionName": "Che Khuyết Điểm", "price": 100000.0, "isDefault": true, "isRemovable": false },
    { "optionType": "COMPONENT", "optionName": "Phấn Phủ", "price": 100000.0, "isDefault": true, "isRemovable": true },
    { "optionType": "ADD_ON", "optionName": "Làm Tóc Cô Dâu", "price": 200000.0, "isDefault": false, "isRemovable": true }
  ]
}
```

---

### 3. Luồng Khách hàng Đặt lịch & Tương tác Tính Giá Thời gian thực (Real-time Pricing)

Khách click vào Gói *"Makeup Cô Dâu Cao Cấp"* (Giá mặc định hiển thị: `500.000đ`).

Giao diện hiển thị danh sách Checkbox:
- [x] Đánh Kem Nền (+100.000đ)
- [x] **Che Khuyết Điểm** (+100.000đ) *(Ô checkbox mờ đi, không thể bỏ tick vì `isRemovable = false`)*
- [x] Phấn Phủ (+100.000đ)
- [ ] **Làm Tóc Cô Dâu** (+200.000đ)

#### 🧮 Công thức tính tiền thời gian thực trên Frontend:
```javascript
const finalPrice = basePrice + selectedOptions.reduce((sum, opt) => sum + opt.price, 0);
```

- **Khách HỦY tick ô "Phấn Phủ"**: Hệ thống tính `200k (base) + 100k (Nền) + 100k (Che KD) = 400.000đ`.
- **Khách TICK chọn "Làm Tóc Cô Dâu"**: Hệ thống tính `400k + 200k (Làm tóc) = 600.000đ`.
- **Khi Bấm Đặt lịch**: Truyền danh sách `selectedOptionIds: [1, 2, 4]` lên API đặt lịch để lưu thông tin chính xác báo cho Thợ chuẩn bị!

#### 🟡 C. Chỉnh sửa Gói dịch vụ (Update)
- **Giao diện**: Bấm icon ✏️ (Chỉnh sửa) trên card dịch vụ ➔ Mở Popup Form điền sẵn dữ liệu cũ.
- **Hành vi**: Thợ chỉnh sửa các trường ➔ Bấm **"Cập nhật"** ➔ Gọi API `PUT`.
- *Lưu ý*: Thay đổi giá gói chỉ áp dụng cho các đơn đặt mới, không làm thay đổi các đơn hàng khách đã đặt từ trước.
- **API Endpoint**: `PUT /api/v1/mua/{muaId}/services/{serviceId}`
- **Request Body**: Tương tự form Tạo mới.

#### 🔴 D. Tạm ẩn / Bật lại dịch vụ (Soft Delete / Toggle Active)
- **Giao diện**: Công tắc Toggle Switch (Bật/Tắt) ở từng Card dịch vụ.
- **Hành vi**:
  - Gạt công tắc **Tắt** ➔ Tạm ẩn dịch vụ khỏi giao diện đặt lịch của Khách hàng, nhưng lịch sử đơn hàng cũ vẫn giữ nguyên.
  - Gạt công tắc **Bật** ➔ Dịch vụ xuất hiện trở lại cho khách đặt.
- **API Endpoint**: `PATCH /api/v1/mua/{muaId}/services/{serviceId}/toggle-status?isActive=false`

#### 🔴 E. Xóa vĩnh viễn Gói dịch vụ (Hard Delete)
- **Giao diện**: Icon 🗑️ (Xóa) trên card dịch vụ.
- **Hành vi**: Bấm xóa ➔ Bật Dialog xác nhận: *"Bạn có chắc chắn muốn xóa gói [Tên dịch vụ] không? Hành động này không thể hoàn tác."*
  - **Xác nhận xóa**: Gọi `DELETE /api/v1/mua/{muaId}/services/{serviceId}?permanent=true` ➔ Xóa vĩnh viễn khỏi Database.
- **API Endpoint**: `DELETE /api/v1/mua/{muaId}/services/{serviceId}?permanent=true`

---

## V. CODE MẪU TÍCH HỢP AXIOS CHO LẬP TRÌNH VIÊN FRONTEND

### 1. Hàm tìm thợ lân cận (Customer App)
```javascript
import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/v1';

export const fetchNearbyWorkers = async (latitude, longitude, radiusKm = 5.0) => {
  try {
    const token = localStorage.getItem('accessToken');
    const response = await axios.get(`${API_BASE_URL}/workers/nearby`, {
      params: { latitude, longitude, radiusKm },
      headers: { Authorization: `Bearer ${token}` }
    });
    
    // Trả về danh sách thợ đầy đủ thông tin: tên, rating, totalCompletedJobs, avatarUrl...
    return response.data.data; 
  } catch (error) {
    console.error("Lỗi tìm kiếm thợ lân cận:", error);
    throw error;
  }
};
```

### 2. Hàm Thợ bấm "Tắt hoạt động" (Worker MUA App)
```javascript
export const toggleWorkerOffline = async (muaId) => {
  try {
    const token = localStorage.getItem('accessToken');
    
    // 1. Gọi xóa tọa độ GPS khỏi Redis GEO
    await axios.delete(`${API_BASE_URL}/workers/location`, {
      headers: { Authorization: `Bearer ${token}` }
    });

    // 2. Cập nhật trạng thái profile thành OFFLINE
    await axios.put(`${API_BASE_URL}/mua/${muaId}/profile`, 
      { currentStatus: 'OFFLINE' },
      { headers: { Authorization: `Bearer ${token}` } }
    );

    console.log("Thợ đã tắt hoạt động & xóa GPS thành công");
  } catch (error) {
    console.error("Lỗi khi tắt hoạt động:", error);
  }
};
```

### 3. Các hàm Quản lý Gói dịch vụ của Thợ (Worker MUA App - CRUD Services)
```javascript
// A. Lấy danh sách gói dịch vụ của Thợ (bao gồm cả gói ẩn nếu includeInactive = true)
export const getMuaServices = async (muaId, includeInactive = true) => {
  const token = localStorage.getItem('accessToken');
  const response = await axios.get(`${API_BASE_URL}/mua/${muaId}/services`, {
    params: { includeInactive },
    headers: { Authorization: `Bearer ${token}` }
  });
  return response.data.data;
};

// B. Tạo mới gói dịch vụ
export const createMuaService = async (muaId, serviceData) => {
  const token = localStorage.getItem('accessToken');
  const response = await axios.post(
    `${API_BASE_URL}/mua/${muaId}/services`,
    serviceData,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data.data;
};

// C. Cập nhật thông tin gói dịch vụ
export const updateMuaService = async (muaId, serviceId, serviceData) => {
  const token = localStorage.getItem('accessToken');
  const response = await axios.put(
    `${API_BASE_URL}/mua/${muaId}/services/${serviceId}`,
    serviceData,
    { headers: { Authorization: `Bearer ${token}` } }
  );
  return response.data.data;
};

// D. Tạm ẩn / Bật lại gói dịch vụ (Toggle Active Status)
export const toggleMuaServiceStatus = async (muaId, serviceId, isActive) => {
  const token = localStorage.getItem('accessToken');
  const response = await axios.patch(
    `${API_BASE_URL}/mua/${muaId}/services/${serviceId}/toggle-status`,
    null,
    {
      params: { isActive },
      headers: { Authorization: `Bearer ${token}` }
    }
  );
  return response.data.data;
};

// E. Xóa vĩnh viễn gói dịch vụ
export const deleteMuaService = async (muaId, serviceId, permanent = true) => {
  const token = localStorage.getItem('accessToken');
  const response = await axios.delete(
    `${API_BASE_URL}/mua/${muaId}/services/${serviceId}`,
    {
      params: { permanent },
      headers: { Authorization: `Bearer ${token}` }
    }
  );
  return response.data.data;
};
```

