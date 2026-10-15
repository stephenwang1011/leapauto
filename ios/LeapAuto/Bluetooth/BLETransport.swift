import Foundation
@preconcurrency import CoreBluetooth
import LeapCore

/// CoreBluetooth transport scaffold. No app entry point creates this object yet.
/// Construction is inert: even CBCentralManager initialization is behind the
/// immutable, fail-closed hardware acceptance gate in LeapCore.
///
/// A CoreBluetooth identifier locates a previously explicitly selected peripheral;
/// it is NOT a VIN, MAC address, certificate binding or authentication result.
/// Fresh instances are required for reconnects. No restoration/background mode.
@MainActor
final class BLETransport: NSObject, @preconcurrency CBCentralManagerDelegate, @preconcurrency CBPeripheralDelegate {
    enum Phase: Equatable {
        case idle, waitingForBluetooth, scanning, candidateFound, connecting
        case discovering, subscribing, transportReady, closed, failed
    }
    enum Failure: Error, Equatable {
        case featureUnavailable, invalidState, targetMismatch, bluetoothUnavailable
        case connectionFailed, serviceMissing, characteristicMissing, notificationFailed
        case receiveFailed, writeFailed, timedOut, disconnected
    }

    private(set) var phase: Phase = .idle
    private(set) var failure: Failure?
    /// Events are transport-only. Consumers must still perform certificate-bound
    /// authentication, current-account/VIN checks and explicit user confirmation.
    var onPhaseChange: ((Phase) -> Void)?
    var onFrame: ((BLEFrame) -> Void)?
    var onWritesCompleted: (() -> Void)?

    private var manager: CBCentralManager?
    private var peripheral: CBPeripheral?
    private var characteristic: CBCharacteristic?
    private var targetID: UUID?
    private var decoder: BLEFrameDecoder?
    private var writes = BLEWriteQueue()
    private var timer: Task<Void, Never>?
    private var pendingOperation: Operation?
    private enum Operation { case scan, connect }
    private let serviceID = CBUUID(nsuuid: BLEKeyProtocol.serviceUUID)
    private let characteristicID = CBUUID(nsuuid: BLEKeyProtocol.characteristicUUID)

    override init() { super.init() }

    /// Explicit, bounded scan for one supplied CoreBluetooth UUID. No name/RSSI
    /// heuristic, automatic selection, scanning on launch or automatic reconnect.
    func scan(for identifier: UUID) throws {
        try requireHardware()
        guard phase == .idle else { throw Failure.invalidState }
        targetID = identifier
        pendingOperation = .scan
        initializeManagerAfterGate()
    }

    /// Explicit selection of the same UUID; service availability is not trust.
    func connect(to identifier: UUID) throws {
        try requireHardware()
        guard [.idle, .scanning, .candidateFound].contains(phase) else { throw Failure.invalidState }
        guard targetID == nil || targetID == identifier else { throw Failure.targetMismatch }
        targetID = identifier
        pendingOperation = .connect
        if manager == nil { initializeManagerAfterGate() } else { executeExplicitOperation() }
    }

    /// Bytes are intentionally opaque to the transport. This entry remains closed
    /// until the missing session/cryptography/vehicle acceptance layer is complete.
    func write(frame: Data, to identifier: UUID) throws {
        try requireHardware()
        guard targetID == identifier else { throw Failure.targetMismatch }
        guard phase == .transportReady, let peripheral, let characteristic,
              peripheral.state == .connected, characteristic.isNotifying else { throw Failure.invalidState }
        let limit = peripheral.maximumWriteValueLength(for: .withResponse)
        try writes.enqueue(frame: frame, maximumWriteValueLength: limit)
        armTimeout(seconds: 8)
        sendNextChunk()
    }

    func disconnect() {
        timer?.cancel(); timer = nil; pendingOperation = nil
        // Cleanup only affects this adapter's own optional manager/peripheral.
        manager?.stopScan()
        if let peripheral { manager?.cancelPeripheralConnection(peripheral); peripheral.delegate = nil }
        peripheral = nil; characteristic = nil; decoder?.clear(); decoder = nil; writes.clear()
        manager?.delegate = nil; manager = nil
        transition(.closed)
    }

    private func requireHardware() throws {
        guard BLEHardwareFeatureGate.isEnabled else { throw Failure.featureUnavailable }
    }

    private func initializeManagerAfterGate() {
        guard BLEHardwareFeatureGate.isEnabled, manager == nil else { return }
        transition(.waitingForBluetooth)
        armTimeout(seconds: 15)
        // No state restoration identifier. Never initialize this in init().
        manager = CBCentralManager(delegate: self, queue: .main,
                                   options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }

    private func executeExplicitOperation() {
        guard BLEHardwareFeatureGate.isEnabled, let manager, manager.state == .poweredOn,
              let targetID, let operation = pendingOperation else { return }
        pendingOperation = nil
        switch operation {
        case .scan:
            transition(.scanning)
            manager.scanForPeripherals(withServices: [serviceID], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
            armTimeout(seconds: 15)
        case .connect:
            manager.stopScan()
            let selected = peripheral ?? manager.retrievePeripherals(withIdentifiers: [targetID]).first
            guard let selected, selected.identifier == targetID else { fail(.targetMismatch); return }
            peripheral = selected; selected.delegate = self
            decoder = try? BLEFrameDecoder()
            transition(.connecting)
            manager.connect(selected, options: nil)
            armTimeout(seconds: 15)
        }
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        guard BLEHardwareFeatureGate.isEnabled, central === manager else { return }
        if central.state == .poweredOn { executeExplicitOperation() }
        else if central.state != .unknown && central.state != .resetting { fail(.bluetoothUnavailable) }
        else if phase != .waitingForBluetooth { fail(.bluetoothUnavailable) }
    }

    func centralManager(_ central: CBCentralManager, didDiscover candidate: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        guard BLEHardwareFeatureGate.isEnabled, central === manager, phase == .scanning,
              candidate.identifier == targetID else { return }
        // Names/RSSI/advertising cannot authenticate a vehicle. Retain only this
        // explicitly supplied UUID; connecting still needs an explicit call.
        peripheral = candidate
        central.stopScan(); timer?.cancel(); timer = nil
        transition(.candidateFound)
    }

    func centralManager(_ central: CBCentralManager, didConnect connected: CBPeripheral) {
        guard BLEHardwareFeatureGate.isEnabled, central === manager, connected === peripheral,
              connected.identifier == targetID, phase == .connecting else { return }
        transition(.discovering)
        connected.discoverServices([serviceID])
        armTimeout(seconds: 10)
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect candidate: CBPeripheral, error: Error?) {
        guard BLEHardwareFeatureGate.isEnabled, central === manager, candidate === peripheral else { return }
        fail(.connectionFailed)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral candidate: CBPeripheral, error: Error?) {
        guard BLEHardwareFeatureGate.isEnabled, central === manager, candidate === peripheral else { return }
        fail(.disconnected)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard accepts(peripheral), phase == .discovering else { return }
        guard error == nil, let services = peripheral.services,
              services.filter({ $0.uuid == serviceID }).count == 1,
              let service = services.first(where: { $0.uuid == serviceID }) else { fail(.serviceMissing); return }
        peripheral.discoverCharacteristics([characteristicID], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        guard accepts(peripheral), phase == .discovering, service.uuid == serviceID else { return }
        guard error == nil, let values = service.characteristics,
              values.filter({ $0.uuid == characteristicID }).count == 1,
              let value = values.first(where: { $0.uuid == characteristicID }),
              value.properties.contains(.write),
              value.properties.contains(.notify) || value.properties.contains(.indicate) else {
            fail(.characteristicMissing); return
        }
        characteristic = value
        transition(.subscribing)
        // CoreBluetooth manages the CCCD; do not write descriptor 0x2902 by hand.
        peripheral.setNotifyValue(true, for: value)
        armTimeout(seconds: 10)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor value: CBCharacteristic, error: Error?) {
        guard accepts(peripheral), value === characteristic else { return }
        guard error == nil, value.isNotifying else { fail(.notificationFailed); return }
        guard phase == .subscribing else { return }
        timer?.cancel(); timer = nil
        transition(.transportReady) // Deliberately NOT authenticated or control-ready.
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor value: CBCharacteristic, error: Error?) {
        guard accepts(peripheral), phase == .transportReady, value === characteristic else { return }
        guard error == nil, let bytes = value.value, var decoder else { fail(.receiveFailed); return }
        do {
            let frames = try decoder.append(bytes)
            self.decoder = decoder
            for frame in frames {
                guard phase == .transportReady else { break }
                onFrame?(frame)
            }
        } catch { fail(.receiveFailed) }
    }

    func peripheral(_ peripheral: CBPeripheral, didModifyServices invalidatedServices: [CBService]) {
        guard accepts(peripheral), invalidatedServices.contains(where: { $0.uuid == serviceID }) else { return }
        // Do not reuse a characteristic or an authenticated session after GATT
        // service invalidation; a fresh, explicit connection is required.
        fail(.serviceMissing)
    }

    private func sendNextChunk() {
        guard BLEHardwareFeatureGate.isEnabled, phase == .transportReady,
              let peripheral, let characteristic, peripheral.state == .connected,
              characteristic.isNotifying, let chunk = writes.takeNext() else { return }
        // No Android requestMtu here. Apple supplies a payload length, not ATT MTU.
        guard chunk.count <= peripheral.maximumWriteValueLength(for: .withResponse) else { fail(.writeFailed); return }
        peripheral.writeValue(chunk, for: characteristic, type: .withResponse)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor value: CBCharacteristic, error: Error?) {
        guard accepts(peripheral), phase == .transportReady, value === characteristic else { return }
        guard error == nil, writes.writeInFlight else { fail(.writeFailed); return }
        do {
            if try writes.acknowledge() {
                timer?.cancel(); timer = nil
                onWritesCompleted?() // Only GATT completion, never an action result.
            } else { sendNextChunk() }
        } catch { fail(.writeFailed) }
    }

    private func accepts(_ candidate: CBPeripheral) -> Bool {
        BLEHardwareFeatureGate.isEnabled && candidate === peripheral && candidate.identifier == targetID
    }
    private func transition(_ next: Phase) { phase = next; onPhaseChange?(next) }
    private func fail(_ reason: Failure) { disconnect(); failure = reason; transition(.failed) }
    private func armTimeout(seconds: UInt64) {
        timer?.cancel()
        timer = Task { [weak self] in
            do { try await Task.sleep(nanoseconds: seconds * 1_000_000_000) } catch { return }
            guard !Task.isCancelled else { return }
            self?.fail(.timedOut)
        }
    }
}
