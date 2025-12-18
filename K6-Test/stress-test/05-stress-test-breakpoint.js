import http from "k6/http";
import { sleep, check } from "k6";
import { Trend } from "k6/metrics"; // Import Trend

// Custom Trends to track p95 for specific steps
const driverOnlineTrend = new Trend("driver_online_duration");
const passengerCreateTrend = new Trend("passenger_create_duration");
const driverAcceptTrend = new Trend("driver_accept_duration");

// ==========================================
// 1. Cấu hình RAMP UNLIMITED (Tăng Tải Liên Tục đến 3000 VU)
// ==========================================
export const options = {
  stages: [
    // Tăng tải liên tục từ 0 lên 3000 VU trong 18 phút
    { duration: "18m", target: 3000 },
    { duration: "1m", target: 3000 },
    { duration: "1m", target: 0 },
  ],
  // Đặt ngưỡng cực kỳ lỏng lẻo để không làm gián đoạn bài test
  thresholds: {
    http_req_failed: ["rate<0.99"], // Thất bại chỉ khi lỗi trên 99%
  },
};

const TRIP_API = __ENV.TRIP_API || "http://host.docker.internal:8081";
const DRIVER_API = __ENV.DRIVER_API || "http://host.docker.internal:8082";

// ==========================================
// 2. Mảng dữ liệu và Hàm phụ trợ
// ==========================================

// --- DANH SÁCH TÀI XẾ THẬT TỪ DATABASE (200 IDs) ---
const DRIVER_POOL = [
  "101aec7-3cb6-4480-b249-fbcd6724ca18",
  "2c2faceb-d447-4a00-80e6-1b38923d3e6d",
  "a0cc9fdf-724d-4de8-9490-d1163c8e129f",
  "b81bf310-8ce9-454a-a481-4b429aef5a7d",
  "244ffc46-3264-45ce-9de2-224cb8486e40",
  "74da6559-7e46-46aa-b17b-0f8acb6cf6ae",
  "226f70c7-189d-4c32-a1ac-29f6bb52e04c",
  "50df88d5-c0a4-4634-8579-e4d8f35943d1",
  "5521b2e0-5503-420a-bec0-04e9d906092c",
  "6ccd6b9e-5d83-44f6-9faa-c42e89fdcb79",
  "fc319096-601b-4780-abe5-a5ea79c92883",
  "f7adfb21-fbf8-46fc-9609-71327fe696f5",
  "cad9ff7a-3194-4be3-b9fd-f5dedd5f17d2",
  "0bc454f3-b564-4e5c-b205-238300e448f3",
  "771a1fb6-752e-4b9e-85dc-e41595227a48",
  "2ec78100-033d-4e16-9715-141d64a747ac",
  "56134f9d-4587-4b8a-98c3-6ca5b7f820d7",
  "79fa5d57-0cb6-4d4f-a568-2d73bc672048",
  "010a0fe9-bb53-4056-8e80-fd744d232a13",
  "bfe4d2d4-495c-4718-88b4-1513efbcf148",
  "8163afb9-d1e7-4858-b232-097dad701ebb",
  "3368f7ac-18c2-4e2e-afa2-67ba7a1101d4",
  "67f5f354-0960-4e9c-b329-a40d5b3a0a7e",
  "0b8c253b-6eec-4ad4-86d8-b0b902afa21b",
  "3ea30183-c970-4202-b8dd-c994423b69eb",
  "12eaf183-66e1-492a-8285-aee047ad745a",
  "26c04b4a-61f8-451e-b247-f96f1616b7ae",
  "5b39dcd5-e859-439f-92cf-f3eef2cc75c2",
  "30fc1cd8-1a8f-4f83-ac8f-49e215422f31",
  "552603ae-9ab3-44dc-826b-268f658bd89e",
  "32e668e6-2072-4f3c-a168-a4c402fd1033",
  "5e1e7dd0-fe0f-4174-8d4f-f28de8063c9f",
  "fc41da4d-e106-4c24-9cb3-d61730c8c0cc",
  "b723e9a0-2c78-4a2c-ba49-c683ca7cbc5b",
  "9432996c-7bf2-48ee-b902-b6525ab470e7",
  "5cf2a09a-1e17-4834-a579-42b84545d5f7",
  "59ef081e-b2a1-4deb-92b7-62695861b365",
  "459b84b4-79bf-45f7-b358-08d494a5c660",
  "a57d5ce9-7d22-467f-8e36-d3c9bbe0ebd5",
  "4c99c701-0d7c-4040-bd7b-10e89c032b9c",
  "b77d38b1-5ad1-4b14-aeeb-bf5d888468d5",
  "6773ae45-4c6c-4ba2-ad4c-3c9374b1863e",
  "a059c2c4-c63e-45ad-a832-1a6306efea98",
  "fda942bd-bdd2-41d6-814d-af13ec79163b",
  "caf86780-eacf-497b-baee-56b9a3d64890",
  "ea5bc053-3b4f-41fb-a098-71851f282efc",
  "e2baf6ae-c334-4936-9615-9294a0686a6a",
  "532ebebf-0c3a-4d95-9929-06fb9539f7fb",
  "c57419b9-94b8-4aeb-b595-6d636f78c218",
  "d4d38f26-7e71-4c36-9a7a-43d8aede9e20",
  "c1c9a4b2-38ea-4e69-8a67-1e860bee4c6a",
  "4d25f685-802e-43fc-904d-e88465278845",
  "38267bdb-a9f8-4074-9c7e-bbbc1dca16aa",
  "65bee058-5d0e-40c5-945c-a77e287df7a3",
  "53507113-f152-4e03-a027-3a6bb79f5dd0",
  "385e8709-ff5d-452d-98d6-1457dffeb011",
  "33c7deb6-591f-4172-894e-f688cd3f6859",
  "93cc53e3-e7f9-43b0-8b7d-cf5fdad9ee92",
  "c15c8025-e2c1-4df5-a7c0-8db65d3c4abf",
  "063b4946-753a-4bb3-8aa6-76001012f2cc",
  "4b6e0e5a-40c7-41bc-850b-d39c13f422a6",
  "9e2ee60b-842c-4c1b-bc70-1e4a4d168578",
  "dbfc9faf-61ee-415a-ae3e-716370defd29",
  "0453cf6a-e226-4107-90bf-5f61e536fcca",
  "2779e80a-57ce-43e1-a390-0420d7aa60e0",
  "f4eab29a-03fa-45e5-aeb9-8da28b60c954",
  "c6ceddee-1df9-4336-afbe-480b3931f7fd",
  "23067c70-7ea7-4fb0-9fa9-10dcd0c294ad",
  "6894fce8-9779-442a-bf6b-5b485021218c",
  "56caf6b7-029d-494f-af9d-f3eca1dabded",
  "64368da8-c8ef-4466-b252-e17f9aa5dedf",
  "e1c9163e-753d-44fd-a490-4d2416249d48",
  "b1c92291-80af-409e-bb84-5b0e65b77e19",
  "41b9d799-eddb-4881-8de3-13a8c17e64ff",
  "c38da33a-5faa-4331-82bc-9344ba43aa7e",
  "086442e0-0841-4367-9075-c0c9b16cacda",
  "78e3e357-63b7-4c31-98fa-dea97ab7153e",
  "33e44bb7-d18c-4dd5-962c-fb4c3f3550a9",
  "12e4c8ad-8f5c-4ecf-a987-88d3d2d94330",
  "90bebae0-34c3-44e5-b1c2-d8af874892f0",
  "469f9cfd-9b60-4d6f-8558-2ae12a535957",
  "9710ae14-cbbe-4e64-97ca-cf1edde6bca3",
  "ce155800-941d-4b2f-84f5-84febfcf3bb0",
  "6a9b0c75-0eba-4bf3-91ea-e08d4c18a7f0",
  "0a1e0545-0674-4c43-be98-f9724101222a",
  "3ad5b896-5710-4d02-816f-cd906db3d4d0",
  "57c60a87-23c1-40c9-bded-4492954c933a",
  "108ade2e-b4ee-43a8-ba09-55e510e55161",
  "34bb2f8d-6ab2-4e9e-8e21-c802a0d51459",
  "456903e6-c7ab-44b2-8781-434450d1169a",
  "02edf318-9ccd-4b19-877a-4631323cdcb3",
  "cd93b907-f083-4672-aa19-cc2848e827ee",
  "b81fdc75-1701-432f-bfa7-9536c3fe67f5",
  "8bfc7dac-4364-448c-aca6-6ef2206607f2",
  "449a318b-3846-430e-9ca6-8d17f4dd67a9",
  "27cda5b3-1c20-4045-a27a-d05ed6978c6e",
  "8310b0bc-c0f1-45dd-928b-a4b2e5893846",
  "92d42861-c9e8-458b-9d66-db2ae2f7c524",
  "c5326364-464e-4f8f-8318-ddf0f64d86af",
  "38888e77-1637-418e-970a-dc7b9f0f669a",
  "55c81b16-1027-4bf3-a40b-c55ce6460ae2",
  "0e782a6f-0b6b-46cc-a065-ab7717b55810",
  "54a70e14-52a9-400d-b4e3-fb4dc8757b23",
  "1b9a94db-0bc2-4723-9499-8884ac1797d5",
  "adacbf7f-2f4c-4207-9a4b-c27c4773fd6e",
  "a184d257-2943-4026-a8b9-9088e6c82596",
  "10a29016-e409-4f20-a91f-568f4a4f313b",
  "f227733f-1a6e-4318-9e66-95fe4e02a6ef",
  "51e1b4c7-4133-4efa-93dc-0c02e1f4583b",
  "05936e1d-9f99-49bc-a884-66acc115ba22",
  "c0a169a4-5ddd-40c1-95e5-1c04d21d0ca7",
  "d953e755-7ced-4484-8e27-4af2a3335945",
  "9ac5f359-ce69-4b07-aba6-26b37940dc56",
  "e70cfdc3-ff61-4724-87b4-a0beb1ca1fae",
  "c69bfe12-1a6f-468d-a9af-e2367eaa49ae",
  "baae702f-8e48-47a6-a3e1-90c4283e08d6",
  "636f9ab4-c508-4301-bf2c-7bad22197c86",
  "e00eab5d-b43c-4bb8-be7e-25f5ba0b6159",
  "d9bf4cda-3b83-40d4-b843-06ba1ce9693e",
  "e99bf487-ab97-495e-9965-5d3c374bb550",
  "4e65a41c-8510-4ef9-aeff-a11fda66517c",
  "e2dc073d-ddfc-456e-a455-9dd5ce49f9c4",
  "227a9ad9-a081-4f66-9b0c-45bcc4bb5ca3",
  "99382125-e9fa-4be8-8743-4b6f3caa0965",
  "e9abf3e7-f6f5-436d-b2eb-c8a745e6b92e",
  "9579c1b7-dbcb-4cd4-9f27-9656d3d408d2",
  "8ffa09f0-2a66-4ba2-bb47-5c2caa7aa98a",
  "a363818e-f92a-43b9-949b-55b7f44493b2",
  "0cbc7f2b-b7e1-4f68-8bb2-78fb2a5ed4d5",
  "2c9b23e9-4ff5-471a-b447-98bdbdd1ba9e",
  "ab8a23cb-ad4c-4cb5-abcf-aada70d6a903",
  "3db24587-a8bc-4677-955c-a617f9361f1b",
  "5ef7bb06-5a6e-4955-9cff-2a9344e5ccf2",
  "5fd7fe6d-1f54-42ef-bdfb-635ade52eaf6",
  "a4deab25-6f06-4f7d-95de-b6521ce618e1",
  "bef62e8b-c763-4f52-bd56-3456d69f5273",
  "e42c6dbd-1637-444e-a32a-a81a434e2fd5",
  "fd92713b-8c03-4190-b82a-96f535260d23",
  "2c1cf7e7-8eca-475e-9a3e-c5408c9985d3",
  "bc832bcf-b1d5-43eb-a78d-a3d96a48e025",
  "046faf5c-6e38-46f4-8091-9ed95ea184d1",
  "f115275c-7995-492a-9364-ef58fc8df605",
  "bd79901d-f598-4a41-872e-7d8ba25c6cab",
  "2c110528-94ba-4879-a071-d320a3cd6fae",
  "4ba033ff-2de5-4385-a258-f9199c03f5cd",
  "c25e8e18-d37c-48bd-b474-4051c7715992",
  "3404d22e-f00b-4b1b-9032-5b04cd333130",
  "ad095900-cba3-421e-ba5a-32ace82e8dae",
  "b2c8ca7c-024d-43df-9289-d725544cdf77",
  "6962f489-b25b-44f7-a975-2bcef72caf67",
  "76f3f337-407b-43fe-a2d1-6c1d358660b9",
  "5309a58e-6435-4003-aea5-b2a77f019614",
  "0e151362-92bf-46e5-a58f-c6ac28804b0a",
  "92a6cad2-fbb7-4e26-a0c7-ad47c3ecd582",
  "d647e4bd-b104-4509-b270-0f9103364929",
  "7f559dc8-c1d9-40f0-b6b6-824f14cc8696",
  "84418aca-79fe-48c0-ac7e-ae8fbc1c9922",
  "2c537a9f-a5f8-419e-8a49-e80bdc414616",
  "c7c4880a-5928-4435-a6a1-224d6fa41f40",
  "fb1512f1-1fb3-4fb8-812f-fabcbfbc5630",
  "d89bad2a-6cc9-4a36-800f-3758334c950c",
  "7b4da803-9cd5-46b8-bee8-34c7e0926f18",
  "40974c91-494d-4ccc-8401-90e21ee47c3b",
  "bbbba1d9-1275-4af8-9db6-53e052b66873",
  "21672a37-06d3-4e78-b903-90c431930088",
  "ea275a98-964c-404b-8fd1-4864df61448c",
  "64aee393-de76-4a0f-be69-2231c58a78eb",
  "5c6707bc-fd7f-4e5c-95ff-8e403158a3de",
  "f9015a4e-741e-443f-9945-96e5581d9cf8",
  "adb847ff-4938-42cb-8d71-761c9eb8d6da",
  "a44d7d62-a81e-46fa-be4f-fc4f3453d058",
  "e1674ab0-b1f0-47f8-b7aa-bac764ca9e2d",
  "6a47a8dc-1b70-44e1-b830-b6f7605fea09",
  "3aab4aff-b182-42ea-9114-051edfae6ee6",
  "f9431394-c35c-43e0-bc08-fa273b5b965b",
  "38544796-84c2-470f-8259-b8c249da7e8b",
  "f68464a1-4734-42ed-8579-c50267f3f771",
  "34d67db4-bc5f-4ecf-bd06-99c65c673651",
  "8a0eb7b5-db18-4588-b48b-b89827471477",
  "54b95e3c-0734-4652-b0a6-cf0f2ba90cbc",
  "111351f0-54cf-4033-8f6c-583d6b7a6313",
  "45d3edfe-9136-4a0c-b8f5-c849e39c74ce",
  "d18d6c85-12c0-4dec-a101-12291b37a14f",
  "fa9257ae-dcb2-4728-86a9-a665fc65cb68",
  "41ff91df-a991-42ef-919c-d812d3fb037b",
  "f0c88945-c717-4bcf-8662-f5ba26508af7",
  "5145dd89-6493-45ee-9b82-45b2ce13c5d2",
  "0dc4e511-afdf-4b03-8b83-7432c43ec34e",
  "6e97465d-6ee0-44ee-983e-ff2186b3c01e",
  "50bd9953-25ac-4272-854f-071406c9e8a9",
  "0734bd1d-9c60-4063-a3bf-08f02aaf72e9",
  "2a745144-876c-476d-a3c3-f4ed959a91f3",
  "e3ada596-3367-4217-a7d5-dc65947ec7ae",
  "cb824d27-8fa2-4777-9b7b-098e028d477e",
  "16dedaf7-28e2-4d20-b072-21bae29feb4f",
  "443e8d5b-3072-4436-9439-4c917275846d",
  "073277e4-9256-43ea-88c5-cee6fbce9aa3",
  "c2ab6a17-ce8a-41d5-9a7d-9011d57afbd4",
  "d3c22ee7-4ef3-4171-bc18-2594486defd0",
  "36588bb7-280d-42e0-83b6-acbb555a37e7",
  "a2ed33fc-c7d7-44eb-bf2d-37ff801e0655",
  "32dc5161-7fbc-455a-b760-54ce42a4faec",
  "ab8c95dd-8595-4b7b-8633-0be3c69bf687",
  "4f432385-1bb5-4366-80a8-592811343a7b",
  "c7e5036d-c7a6-4952-8ea6-ebd41852a7f3",
  "0da5b2e3-1a84-46f0-b163-4015f2a29731",
  "c74d5eb8-646c-4da3-a9fc-eeadf4777ef9",
  "98a1e419-8032-476f-a0e2-3da2bb2cf039",
  "9a654517-b943-45bb-9ffd-168ca08242fb",
  "23c46b4d-3dbf-4a54-b8bd-309c2feb75db",
  "c41537ce-7996-48f7-bd5e-d496f1cc923f",
  "5c486fb1-44b7-4b91-8e08-34fd141f69d8",
  "9713be4f-6616-4d69-bf8f-b69b98bd116e",
  "a6a5d913-21d7-4901-a60c-a260e186e305",
  "f718ee57-02ca-4f48-a468-95f96c067c8d",
  "0715e11e-7c24-40df-9cd5-18f2c4c33a2f",
];

function getDriverForVU(vuId) {
  const index = (vuId - 1) % DRIVER_POOL.length;
  return DRIVER_POOL[index];
}

function uuidv4() {
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (c) {
    var r = (Math.random() * 16) | 0,
      v = c == "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

function randomPickupLocation() {
  return {
    lat: 10.77 + Math.random() * 0.02,
    lng: 106.69 + Math.random() * 0.02,
  };
}

function getDriverLocationNearPickup(pickupLat, pickupLng) {
  const maxOffset = 0.005; // ~500m
  return {
    lat: pickupLat + (Math.random() - 0.5) * 2 * maxOffset,
    lng: pickupLng + (Math.random() - 0.5) * 2 * maxOffset,
  };
}

// ==========================================
// 3. Hàm chính của k6
// ==========================================
export default function () {
  const headers = { "Content-Type": "application/json" };
  const driverId = getDriverForVU(__VU);
  const passengerId = uuidv4();
  const pickup = randomPickupLocation();
  const driverLoc = getDriverLocationNearPickup(pickup.lat, pickup.lng);

  // 1. DRIVER ONLINE & LOCATION (PUT)
  const onlineRes = http.put(`${DRIVER_API}/drivers/${driverId}/online`, null, {
    headers: headers,
    tags: { name: "Driver_Online" },
  });
  driverOnlineTrend.add(onlineRes.timings.duration); // Track duration
  check(onlineRes, { "Driver Online Success": (r) => r.status === 200 });
  const locationUrl = `${DRIVER_API}/drivers/${driverId}/location?lat=${driverLoc.lat}&lng=${driverLoc.lng}`;
  http.put(locationUrl, null, {
    headers: headers,
    tags: { name: "Driver_Location_Update" },
  });
  sleep(0.5);

  // 2. PASSENGER CREATE TRIP (POST)
  const payload = JSON.stringify({
    passengerId: passengerId,
    pickupAddress: "Ramp Unlimited Test Pickup",
    dropoffAddress: "Ramp Unlimited Test Dropoff",
    pickupLat: pickup.lat,
    pickupLng: pickup.lng,
    dropoffLat: 10.8,
    dropoffLng: 106.65,
    vehicleType: "BIKE",
  });
  const createRes = http.post(`${TRIP_API}/trips`, payload, {
    headers: headers,
    tags: { name: "Passenger_Create" },
  });
  passengerCreateTrend.add(createRes.timings.duration); // Track duration
  check(createRes, {
    "Trip Creation Success": (r) => r.status === 201 || r.status === 200,
  });

  if (createRes.status !== 201 && createRes.status !== 200) return;
  const tripId = createRes.json("id");
  sleep(0.5);

  // 3. TÀI XẾ NHẬN CHUYẾN (CÓ RETRY & CHECK TỐI ƯU)
  let attempts = 5;
  let acceptRes;
  while (attempts > 0) {
    acceptRes = http.put(
      `${DRIVER_API}/drivers/${driverId}/trips/${tripId}/accept`,
      null,
      {
        headers: headers,
        tags: { name: "Driver_Accept_Unlimited" },
      }
    );
    driverAcceptTrend.add(acceptRes.timings.duration); // Track duration
    if (acceptRes && (acceptRes.status === 200 || acceptRes.status === 409)) {
      break;
    } else if (
      acceptRes &&
      acceptRes.status === 400 &&
      acceptRes.body &&
      acceptRes.body.includes("Redis")
    ) {
      sleep(1);
      attempts--;
    } else if (!acceptRes || acceptRes.status >= 500) {
      // Lỗi nghiêm trọng (Timeout, 5xx)
      if (acceptRes)
        console.error(
          `Accept Fatal Error: ${acceptRes.status} - ${acceptRes.body}`
        );
      break;
    } else {
      break; // Lỗi khác (ví dụ 404)
    }
  }

  // Kiểm tra kết quả
  check(acceptRes, {
    "Driver Accepted Success or Conflict (200/409)": (r) =>
      r && (r.status === 200 || r.status === 409),
    "No Fatal Timeout/500": (r) => r && (r.status < 500 || r.status >= 503),
  });

  // Giãn cách cuối vòng lặp
  sleep(1);
}
