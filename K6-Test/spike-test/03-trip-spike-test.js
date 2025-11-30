import http from 'k6/http';
import { sleep, check } from 'k6';
// Import thư viện tạo UUID
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    // Kịch bản Spike: Tăng sốc và giảm nhanh
    stages: [
        { duration: '10s', target: 10 },  // Khởi động nhẹ
        { duration: '20s', target: 200 }, // BÙM! Tăng thốc lên 200 user (Spike)
        { duration: '1m',  target: 200 }, // Giữ đỉnh cao trào trong 1 phút
        { duration: '10s', target: 0 },   // Hạ màn nhanh
    ],
    // Spike Test chấp nhận lỗi cao hơn Load Test tí xíu (nhưng vẫn phải thấp)
    thresholds: {
        http_req_failed: ['rate<0.05'], // Lỗi < 5% là đạt
        http_req_duration: ['p(95)<3000'], // Phản hồi dưới 3s khi bị Spike
    },
};

const TRIP_API = __ENV.TRIP_API || 'http://host.docker.internal:8081';

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

    // Tạo Passenger ID mới cho mỗi lần bắn
    const passengerId = uuidv4();

    const body = JSON.stringify({
        passengerId: passengerId,
        pickupAddress: 'Spike Pickup',
        dropoffAddress: 'Spike Dropoff',
        pickupLat: pickup.lat,
        pickupLng: pickup.lng,
        dropoffLat: dropoff.lat,
        dropoffLng: dropoff.lng,
        vehicleType: randomItem(['BIKE', 'CAR_4_SEAT', 'CAR_PREMIUM']), // Random loại xe
    });

    const headers = {
        'Content-Type': 'application/json',
        'k6-test-type': 'spike-test'
    };

    // Gửi request
    const res = http.post(`${TRIP_API}/trips`, body, {
        headers: headers,
        tags: { name: 'Spike_Create_Trip' }
    });

    // Check kết quả (Quan trọng để biết server có chịu nổi nhiệt không)
    check(res, {
        'status is 201/200': (r) => r.status === 201 || r.status === 200,
    });

    // Sleep ngẫu nhiên 0.5s - 1s (Đỡ bị timeout mạng local)
    sleep(Math.random() * 0.5 + 0.5);
}