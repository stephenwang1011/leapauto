import Foundation
import XCTest
@testable import LeapCore

/// Byte vectors copied from upstream BleKeyProtocolTest; no radio or cryptography.
final class BLEProtocolTests: XCTestCase {
    private func hex(_ value: String) -> Data {
        let bytes = Array(value.utf8)
        return Data(stride(from: 0, to: bytes.count, by: 2).map {
            UInt8(String(decoding: bytes[$0..<$0 + 2], as: UTF8.self), radix: 16)!
        })
    }
    private func frame(_ value: String) throws -> BLEFrame {
        var decoder = try BLEFrameDecoder()
        return try XCTUnwrap(decoder.append(hex(value)).first)
    }
    private func event(_ type: UInt8, _ payload: [UInt8] = []) throws -> BLEFrame {
        var decoder = try BLEFrameDecoder()
        return try XCTUnwrap(decoder.append(Data([0xAA, 0xAC, 0, 0, 0, type, UInt8(payload.count), 0, 0, 0] + payload)).first)
    }
    private let encrypted = "aaab180000004676454a4669644f47712f505a72663852356a4e61673d3d"
    private let activeUnlock = "aaac00000002030000007c0101"

    func testHardwareGateCannotBeEnabledByConstruction() {
        XCTAssertFalse(BLEHardwareFeatureGate.isEnabled)
        XCTAssertThrowsError(try BLEHardwareFeatureGate.requireEnabled()) {
            XCTAssertEqual($0 as? BLEProtocolError, .hardwareNotAccepted)
        }
    }
    func testFragmentsCoalescedFramesAndHeaderNoiseUseUpstreamVector() throws {
        let first = hex(encrypted), second = hex(activeUnlock)
        var decoder = try BLEFrameDecoder()
        XCTAssertTrue(try decoder.append(hex("1122aa99") + first.prefix(1)).isEmpty)
        XCTAssertTrue(try decoder.append(first.subdata(in: 1..<9)).isEmpty)
        let result = try decoder.append(first.dropFirst(9) + second)
        XCTAssertEqual(result.map(\.type), [.encrypted, .event])
        XCTAssertEqual(result[0].payload, first.dropFirst(6))
        XCTAssertEqual(result[1].payload, hex("7c0101"))
        XCTAssertEqual(decoder.bufferedByteCount, 0)
    }
    func testEveryByteBoundaryAndSingleByteNotificationsReassemble() throws {
        let stream = hex(encrypted + activeUnlock + "aaae0100ff" + "aaee0200abcd")
        for split in 0...stream.count {
            var decoder = try BLEFrameDecoder()
            let first = try decoder.append(stream.prefix(split))
            let second = try decoder.append(stream.dropFirst(split))
            XCTAssertEqual((first + second).map(\.type), [.encrypted, .event, .authentication, .reconnect], "split \(split)")
        }
        var decoder = try BLEFrameDecoder(), frames: [BLEFrame] = []
        for byte in stream { frames += try decoder.append(Data([byte])) }
        XCTAssertEqual(frames.map(\.type), [.encrypted, .event, .authentication, .reconnect])
    }
    func testAllHeaderFormatsUseExactLengthWidths() throws {
        var decoder = try BLEFrameDecoder()
        let frames = try decoder.append(hex("aaae0100ff" + "aaee0200abcd" + "aaac000000010100000003"))
        XCTAssertEqual(frames.map(\.type), [.authentication, .reconnect, .event])
        XCTAssertEqual(frames[1].payload, hex("abcd"))
        XCTAssertEqual(BLEKeyProtocol.parseEvent(frames[2]), .rejected(resultCode: 3))
    }
    func testAuthenticationFrameFromIndependentUpstreamVectorIsFramedWithoutDecrypting() throws {
        let value = "qq7zABI0VngEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9WdWcnc4bDNkZWNuYVdBZGRCQ2g2TExUQkM1OVBSTVI4aUxJYmVEcms2WUFXQXZrdFJTT1BBWFZMWlRrWngyd2hNWmhzNFVRWWZqSHlJdWhucmJja2tpOHpZMFp3SWptWUVXdUNtRmVRa3NMdGlRZjZoR1dSTkhYV25GNzdENTJ1ZFVLamNBTVBnQ3luR3VQN2ZWY3JVZ1BWOVF5NTNXTk1lNzcrLzU1bnJ2Yz0BCQ=="
        let data = try XCTUnwrap(Data(base64Encoded: value))
        var decoder = try BLEFrameDecoder()
        let frames = try decoder.append(data)
        XCTAssertEqual(frames.count, 1); XCTAssertEqual(frames[0].type, .authentication)
        XCTAssertEqual(frames[0].payload.count, 243)
        XCTAssertEqual(frames[0].bytes, data)
    }
    func testClearDiscardsPriorConnectionPartialFrame() throws {
        var decoder = try BLEFrameDecoder()
        XCTAssertTrue(try decoder.append(hex("aaab18000000")).isEmpty)
        decoder.clear()
        XCTAssertEqual(decoder.bufferedByteCount, 0)
        XCTAssertEqual(try decoder.append(hex(activeUnlock)).count, 1)
    }
    func testCorruptAndZeroLengthsCannotHideFollowingValidFrame() throws {
        var decoder = try BLEFrameDecoder(maxFrameSize: 64)
        for prefix in ["aaabffffffff", "aaab00000000", "aaac00000002ffffffff", "aaaeffff", "aaeeffff", "aa00"] {
            XCTAssertEqual(try decoder.append(hex(prefix + activeUnlock)).map(\.type), [.event], prefix)
            XCTAssertLessThanOrEqual(decoder.bufferedByteCount, 64)
        }
    }
    func testInvalidLimitsAndOversizedNotificationsAreRejectedWithoutConsumingBuffer() throws {
        for limit in [Int.min, -1, 0, 9, 1_048_577, Int.max] {
            XCTAssertThrowsError(try BLEFrameDecoder(maxFrameSize: limit))
        }
        var decoder = try BLEFrameDecoder(maxFrameSize: 64)
        XCTAssertTrue(try decoder.append(hex("aaac0000000203000000")).isEmpty)
        XCTAssertThrowsError(try decoder.append(Data(repeating: 0, count: 65))) {
            XCTAssertEqual($0 as? BLEProtocolError, .notificationTooLarge)
        }
        XCTAssertEqual(try decoder.append(hex("7c0101")).count, 1)
    }
    func testNoiseRetainsOnlyPossiblePrefixAndBoundedPartialFrame() throws {
        var decoder = try BLEFrameDecoder(maxFrameSize: 64)
        for _ in 0..<32 { XCTAssertTrue(try decoder.append(Data(repeating: 0x99, count: 64)).isEmpty) }
        XCTAssertEqual(decoder.bufferedByteCount, 0)
        XCTAssertTrue(try decoder.append(Data([0xAA])).isEmpty)
        XCTAssertEqual(decoder.bufferedByteCount, 1)
        XCTAssertTrue(try decoder.append(hex("ab3a000000") + Data(repeating: 1, count: 57)).isEmpty)
        XCTAssertEqual(decoder.bufferedByteCount, 63)
        XCTAssertEqual(try decoder.append(Data([1])).first?.bytes.count, 64)
    }
    func testZeroLengthNonEncryptedFramesStayUnknown() throws {
        var decoder = try BLEFrameDecoder()
        let frames = try decoder.append(hex("aaae0000aaee0000aaac0000000200000000"))
        XCTAssertEqual(frames.count, 3)
        XCTAssertTrue(frames.allSatisfy { $0.payload.isEmpty })
        XCTAssertTrue(frames.allSatisfy { BLEKeyProtocol.parseEvent($0) == .unknown })
    }
    func testLengthWidthsPreservePayloadHeadersAndMaximumAuthenticationSize() throws {
        // A valid 256-byte payload contains header-like bytes; the decoder must
        // not resynchronize inside it while waiting for the declared body.
        var decoder = try BLEFrameDecoder()
        let payload = Data([0xAA, 0xAB, 0, 0, 0, 0] + Array(repeating: UInt8(1), count: 250))
        let bytes = Data([0xAA, 0xAC, 0, 0, 0, 2, 0, 1, 0, 0]) + payload
        XCTAssertTrue(try decoder.append(bytes.prefix(20)).isEmpty)
        XCTAssertEqual(try decoder.append(bytes.dropFirst(20)).first?.payload, payload)
        let largest = Data([0xAA, 0xAE, 0xFF, 0xFF]) + Data(repeating: 1, count: 65_535)
        XCTAssertEqual(try decoder.append(largest).first?.bytes.count, 65_539)
        // This would only look small if UInt32's high bytes were ignored.
        XCTAssertEqual(try decoder.append(hex("aaab01000100" + activeUnlock)).map(\.type), [.event])
    }
    func testDeterministicNoisyNotificationsKeepBufferBounded() throws {
        var decoder = try BLEFrameDecoder(maxFrameSize: 64)
        var seed: UInt32 = 0x12345678
        for index in 0..<2_000 {
            var bytes: [UInt8] = []
            for _ in 0..<(index % 64 + 1) {
                seed = 1_664_525 &* seed &+ 1_013_904_223
                bytes.append(UInt8(truncatingIfNeeded: seed >> 16))
            }
            let frames = try decoder.append(Data(bytes))
            XCTAssertLessThanOrEqual(decoder.bufferedByteCount, 64)
            XCTAssertTrue(frames.allSatisfy { $0.bytes.count <= 64 && $0.bytes.count >= $0.type.headerSize })
        }
        decoder.clear()
        XCTAssertEqual(try decoder.append(hex(activeUnlock)).map(\.type), [.event])
    }
    func testDefaultProtocolMinorDistinguishesOnlyKnownModelDefaults() {
        for model in ["C16", "零跑C16", "C10", "c10"] { XCTAssertEqual(BLEKeyProtocol.defaultMinor(carType: model), 9) }
        for model in ["T03", "C11", "C01", "", "unknown"] { XCTAssertEqual(BLEKeyProtocol.defaultMinor(carType: model), 8) }
        XCTAssertEqual(BLEKeyProtocol.defaultMinor(), 8)
    }
    func testAdvertisingMinorMatchesOnlyExactUUIDSuffixAndUsesFirstMatch() {
        func uuid(_ value: String) -> UUID { UUID(uuidString: value)! }
        let minor8 = uuid("00000108-0000-1000-8000-00805f9b34fb")
        let minor255 = uuid("000001ff-0000-1000-8000-00805f9b34fb")
        XCTAssertNil(BLEKeyProtocol.advertisedProtocolMinor(serviceUUIDs: []))
        XCTAssertNil(BLEKeyProtocol.advertisedProtocolMinor(serviceUUIDs: [BLEKeyProtocol.serviceUUID]))
        XCTAssertNil(BLEKeyProtocol.advertisedProtocolMinor(serviceUUIDs: [uuid("00000109-0000-1000-8000-00805f9b34fc")]))
        XCTAssertEqual(BLEKeyProtocol.advertisedProtocolMinor(serviceUUIDs: [minor255]), 255)
        XCTAssertEqual(BLEKeyProtocol.protocolMinor(serviceUUIDs: [BLEKeyProtocol.serviceUUID, minor8, minor255], carType: "C16"), 8)
        XCTAssertEqual(BLEKeyProtocol.protocolMinor(serviceUUIDs: [], carType: "C16"), 9)
    }
    func testUpstreamMTULimitsAndChunkVectors() {
        for mtu in [Int.min, 0, 23] { XCTAssertEqual(BLEKeyProtocol.chunkLimit(mtu: mtu), 20) }
        for profile in [BLEChunkProfile.legacy160, .onePaoV010] {
            for mtu in [200, Int.max] { XCTAssertEqual(BLEKeyProtocol.chunkLimit(mtu: mtu, profile: profile), 160) }
        }
        XCTAssertEqual(BLEKeyProtocol.chunks(frame: Data(repeating: 0, count: 41), mtu: 23).map(\.count), [20, 20, 1])
        XCTAssertEqual(BLEKeyProtocol.chunks(frame: Data(repeating: 0, count: 161), mtu: 200).map(\.count), [160, 1])
        XCTAssertEqual(BLEKeyProtocol.chunks(frame: Data(repeating: 0, count: 311), mtu: 247, profile: .onePaoV010).map(\.count), [160, 151])
        XCTAssertTrue(BLEKeyProtocol.chunks(frame: Data(), mtu: 23).isEmpty)
    }
    func testCoreBluetoothPayloadLimitDoesNotSubtractATTHeader() throws {
        let frame = Data((0..<41).map(UInt8.init))
        let chunks = try BLEKeyProtocol.chunks(frame: frame, maximumWriteValueLength: 20)
        XCTAssertEqual(chunks.map(\.count), [20, 20, 1]); XCTAssertEqual(chunks.reduce(Data(), +), frame)
        XCTAssertEqual(try BLEKeyProtocol.chunks(frame: Data(repeating: 1, count: 161), maximumWriteValueLength: 512).map(\.count), [160, 1])
        XCTAssertEqual(try BLEKeyProtocol.chunks(frame: frame, maximumWriteValueLength: 1).count, 41)
        for limit in [Int.min, -1, 0] { XCTAssertThrowsError(try BLEKeyProtocol.chunks(frame: frame, maximumWriteValueLength: limit)) }
    }
    func testCRC8MatchesIndependentVector() { XCTAssertEqual(BLEKeyProtocol.crc8(Data("123456789".utf8)), 0xF4) }
    func testOnlyActiveMatchingActionEventCanConfirm() throws {
        let unlock = BLEKeyProtocol.parseEvent(try event(2, [124, 1, 1]))
        XCTAssertEqual(unlock, .lockAction(crc8: 124, trigger: 1, action: .unlock))
        XCTAssertTrue(unlock.confirms(.unlock)); XCTAssertFalse(unlock.confirms(.lock))
        XCTAssertTrue(BLEKeyProtocol.parseEvent(try event(2, [117, 1, 2])).confirms(.lock))
        for trigger: UInt8 in [0, 2, 3, 255] { XCTAssertFalse(BLEKeyProtocol.parseEvent(try event(2, [124, trigger, 1])).confirms(.unlock)) }
        XCTAssertFalse(BLEKeyProtocol.parseEvent(try event(2, [124, 1, 255])).confirms(.unlock))
    }
    func testRejectionUsesLastPayloadByteAndTruncatedEventsRemainUnknown() throws {
        XCTAssertEqual(BLEKeyProtocol.parseEvent(try event(1, [0, 7])), .rejected(resultCode: 7))
        for pair: (UInt8, [UInt8]) in [(1, []), (2, [124, 1]), (3, [124, 1])] {
            XCTAssertEqual(BLEKeyProtocol.parseEvent(try event(pair.0, pair.1)), .unknown)
        }
        XCTAssertEqual(BLEKeyProtocol.parseEvent(try frame(encrypted)), .unknown)
    }
    func testDecryptedResponseParserPreservesUnknownLockState() throws {
        XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data("0;locked".utf8)), .authenticated(lockState: "locked"))
        XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data("0;future-unknown-state".utf8)), .authenticated(lockState: "future-unknown-state"))
        for value in ["", "0", "0;", "9;ignored", "1;gg", "1;"] {
            XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data(value.utf8)), .unknown)
        }
    }
    func testResponseDistinguishesCommandReconnectAndTrailingEmptyFields() throws {
        let configuration = try BLEKeyProtocol.parseDecryptedResponse(Data("2;3;00".utf8))
        XCTAssertEqual(configuration, .commandResult(identifier: "3", result: "00")); XCTAssertTrue(configuration.confirmsConfiguration)
        XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data("2;3".utf8)), .commandResult(identifier: "3", result: nil))
        XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data("2;3;".utf8)), .commandResult(identifier: "3", result: ""))
        XCTAssertEqual(try BLEKeyProtocol.parseDecryptedResponse(Data("1;00Aa10ff".utf8)), .reconnectCredential(try BLEReconnectCredential(hex: "00aa10ff")))
        for value in ["2;3;000", "2;03;0", "2;3; 0", "2;3;1", "2;1;0"] {
            XCTAssertFalse(try BLEKeyProtocol.parseDecryptedResponse(Data(value.utf8)).confirmsConfiguration)
        }
    }
    func testMalformedUTF8AndOversizePlaintextAreRejected() {
        for data in [Data([0xC0, 0x80]), Data([0xFF]), Data([0xE2, 0x82])] {
            XCTAssertThrowsError(try BLEKeyProtocol.parseDecryptedResponse(data)) { XCTAssertEqual($0 as? BLEProtocolError, .invalidUTF8) }
        }
        XCTAssertThrowsError(try BLEKeyProtocol.parseDecryptedResponse(Data(repeating: 65, count: BLEKeyProtocol.maximumFrameSize + 1)))
    }
    func testReconnectCredentialValidationAndRedaction() throws {
        let credential = try BLEReconnectCredential(hex: "00Aa10ff")
        XCTAssertEqual(credential.hex, "00aa10ff"); XCTAssertEqual(credential.bytes, hex("00aa10ff"))
        for value in ["", "0", "0x", "gg", "１２", "aa ", String(repeating: "aa", count: 513)] {
            XCTAssertThrowsError(try BLEReconnectCredential(hex: value), value)
        }
        XCTAssertEqual(try BLEReconnectCredential(hex: String(repeating: "aa", count: 512)).bytes.count, 512)
        XCTAssertFalse(String(reflecting: credential).contains(credential.hex))
        XCTAssertFalse(String(reflecting: BLEResponse.reconnectCredential(credential)).contains(credential.hex))
    }
    func testSerialWriteQueueRequiresAcknowledgmentAndBoundsFrames() throws {
        var queue = BLEWriteQueue()
        XCTAssertTrue(queue.isIdle)
        XCTAssertThrowsError(try queue.enqueue(frame: Data(), maximumWriteValueLength: 20))
        XCTAssertThrowsError(try queue.enqueue(frame: Data([1]), maximumWriteValueLength: 0))
        XCTAssertThrowsError(try queue.enqueue(frame: Data(repeating: 0, count: BLEKeyProtocol.maximumFrameSize + 1), maximumWriteValueLength: 20))
        let frame = Data((0..<41).map(UInt8.init))
        try queue.enqueue(frame: frame, maximumWriteValueLength: 20)
        XCTAssertFalse(queue.isIdle)
        XCTAssertEqual(queue.takeNext(), frame.prefix(20)); XCTAssertNil(queue.takeNext())
        XCTAssertThrowsError(try queue.enqueue(frame: Data([1]), maximumWriteValueLength: 20))
        XCTAssertFalse(try queue.acknowledge())
        XCTAssertEqual(queue.takeNext(), frame.subdata(in: 20..<40)); XCTAssertFalse(try queue.acknowledge())
        XCTAssertEqual(queue.takeNext(), frame.suffix(1)); XCTAssertTrue(try queue.acknowledge())
        XCTAssertTrue(queue.isIdle); XCTAssertNil(queue.takeNext())
        XCTAssertThrowsError(try queue.acknowledge())
    }
    func testQueueClearDropsConnectionBytesAndLateAcknowledgment() throws {
        var queue = BLEWriteQueue()
        try queue.enqueue(frame: hex(encrypted), maximumWriteValueLength: 20)
        XCTAssertNotNil(queue.takeNext()); queue.clear()
        XCTAssertTrue(queue.isIdle); XCTAssertNil(queue.takeNext()); XCTAssertThrowsError(try queue.acknowledge())
    }
    func testConnectionPhasesCannotEnableControlsEarly() {
        for phase in BLEConnectionPhase.allCases { XCTAssertEqual(phase.canControl, phase == .ready) }
    }
    func testOnlyCurrentMatchingActionAfterWriteBeforeTimeoutConfirms() throws {
        var tracker = BLECommandTracker()
        XCTAssertTrue(try tracker.begin(.lock, nowMilliseconds: 100))
        XCTAssertFalse(try tracker.begin(.unlock, nowMilliseconds: 101))
        XCTAssertNil(tracker.confirm(.lock, nowMilliseconds: 102)); tracker.markWriteStarted()
        XCTAssertNil(tracker.confirm(.unlock, nowMilliseconds: 103))
        XCTAssertEqual(tracker.confirm(.lock, nowMilliseconds: 104), .confirmed)
        XCTAssertNil(tracker.confirm(.lock, nowMilliseconds: 105))
        XCTAssertFalse(try tracker.begin(.lock, nowMilliseconds: 106))
    }
    func testPassiveEventsAndClockRollbackCannotConfirmCommand() throws {
        var tracker = BLECommandTracker()
        try tracker.begin(.unlock, nowMilliseconds: 100); tracker.markWriteStarted()
        XCTAssertNil(tracker.receive(.lockAction(crc8: 124, trigger: 0, action: .unlock), nowMilliseconds: 101))
        XCTAssertNil(tracker.receive(.rejected(resultCode: 0), nowMilliseconds: 102))
        XCTAssertNil(tracker.confirm(.unlock, nowMilliseconds: 99))
        XCTAssertEqual(tracker.receive(.lockAction(crc8: 124, trigger: 1, action: .unlock), nowMilliseconds: 103), .confirmed)
    }
    func testDisconnectAndTimeoutAfterWriteRemainUnknown() throws {
        var tracker = BLECommandTracker()
        try tracker.begin(.unlock, nowMilliseconds: 1); tracker.markWriteStarted()
        XCTAssertNil(tracker.confirm(.unlock, nowMilliseconds: 6_001))
        XCTAssertEqual(tracker.end(), .unknown); XCTAssertNil(tracker.end())
        XCTAssertNil(tracker.confirm(.unlock, nowMilliseconds: 6_002))
        XCTAssertFalse(try tracker.begin(.unlock, nowMilliseconds: 6_003))
    }
    func testCancellationBeforeWriteIsNotSentAndInvalidClocksDoNotConsumeConnection() throws {
        var tracker = BLECommandTracker()
        for time in [Int64(-1), Int64.max] { XCTAssertThrowsError(try tracker.begin(.lock, nowMilliseconds: time)) }
        XCTAssertTrue(try tracker.begin(.lock, nowMilliseconds: 0))
        XCTAssertEqual(tracker.end(), .notSent)
    }
    func testIdentityAndConfirmationInvalidateOnEveryBoundary() {
        let identity = BLESessionIdentity(accountID: "ACCOUNT-A", vin: "VIN-A", generation: 3, deviceID: "DEVICE-A")
        let confirmation = BLEControlConfirmation(action: .lock, identity: identity, connectionGeneration: 8)
        func current(_ identity: BLESessionIdentity?, generation: UInt64 = 8, foreground: Bool = true, authenticated: Bool = true, visible: Bool = true) -> Bool {
            confirmation.isCurrent(identity: identity, connectionGeneration: generation, foreground: foreground, authenticated: authenticated, managementVisible: visible)
        }
        XCTAssertTrue(current(identity))
        for changed in [BLESessionIdentity(accountID: "B", vin: "VIN-A", generation: 3, deviceID: "DEVICE-A"),
                        BLESessionIdentity(accountID: "ACCOUNT-A", vin: "B", generation: 3, deviceID: "DEVICE-A"),
                        BLESessionIdentity(accountID: "ACCOUNT-A", vin: "VIN-A", generation: 4, deviceID: "DEVICE-A"),
                        BLESessionIdentity(accountID: "ACCOUNT-A", vin: "VIN-A", generation: 3, deviceID: "B")] {
            XCTAssertFalse(current(changed))
        }
        XCTAssertFalse(current(nil)); XCTAssertFalse(current(identity, generation: 9)); XCTAssertFalse(current(identity, foreground: false))
        XCTAssertFalse(current(identity, authenticated: false)); XCTAssertFalse(current(identity, visible: false))
        let empty = BLESessionIdentity(accountID: " ", vin: " ", generation: 0, deviceID: " ")
        XCTAssertFalse(empty.matches(empty)); XCTAssertFalse(String(reflecting: identity).contains("ACCOUNT-A"))
    }
    func testConfigurationNeedsBothExactReplyAndCompletedWrites() throws {
        var tracker = BLEConfigurationTracker<String>()
        XCTAssertTrue(try tracker.begin("manual fixture", nowMilliseconds: 100))
        XCTAssertFalse(try tracker.begin("other", nowMilliseconds: 101))
        XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: 102), .ignored)
        tracker.markWriteStarted()
        XCTAssertNil(tracker.confirm(writesComplete: true, nowMilliseconds: 103))
        XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "00"), nowMilliseconds: 104), .accepted)
        XCTAssertNil(tracker.confirm(writesComplete: false, nowMilliseconds: 105))
        XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: 106), .ignored)
        XCTAssertEqual(tracker.confirm(writesComplete: true, nowMilliseconds: 107), "manual fixture")
        XCTAssertFalse(tracker.canBegin); XCTAssertNil(tracker.pending)
    }
    func testConfigurationWrongIdentifiersCannotConsumeExpectedReply() throws {
        var tracker = BLEConfigurationTracker<Int>()
        try tracker.begin(1, nowMilliseconds: 100); tracker.markWriteStarted()
        for identifier in ["1", "2", "03", "3 ", " 3", ""] {
            XCTAssertEqual(tracker.receive(.commandResult(identifier: identifier, result: "0"), nowMilliseconds: 101), .ignored)
        }
        XCTAssertEqual(tracker.receive(.authenticated(lockState: "locked"), nowMilliseconds: 102), .ignored)
        XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: 103), .accepted)
    }
    func testConfigurationExactFailureCannotBeReplacedWithLaterSuccess() throws {
        for result: String? in [nil, "", "1", "000", "0 ", " 00", "+0", "0.0"] {
            var tracker = BLEConfigurationTracker<Int>()
            try tracker.begin(1, nowMilliseconds: 100); tracker.markWriteStarted()
            XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: result), nowMilliseconds: 101), .rejected)
            XCTAssertNil(tracker.pending); XCTAssertFalse(tracker.canBegin)
            XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: 102), .ignored)
        }
    }
    func testConfigurationExclusiveDeadlineRollbackAndDisconnect() throws {
        for time: Int64 in [99, 8_100, 8_101] {
            var tracker = BLEConfigurationTracker<Int>()
            try tracker.begin(1, nowMilliseconds: 100); tracker.markWriteStarted()
            XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: time), .ignored)
            XCTAssertEqual(tracker.receive(.commandResult(identifier: "3", result: "0"), nowMilliseconds: 101), .accepted)
            XCTAssertNil(tracker.confirm(writesComplete: true, nowMilliseconds: time))
            tracker.end(); XCTAssertNil(tracker.confirm(writesComplete: true, nowMilliseconds: 102)); XCTAssertFalse(tracker.canBegin)
        }
    }
    func testInvalidConfigurationClockDoesNotConsumeConnection() throws {
        var tracker = BLEConfigurationTracker<Int>()
        for time in [Int64(-1), Int64.max] {
            XCTAssertThrowsError(try tracker.begin(1, nowMilliseconds: time)); XCTAssertTrue(tracker.canBegin)
        }
        XCTAssertTrue(try tracker.begin(1, nowMilliseconds: 0))
    }
}
