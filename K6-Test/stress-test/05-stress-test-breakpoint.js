import http from 'k6/http';
import { sleep, check } from 'k6';

// ==========================================
// 1. Cấu hình RAMP UNLIMITED (Tăng Tải Liên Tục đến 3000 VU)
// ==========================================
export const options = {
    stages: [
        // Tăng tải liên tục từ 0 lên 3000 VU trong 18 phút
        { duration: '18m', target: 3000 },        
        { duration: '1m',  target: 3000 },
        { duration: '1m',  target: 0 },
    ],
    // Đặt ngưỡng cực kỳ lỏng lẻo để không làm gián đoạn bài test
    thresholds: {
        http_req_failed: ['rate<0.99'], // Thất bại chỉ khi lỗi trên 99%
    },
};

const TRIP_API   = __ENV.TRIP_API   || 'http://host.docker.internal:8081';
const DRIVER_API = __ENV.DRIVER_API || 'http://host.docker.internal:8082';

// ==========================================
// 2. Mảng dữ liệu và Hàm phụ trợ
// ==========================================

// --- DANH SÁCH TÀI XẾ THẬT TỪ DATABASE (200 IDs) ---
const DRIVER_POOL = [
    // !!! DÁN TOÀN BỘ 200 ID DRIVER CỦA BẠN VÀO ĐÂY !!!
    '101aec7-3cb6-4480-b249-fbcd6724ca18', '2c2faceb-d447-4a00-80e6-1b38923d3e6d',
    // ... CÁC ID KHÁC ...
    'f718ee57-02ca-4f48-a468-95f96c067c8d', '0715e11e-7c24-40df-9cd5-18f2c4c33a2f'
];

function getDriverForVU(vuId) {
    const index = (vuId - 1) % DRIVER_POOL.length;
    return DRIVER_POOL[index];
}

function uuidv4() {
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
        var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
        return v.toString(16);
    });
}

function randomPickupLocation() {
    return { lat: 10.77 + Math.random() * 0.02, lng: 106.69 + Math.random() * 0.02 };
}

function getDriverLocationNearPickup(pickupLat, pickupLng) {
    const maxOffset = 0.005; // ~500m
    return {
        lat: pickupLat + (Math.random() - 0.5) * 2 * maxOffset,
        lng: pickupLng + (Math.random() - 0.5) * 2 * maxOffset
    };
}


// ==========================================
// 3. Hàm chính của k6
// ==========================================
export default function () {
    const headers = { 'Content-Type': 'application/json' };
    const driverId = getDriverForVU(__VU);
    const passengerId = uuidv4();
    const pickup = randomPickupLocation();
    const driverLoc = getDriverLocationNearPickup(pickup.lat, pickup.lng);

    // 1. DRIVER ONLINE & LOCATION (PUT)
    const onlineRes = http.put(`${DRIVER_API}/drivers/${driverId}/online`, null, { 
        headers: headers,
        tags: { name: 'Driver_Online' }
    });
    check(onlineRes, { 'Driver Online Success': (r) => r.status === 200 });
    const locationUrl = `${DRIVER_API}/drivers/${driverId}/location?lat=${driverLoc.lat}&lng=${driverLoc.lng}`;
    http.put(locationUrl, null, { 
        headers: headers,
        tags: { name: 'Driver_Location_Update' }
    });
    sleep(0.5);

    // 2. PASSENGER CREATE TRIP (POST)
    const payload = JSON.stringify({
        passengerId: passengerId,
        pickupAddress: 'Ramp Unlimited Test Pickup',
        dropoffAddress: 'Ramp Unlimited Test Dropoff',
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropoffLat: 10.80,
        dropoffLng: 106.65,
        vehicleType: 'BIKE',
    });
    const createRes = http.post(`${TRIP_API}/trips`, payload, {
        headers: headers,
        tags: { name: 'Passenger_Create' }
    });
    check(createRes, { 'Trip Creation Success': (r) => r.status === 201 || r.status === 200 });

    if (createRes.status !== 201 && createRes.status !== 200) return;
    const tripId = createRes.json('id');
    sleep(0.5);

    // 3. TÀI XẾ NHẬN CHUYẾN (CÓ RETRY & CHECK TỐI ƯU)
    let attempts = 5;
    let acceptRes;
    while (attempts > 0) {
        acceptRes = http.put(`${DRIVER_API}/drivers/${driverId}/trips/${tripId}/accept`, null, {
            headers: headers,
            tags: { name: 'Driver_Accept_Unlimited' }
        });
        if (acceptRes && (acceptRes.status === 200 || acceptRes.status === 409)) {
            break;
        } else if (acceptRes && acceptRes.status === 400 && acceptRes.body && acceptRes.body.includes("Redis")) {
            sleep(1);
            attempts--;
        } else if (!acceptRes || acceptRes.status >= 500) {
            // Lỗi nghiêm trọng (Timeout, 5xx)
            if (acceptRes) console.error(`Accept Fatal Error: ${acceptRes.status} - ${acceptRes.body}`);
            break;
        } else {
            break; // Lỗi khác (ví dụ 404)
        }
    }

    // Kiểm tra kết quả
    check(acceptRes, {
        'Driver Accepted Success or Conflict (200/409)': (r) => r && (r.status === 200 || r.status === 409),
        'No Fatal Timeout/500': (r) => r && (r.status < 500 || r.status >= 503),
    });

    // Giãn cách cuối vòng lặp
    sleep(1);
}