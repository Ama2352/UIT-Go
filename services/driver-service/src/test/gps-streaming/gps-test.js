const WebSocket = require("ws");

const ws = new WebSocket("ws://localhost:8082/ws/drivers");

ws.on("open", () => {
  console.log("Connected. Starting GPS stream...");

  setInterval(() => {
    const payload = {
      driverId: "driver-123",
      lat: 10.762622 + (Math.random() * 0.0005),
      lng: 106.660172 + (Math.random() * 0.0005),
      heading: Math.floor(Math.random() * 360),
      speed: Number((Math.random() * 40).toFixed(2)),
      timestamp: new Date().toISOString()
    };

    console.log("Sending:", payload);
    ws.send(JSON.stringify(payload));
  }, 1000);
});

ws.on("message", (msg) => console.log("Received:", msg));
ws.on("close", () => console.log("WS closed"));
ws.on("error", (err) => console.error("WS error:", err));
