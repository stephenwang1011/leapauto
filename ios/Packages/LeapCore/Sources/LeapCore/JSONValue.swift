import Foundation

/// Typed JSON structure used at the protocol boundary. Unknown scalars remain
/// unknown: e.g. `2` is not silently interpreted as a Boolean true.
public enum JSONValue: Codable, Equatable, Sendable {
    case object([String: JSONValue])
    case array([JSONValue])
    case string(String)
    case number(Double)
    case bool(Bool)
    case null

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { self = .null }
        else if let value = try? container.decode(Bool.self) { self = .bool(value) }
        else if let value = try? container.decode(String.self) { self = .string(value) }
        else if let value = try? container.decode(Double.self), value.isFinite { self = .number(value) }
        else if let value = try? container.decode([String: JSONValue].self) { self = .object(value) }
        else if let value = try? container.decode([JSONValue].self) { self = .array(value) }
        else { throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unsupported JSON value") }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .object(let value): try container.encode(value)
        case .array(let value): try container.encode(value)
        case .string(let value): try container.encode(value)
        case .number(let value): try container.encode(value)
        case .bool(let value): try container.encode(value)
        case .null: try container.encodeNil()
        }
    }

    public init(data: Data) throws { self = try JSONDecoder().decode(Self.self, from: data) }

    public func encodedData(sortedKeys: Bool = true) throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = sortedKeys ? [.sortedKeys, .withoutEscapingSlashes] : [.withoutEscapingSlashes]
        return try encoder.encode(self)
    }

    public subscript(_ key: String) -> JSONValue? { objectValue?[key] }
    public var objectValue: [String: JSONValue]? { if case .object(let value) = self { return value }; return nil }
    public var arrayValue: [JSONValue]? { if case .array(let value) = self { return value }; return nil }
    public var stringValue: String? { if case .string(let value) = self { return value }; return nil }
    public var isNull: Bool { self == .null }

    public var scalarString: String? {
        switch self {
        case .string(let value): return value
        case .number(let value):
            guard value.isFinite else { return nil }
            if let integer = Int64(exactly: value) { return String(integer) }
            return String(value)
        case .bool(let value): return value ? "true" : "false"
        default: return nil
        }
    }

    public var doubleValue: Double? {
        let value: Double?
        switch self {
        case .number(let number): value = number
        case .string(let string): value = Double(string.trimmingCharacters(in: .whitespacesAndNewlines))
        default: value = nil
        }
        return value.flatMap { $0.isFinite ? $0 : nil }
    }

    public var intValue: Int? { doubleValue.flatMap(Int.init(exactly:)) }

    public var boolValue: Bool? {
        switch self {
        case .bool(let value): return value
        case .number(0): return false
        case .number(1): return true
        case .string(let value):
            switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
            case "0", "false": return false
            case "1", "true": return true
            default: return nil
            }
        default: return nil
        }
    }
}
