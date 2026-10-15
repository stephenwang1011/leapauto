import LocalAuthentication

@MainActor
struct LocalControlAuthentication {
    func verify() async throws {
        let context = LAContext()
        context.localizedCancelTitle = "取消控车"
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &error) else {
            throw AuthenticationError.unavailable
        }
        do {
            let accepted = try await context.evaluatePolicy(
                .deviceOwnerAuthentication,
                localizedReason: "确认是本人操作车辆；接下来将发送你确认的指令。"
            )
            guard accepted else { throw AuthenticationError.cancelled }
        } catch {
            throw AuthenticationError.cancelled
        }
    }

    enum AuthenticationError: LocalizedError {
        case unavailable, cancelled
        var errorDescription: String? {
            switch self {
            case .unavailable: return "请先为 iPhone 设置设备密码或生物识别，再使用远程控制。"
            case .cancelled: return "身份验证已取消，未发送指令。"
            }
        }
    }
}
