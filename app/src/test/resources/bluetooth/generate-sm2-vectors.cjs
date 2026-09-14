// Offline synthetic reference: Node/OpenSSL supplies all EC, SM3 and SM4 primitives.
const crypto = require("node:crypto");
const fromHex = (value) => Buffer.from(value, "hex");
const scalarBytes = (value) => fromHex(value.toString(16).padStart(64, "0"));
const hash = (bytes) => crypto.createHash("sm3").update(bytes).digest();
const le = (value, size) => {
    const bytes = Buffer.alloc(size);
    if (size === 8) bytes.writeBigUInt64LE(BigInt(value));
    else if (size === 4) bytes.writeUInt32LE(value);
    else bytes.writeUInt16LE(value);
    return bytes;
};
const point = (scalar) => {
    const key = crypto.createECDH("SM2");
    key.setPrivateKey(scalarBytes(scalar));
    return key.getPublicKey();
};
const xBar = (publicKey) => (BigInt("0x" + publicKey.subarray(1, 33).toString("hex")) & ((1n << 127n) - 1n)) | (1n << 127n);
const curve = {
    a: fromHex("fffffffeffffffffffffffffffffffffffffffff00000000fffffffffffffffc"),
    b: fromHex("28e9fa9e9d9f5e344d5a9e4bcf6509a7f39789f515ab8f92ddbcbd414d940e93"),
    n: BigInt("0xfffffffeffffffffffffffffffffffff7203df6b21c6052b53bbf40939d54123")
};
const generator = point(1n);
const localScalar = 1n;
const peerScalar = 2n;
const local = point(localScalar);
const peer = point(peerScalar);
const z = (publicKey) => hash(Buffer.concat([
    fromHex("0080"), Buffer.from("1234567812345678"), curve.a, curve.b,
    generator.subarray(1), publicKey.subarray(1)
]));
const zLocal = z(local);
const zPeer = z(peer);
// For known synthetic peer scalar, the nested source multiplications reduce to k*G.
const combined = peerScalar * (xBar(peer) + 1n) * localScalar * (xBar(local) + 1n) % curve.n;
const shared = point(combined);
const kdf = hash(Buffer.concat([shared.subarray(1), zLocal, zPeer, fromHex("00000001")]));
const vin = "TESTVIN0000000001";
const sessionId = 0x78563412;
const timestamp = 1800000000;
const card = Buffer.from("0123456789abcdef".repeat(5));
const selector = hash(Buffer.concat([le(sessionId, 4), Buffer.from([...vin].reverse().join(""))]));
const cardOffset = (selector[0] >>> 4) * 5;
const derived = hash(Buffer.concat([card.subarray(cardOffset, cardOffset + 5), kdf])).toString("hex");
const key = Buffer.from(derived.slice(0, 16));
const iv = Buffer.from(derived.slice(-16));
const encrypt = (bytes) => {
    const cipher = crypto.createCipheriv("sm4-cbc", key, iv);
    return Buffer.concat([cipher.update(bytes), cipher.final()]);
};
const wrap = (text) => {
    const payload = Buffer.from(encrypt(text).toString("base64"));
    return Buffer.concat([fromHex("aaab"), le(payload.length, 4), payload]);
};
const authentication = (minor) => {
    const configuration = minor >= 9 ? "56;2.00;08;16;1;0;0;0;" : "56;2.00;08;16;0;0;";
    const text = Buffer.from(`${timestamp};v1;test-account;test-device;${vin};1700000000;1900000000;${configuration}`);
    const signature = Buffer.from(Array.from({ length: 16 }, (_, index) => index + 1));
    const payload = Buffer.concat([
        le(sessionId, 4), local,
        Buffer.from(encrypt(Buffer.concat([le(text.length, 4), text, signature])).toString("base64")),
        fromHex("0109")
    ]);
    return Buffer.concat([fromHex("aaae"), le(payload.length, 2), payload]).toString("base64");
};
const command = (id) => {
    const body = Buffer.concat([le(timestamp, 8), Buffer.from([id])]);
    let crc = 0;
    for (const byte of body) {
        crc ^= byte;
        for (let index = 0; index < 8; index++) crc = ((crc << 1) ^ ((crc & 128) ? 7 : 0)) & 255;
    }
    return wrap(Buffer.concat([Buffer.from([crc]), body])).toString("hex");
};
console.log(JSON.stringify({
    local: local.toString("hex"), peer: peer.toString("hex"),
    zLocal: zLocal.toString("hex"), zPeer: zPeer.toString("hex"),
    combinedScalar: combined.toString(16), shared: shared.toString("hex"),
    kdf: kdf.toString("hex"), selector: selector.toString("hex"), cardOffset, derived,
    key: key.toString(), iv: iv.toString(),
    authentication8: authentication(8), authentication9: authentication(9),
    unlock: command(1), lock: command(2),
    responses: ["0;locked;", "2;3;00;", "0;;", "1;1234abcd;"].map((text) => ({
        text, frame: wrap(Buffer.from(text)).toString("hex")
    })),
    malformedUtf8Frame: wrap(fromHex("303bc328")).toString("hex")
}, null, 2));
