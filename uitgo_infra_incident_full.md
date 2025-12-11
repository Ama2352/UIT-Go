# INFRASTRUCTURE INCIDENT REPORT & POST-MORTEM

**Dự án:** UIT Go (Microservices System)  
**Ngày báo cáo:** 11/12/2025  
**Trạng thái:** ✅ RESOLVED  
**Hạng mục:** Database Architecture, Docker Infrastructure  

---

## 1. Cơ sở lý thuyết: Read Replica là gì? (Technical Context)

Để hiểu rõ nguyên nhân sự cố, cần làm rõ kiến trúc Database Master–Slave (Primary–Replica) mà hệ thống đang sử dụng.

### 1.1. Định nghĩa

**Read Replica (Bản sao Đọc)** là một bản copy thời gian thực (real-time) hoặc gần thời gian thực của Database chính.

- **Primary Node (Master):**  
  Nơi duy nhất chấp nhận các yêu cầu làm thay đổi dữ liệu (`INSERT`, `UPDATE`, `DELETE`).

- **Replica Node (Slave):**  
  Chỉ chấp nhận các yêu cầu truy vấn dữ liệu (`SELECT`). Node này liên tục nhận các bản ghi nhật ký (**WAL Logs**) từ Master để cập nhật dữ liệu của chính nó sao cho giống hệt Master.

### 1.2. Luồng hoạt động chuẩn (Happy Path)

Trong kiến trúc Microservices của **UIT Go**:

- **Ghi (Write):**  
  Khi User đặt xe, Trip Service gửi lệnh `INSERT` vào **Primary DB**.

- **Đồng bộ (Sync):**  
  Primary DB tự động đẩy dữ liệu đó sang **Replica DB** thông qua cơ chế streaming replication.

- **Đọc (Read):**  
  Khi User xem lại lịch sử chuyến đi, Trip Service gửi lệnh `SELECT` vào **Replica DB** để giảm tải cho Primary.

---

## 2. Phân tích sự cố (Incident Analysis)

### 2.1. Lỗi chính: Replication Failure (Critical)

#### Triệu chứng

- Hệ thống cho phép **Tạo chuyến đi** thành công.  
- Khi thực hiện quy trình **Hoàn thành/Thông báo**, hệ thống trả về lỗi **HTTP 500** từ phía backend.  

**Log lỗi:**

```text
org.postgresql.util.PSQLException: ERROR: relation "trips" does not exist
```

#### Nguyên nhân sâu xa (Deep Dive Root Cause)

Sự cố xảy ra do sự hiểu nhầm về cách hoạt động của Docker Image `postgres:15-alpine`.

- **Cấu hình sai:**  
  File `docker-compose.yml` cũ khai báo các biến môi trường liên quan tới Replication (như `POSTGRES_REPLICATION_MODE`, v.v.) cho image gốc `postgres:15-alpine`.

- **Cơ chế của image gốc:**  
  Image `postgres:15-alpine` **không có** các script tự động xử lý những biến môi trường này. Nó chỉ hiểu đây là một lần khởi động container Postgres bình thường, **không bật replication**.

##### Hậu quả – “Replica giả”

- Container **`trip-replica`** khởi động, thấy thư mục `data` **trống**.
- Nó tự động chạy `initdb` → Tạo ra một **database mới tinh, rỗng tuếch**.
- Nó hoạt động như một **Standalone Master** (một "ông chủ" độc lập) chứ **không phải Slave**.
- Nó không hề kết nối hay copy dữ liệu từ **`trip-postgres`** (master thực sự).

##### Tại sao lỗi `relation "trips" does not exist`?

Code backend (Trip Service) thực hiện logic tách luồng Đọc/Ghi (**Read/Write Split**):

- Lệnh `INSERT` (Tạo trip) → chạy vào **`trip-postgres`** (Primary) → **Thành công, bảng `trips` có dữ liệu**.
- Lệnh `SELECT` (Tìm trip để hoàn thành) → chạy vào **`trip-replica`**.
- Tại `trip-replica`, do là một database rỗng mới tạo → **không có bảng `trips`** → Postgres ném lỗi:

  ```text
  ERROR: relation "trips" does not exist
  ```

→ Đây là lý do trực tiếp dẫn tới HTTP 500 ở tầng ứng dụng.

---

### 2.2. Các lỗi hạ tầng khác (Infrastructure Bugs)

Ngoài lỗi Replication, quá trình audit phát hiện thêm các lỗi cấu hình tiềm ẩn rủi ro cao.

#### A. Lỗi Volume Mount (File vs Directory)

**Cấu hình lỗi:**

```yaml
volumes:
  - ./config/postgresql.conf:/etc/postgresql/postgresql.conf
```

**Rủi ro:**  
Docker có cơ chế: nếu đường dẫn trên máy host **chưa tồn tại**, Docker sẽ tự tạo nó dưới dạng một **THƯ MỤC**.

**Hậu quả:**  

- Nếu developer mới `git pull` code về và **chưa tạo file** `./config/postgresql.conf`:
  - Docker sẽ tạo **thư mục** `postgresql.conf` thay vì file.
  - Khi container Postgres khởi động, nó cố đọc file config tại `/etc/postgresql/postgresql.conf` nhưng lại gặp **một thư mục**.
  - Điều này gây lỗi và container Postgres có thể **crash ngay lập tức**.

---

#### B. Lỗi Race Condition (Healthcheck Missing)

**Vấn đề:**  

- Gateway (Kong) khởi động cùng lúc với Backend (User/Trip Service).
- Gateway mở cổng và bắt đầu nhận request **trước khi** Backend khởi động xong (Java Spring Boot thường mất ~15–30 giây để warm-up).

**Hậu quả:**  

- Trong khoảng thời gian 1 phút đầu tiên sau khi deploy, các request gửi vào Gateway sẽ gặp lỗi **`502 Bad Gateway`** do upstream (Backend) chưa sẵn sàng.

---

#### C. Lỗi Localstack Permission

**Vấn đề:**  

- Hệ thống mount socket Docker (`/var/run/docker.sock`) vào container Localstack để giả lập AWS Lambda.

**Hậu quả:**  

- Trên môi trường Linux/MacOS, user bên trong container **thường không có quyền** truy cập vào Docker socket của máy host.
- Điều này dẫn đến lỗi **`Permission Denied`** khi Localstack cố gắng tương tác với Docker daemon.

---

## 3. Giải pháp khắc phục (Resolution)

### 3.1. Khắc phục Replication (Fix triệt để)

**Chiến lược:**  
Chuyển đổi cách deploy database, đảm bảo cơ chế **Clone Data** được thực thi chuẩn xác khi khởi động.

**Phương án:**  

- Sử dụng **Bitnami PostgreSQL** (hoặc **Custom Script Injection**) để hỗ trợ replication chính thống.

**Cơ chế mới:**  

Khi container `trip-replica` khởi động:

1. Kiểm tra các biến môi trường liên quan (ví dụ: master host, replication user, v.v.).  
2. **XÓA** thư mục `data` hiện tại (nếu có).  
3. Chạy lệnh:

   ```bash
   pg_basebackup -h trip-postgres -D /bitnami/postgresql/data --progress --write-recovery-conf
   ```

   để copy toàn bộ dữ liệu từ Master về Replica.

4. Tạo file `standby.signal` để báo hiệu cho PostgreSQL biết:  
   > "Tao là Slave, hãy vào chế độ Read-Only".

→ Nhờ vậy, Replica **luôn** là bản sao chính xác của Master, không còn tình trạng “Replica giả, DB rỗng”.

---

### 3.2. Khắc phục lỗi Mount & Config

- **Bỏ mount file:** Loại bỏ việc mount `postgresql.conf` từ host vào container để tránh lỗi file/dir.
- **Dùng command:** Cấu hình các tham số cần thiết trực tiếp qua `command` của Docker Compose, ví dụ:

  ```yaml
  command: postgres -c wal_level=replica -c hot_standby=on -c max_connections=200
  ```

Điều này giúp cấu hình Postgres phục vụ replication mà **không phụ thuộc** vào file config bên ngoài (dễ thiếu / sai path).

---

### 3.3. Ổn định quy trình khởi động

- Thêm **healthcheck** vào toàn bộ service quan trọng: DB, Backend, RabbitMQ.
- Cập nhật `depends_on` với điều kiện `service_healthy`.

Điều này đảm bảo thứ tự khởi động như sau:

- DB **sống** → Backend mới chạy.
- Backend **sống** → Gateway mới chạy.

→ Loại bỏ phần lớn lỗi **502 Bad Gateway** do Backend chưa sẵn sàng khi Gateway đã nhận request.

---

## 4. Kiểm tra & Xác nhận (Verification)

Hệ thống đã được kiểm tra lại với kết quả tích cực.  

| Hạng mục kiểm tra | Lệnh thực thi / Hành động                 | Kết quả mong đợi                                          | Trạng thái |
|-------------------|-------------------------------------------|-----------------------------------------------------------|-----------|
| Master Status     | `SELECT * FROM pg_stat_replication;`     | Có dòng dữ liệu với `state = streaming`                  | ✅ PASS   |
| Replica Status    | `SELECT pg_is_in_recovery();`            | Trả về `t` (True)                                         | ✅ PASS   |
| Data Sync         | `INSERT` vào Master, `SELECT` tại Replica| Dữ liệu xuất hiện tại Replica gần như ngay lập tức       | ✅ PASS   |
| App Logic         | Flow: `Create Trip → Complete Trip`      | Không còn lỗi `relation "trips" does not exist`          | ✅ PASS   |

---

## 5. Kết luận

- Hạ tầng Docker Compose hiện tại đã được **ổn định hóa**.  
- Cơ chế **High Availability (HA)** cơ bản cho database đã được chuẩn hóa thông qua kiến trúc **Primary–Replica** đúng chuẩn.  
- Các lỗi cấu hình tiềm ẩn (mount sai, race condition, permission Localstack) đã được nhận diện và có hướng xử lý rõ ràng.  
- Hệ thống sẵn sàng cho việc phát triển các tính năng tiếp theo trên nền hạ tầng vững chắc hơn.

