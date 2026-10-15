import Foundation

/// Hardware stays unavailable until cryptographic identity binding and real-device
/// acceptance are implemented. This is deliberately not a user/defaults/DI flag.
public enum BLEHardwareFeatureGate {
    public static var isEnabled: Bool { false }
    public static func requireEnabled() throws {
        guard isEnabled else { throw BLEProtocolError.hardwareNotAccepted }
    }
}

public enum BLEProtocolError: Error, Equatable, Sendable {
    case hardwareNotAccepted
    case invalidFrameLimit
    case notificationTooLarge
    case invalidUTF8
    case invalidReconnectCredential
    case invalidWriteLimit
    case frameTooLarge
    case emptyFrame
    case writeAlreadyPending
    case noWriteInFlight
    case invalidClock
}

public enum BLELockAction: UInt8, CaseIterable, Sendable { case unlock = 1, lock = 2 }

public enum BLEFrameType: UInt8, Sendable {
    case encrypted = 0xAB, event = 0xAC, authentication = 0xAE, reconnect = 0xEE
    public var headerSize: Int {
        switch self { case .encrypted: return 6; case .event: return 10; case .authentication, .reconnect: return 4 }
    }
}

public enum BLEChunkProfile: Sendable {
    case legacy160, onePaoV010
    // Executable upstream source/tests cap BOTH profiles at 160. API.md's older
    // 197-byte statement is not the implemented contract.
    public var maximum: Int { 160 }
}

public struct BLEFrame: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let type: BLEFrameType
    public let bytes: Data
    public var payload: Data { Data(bytes.dropFirst(type.headerSize)) }
    fileprivate init(type: BLEFrameType, bytes: Data) { self.type = type; self.bytes = bytes }
    public var description: String { "BLEFrame(type: \(type), bytes: redacted)" }
    public var debugDescription: String { description }
}

public struct BLEReconnectCredential: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible, CustomReflectable {
    public let hex: String
    public init(hex: String) throws {
        let bytes = Array(hex.utf8)
        guard !bytes.isEmpty, bytes.count <= 1_024, bytes.count % 2 == 0,
              bytes.allSatisfy({ (48...57).contains($0) || (65...70).contains($0) || (97...102).contains($0) }) else {
            throw BLEProtocolError.invalidReconnectCredential
        }
        self.hex = hex.lowercased()
    }
    public var bytes: Data {
        let text = Array(hex.utf8)
        func digit(_ byte: UInt8) -> UInt8 { byte <= 57 ? byte - 48 : byte - 87 }
        return Data(stride(from: 0, to: text.count, by: 2).map { (digit(text[$0]) << 4) | digit(text[$0 + 1]) })
    }
    public var description: String { "BLEReconnectCredential(redacted)" }
    public var debugDescription: String { description }
    public var customMirror: Mirror { Mirror(self, children: ["value": "redacted"]) }
}

public enum BLEResponse: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    /// A parsed plaintext field, not proof of cryptographic authentication.
    case authenticated(lockState: String)
    case reconnectCredential(BLEReconnectCredential)
    case commandResult(identifier: String, result: String?)
    case unknown

    public var confirmsConfiguration: Bool {
        if case .commandResult(let identifier, let result) = self {
            return identifier == "3" && (result == "0" || result == "00")
        }
        return false
    }
    public var description: String { "BLEResponse(redacted)" }
    public var debugDescription: String { description }
}

public enum BLEEvent: Equatable, Sendable {
    case rejected(resultCode: UInt8)
    case lockAction(crc8: UInt8, trigger: UInt8, action: BLELockAction?)
    case unknown
    public func confirms(_ expected: BLELockAction) -> Bool {
        if case .lockAction(_, let trigger, let action) = self { return trigger == 1 && action == expected }
        return false
    }
}

/// Pure framing only. No session keys, decryption, certificate acceptance or I/O.
public enum BLEKeyProtocol {
    public static let serviceUUID = UUID(uuidString: "0000FFFE-0000-1000-8000-00805F9B34FB")!
    public static let characteristicUUID = UUID(uuidString: "0000FFF2-0000-1000-8000-00805F9B34FB")!
    public static let defaultProtocolMinor: UInt8 = 8
    public static let leap3ProtocolMinor: UInt8 = 9
    public static let maximumFrameSize = 65_539

    public static func defaultMinor(carType: String? = nil) -> UInt8 {
        let model = (carType ?? "").uppercased()
        return model.contains("C16") || model.contains("C10") ? leap3ProtocolMinor : defaultProtocolMinor
    }
    /// Advertising UUIDs describe a protocol hint only; they never establish vehicle identity.
    public static func advertisedProtocolMinor(serviceUUIDs: [UUID]) -> UInt8? {
        for uuid in serviceUUIDs {
            let value = uuid.uuidString.lowercased()
            if value.hasPrefix("000001"), value.hasSuffix("-0000-1000-8000-00805f9b34fb") {
                return UInt8(value.dropFirst(6).prefix(2), radix: 16)
            }
        }
        return nil
    }
    public static func protocolMinor(serviceUUIDs: [UUID], carType: String? = nil) -> UInt8 {
        advertisedProtocolMinor(serviceUUIDs: serviceUUIDs) ?? defaultMinor(carType: carType)
    }
    /// Android/ATT compatibility helper. Do NOT feed CoreBluetooth write limits here.
    public static func chunkLimit(mtu: Int, profile: BLEChunkProfile = .legacy160) -> Int {
        min(max(mtu, 23) - 3, profile.maximum)
    }
    public static func chunks(frame: Data, mtu: Int, profile: BLEChunkProfile = .legacy160) -> [Data] {
        split(frame, limit: chunkLimit(mtu: mtu, profile: profile))
    }
    /// CoreBluetooth already returns a payload limit; no ATT-header subtraction.
    public static func chunks(frame: Data, maximumWriteValueLength: Int, profile: BLEChunkProfile = .legacy160) throws -> [Data] {
        guard maximumWriteValueLength > 0 else { throw BLEProtocolError.invalidWriteLimit }
        return split(frame, limit: min(maximumWriteValueLength, profile.maximum))
    }
    private static func split(_ frame: Data, limit: Int) -> [Data] {
        let bytes = Array(frame)
        return stride(from: 0, to: bytes.count, by: limit).map { Data(bytes[$0..<min($0 + limit, bytes.count)]) }
    }
    public static func crc8(_ bytes: Data) -> UInt8 {
        var crc: UInt8 = 0
        for byte in bytes {
            crc ^= byte
            for _ in 0..<8 { crc = (crc &<< 1) ^ ((crc & 0x80) == 0 ? 0 : 0x07) }
        }
        return crc
    }
    public static func parseEvent(_ frame: BLEFrame) -> BLEEvent {
        guard frame.type == .event else { return .unknown }
        let bytes = Array(frame.bytes), payload = Array(frame.payload)
        switch bytes[5] {
        case 1: return payload.last.map { .rejected(resultCode: $0) } ?? .unknown
        case 2:
            guard payload.count >= 3 else { return .unknown }
            return .lockAction(crc8: payload[0], trigger: payload[1], action: BLELockAction(rawValue: payload[2]))
        default: return .unknown
        }
    }
    /// Accept only plaintext supplied by a future verified decryption layer. This
    /// parser alone MUST NOT advance a live connection to authenticated/ready.
    public static func parseDecryptedResponse(_ plaintext: Data) throws -> BLEResponse {
        guard plaintext.count <= maximumFrameSize else { throw BLEProtocolError.frameTooLarge }
        guard let text = String(data: plaintext, encoding: .utf8) else { throw BLEProtocolError.invalidUTF8 }
        let fields = text.components(separatedBy: ";")
        guard fields.count >= 2 else { return .unknown }
        switch fields[0] {
        case "0": return fields[1].isEmpty ? .unknown : .authenticated(lockState: fields[1])
        case "1":
            guard let credential = try? BLEReconnectCredential(hex: fields[1]) else { return .unknown }
            return .reconnectCredential(credential)
        case "2": return .commandResult(identifier: fields[1], result: fields.count > 2 ? fields[2] : nil)
        default: return .unknown
        }
    }
}

/// Streaming decoder ported from BleFrameDecoder. Unrecognized bytes and invalid
/// lengths are resynchronized without treating an embedded header as authenticated.
public struct BLEFrameDecoder: Sendable {
    private let maxFrameSize: Int
    private var pending: [UInt8] = []
    public var bufferedByteCount: Int { pending.count }
    public init(maxFrameSize: Int = BLEKeyProtocol.maximumFrameSize) throws {
        guard (10...1_048_576).contains(maxFrameSize) else { throw BLEProtocolError.invalidFrameLimit }
        self.maxFrameSize = maxFrameSize
    }
    public mutating func append(_ bytes: Data) throws -> [BLEFrame] {
        guard bytes.count <= maxFrameSize else { throw BLEProtocolError.notificationTooLarge }
        pending.append(contentsOf: bytes)
        var result: [BLEFrame] = [], offset = 0
        while pending.count - offset >= 2 {
            guard pending[offset] == 0xAA, let type = BLEFrameType(rawValue: pending[offset + 1]) else {
                offset += 1; continue
            }
            guard pending.count - offset >= type.headerSize else { break }
            let lengthOffset = type == .event ? 6 : 2
            let lengthWidth = type.headerSize == 4 ? 2 : 4
            var length: UInt64 = 0
            for index in 0..<lengthWidth { length |= UInt64(pending[offset + lengthOffset + index]) << (8 * index) }
            guard !(type == .encrypted && length == 0), length + UInt64(type.headerSize) <= UInt64(maxFrameSize) else {
                offset += 1; continue
            }
            let total = type.headerSize + Int(length)
            guard pending.count - offset >= total else { break }
            result.append(BLEFrame(type: type, bytes: Data(pending[offset..<offset + total])))
            offset += total
        }
        pending = Array(pending.dropFirst(offset))
        if pending.count == 1 && pending[0] != 0xAA { pending.removeAll(keepingCapacity: false) }
        // A partial frame is always shorter than its already bounded total.
        return result
    }
    public mutating func clear() {
        // Swift value copies mean this is best-effort disposal, not guaranteed zeroization.
        pending = Array(repeating: 0, count: pending.count)
        pending.removeAll(keepingCapacity: false)
    }
}

/// One bounded frame at a time, serial .withResponse transport acknowledgments.
/// Completion means the GATT writes completed, never that a vehicle acted.
public struct BLEWriteQueue: Sendable {
    private var chunks: [Data] = []
    private var cursor = 0
    public private(set) var writeInFlight = false
    public var isIdle: Bool { chunks.isEmpty && !writeInFlight }
    public init() {}
    public mutating func enqueue(frame: Data, maximumWriteValueLength: Int) throws {
        guard isIdle else { throw BLEProtocolError.writeAlreadyPending }
        guard !frame.isEmpty else { throw BLEProtocolError.emptyFrame }
        guard frame.count <= BLEKeyProtocol.maximumFrameSize else { throw BLEProtocolError.frameTooLarge }
        chunks = try BLEKeyProtocol.chunks(frame: frame, maximumWriteValueLength: maximumWriteValueLength)
        cursor = 0
    }
    public mutating func takeNext() -> Data? {
        guard !writeInFlight, cursor < chunks.count else { return nil }
        writeInFlight = true
        return chunks[cursor]
    }
    @discardableResult public mutating func acknowledge() throws -> Bool {
        guard writeInFlight else { throw BLEProtocolError.noWriteInFlight }
        writeInFlight = false; cursor += 1
        if cursor == chunks.count { clear(); return true }
        return false
    }
    public mutating func clear() { chunks.removeAll(keepingCapacity: false); cursor = 0; writeInFlight = false }
}

public enum BLEConnectionPhase: CaseIterable, Sendable {
    case idle, scanning, connecting, discovering, subscribing, authenticating, configuring, ready, sending, failed
    public var canControl: Bool { self == .ready }
    public var isBusy: Bool { [.connecting, .discovering, .subscribing, .authenticating, .configuring, .sending].contains(self) }
}

public enum BLECommandOutcome: Equatable, Sendable { case confirmed, notSent, unknown }

/// One explicit action per connection. Transport completion cannot confirm it.
public struct BLECommandTracker: Sendable {
    public static let confirmationTimeoutMilliseconds: Int64 = 6_000
    public private(set) var pending: BLELockAction?
    public private(set) var writeStarted = false
    private var used = false
    private var startedAt: Int64 = 0
    private var deadline: Int64 = 0
    public init() {}
    @discardableResult public mutating func begin(_ action: BLELockAction, nowMilliseconds: Int64) throws -> Bool {
        guard !used else { return false }
        guard nowMilliseconds >= 0, nowMilliseconds <= Int64.max - Self.confirmationTimeoutMilliseconds else {
            throw BLEProtocolError.invalidClock
        }
        used = true; pending = action; writeStarted = false; startedAt = nowMilliseconds
        deadline = nowMilliseconds + Self.confirmationTimeoutMilliseconds
        return true
    }
    public mutating func markWriteStarted() { if pending != nil { writeStarted = true } }
    public mutating func confirm(_ action: BLELockAction, nowMilliseconds: Int64) -> BLECommandOutcome? {
        guard pending == action, writeStarted, nowMilliseconds >= startedAt, nowMilliseconds < deadline else { return nil }
        clear(); return .confirmed
    }
    public mutating func receive(_ event: BLEEvent, nowMilliseconds: Int64) -> BLECommandOutcome? {
        guard let action = pending, event.confirms(action) else { return nil }
        return confirm(action, nowMilliseconds: nowMilliseconds)
    }
    public mutating func end() -> BLECommandOutcome? {
        guard pending != nil else { return nil }
        let result: BLECommandOutcome = writeStarted ? .unknown : .notSent
        clear(); return result
    }
    private mutating func clear() { pending = nil; writeStarted = false; startedAt = 0; deadline = 0 }
}

public struct BLESessionIdentity: Equatable, Sendable, CustomStringConvertible, CustomDebugStringConvertible {
    public let accountID: String
    public let vin: String
    public let generation: Int64
    public let deviceID: String
    public init(accountID: String, vin: String, generation: Int64, deviceID: String) {
        self.accountID = accountID; self.vin = vin; self.generation = generation; self.deviceID = deviceID
    }
    public func matches(_ other: BLESessionIdentity?) -> Bool {
        guard let other, [accountID, vin, deviceID].allSatisfy({ !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) else { return false }
        return self == other
    }
    public var description: String { "BLESessionIdentity(redacted)" }
    public var debugDescription: String { description }
}

public struct BLEControlConfirmation: Equatable, Sendable {
    public let action: BLELockAction
    public let identity: BLESessionIdentity
    public let connectionGeneration: UInt64
    public init(action: BLELockAction, identity: BLESessionIdentity, connectionGeneration: UInt64) {
        self.action = action; self.identity = identity; self.connectionGeneration = connectionGeneration
    }
    public func isCurrent(identity currentIdentity: BLESessionIdentity?, connectionGeneration: UInt64,
                          foreground: Bool, authenticated: Bool, managementVisible: Bool) -> Bool {
        managementVisible && foreground && authenticated && self.connectionGeneration == connectionGeneration && identity.matches(currentIdentity)
    }
}

public enum BLEConfigurationReply: Equatable, Sendable { case ignored, accepted, rejected }

/// Command 3 has no request identifier. One configuration attempt per connection.
/// The opaque value lets a future, verified configuration model use these gates
/// without introducing unverified passive-action encodings in the iOS port.
public struct BLEConfigurationTracker<Configuration: Equatable & Sendable>: Sendable {
    public static var confirmationTimeoutMilliseconds: Int64 { 8_000 }
    public private(set) var pending: Configuration?
    private var used = false, writeStarted = false, acknowledged = false
    private var startedAt: Int64 = 0, deadline: Int64 = 0
    public var canBegin: Bool { !used }
    public init() {}
    @discardableResult public mutating func begin(_ configuration: Configuration, nowMilliseconds: Int64) throws -> Bool {
        guard !used else { return false }
        guard nowMilliseconds >= 0, nowMilliseconds <= Int64.max - Self.confirmationTimeoutMilliseconds else { throw BLEProtocolError.invalidClock }
        used = true; pending = configuration; startedAt = nowMilliseconds
        deadline = nowMilliseconds + Self.confirmationTimeoutMilliseconds
        return true
    }
    public mutating func markWriteStarted() { if pending != nil { writeStarted = true } }
    public mutating func receive(_ response: BLEResponse, nowMilliseconds: Int64) -> BLEConfigurationReply {
        guard eligible(nowMilliseconds), !acknowledged,
              case .commandResult(let identifier, _) = response, identifier == "3" else { return .ignored }
        guard response.confirmsConfiguration else { end(); return .rejected }
        acknowledged = true; return .accepted
    }
    public mutating func confirm(writesComplete: Bool, nowMilliseconds: Int64) -> Configuration? {
        guard writesComplete, acknowledged, eligible(nowMilliseconds) else { return nil }
        let result = pending; end(); return result
    }
    public mutating func end() { pending = nil; writeStarted = false; acknowledged = false; startedAt = 0; deadline = 0 }
    private func eligible(_ now: Int64) -> Bool { pending != nil && writeStarted && now >= startedAt && now < deadline }
}
