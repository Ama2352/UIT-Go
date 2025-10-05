# ADR 001: Chọn Redis thay vì DynamoDB cho lưu trữ dữ liệu định vị tài xế

## 🎯 Bối cảnh

DriverService là microservice quan trọng trong hệ thống UIT-Go, chịu trách nhiệm quản lý trạng thái và vị trí tài xế theo thời gian thực. Các yêu cầu chính gồm:

- Cập nhật vị trí tài xế liên tục (5-10 giây/lần)
- Tìm kiếm tài xế trong bán kính nhất định từ hành khách
- Đảm bảo độ trễ thấp để trải nghiệm mượt mà
- Xử lý hàng nghìn tài xế đồng thời vào giờ cao điểm

Hai hướng tiếp cận được cân nhắc:

1. **Redis (ElastiCache)** – Ưu tiên tốc độ, real-time
2. **DynamoDB với Geohashing** – Ưu tiên khả năng mở rộng và chi phí

---

## 🤔 Quyết định

**Nhóm quyết định sử dụng Redis (AWS ElastiCache) với tính năng Geospatial cho DriverService.**

---

## 💡 Lý do

### 1. Hiệu năng & độ trễ cực thấp

Redis cung cấp độ trễ sub-millisecond, phù hợp cho:

- Cập nhật vị trí real-time của tài xế
- Tìm kiếm tài xế gần hành khách (<100ms)
- Tracking vị trí trên bản đồ cho hành khách

**So sánh:**

| Công nghệ | Độ trễ read/write                 |
| --------- | --------------------------------- |
| Redis     | <1ms                              |
| DynamoDB  | 5-10ms (có thể cao hơn khi write) |

---

### 2. Hỗ trợ Geospatial tích hợp sẵn

Redis cung cấp các lệnh GEO:

- **GEOADD**: thêm vị trí
- **GEORADIUS / GEODIST**: tìm kiếm & sắp xếp theo khoảng cách

Trong khi đó, DynamoDB cần triển khai thư viện/geohash và nhiều logic phức tạp hơn.

---

### 3. Thích hợp cho môi trường local và MVP

- Redis chạy nhanh bằng Docker, hỗ trợ offline và CI
- DynamoDB yêu cầu AWS setup hoặc giả lập, phức tạp hơn

---

### 4. Chi phí & time-to-market

- Redis (self-hosted hoặc ElastiCache nhỏ) rẻ và triển khai nhanh
- Giảm độ phức tạp hạ tầng, team tập trung vào business logic

---

### ⚖️ Trade-offs

- **Persistence:** Redis là in-memory, cần bật RDB/AOF nếu muốn giảm rủi ro mất dữ liệu. Tuy nhiên, vị trí tài xế là dữ liệu tạm thời.
- **Mở rộng:** DynamoDB scale tốt hơn; nếu số lượng tài xế tăng lên hàng chục nghìn, có thể dùng hybrid: Redis cho hot data, DynamoDB cho historical data.

---

## ✅ Kết luận

Redis với Geospatial là lựa chọn tối ưu cho DriverService ở **Giai đoạn 1** vì:

- ✅ Đáp ứng real-time tracking
- ✅ API đơn giản, dễ implement
- ✅ Chi phí hợp lý cho MVP
- ✅ Giảm time-to-market
- ✅ Team tập trung vào business logic

**Tương lai:** Khi hệ thống phát triển và có dữ liệu usage thực tế, nhóm sẽ đánh giá lại và cân nhắc hybrid hoặc migrate sang DynamoDB nếu cần.
