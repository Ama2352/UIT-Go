import http from 'k6/http';
import { sleep, check } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// 1. Cấu hình Load Test (Tăng dần tải)
export const options = {
    stages: [
        { duration: '30s', target: 20 }, // Khởi động: Tăng lên 20 user
        { duration: '1m',  target: 50 }, // Duy trì: Giữ 50 user
        { duration: '30s', target: 0 },  // Kết thúc: Giảm về 0
    ],
    thresholds: {
        http_req_failed: ['rate<0.01'], // Lỗi < 1%
        http_req_duration: ['p(95)<2000'], // 95% phản hồi < 2s
    },
};

const TRIP_API = __ENV.TRIP_API || 'http://host.docker.internal:8081/api/v1';
// Bỏ dòng lấy PASSENGER_ID từ ENV vì ta sẽ tự tạo

function randomLocation() {
    const lat = 10.78 + Math.random() * 0.06;
    const lng = 106.65 + Math.random() * 0.08;
    return { lat, lng };
}

function randomItem(array) {
    return array[Math.floor(Math.random() * array.length)];
}

export default function () {
    const pickup = randomLocation();
    const dropoff = randomLocation();
    const vehicleType = randomItem(['BIKE', 'CAR_4_SEAT', 'CAR_PREMIUM']);

    // --- QUAN TRỌNG: Tự tạo ID mới cho mỗi lần chạy ---
    const fakePassengerId = uuidv4();

    const payload = JSON.stringify({
        passengerId: fakePassengerId, // Dùng ID ngẫu nhiên vừa tạo
        pickupAddress: 'UIT - LoadTest Pickup',
        dropoffAddress: 'LoadTest Dropoff',
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropoffLat: dropoff.lat,
        dropoffLng: dropoff.lng,
        vehicleType: vehicleType,
    });

    const headers = {
        'Content-Type': 'application/json',
        'k6-test-type': 'load-test'
    };

    const res = http.post(`${TRIP_API}/trips`, payload, {
        headers: headers,
        tags: { name: 'CreateTripAPI' }
    });

    // Check kết quả
    check(res, {
        'status is 201': (r) => r.status === 201 || r.status === 200,
        // Kiểm tra xem server có trả về ID chuyến đi không
        'has trip id': (r) => r.json('id') !== undefined || r.json('tripId') !== undefined,
    });

    // Nghỉ ngẫu nhiên
    sleep(Math.random() * 2 + 1);
}