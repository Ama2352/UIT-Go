# ADR 002: Chọn RESTful thay vì gRPC

## 🎯 Bối cảnh

UIT-Go là ứng dụng đặt xe, kết nối hành khách với tài xế gần đó. Giai đoạn 1 xây dựng nền tảng với **3 microservice cơ bản**: UserService, TripService và DriverService. Các service cần giao tiếp với nhau một cách tin cậy để xử lý các nghiệp vụ cơ bản như đăng ký, đặt chuyến, cập nhật vị trí tài xế và quản lý trạng thái chuyến đi.

Nhóm cân nhắc hai giải pháp giao tiếp giữa các service: **RESTful API (HTTP + JSON)** và **gRPC (HTTP/2 + Protobuf)**.

---

## 🤔 Quyết định

**Nhóm quyết định sử dụng RESTful API cho tất cả các microservice của UIT-Go.**

---

## 💡 Lý do

### 1. Tương thích frontend

- REST dễ tiêu thụ cho ReactJS và mobile client
- Không cần generator code phức tạp như gRPC

### 2. Dễ phát triển, debug và test

- Test trực tiếp bằng Postman, curl, trình duyệt
- Triển khai và debug nhanh, phù hợp MVP

### 3. Hỗ trợ caching & monitoring

- REST tận dụng HTTP caching, API Gateway và load balancer
- Logging và metrics dễ tích hợp hơn gRPC

### 4. Độ trễ và performance

- REST đủ đáp ứng MVP với độ trễ <50ms
- gRPC nhanh hơn nhưng complexity không cần thiết giai đoạn này

---

### ⚖️ Trade-offs

| Tiêu chí             | REST              | gRPC                     |
| -------------------- | ----------------- | ------------------------ |
| Tương thích frontend | ✅ Rất tốt        | ⚠️ Cần thư viện/protobuf |
| Dễ debug / test      | ✅ Rất dễ         | ⚠️ Khó hơn               |
| Performance          | ⚠️ Chấp nhận được | ✅ Tốt hơn               |
| HTTP caching         | ✅ Hỗ trợ         | ⚠️ Không trực tiếp       |
| Learning curve       | ✅ Thấp           | ⚠️ Cao hơn               |

---

## ✅ Kết luận

RESTful API là lựa chọn tối ưu cho **Giai đoạn 1 (MVP)** của UIT-Go vì:

- ✅ Triển khai nhanh, dễ debug và test
- ✅ Dễ tích hợp với frontend
- ✅ Hỗ trợ caching, monitoring và hạ tầng sẵn có

**Tương lai:** Khi hệ thống phát triển hoặc cần tối ưu performance, một số service có thể migrate sang gRPC.
