import http from 'k6/http';
import { sleep, check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 1. Cấu hình Load Test
export const options = {
    stages: [
        { duration: '30s', target: 10 }, // Khởi động 10 cặp
        { duration: '1m',  target: 20 }, // Duy trì 20 cặp chạy song song
        { duration: '30s', target: 0 },  // Kết thúc
    ],
    // Cho phép lỗi < 5% (Do tranh chấp tài xế là bình thường)
    thresholds: { http_req_failed: ['rate<0.05'] },
};

const TRIP_API   = __ENV.TRIP_API   || 'http://host.docker.internal:8081';
const DRIVER_API = __ENV.DRIVER_API || 'http://host.docker.internal:8082';

// --- DRIVER POOL: 50 Tài xế cố định ---
const MAX_DRIVERS = 50;
const DRIVER_POOL = new Array(MAX_DRIVERS).fill(0).map((_, i) => {
    // Tạo UUID giả lập cố định: ...0001, ...0002
    const idSuffix = (i + 1).toString().padStart(12, '0');
    return `00000000-0000-0000-0000-${idSuffix}`;
});

function getRandomDriver() {
    return DRIVER_POOL[Math.floor(Math.random() * DRIVER_POOL.length)];
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

export default function () {
    const headers = { 'Content-Type': 'application/json' };

    // 1. Chuẩn bị dữ liệu
    const passengerId = uuidv4();
    const driverId = getRandomDriver(); // Lấy từ Pool 50 ông
    const pickup = randomPickupLocation();
    const driverLoc = getDriverLocationNearPickup(pickup.lat, pickup.lng);

    // ==========================================
    // BƯỚC 1: TÀI XẾ ONLINE (PUT - Driver Service)
    // ==========================================

    // Online
    http.put(`${DRIVER_API}/drivers/${driverId}/online`, null, { headers: headers });

    // Update Location
    const locationUrl = `${DRIVER_API}/drivers/${driverId}/location?lat=${driverLoc.lat}&lng=${driverLoc.lng}`;
    http.put(locationUrl, null, { headers: headers });

    sleep(0.5); // Chờ Redis cập nhật

    // ==========================================
    // BƯỚC 2: KHÁCH ĐẶT XE (POST - Trip Service)
    // ==========================================
    const payload = JSON.stringify({
        passengerId: passengerId,
        pickupAddress: 'Pool Test Pickup',
        dropoffAddress: 'Pool Test Dropoff',
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

    if (createRes.status !== 201 && createRes.status !== 200) {
        console.error(`Create Trip Failed: ${createRes.body}`);
        return;
    }
    const tripId = createRes.json('id');

    sleep(1);

    // ==========================================
    // BƯỚC 3: TÀI XẾ NHẬN CHUYẾN (PUT - Driver Service)
    // ==========================================
    // (Lưu ý: Log cũ báo POST lỗi, nên ta dùng PUT)
    const acceptRes = http.put(`${DRIVER_API}/drivers/${driverId}/trips/${tripId}/accept`, null, {
        headers: headers,
        tags: { name: 'Driver_Accept' }
    });

    if (acceptRes.status !== 200) {
        // Nếu lỗi 400 (Trip taken) hoặc 404 (Not found) thì dừng
        return;
    }

    // ==========================================
    // BƯỚC 4: START & COMPLETE (POST - Trip Service)
    // ==========================================
    sleep(1);

    // Start Trip (POST)
    const startRes = http.post(`${TRIP_API}/trips/${tripId}/start`, null, {
        headers: headers,
        tags: { name: 'Trip_Start' }
    });

    if (startRes.status !== 200) console.error(`Start Failed: ${startRes.status}`);

    sleep(1);

    // Complete Trip (POST)
    const completeRes = http.post(`${TRIP_API}/trips/${tripId}/complete`, null, {
        headers: headers,
        tags: { name: 'Trip_Complete' }
    });

    if (completeRes.status !== 200) console.error(`Complete Failed: ${completeRes.status}`);
}