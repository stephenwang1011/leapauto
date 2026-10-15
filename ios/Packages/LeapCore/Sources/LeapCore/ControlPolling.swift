import Foundation

/// A successful POST is only acceptance, never proof of vehicle execution.
public enum ControlOutcome: Equatable, Sendable {
    case accepted(messageID: String?)
    case verified
    case timedOut
    case failed(message: String)

    public var title: String {
        switch self {
        case .accepted: return "指令已受理，等待车辆确认"
        case .verified: return "车辆已确认执行"
        case .timedOut: return "等待车辆响应超时，执行结果尚未确认"
        case .failed(let message): return message
        }
    }
}

public enum ControlPollResult: Equatable, Sendable {
    case pending
    case succeeded
    case failed(message: String)

    public init(raw: JSONValue) {
        self = ControlPollingPolicy.querySucceeded(raw) ? .succeeded : .pending
    }
}

public struct ControlPollingPolicy: Equatable, Sendable {
    public let initialDelay: TimeInterval
    public let interval: TimeInterval
    public let timeout: TimeInterval
    // Port the current executable Kotlin policy: 500ms first query, then 500ms.
    // Older AGENTS/API prose says 1s. Network time also counts toward timeout.
    public static let app = ControlPollingPolicy(timeout: 24)
    public static let widget = ControlPollingPolicy(timeout: 12)

    public init(timeout: TimeInterval = 24, initialDelay: TimeInterval = 0.5, interval: TimeInterval = 0.5) {
        precondition(timeout.isFinite && timeout >= 0)
        precondition(initialDelay.isFinite && initialDelay > 0)
        precondition(interval.isFinite && interval > 0)
        self.timeout = timeout
        self.initialDelay = initialDelay
        self.interval = interval
    }

    public var attempts: Int {
        guard timeout >= initialDelay else { return 0 }
        return 1 + Int(floor((timeout - initialDelay) / interval))
    }

    public func delayBeforeAttempt(_ attempt: Int) -> TimeInterval? {
        guard attempt >= 0, attempt < attempts else { return nil }
        return attempt == 0 ? initialDelay : interval
    }

    public func hasTimedOut(elapsed: TimeInterval) -> Bool { !elapsed.isFinite || elapsed >= timeout }

    /// The result key has priority over code, including a nonzero result with
    /// code=0. Neither HTTP 200 nor an absent business result proves execution.
    public static func querySucceeded(_ response: JSONValue) -> Bool {
        let result = response["result"].flatMap { $0.isNull ? nil : $0 }
        let code = response["code"].flatMap { $0.isNull ? nil : $0 }
        return (result ?? code)?.intValue == 0
    }
}

/// Pure lifecycle state machine; networking and sleeping are injected by the app.
/// Empty polling IDs remain accepted and unverified. A timeout never re-sends.
public struct ControlProgress: Equatable, Sendable {
    public private(set) var outcome: ControlOutcome
    public let acceptedAt: Date
    public let policy: ControlPollingPolicy

    public init(messageID: String?, acceptedAt: Date = Date(), policy: ControlPollingPolicy = .app) {
        let trimmed = messageID?.trimmingCharacters(in: .whitespacesAndNewlines)
        outcome = .accepted(messageID: trimmed.flatMap { $0.isEmpty ? nil : $0 })
        self.acceptedAt = acceptedAt
        self.policy = policy
    }

    public mutating func receive(_ result: ControlPollResult, at now: Date = Date()) {
        guard case .accepted(let messageID) = outcome, messageID != nil else { return }
        let elapsed = now.timeIntervalSince(acceptedAt)
        guard elapsed >= 0 else { return }
        guard !policy.hasTimedOut(elapsed: elapsed) else {
            outcome = .timedOut
            return
        }
        switch result {
        case .succeeded: outcome = .verified
        case .failed(let message): outcome = .failed(message: message)
        case .pending: break
        }
    }

    public mutating func checkTimeout(at now: Date = Date()) {
        guard case .accepted(let messageID) = outcome, messageID != nil else { return }
        if policy.hasTimedOut(elapsed: now.timeIntervalSince(acceptedAt)) { outcome = .timedOut }
    }
}
