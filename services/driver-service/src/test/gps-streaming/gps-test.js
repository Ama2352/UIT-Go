const WebSocket = require("ws");

function runTest(name, url, overrideDriverId) {
  return new Promise((resolve) => {
    console.log(`\n🚀 Running test: ${name}`);
    console.log(`Connecting to: ${url}`);

    let resolved = false;

    const finish = () => {
      if (!resolved) {
        resolved = true;
        resolve();
      }
    };

    // ⭐ Attach error BEFORE other listeners
    const ws = new WebSocket(url);

    ws.once("error", (err) => {
      console.log(`⚠️ ERROR (during handshake or runtime): ${err.message}`);
      finish();
    });

    ws.once("open", () => {
      console.log("✅ WebSocket OPENED");

      const msg = {
        driverId: overrideDriverId ?? "driver-123",
        lat: 10.123,
        lng: 106.123,
        heading: 90,
        speed: 20,
        timestamp: new Date().toISOString(),
      };

      console.log("📤 Sending message:", msg);
      ws.send(JSON.stringify(msg));
    });

    ws.once("close", (code, reason) => {
      console.log(`❌ WebSocket CLOSED — code: ${code}, reason: ${reason}`);
      finish();
    });

    ws.on("message", (msg) => {
      console.log("📩 Message:", msg.toString());
    });

    // Backup timeout — guarantees the test won't hang
    setTimeout(() => {
      console.log("⏳ Timeout → forcing test to continue");
      finish();
    }, 3000);
  });
}


// ---------------------------------------------------------
// 🟦 IMPORTANT: Tokens MUST be strings wrapped in quotes!
// ---------------------------------------------------------

const valid = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkcml2ZXItMTIzIiwicm9sZSI6IkRSSVZFUiIsImlhdCI6MTc2Mzk3ODAzMiwiZXhwIjoxNzYzOTgxNjMyfQ.MemLMZcTFUJ9p7D-a99EK-pveUODHyP_4qwVqS98Cbs8AEzv2EHUaWjXIM0_0MSwaLUdln7keUWuf735wv7rqJyZLYBGFTJbjGUV8Tbt3Bpo9PElR7mVhzU8Joj0lVfB4fjMQ4GImjcRJz116KYWHFmSdHHyPLmbi9P3y-PNrvc0ryyH5VPWQy5Ji5vRIN0LnjB7-5dihhshb-wl8huFenaWM4GQ9InaTgGh3-NpXcw6amGBGpwpbbzroZVnCgqWpzgkoRpKKfhXL2Txq0Wf4JfyYXQWvHA1JELdRdswmZlqhLQv2W8o749uTR3tiBmO2gmgUqbWCbBZnps27OeXrQ";

const expired = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJkcml2ZXItMTIzIiwicm9sZSI6IkRSSVZFUiIsImV4cCI6MTY5MDAwMDAwMH0.njxJlQLc5TLsxMXcgU45Av-EOVUiH57VcqJgM_7Kcivz-Z8rkPVC2YKxKFgMIPNwnpy6nsW2A8oYkA_H09nYvK5NzxMWmt3BzZz4KkD7K15HpXepnt5cPv-fdeD4E2b7oJ3RopjaRlvXGAcKqGEguGw5AEzH1yS2Hd-66_PQpibA3ErlBZfc7nKcpfj6s3xrGqVg1ML4bJ6LkkvqbvUPG4eS3b8wCLu8P1zZzo4FtnwhOmWdbcnPGkuhaWfDJ1zpcER1VWkFUDYFpBNBamv0Ff0zHAbu_IynEeBO2MsWkVHA-evPsSmSIYc48Fm2cdG6gjtbHYL4Qo7mSNTzGPeg";

const tampered = valid.slice(0, -1) + "X"; // quick way to break signature

// ---------------------------------------------------------
// Synchronous-like sequential test runner
// ---------------------------------------------------------

const tests = [
  {
    name: "1) Valid token",
    url: `ws://localhost:8082/ws/driver-location?token=${valid}`
  },
  {
    name: "2) Valid token but WRONG driverId",
    url: `ws://localhost:8082/ws/driver-location?token=${valid}`,
    overrideDriverId: "fake-driver-999"
  },
  {
    name: "3) Expired token",
    url: `ws://localhost:8082/ws/driver-location?token=${expired}`
  },
  {
    name: "4) Invalid signature token",
    url: `ws://localhost:8082/ws/driver-location?token=${tampered}`
  },
  {
    name: "5) Missing token",
    url: `ws://localhost:8082/ws/driver-location`
  }
];

// ---------------------------------------------------------
// Run all tests in strict order
// ---------------------------------------------------------
(async () => {
  for (const t of tests) {
    await runTest(t.name, t.url, t.overrideDriverId);
  }

  console.log("\n🎉 ALL TESTS COMPLETED");
})();
