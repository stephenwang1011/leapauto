import Combine
import Foundation
import LeapCore

enum AppMode: Equatable { case signedOut, demo, connected }

@MainActor
final class AppModel: ObservableObject {
    @Published var mode: AppMode = .signedOut
    @Published var vehicles: [Vehicle] = []
    @Published var selectedVehicle: Vehicle?
    @Published var status: VehicleStatus?
    @Published var isBusy = false
    @Published var errorMessage: String?
    @Published var operationMessage: String?
    @Published var pendingCommand: LeapCommand?
    @Published var isCommandRunning = false
    @Published var sessionDeletionFailed = false

    private var api: LeapAPI?
    private let keychain = KeychainStore()
    private let lifecycle = SessionLifecycleStore()
    private var generation = UUID()
    private var didRestore = false

    static var isOfflineTestRun: Bool {
        let env = ProcessInfo.processInfo.environment
        return env["LEAPAUTO_OFFLINE_TESTS"] == "1"
            || env["XCTestConfigurationFilePath"] != nil
            || env["XCTestBundlePath"] != nil
            || ProcessInfo.processInfo.arguments.contains("--offline-tests")
    }

    var canControl: Bool {
        guard !isBusy, !isCommandRunning, selectedVehicle != nil else { return false }
        if mode == .demo { return true }
        guard mode == .connected else { return false }
        do { try LeapCommand.lock.validate(status: status); return true }
        catch { return false }
    }

    func restore() async {
        guard !Self.isOfflineTestRun, !didRestore, mode == .signedOut else { return }
        didRestore = true
        guard !lifecycle.logoutRequested else {
            sessionDeletionFailed = true
            errorMessage = "上次退出尚未完成本机会话清理，请点击“重试清除会话”。"
            return
        }
        do {
            guard let session = try keychain.load(Session.self, account: "session"), session.isAuthenticated else { return }
            api = LeapAPI(session: session)
            mode = .connected
            await bootstrap()
        } catch {
            sessionDeletionFailed = true
            errorMessage = "本机会话无法读取。请解锁设备后点击“重试清除会话”，再重新导入。"
        }
    }

    func enterDemo() {
        guard !isBusy, !isCommandRunning else { return }
        generation = UUID()
        api = nil
        mode = .demo
        vehicles = [DemoFixtures.vehicle]
        selectedVehicle = DemoFixtures.vehicle
        status = DemoFixtures.status()
        errorMessage = nil
        operationMessage = nil
    }

    func importSession(from url: URL) async {
        guard !Self.isOfflineTestRun else {
            errorMessage = "自动化测试模式仅允许离线演示。"
            return
        }
        guard !isBusy, !isCommandRunning else { return }
        isBusy = true
        errorMessage = nil
        generation = UUID()
        let scope = generation
        var importedClientCommitted = false
        defer { if generation == scope { isBusy = false } }
        do {
            let accessed = url.startAccessingSecurityScopedResource()
            defer { if accessed { url.stopAccessingSecurityScopedResource() } }
            let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
            guard size <= 131_072 else { throw AppError.invalidImport }
            let bytes = try Data(contentsOf: url, options: .mappedIfSafe)
            guard bytes.count <= 131_072, let raw = String(data: bytes, encoding: .utf8) else {
                throw AppError.invalidImport
            }
            let client = LeapAPI()
            try await client.importLegacyAuth(rawJSON: raw)
            let imported = await client.snapshot()
            guard generation == scope else { return }
            // Save successful credential rotation before any subsequent read can fail.
            try keychain.save(imported, account: "session")
            api = client
            importedClientCommitted = true
            lifecycle.clearLogoutRequest()
            mode = .connected
            vehicles = []
            selectedVehicle = nil
            status = nil
            sessionDeletionFailed = false
            let list = try await client.bootstrap()
            let snapshot = await client.snapshot()
            guard generation == scope else { return }
            vehicles = list
            selectedVehicle = list.first(where: { $0.vin == snapshot.selectedVIN }) ?? list.first
            if selectedVehicle != nil {
                let fresh = try await client.refreshStatus()
                guard generation == scope else { return }
                status = fresh
            }
            await persistCurrentSession(scope: scope)
            guard generation == scope else { return }
            operationMessage = "会话已导入本机钥匙串。请先核对所选车辆与车况。"
        } catch {
            guard generation == scope else { return }
            if importedClientCommitted { await handleConnectedFailure(error, scope: scope) }
            else { errorMessage = safeMessage(error) }
        }
    }

    private func bootstrap() async {
        guard let client = api, !isBusy else { return }
        isBusy = true
        let scope = generation
        defer { if generation == scope { isBusy = false } }
        do {
            let list = try await client.bootstrap()
            let snapshot = await client.snapshot()
            guard generation == scope else { return }
            vehicles = list
            selectedVehicle = list.first(where: { $0.vin == snapshot.selectedVIN }) ?? list.first
            if selectedVehicle != nil {
                let fresh = try await client.refreshStatus()
                guard generation == scope else { return }
                status = fresh
            }
            await persistCurrentSession(scope: scope)
            guard generation == scope else { return }
        } catch {
            guard generation == scope else { return }
            await handleConnectedFailure(error, scope: scope)
        }
    }

    func refresh() async {
        guard !isBusy, !isCommandRunning else { return }
        if mode == .demo {
            status = DemoFixtures.status()
            operationMessage = "演示数据已刷新；没有网络请求。"
            return
        }
        guard let client = api else { return }
        if selectedVehicle == nil { await bootstrap(); return }
        let scope = generation
        isBusy = true
        errorMessage = nil
        defer { if generation == scope { isBusy = false } }
        do {
            let fresh = try await client.refreshStatus()
            guard generation == scope else { return }
            status = fresh
            await persistCurrentSession(scope: scope)
            guard generation == scope else { return }
        } catch {
            guard generation == scope else { return }
            await handleConnectedFailure(error, scope: scope)
        }
    }

    func selectVehicle(_ vehicle: Vehicle) async {
        guard !isBusy, !isCommandRunning, pendingCommand == nil, mode == .connected, let client = api else { return }
        generation = UUID()
        let scope = generation
        isBusy = true
        status = nil
        selectedVehicle = nil
        operationMessage = nil
        defer { if generation == scope { isBusy = false } }
        do {
            try await client.selectVehicle(vehicle)
            guard generation == scope else { return }
            selectedVehicle = vehicle
            let fresh = try await client.refreshStatus()
            guard generation == scope else { return }
            status = fresh
            await persistCurrentSession(scope: scope)
            guard generation == scope else { return }
        } catch {
            guard generation == scope else { return }
            await handleConnectedFailure(error, scope: scope)
        }
    }

    func request(_ command: LeapCommand) {
        guard !isCommandRunning, !isBusy, pendingCommand == nil else { return }
        errorMessage = nil
        do {
            guard selectedVehicle != nil else { throw AppError.noVehicle }
            if mode != .demo { try command.validate(status: status) }
            pendingCommand = command
        } catch { errorMessage = safeMessage(error) }
    }

    func cancelPendingCommand() {
        guard !isCommandRunning else { return }
        pendingCommand = nil
    }

    func executePendingCommand(pin: String) async {
        guard let command = pendingCommand, !isCommandRunning, !isBusy else { return }
        guard pin.utf8.count == 4, pin.utf8.allSatisfy({ (48...57).contains($0) }) else {
            errorMessage = "操作密码必须为四位数字。"
            return
        }
        isCommandRunning = true
        let scope = generation
        let vin = selectedVehicle?.vin
        var submitted = false
        defer {
            if generation == scope {
                isCommandRunning = false
                pendingCommand = nil
            }
        }
        if mode == .demo {
            operationMessage = "演示：已模拟“\(command.title)”流程。没有发送指令，样例车况保持不变。"
            return
        }
        do {
            guard let client = api else { throw AppError.noVehicle }
            try await LocalControlAuthentication().verify()
            guard generation == scope, selectedVehicle?.vin == vin else { return }
            let fresh = try await client.refreshStatus()
            guard generation == scope, selectedVehicle?.vin == vin else { return }
            status = fresh
            try command.validate(status: fresh)
            // Once this call starts, a network error does not prove that the
            // vehicle did not receive the instruction. Never automatically resend.
            try Task.checkCancellation()
            submitted = true
            let receipt = try await client.send(command: command, pin: pin)
            guard generation == scope else { return }
            await persistCurrentSession(scope: scope)
            guard generation == scope else { return }
            guard let messageID = receipt.messageID, !messageID.isEmpty else {
                operationMessage = "服务端已受理，尚未确认车辆执行。请刷新车况，勿重复操作。"
                return
            }
            operationMessage = "服务端已受理，正在确认车辆执行结果…"
            try await pollResult(client: client, messageID: messageID, scope: scope)
            await persistCurrentSession(scope: scope)
        } catch {
            guard generation == scope else { return }
            if submitted {
                operationMessage = "执行结果尚未确认。请刷新车况核对，勿重复操作。"
            }
            await handleConnectedFailure(error, scope: scope, submitted: submitted)
        }
    }

    private func pollResult(client: LeapAPI, messageID: String, scope: UUID) async throws {
        // Bounded observation of this one request; no mutation retries.
        let deadline = Date().addingTimeInterval(24)
        try await Task.sleep(nanoseconds: 500_000_000)
        while Date() < deadline {
            try Task.checkCancellation()
            guard generation == scope else { return }
            let result = try await client.pollResult(messageID: messageID)
            guard generation == scope else { return }
            guard Date() <= deadline else { break }
            switch result {
            case .succeeded:
                operationMessage = "车辆接口已确认指令完成。正在刷新车况…"
                let fresh = try await client.refreshStatus()
                guard generation == scope else { return }
                status = fresh
                operationMessage = "车辆接口已确认指令完成，车况已刷新。"
                return
            case .failed(let message):
                operationMessage = message
                return
            case .pending:
                break
            }
            try await Task.sleep(nanoseconds: 500_000_000)
        }
        operationMessage = "等待结果超时，执行状态未知。请刷新车况核对，勿重复操作。"
    }

    @discardableResult
    func logout() async -> UUID {
        // Invalidate late responses before crossing an actor boundary.
        lifecycle.markLogoutRequested()
        generation = UUID()
        let logoutScope = generation
        let old = api
        api = nil
        mode = .signedOut
        vehicles = []
        selectedVehicle = nil
        status = nil
        pendingCommand = nil
        isCommandRunning = false
        isBusy = false
        operationMessage = nil
        errorMessage = nil
        do {
            try keychain.remove(account: "session")
            lifecycle.clearLogoutRequest()
            sessionDeletionFailed = false
        } catch {
            sessionDeletionFailed = true
            errorMessage = "本机会话未能从钥匙串删除。请解锁设备后点击“重试清除会话”。"
        }
        await old?.logout()
        return logoutScope
    }

    private func handleConnectedFailure(_ error: Error, scope: UUID, submitted: Bool = false) async {
        guard generation == scope else { return }
        if let apiError = error as? LeapAPIError,
           apiError == .authenticationRequired || apiError == .invalidSession {
            let logoutScope = await logout()
            guard generation == logoutScope else { return }
            errorMessage = sessionDeletionFailed
                ? "会话已失效，本机清理未完成。请解锁后点击“重试清除会话”。"
                : "会话已失效，已退出。请重新导入本人有效会话。"
            if submitted { operationMessage = "已提交指令的执行状态仍未知，请通过车辆实际状态核对，勿重复操作。" }
            return
        }
        errorMessage = safeMessage(error)
        await persistCurrentSession(scope: scope)
    }

    private func persistCurrentSession(scope: UUID) async {
        guard generation == scope, mode == .connected, let client = api else { return }
        let snapshot = await client.snapshot()
        guard generation == scope else { return }
        do { try keychain.save(snapshot, account: "session") }
        catch { errorMessage = "会话更新未能保存到本机钥匙串，请解锁设备后重试。" }
    }

    private func safeMessage(_ error: Error) -> String {
        if error is CancellationError { return "操作已取消。已发送指令的执行状态请刷新车况核对。" }
        // All domain errors have controlled, non-sensitive descriptions. Never
        // surface URLs, raw responses, or arbitrary NSError.userInfo.
        switch error {
        case let value as LeapAPIError: return value.errorDescription ?? "接口请求失败。"
        case let value as SessionImportError: return value.errorDescription ?? "会话文件无效。"
        case let value as ProtocolCryptoError: return value.errorDescription ?? "协议加密失败。"
        case let value as CommandValidationError: return value.errorDescription ?? "当前不能控制车辆。"
        case let value as KeychainStore.StoreError: return value.errorDescription ?? "钥匙串不可用。"
        case let value as LocalControlAuthentication.AuthenticationError: return value.errorDescription ?? "身份验证未通过。"
        case let value as AppError: return value.errorDescription ?? "操作失败。"
        default: break
        }
        return "操作失败，请检查网络或稍后重试。"
    }

    enum AppError: LocalizedError {
        case invalidImport, noVehicle
        var errorDescription: String? {
            switch self {
            case .invalidImport: return "请选择不超过 128 KB 的有效 JSON 会话文件。"
            case .noVehicle: return "请先连接并选择自己的车辆。"
            }
        }
    }
}
