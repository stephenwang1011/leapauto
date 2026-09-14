// Offline framing vectors. OpenSSL performs AES/SM4 independently of Kotlin/BC.
const crypto = require("node:crypto");
const hex = (value) => Buffer.from(value, "hex");
const le = (value, size) => {
    const bytes = Buffer.alloc(size);
    if (size === 8) bytes.writeBigUInt64LE(BigInt(value));
    else if (size === 4) bytes.writeUInt32LE(value);
    else bytes.writeUInt16LE(value);
    return bytes;
};
const timestamp = 1800000000;
const sessionId = 0x78563412;
const publicKey = hex("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
    "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");
const key = Buffer.from("0123456789abcdef");
const iv = Buffer.from("fedcba9876543210");
const signature = Buffer.from(Array.from({ length: 16 }, (_, index) => index + 1));
const certificateFields = ["synthetic-public-key", "0", "0123456789abcdef".repeat(5),
    "v1;old-account;old-device;TESTVIN0000000001;1700000000;1900000000",
    signature.toString("base64"), "TESTVIN0000000001"];
const fingerprint = crypto.createHash("sha256").update(Buffer.concat(certificateFields.flatMap(
    (field) => [le(Buffer.byteLength(field), 4), Buffer.from(field)]))).digest("hex");
const crc8 = (bytes) => {
    let value = 0;
    for (const byte of bytes) {
        value ^= byte;
        for (let bit = 0; bit < 8; bit++) value = ((value << 1) ^ (value & 128 ? 7 : 0)) & 255;
    }
    return value;
};
const configurations = [
    { name: "off8", binary: "38c80008100000", text: "56;2.00;08;16;0;0;" },
    { name: "auto8", binary: "38c80008100101", text: "56;2.00;08;16;1;1;" },
    { name: "off9", binary: "38c800081000000000", text: "56;2.00;08;16;0;0;0;0;" },
    { name: "manual9", binary: "38c800081001000000", text: "56;2.00;08;16;1;0;0;0;" },
    { name: "button9", binary: "38c800081001000001", text: "56;2.00;08;16;1;0;0;1;" },
    { name: "all9", binary: "38c800081001010101", text: "56;2.00;08;16;1;1;1;1;" }
];
const algorithms = ["aes-128-cbc", "sm4-cbc"].map((algorithm) => {
    const encrypt = (bytes) => {
        const cipher = crypto.createCipheriv(algorithm, key, iv);
        return Buffer.concat([cipher.update(bytes), cipher.final()]);
    };
    const wrap = (bytes) => {
        const encrypted = Buffer.from(encrypt(bytes).toString("base64"));
        return Buffer.concat([hex("aaab"), le(encrypted.length, 4), encrypted]).toString("hex");
    };
    return { algorithm, configurations: configurations.map((configuration) => {
        const body = Buffer.concat([le(timestamp, 8), Buffer.from([3]), hex(configuration.binary)]);
        const text = Buffer.from(`${timestamp};v1;test-account;test-device;TESTVIN0000000001;1700000000;1900000000;${configuration.text}`);
        const ciphertext = Buffer.from(encrypt(Buffer.concat([le(text.length, 4), text, signature])).toString("base64"));
        const payload = Buffer.concat([le(sessionId, 4), publicKey, ciphertext, hex("0109")]);
        return { name: configuration.name, command: wrap(Buffer.concat([Buffer.from([crc8(body)]), body])),
            authentication: Buffer.concat([hex("aaae"), le(payload.length, 2), payload]).toString("base64") };
    }), replies: ["2;3;0;", "2;3;00;", "2;3;", "2;3;000;", "2;3; 0;", "2;03;0;", "2;1;00;", "2;3"].map(
        (text) => ({ text, frame: wrap(Buffer.from(text)) })) };
});
const output = JSON.stringify({ fingerprint, algorithms }, null, 2) + "\n";
if (process.argv[2]) require("node:fs").writeFileSync(process.argv[2], output);
else process.stdout.write(output);
