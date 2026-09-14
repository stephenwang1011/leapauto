# Bluetooth Key Protocol: Static Integration Contract

Evidence date: 2026-09-14. This document describes a bounded, offline reconstruction
of the ordinary FFFE/FFF2 Bluetooth key path. It is not a claim of interoperability
with a real vehicle or of server acceptance. No BLE scan, connection, control write,
or cloud request was used to derive these results.

## Sources and Scope

Source APK: `D:/young/work/hackapp/base.apk`.
SHA-256: `43A0EBE3442E4E7DD6C3C8C9C9B9C838C1AE6E8433C842509EF913996BF8D113`.

Navigation document: `D:/young/work/hackapp/BLUETOOTH_ANALYSIS.md`.
Implementation sources are in `analysis/payload_src/sources/defpackage/` beneath
that workspace, with original instructions in `analysis/smali1/`.

| Contract | Java source | Smali cross-check |
| --- | --- | --- |
| P-256 ECDH, card selection, key/IV | `xn0.t()` | `xn0.smali`, method `t`, especially lines 3240-3516 |
| SM2 X.509 extraction and custom agreement | `xn0.t()`, `kb1.e()` | `xn0.smali:2131`, `2297-2393`; `kb1.smali:718` |
| SM2 identity digest and KDF | `kb1.a()`, `xn0.t()` | `kb1.smali:21`, `84`; `xn0.smali:2467-2720` |
| SM3 digest and SM4-CBC | `kb1.c/d()`, `j91.c/d()` | `kb1.smali:443`, `536`; `j91.smali:2207` |
| Full authentication | `j91.f()` | `j91.smali:515`, payload construction at 1068-1230 |
| Command CRC and encryption | `j91.e()` | `j91.smali:2257`, CRC constants at 2319-2355 |
| UInt16/32/64 little endian | `cc3.a0/b0/c0()` | `cc3.smali:7964`, `8248`, `9376` |
| Frame extraction | `xj.c()`, `v91.a()` | `xj.smali`, method `c` |
| Event parsing and confirmation | `xj.c()`, `r91`, `u91`, `s91` | `xj.smali:4260`, `4385`, `4535`, `5275` |
| Authentication response | `u40.o()`, `xj.j()` | `u40.smali`, method `o`; `xj.smali`, method `j` |
| Manual versus passive settings | `h91`, `j91.f()` | `j91.smali:680-1015` |

The implemented subset supports keyType 0 (P-256 plus AES-CBC) and keyType 1
(SM2/SM3/SM4), full authentication, explicitly requested lock/unlock, and the
original passive configuration flags. Fast reconnect credentials are ignored and
not persisted. The EEED/EEE2 cabin channel and other remote-control commands are
outside this subset. Background residency is implemented only through the local
foreground service after an explicit, PIN-gated configuration save; it does not
turn RSSI into a phone-issued lock command.

## P-256 Session Derivation

Certificate fields: `ecdhPublicKey`, `keyType`, `passwordCard`, `plainText`,
`signResult`, `vin`. The certificate must belong to the currently selected VIN.
The P-256 public key uses standard Base64 encoding of the 65-byte uncompressed point
`04 || X32 || Y32`. `passwordCard` must contain at least 80 ASCII bytes.

1. Generate a fresh P-256 key pair and random UInt32 session ID.
2. ECDH with the vehicle public key yields the fixed-width 32-byte shared X coordinate.
3. Hash `UInt32LE(sessionID) || UTF8(reverse(VIN))` with SHA-256.
4. Compute `offset = (digest[0] unsigned >> 4) * 5`; select five card bytes at offset.
5. Hash `cardSlice5 || sharedX32` with SHA-256; encode as 64 lowercase hex characters.
6. The AES key is the UTF-8 bytes of the first 16 hex characters. The IV is the UTF-8
   bytes of the last 16 characters. This is not a split of the raw hash bytes.

The P-256 implementation uses platform JCA `EC`/`ECDH` and `AES/CBC/PKCS5Padding`;
it does not implement elliptic-curve multiplication or AES manually. Keys remain in memory
for the connection and are cleared when the session closes. The session binds a
SHA-256 fingerprint of length-prefixed certificate fields; authentication rejects
a certificate changed after derivation. No credentials or plaintext response
contents should be logged.

## SM2 Session Derivation

For keyType 1, `ecdhPublicKey` contains a complete X.509 Certificate as DER Base64
or a `BEGIN CERTIFICATE` PEM block. A bare SubjectPublicKeyInfo or 65-byte point is
not this certificate format. The source extracts the certificate's public-key bit
string and decodes it on `sm2p256v1`.

The implementation additionally requires the SubjectPublicKeyInfo algorithm to be
`id-ecPublicKey` (`1.2.840.10045.2.1`) with named curve `sm2p256v1`
(`1.2.156.10197.1.301`); this is stricter than the source, which only checked point
validity on the SM2 curve. Complete DER consumption, zero bit-string padding,
uncompressed `04` (65 bytes) or compressed `02/03` (33 bytes), and a valid finite
curve point are required. Malformed PEM, extra DER bytes, other curves and raw key
containers are rejected. This is protocol key extraction, not certificate-chain
or CA verification; the vehicle handles the existing `plainText/signResult` proof.

Using `n` for curve order, `G` for generator, `P` for peer public point, and a fresh
private scalar `r` uniformly selected in `[1, n-1]`, the recovered custom agreement is:

```text
R = [r]G
xBar(Q) = 2^127 + (x(Q) mod 2^127)
t = ((xBar(R) + 1) * r) mod n
U = [t]([(xBar(P) + 1) mod n]P)
Z(Q) = SM3(0080 || ASCII("1234567812345678") || a32 || b32 || Gx32 || Gy32 || Qx32 || Qy32)
sharedMaterial = SM3(Ux32 || Uy32 || Z(R) || Z(P) || 00000001)
```

Coordinates and curve coefficients are unsigned 32-byte big endian. `0080` is the
16-bit big endian user-ID length in bits; `00000001` is the first KDF counter in
32-bit big endian. Z(local) precedes Z(peer). The required output is exactly the
first 32-byte SM3 KDF block, so no additional counter blocks are produced. This is
the specific recovered key-agreement construction, not a call to generic ECDH or
to a generic SM2 encryption engine.

The remaining card selection and final digest follow the P-256 layout, replacing
both SHA-256 calls with SM3 and `sharedX32` with `sharedMaterial`. The symmetric key
and IV still use the first and last 16 lowercase hex characters as ASCII bytes.
Full-authentication and command payloads use SM4/CBC/PKCS7Padding.

`BleSm2Crypto` uses the Bouncy Castle low-level curve, SM3 and SM4 APIs from
`org.bouncycastle:bcprov-jdk18on:1.86`. No global JCA provider is registered or
replaced. P-256 continues using the existing platform implementation. The SM2
agreement output and session key/IV buffers are cleared after use or connection
close; immutable library scalar/point objects follow normal JVM object lifetime.
Certificate identity remains bound by SHA-256 for both types; that fingerprint is
an internal safety check and is not part of the vehicle protocol.

## Full Authentication (AA AE)

Split the certificate's `plainText` on `;`, retaining empty fields. It must contain
at least six fields. Replace zero-based field 1 with the authenticated account ID
and field 2 with the current device ID. Both identifiers must be nonempty and must
not contain `;`. Preserve the other certificate fields.

Build UTF-8 text:

```text
epochSeconds;modifiedCertificateText;distance;coefficient;unlockCalibration;lockCalibration;flags;
```

The calibration defaults are `56;2.00;08;16`. The four local settings are
`enabled`, `autoUnlock`, `autoLock`, and `buttonEnabled`; all default to `false`.
The textual flag fields use these Boolean values as `0` or `1`:

| Protocol minor | Ordered flags |
| --- | --- |
| Below 9 | `enabled && autoUnlock`, `enabled && autoLock` |
| At least 9 | `enabled`, `enabled && autoLock`, `enabled && autoUnlock`, `enabled && buttonEnabled` |

The legacy form combines the master and auto-unlock bits. The ordinary manual
session uses `BlePassiveConfiguration.MANUAL` (`enabled=true`, other fields false),
which produces `0;0;` for legacy vehicles and `1;0;0;0;` for newer vehicles; this
transport profile does not enable the app's persistent background master switch.
Managed sessions use the explicitly saved desired configuration instead. The
application rejects enabled button control for minor below 9, and the settings UI
also disables it while capability is unknown.

Authentication already carries configuration. A later command-3 acknowledgement
is the client's applied-state gate, not evidence that the vehicle waits until
that acknowledgement to start using the authentication configuration. Calibration
units, real distance thresholds and physical behavior remain unverified.

```text
plaintext = UInt32LE(UTF8Text.size) || UTF8Text || Base64Decode(signResult)
ciphertextText = ASCII(Base64(sessionEncrypt(plaintext)))
payload = UInt32LE(sessionID) || temporaryPublicKey65 || ciphertextText || 01 09
frame = AA AE || UInt16LE(payload.size) || payload
```

The trailing `01 09` is included for both legacy and newer protocol minor values,
as confirmed in original smali. It is included in the declared payload length.
The signature has no separate length field; the inner UInt32 describes only the
UTF-8 text. No command CRC is prepended to full-authentication plaintext.
`sessionEncrypt` is AES-CBC-PKCS5 for keyType 0 or SM4-CBC-PKCS7 for keyType 1.
SM2 does not add an algorithm byte or an SM3 trailer to full authentication;
the SM3 trailer in the source belongs to fast reconnect, which is not implemented.

The advertised minor is encoded by service UUID
`000001xx-0000-1000-8000-00805f9b34fb`; `xx` is hexadecimal. The source fallback
for an unbound device with no advertised minor is 8.

Scan metadata must be merged by address across advertisements, as in `xj.b()`
(`xj.java:254`, `xj.smali:1281/1317/1452/1938`). A later advertised minor replaces
the previous one; an advertisement without a minor preserves the known value.
FFFE and minor advertisements may arrive separately and in either order. Scanning
therefore uses no FFFE controller filter, but only displays devices observed with
FFFE that are connectable. The per-scan cache is bounded to 128 addresses and the
visible list to 30 devices; a new scan clears it. Authentication diagnostics report
the selected minor and source (`1` advertised, `2` default, `3` saved binding,
`0` unknown) without recording device addresses or UUID lists.

## Authentication Rejection Investigation (2026-09-14)

User-supplied diagnostics from version 3.3.33 show successful SM2 derivation,
GATT connection, FFF2 notification subscription, MTU 247 and both writes of a
311-byte AA AE frame. A 26-byte AA AC event then reports result code 9 during
authentication. No authenticated state or lock command appears in that trace.
The user reports that the original app succeeds on the same phone and vehicle;
this is user-provided field evidence, not an agent-run interoperability test.

Original `xj.c()` reads the last event payload byte as the rejection code. Code 9
has no named meaning there; only 3/4/7 have special reconnect-cache handling.
Do not label 9 as an expired certificate, wrong password, or unsupported SM2.
The app now names the rejected phase and numeric code without prescribing a cause.

Confirmed implementation discrepancies addressed in this investigation are split
advertisement minor loss, stale device identity across certificate refresh and
authentication, and the certificate request's app/sub-version header mix-up.
None is individually established as the cause of this field rejection. Offline
SM2, ZA/ZB, SM3 KDF, card slicing, SM4 padding, auth fields, trailer, write type and
chunk-size comparisons found no corresponding discrepancy. The different MTU vs.
service discovery order has not been shown to cause rejection and is unchanged.

## Manual Commands (AA AB)

Only `UNLOCK = 1` and `LOCK = 2` are enabled by this implementation. Both have an
empty command body.

```text
body = UInt64LE(epochSeconds) || commandByte
plaintext = CRC8(body) || body
ciphertextText = ASCII(Base64(sessionEncrypt(plaintext)))
frame = AA AB || UInt32LE(ciphertextText.size) || ciphertextText
```

CRC8 is unreflected, initial value 0, polynomial 0x07, and no final XOR. Original
smali confirms the 0xFF masks, 0x80 high-bit test, and 0x07 XOR. A command write must
follow the application's user confirmation and operation-password boundary.

## Incoming Frames and Confirmation

| Type | Length offset/width | Total bytes |
| --- | --- | --- |
| AA AB | offset 2, UInt32LE | 6 + length |
| AA AC | offset 6, UInt32LE | 10 + length |
| AA AE / AA EE | offset 2, UInt16LE | 4 + length |

Reassembly supports notification fragments, coalesced frames, and leading noise.
The app caps a single reassembled frame at 65,539 bytes; the source cap was 1 MiB.
An oversized declared length is rejected before buffering that payload. Connection
changes must clear the decoder and discard all pending results.

AA AB payload is Base64 text. After session-specific AES or SM4 decryption, parse UTF-8 semicolon fields;
this response path does not contain the outbound command's binary CRC/timestamp.

| First text field | Source meaning | Integration handling |
| --- | --- | --- |
| `0`, with nonempty second field | Authentication state / lock-state text | Completes pending authentication only |
| `1` | Reconnect credential | Ignore; do not store or log |
| `2` | Command identifier, optional result, extra fields | Parse, but never use as a lock/unlock confirmation |
| Anything else | Other/unknown response | Does not confirm authentication or control |

The second field of a type-0 response is not mapped to a displayed lock state:
its exact enumerations have not been verified. A successful decode is required;
GATT connection or write success alone is insufficient for authentication.

For AA AC, event type is unsigned byte at frame offset 5:

- Type 1, nonempty payload: the final payload byte is a result/rejection code.
  The source rejects the operation for every such event. Codes 3, 4, and 7 also
  invalidate fast reconnect credentials, which this integration does not use.
- Type 2, at least three payload bytes: `[crc8, trigger, operation, ...]`.
  Trigger values are 0=None, 1=Active, 2=Passive, 3=Button, otherwise unknown.
  Operation values are 0=None, 1=Unlock, 2=Lock, otherwise unknown.
- Type 3 and other events do not confirm a manual operation in this subset.

A pending manual operation is confirmed only by type 2 with Active trigger and the
matching lock/unlock action on the same authenticated connection, after writing
has started. The source does not compare the event's `crc8` field with the outbound
CRC. Whether it echoes the command CRC has not been established. There is also no
verified request-ID or nonce binding for this plaintext event. Delayed events for
the same action remain a device-validation risk; do not present stronger guarantees.

Only one control request may be pending. The integration must close the connection
after each completed manual command; another action requires fresh authentication
to reduce delayed same-action event ambiguity. Once writing has started, failure or
confirmation timeout must be reported as uncertain; never automatically submit
the same action over HTTP or resend it. A GATT write callback only acknowledges
transport progress.

## Independent Synthetic Vectors

All inputs below are synthetic, not account credentials or real vehicle data.
`BleKeyProtocolTest.kt` contains full byte-for-byte frame vectors generated
independently with Node's OpenSSL-backed `createECDH` and `createCipheriv`.

```text
curve: prime256v1 / secp256r1
local private scalar: 1
peer private scalar: 2
VIN: TESTVIN0000000001
session ID: 0x78563412 (wire bytes 12 34 56 78)
timestamp: 1800000000 seconds
password card: 0123456789abcdef repeated 5 times
account/device: test-account / test-device
plainText: v1;old-account;old-device;TESTVIN0000000001;1700000000;1900000000
signature bytes: 01 02 03 04 05 06 07 08 09 0a 0b 0c 0d 0e 0f 10
shared X: 7cf27b188d034f7e8a52380304b51ac3c08969e277f21b35a60b48fc47669978
card offset: 50
derived hex: 157589bbe20f3839af5238ab69d8ca1661df0c340ee8886d30a86955bf431439
AES key text: 157589bbe20f3839
IV text: 30a86955bf431439
legacy/newer authentication text length: 97 / 101 bytes
authentication outer payload length: 243 bytes in either vector
unlock CRC: 0x7c
lock CRC: 0x75
CRC8("123456789"): 0xf4
unlock frame hex: aaab180000005578394f32343073464c6f41496e704d3172506e5a413d3d
lock frame hex: aaab18000000774465434e5443727a6d3430706a4e41534b666d54413d3d
synthetic Active unlock event: aaac00000002030000007c0101
```

SM2 vectors reuse the same synthetic VIN, timestamp, card, identities, proof text,
signature and private scalars. The exact independent generator is checked in at
`app/src/test/resources/bluetooth/generate-sm2-vectors.cjs`; execute it offline with:

```powershell
node app/src/test/resources/bluetooth/generate-sm2-vectors.cjs
```

It uses Node/OpenSSL `createECDH("SM2")`, `createHash("sm3")` and
`createCipheriv("sm4-cbc")`. For the known synthetic peer scalar 2 it computes the
equivalent generator scalar `2 * (xBar(P)+1) * r * (xBar(R)+1) mod n`, then asks
OpenSSL for that public point. No curve point multiplication is implemented by the
reference script. The full output includes ZA(local/peer), shared point, KDF,
card selector, authentication frames for minor 8 and 9, lock/unlock frames,
and valid and malformed response frames.

```text
SM2 Z(local): 5b32bfe35482899b195d72c09d33ccdb465b2ded883240ff91f120a68bc91de8
SM2 Z(peer): 60371fc8ee7c0ce0aa65d8837b874cd744602ad5ac009830fa40ba371984526a
shared X: 84bc6a2aa48bb01a485d83f235d53780e1dff6f8d24cef22601959b93c485553
shared Y: 72314b7108a7dc87d3a9cf66f946aa744cab7c3c3928abe9024ce6aa5a27b08b
SM3 KDF: 5f86e6b17708f60dfb5d8e3f3ce2b35d3d29744d51c46fc14cdb7bb95fdaf48b
card offset: 15
derived hex: 6fa340d39f4141fea70453772e7dc265248f0c0a1fe1dc3e437410132db0dd25
SM4 key text: 6fa340d39f4141fe
IV text: 437410132db0dd25
unlock frame hex: aaab1800000065447542464750772f3477547866484335386b3336773d3d
lock frame hex: aaab18000000594c565a4144765a4155776d6433354a394855584e773d3d
```

An independent Python check using `hashlib` SM3 and `cryptography` SM4 reproduced
both identity digests, the KDF/card/key derivation, and decrypted both complete
authentication frames, both commands and four response vectors. It used the
OpenSSL-produced shared point, so the independent EC multiplication comparison
is specifically OpenSSL versus the Kotlin/Bouncy Castle agreement-vector test.

`BleSm2ProtocolTest.kt` constructs structurally valid synthetic X.509 DER fixtures
with placeholder signatures, then verifies the full frames against those fixed
OpenSSL vectors. They are not real server certificates. Tests also cover standard
SM3, PEM/Base64 whitespace, compressed points, certificate/curve/scalar rejection,
certificate binding, ciphertext and UTF-8 failures, key cleanup, random session
creation and absence of global provider registration. Existing P-256 vectors and
frame/event/MTU tests remain in place. Test results and Android/device acceptance
are reported separately; these offline checks do not establish vehicle acceptance.

## Current Managed-Key Addendum (2026-09-14)

The settings flow adds `cmdId=3` configuration synchronization to the manual subset. Four switches (`enabled`, `autoUnlock`, `autoLock`, `buttonEnabled`) default to false. A device is bound only after full authentication and is keyed to account, VIN, address, protocol minor and certificate fingerprint in encrypted storage. `desired`, `applied`, `requested`, `revision` and `confirmedRevision` are separate; repeated requests still need a matching acknowledgement.

The managed runtime uses a non-exported `connectedDevice` foreground service after explicit PIN-gated save and confirmation. It performs fresh authentication on reconnect and may synchronize a pending disable before stopping. It does not calculate distance from RSSI, send phone-generated automatic lock commands or replay manual actions. Force-stop, task removal, vendor power restrictions and permission/notification policy can stop residency; verify these behaviors on the target device.

The earlier installed package was `3.3.29` manual-only. This addendum describes current source pending a new release build and does not claim installation or vehicle interoperability. Keep diagnostics and test records free of VIN, MAC, certificate, key, PIN and raw frames.
