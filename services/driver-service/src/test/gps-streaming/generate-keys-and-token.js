const { generateKeyPairSync } = require("crypto");
const fs = require("fs");
const jwt = require("jsonwebtoken");

// 1) Generate RSA keypair (2048-bit, PKCS#8 private, SPKI public)
const { publicKey, privateKey } = generateKeyPairSync("rsa", {
  modulusLength: 2048,
  publicKeyEncoding: {
    type: "spki",
    format: "pem",
  },
  privateKeyEncoding: {
    type: "pkcs8",
    format: "pem",
  },
});

// 2) Save keys to files so Java can load the public key
fs.writeFileSync("private.pem", privateKey);
fs.writeFileSync("public.pem", publicKey);

console.log("✅ Generated private.pem and public.pem");

// 3) Generate a valid RS256 JWT for driver-123
const token = jwt.sign(
  {
    sub: "driver-123",
    role: "DRIVER",
  },
  privateKey,
  {
    algorithm: "RS256",
    expiresIn: "12h",
  }
);

console.log("\n✅ Valid RS256 JWT token:\n");
console.log(token);
