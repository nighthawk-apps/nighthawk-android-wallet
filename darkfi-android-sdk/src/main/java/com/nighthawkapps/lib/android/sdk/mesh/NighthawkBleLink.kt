package com.nighthawkapps.lib.android.sdk.mesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelUuid
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.nighthawkapps.lib.android.spackle.Twig
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val UNSIGNED_BYTE_MASK: Int = 0xFF

/**
 * Dual-role BLE radio. Packet semantics stay in Rust / [MeshWireCodec].
 * Does not log Bluetooth addresses.
 */
@RequiresApi(Build.VERSION_CODES.S)
class NighthawkBleLink(
    internal val context: Context,
    internal val sink: MeshLinkSink,
) {
    internal val peers = MeshPeerTable()
    internal val inboxAssemblers = ConcurrentHashMap<String, AttFrameAssembler>()
    internal val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()
    internal val serverDevices = ConcurrentHashMap<String, BluetoothDevice>()
    internal val sessionHint = ByteArray(MeshWireCodec.SENDER_LEN)
    internal val meshToAtt = ConcurrentHashMap<String, String>()
    internal val attToMesh = ConcurrentHashMap<String, ByteArray>()

    var onEngineFlush: (() -> Unit)? = null

    internal var thread: HandlerThread? = null
    internal var radio: Handler? = null
    internal var adapter: BluetoothAdapter? = null
    internal var scanner: BluetoothLeScanner? = null
    internal var advertiser: BluetoothLeAdvertiser? = null
    internal var gattServer: BluetoothGattServer? = null
    internal var scanning = false
    internal var advertising = false
    internal var started = false
    internal var foreground = true
    internal var charging = false
    internal var gatewayArmed = false
    internal var alwaysOn = true
    internal var mtu = MeshAttPolicy.DEFAULT_ATT_MTU

    internal val adapterReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                ctx: Context?,
                intent: Intent?,
            ) {
                if (intent?.action != BluetoothAdapter.ACTION_STATE_CHANGED) return
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                radio?.post {
                    when (state) {
                        BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> tearDownRadio()
                        BluetoothAdapter.STATE_ON -> if (started) startRadioLocked()
                    }
                }
            }
        }

    fun start() {
        if (!MeshPermissionGate.isMeshSdkSupported() || !MeshPermissionGate.hasBlePermissions(context)) {
            Twig.warn { "mesh: radio start refused (sdk or permission)" }
            return
        }
        if (started) return
        started = true
        val t = HandlerThread("nh-mesh-radio").also { it.start() }
        thread = t
        radio = Handler(t.looper)
        try {
            ContextCompat.registerReceiver(
                context,
                adapterReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        } catch (e: SecurityException) {
            Twig.warn(e) { "mesh: adapter receiver not registered" }
        } catch (e: IllegalStateException) {
            Twig.warn(e) { "mesh: adapter receiver not registered" }
        } catch (e: IllegalArgumentException) {
            Twig.warn(e) { "mesh: adapter receiver not registered" }
        }
        radio?.post { startRadioLocked() }
    }

    fun stop() {
        started = false
        radio?.post { tearDownRadio() }
        try {
            context.unregisterReceiver(adapterReceiver)
        } catch (_: Exception) {
        }
        radio?.post {
            thread?.quitSafely()
            thread = null
            radio = null
        }
    }

    fun setPower(
        foreground: Boolean,
        charging: Boolean,
        gatewayArmed: Boolean,
        alwaysOn: Boolean = true,
    ) {
        this.foreground = foreground
        this.charging = charging
        this.gatewayArmed = gatewayArmed
        this.alwaysOn = alwaysOn
        radio?.post { applyDutyLocked() }
    }

    fun send(frame: ByteArray) {
        radio?.post { sendLocked(frame) }
    }

    val peerCount: Int get() = peers.size

    @SuppressLint("MissingPermission")
    internal fun startRadioLocked() {
        val mgr = context.getSystemService(BluetoothManager::class.java) ?: return
        BleRadioBoot.start(this, mgr)
    }

    @SuppressLint("MissingPermission")
    internal fun tearDownRadio() {
        stopScanLocked()
        try {
            advertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {
        }
        advertising = false
        advertiser = null
        clientGatts.values.forEach { gatt ->
            try {
                gatt.disconnect()
                gatt.close()
            } catch (_: Exception) {
            }
        }
        clientGatts.clear()
        try {
            gattServer?.close()
        } catch (_: Exception) {
        }
        gattServer = null
        serverDevices.clear()
        meshToAtt.clear()
        attToMesh.clear()
        peers.clear()
        inboxAssemblers.clear()
        scanner = null
        adapter = null
    }

    @SuppressLint("MissingPermission")
    internal fun openGattServerLocked(mgr: BluetoothManager) {
        if (gattServer != null) return
        gattServer = BleRadioBoot.openServer(this, mgr)
    }

    @SuppressLint("MissingPermission")
    internal fun startAdvertisingLocked(ad: BluetoothAdapter) {
        val adv = ad.bluetoothLeAdvertiser ?: return
        advertiser = adv
        val pid = MeshNative.peerId()
        if (pid != null && pid.size == MeshWireCodec.SENDER_LEN) {
            System.arraycopy(pid, 0, sessionHint, 0, MeshWireCodec.SENDER_LEN)
        } else {
            SecureRandom().nextBytes(sessionHint)
        }
        val settings =
            AdvertiseSettings
                .Builder()
                .setAdvertiseMode(
                    if (foreground) {
                        AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                    } else {
                        AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                    },
                ).setConnectable(true)
                .setTimeout(0)
                .build()
        val data =
            AdvertiseData
                .Builder()
                .addServiceUuid(ParcelUuid(MeshGattConstants.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build()
        val scan =
            AdvertiseData
                .Builder()
                .addServiceData(ParcelUuid(MeshGattConstants.SERVICE_UUID), sessionHint)
                .build()
        try {
            adv.startAdvertising(settings, data, scan, advertiseCallback)
        } catch (e: SecurityException) {
            Twig.warn(e) { "mesh: advertise failed" }
        } catch (e: IllegalStateException) {
            Twig.warn(e) { "mesh: advertise failed" }
        } catch (e: IllegalArgumentException) {
            Twig.warn(e) { "mesh: advertise failed" }
        }
    }

    internal val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                advertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                advertising = false
                Twig.warn { "mesh: advertise error $errorCode" }
            }
        }

    internal fun applyDutyLocked() {
        val duty =
            MeshPowerPolicy.scanDuty(
                foreground = foreground,
                hasPeers = peers.size > 0,
                charging = charging,
                gatewayArmed = gatewayArmed,
                alwaysOn = alwaysOn,
            )
        adapter?.let { restartAdvertisingLocked(it) }
        stopScanLocked()
        if (duty.scanOnMs == 0L) {
            return
        }
        startScanLocked()
        if (!duty.isContinuous) {
            radio?.postDelayed({ stopScanLocked() }, duty.scanOnMs)
            radio?.postDelayed({ if (started) applyDutyLocked() }, duty.scanOnMs + duty.scanOffMs)
        }
    }

    internal val scanCallback =
        object : ScanCallback() {
            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult?,
            ) {
                val device = result?.device ?: return
                radio?.post {
                    considerConnectLocked(
                        device,
                        result.scanRecord?.getServiceData(ParcelUuid(MeshGattConstants.SERVICE_UUID)),
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                scanning = false
                Twig.warn { "mesh: scan failed $errorCode" }
            }
        }

    internal val clientCallback =
        object : BluetoothGattCallback() {
            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int,
            ) {
                val id = gatt.device.address
                radio?.post {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        try {
                            gatt.requestMtu(MeshGattConstants.REQUEST_MTU)
                            gatt.discoverServices()
                        } catch (_: Exception) {
                        }
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        dropPeerLocked(id)
                    }
                }
            }

            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int,
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    this@NighthawkBleLink.mtu = mtu
                }
            }

            @SuppressLint("MissingPermission")
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int,
            ) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                val ch =
                    gatt
                        .getService(MeshGattConstants.SERVICE_UUID)
                        ?.getCharacteristic(MeshGattConstants.CHARACTERISTIC_UUID)
                        ?: return
                try {
                    gatt.setCharacteristicNotification(ch, true)
                    ch.getDescriptor(MeshGattConstants.CCCD_UUID)?.let { d ->
                        d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(d)
                    }
                    attToMesh[gatt.device.address]?.let { mesh ->
                        MeshNative.neighborUp(mesh)
                        onEngineFlush?.invoke()
                    }
                } catch (_: Exception) {
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                val id = gatt.device.address
                val value = characteristic.value ?: return
                radio?.post { ingestAttLocked(id, value) }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                val id = gatt.device.address
                radio?.post { ingestAttLocked(id, value) }
            }
        }

    internal val serverCallback =
        object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(
                device: BluetoothDevice,
                status: Int,
                newState: Int,
            ) {
                val id = device.address
                radio?.post {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        serverDevices[id] = device
                        peers.admit(id, System.currentTimeMillis())
                    } else {
                        serverDevices.remove(id)
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                try {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_READ_NOT_PERMITTED,
                        offset,
                        null,
                    )
                } catch (_: Exception) {
                }
            }

            @SuppressLint("MissingPermission")
            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                if (characteristic.uuid != MeshGattConstants.CHARACTERISTIC_UUID) {
                    rejectWrite(device, requestId, responseNeeded, offset)
                    return
                }
                val bytes = value ?: ByteArray(0)
                radio?.post { ingestAttLocked(device.address, bytes) }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            value,
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onDescriptorWriteRequest(
                device: BluetoothDevice,
                requestId: Int,
                descriptor: BluetoothGattDescriptor,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?,
            ) {
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(
                            device,
                            requestId,
                            BluetoothGatt.GATT_SUCCESS,
                            offset,
                            value,
                        )
                    } catch (_: Exception) {
                    }
                }
            }

            private fun rejectWrite(
                device: BluetoothDevice,
                requestId: Int,
                responseNeeded: Boolean,
                offset: Int,
            ) {
                if (!responseNeeded) return
                try {
                    gattServer?.sendResponse(
                        device,
                        requestId,
                        BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
                        offset,
                        null,
                    )
                } catch (_: Exception) {
                }
            }
        }

    @SuppressLint("MissingPermission")
    internal fun sendLocked(frame: ByteArray) {
        val chunks = AttFrameSplitter.split(frame, MeshAttPolicy.writePayloadCap(mtu))
        val dest = MeshWireCodec.decode(frame)?.recipient
        if (dest == null || dest.size != MeshWireCodec.SENDER_LEN) return
        val att = meshToAtt[meshHex(dest)] ?: return
        writeChunksToAttLocked(att, chunks)
    }
}

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.restartAdvertisingLocked(ad: BluetoothAdapter) {
    try {
        advertiser?.stopAdvertising(advertiseCallback)
    } catch (_: Exception) {
    }
    advertising = false
    startAdvertisingLocked(ad)
}

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.startScanLocked() {
    val sc = scanner ?: return
    if (scanning) return
    val filter =
        ScanFilter
            .Builder()
            .setServiceUuid(ParcelUuid(MeshGattConstants.SERVICE_UUID))
            .build()
    val settings =
        ScanSettings
            .Builder()
            .setScanMode(
                if (foreground) {
                    ScanSettings.SCAN_MODE_BALANCED
                } else {
                    ScanSettings.SCAN_MODE_LOW_POWER
                },
            ).build()
    try {
        sc.startScan(listOf(filter), settings, scanCallback)
        scanning = true
    } catch (e: SecurityException) {
        Twig.warn(e) { "mesh: scan start failed" }
    } catch (e: IllegalStateException) {
        Twig.warn(e) { "mesh: scan start failed" }
    } catch (e: IllegalArgumentException) {
        Twig.warn(e) { "mesh: scan start failed" }
    }
}

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.stopScanLocked() {
    if (!scanning) return
    try {
        scanner?.stopScan(scanCallback)
    } catch (_: Exception) {
    }
    scanning = false
}

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.considerConnectLocked(
    device: BluetoothDevice,
    advertisedMeshId: ByteArray?,
) {
    val id = BleConnect.connectableId(this, device) ?: return
    BleConnect.open(this, device, id, advertisedMeshId)
}

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.dropPeerLocked(id: String) {
    attToMesh.remove(id)?.let { mesh ->
        meshToAtt.remove(meshHex(mesh))
        MeshNative.neighborDown(mesh)
    }
    clientGatts.remove(id)?.let { gatt ->
        try {
            gatt.disconnect()
            gatt.close()
        } catch (_: Exception) {
        }
    }
    serverDevices.remove(id)
    peers.forget(id)
    inboxAssemblers.remove(id)
}

private fun NighthawkBleLink.ingestAttLocked(
    peerId: String,
    chunk: ByteArray,
) {
    val asm = inboxAssemblers.getOrPut(peerId) { AttFrameAssembler() }
    for (frame in asm.ingest(chunk)) {
        MeshWireCodec.decode(frame)?.sender?.let { rememberMeshLocked(peerId, it) }
        sink.onLinkFrame(frame)
    }
}

private fun NighthawkBleLink.rememberMeshLocked(
    attKey: String,
    meshId: ByteArray,
) {
    if (meshId.size != MeshWireCodec.SENDER_LEN) return
    attToMesh[attKey] = meshId
    meshToAtt[meshHex(meshId)] = attKey
}

private fun NighthawkBleLink.meshHex(id: ByteArray): String = id.joinToString("") { b -> "%02x".format(b.toInt() and UNSIGNED_BYTE_MASK) }

@SuppressLint("MissingPermission")
private fun NighthawkBleLink.writeChunksToAttLocked(
    att: String,
    chunks: List<ByteArray>,
) {
    val chUuid = MeshGattConstants.CHARACTERISTIC_UUID
    if (BleChunks.writeClient(this, att, chUuid, chunks)) return
    BleChunks.writeServer(this, att, chUuid, chunks)
}

private object BleRadioBoot {
    @SuppressLint("MissingPermission")
    fun start(
        link: NighthawkBleLink,
        mgr: BluetoothManager,
    ) {
        val ad = mgr.adapter ?: return
        if (!ad.isEnabled) {
            Twig.warn { "mesh: bluetooth adapter off" }
            return
        }
        link.adapter = ad
        link.openGattServerLocked(mgr)
        link.startAdvertisingLocked(ad)
        link.scanner = ad.bluetoothLeScanner
        link.applyDutyLocked()
    }

    @SuppressLint("MissingPermission")
    fun openServer(
        link: NighthawkBleLink,
        mgr: BluetoothManager,
    ): BluetoothGattServer? {
        val server = mgr.openGattServer(link.context, link.serverCallback) ?: return null
        return installMeshService(server)
    }

    private fun installMeshService(server: BluetoothGattServer): BluetoothGattServer? {
        if (!addMeshService(server)) {
            server.close()
            return null
        }
        return server
    }

    private fun addMeshService(server: BluetoothGattServer): Boolean {
        val service =
            BluetoothGattService(
                MeshGattConstants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY,
            )
        val ch =
            BluetoothGattCharacteristic(
                MeshGattConstants.CHARACTERISTIC_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_WRITE,
            )
        ch.addDescriptor(
            BluetoothGattDescriptor(
                MeshGattConstants.CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(ch)
        return server.addService(service)
    }
}

private object BleConnect {
    fun connectableId(
        link: NighthawkBleLink,
        device: BluetoothDevice,
    ): String? {
        val id = device.address ?: return null
        return if (alreadyLinked(link, id)) null else id
    }

    @SuppressLint("MissingPermission")
    fun open(
        link: NighthawkBleLink,
        device: BluetoothDevice,
        id: String,
        advertisedMeshId: ByteArray?,
    ) {
        val evicted = link.peers.admit(id, System.currentTimeMillis())
        if (evicted != null) link.dropPeerLocked(evicted)
        if (advertisedMeshId != null && advertisedMeshId.size == MeshWireCodec.SENDER_LEN) {
            link.rememberMeshLocked(id, advertisedMeshId)
        }
        try {
            val gatt =
                device.connectGatt(
                    link.context,
                    false,
                    link.clientCallback,
                    BluetoothDevice.TRANSPORT_LE,
                )
            link.clientGatts[id] = gatt
        } catch (e: SecurityException) {
            fail(link, id, e)
        } catch (e: IllegalArgumentException) {
            fail(link, id, e)
        } catch (e: IllegalStateException) {
            fail(link, id, e)
        }
    }

    private fun alreadyLinked(
        link: NighthawkBleLink,
        id: String,
    ): Boolean = id == link.adapter?.address || link.clientGatts.containsKey(id) || link.peers.contains(id)

    private fun fail(
        link: NighthawkBleLink,
        id: String,
        error: Exception,
    ) {
        Twig.warn(error) { "mesh: connect failed" }
        link.peers.forget(id)
    }
}

private class ClientCh(
    val gatt: BluetoothGatt,
    val ch: BluetoothGattCharacteristic,
)

private class ServerCh(
    val server: BluetoothGattServer,
    val device: BluetoothDevice,
    val ch: BluetoothGattCharacteristic,
)

private object BleChunks {
    @SuppressLint("MissingPermission")
    fun writeClient(
        link: NighthawkBleLink,
        att: String,
        chUuid: UUID,
        chunks: List<ByteArray>,
    ): Boolean {
        val target = clientChar(link, att, chUuid) ?: return false
        writeEach(chunks) { chunk ->
            target.ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            target.ch.value = chunk
            target.gatt.writeCharacteristic(target.ch)
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun writeServer(
        link: NighthawkBleLink,
        att: String,
        chUuid: UUID,
        chunks: List<ByteArray>,
    ) {
        val target = serverTarget(link, att, chUuid) ?: return
        writeEach(chunks) { chunk ->
            target.ch.value = chunk
            target.server.notifyCharacteristicChanged(target.device, target.ch, false)
        }
    }

    private fun clientChar(
        link: NighthawkBleLink,
        att: String,
        chUuid: UUID,
    ): ClientCh? {
        val gatt = link.clientGatts[att] ?: return null
        return clientCharacteristic(gatt, chUuid)
    }

    private fun clientCharacteristic(
        gatt: BluetoothGatt,
        chUuid: UUID,
    ): ClientCh? {
        val ch = gatt.getService(MeshGattConstants.SERVICE_UUID)?.getCharacteristic(chUuid) ?: return null
        return ClientCh(gatt, ch)
    }

    private fun serverTarget(
        link: NighthawkBleLink,
        att: String,
        chUuid: UUID,
    ): ServerCh? {
        val server = link.gattServer ?: return null
        return serverDevice(link, server, att, chUuid)
    }

    private fun serverDevice(
        link: NighthawkBleLink,
        server: BluetoothGattServer,
        att: String,
        chUuid: UUID,
    ): ServerCh? {
        val device = link.serverDevices[att] ?: return null
        return serverCharacteristic(server, device, chUuid)
    }

    private fun serverCharacteristic(
        server: BluetoothGattServer,
        device: BluetoothDevice,
        chUuid: UUID,
    ): ServerCh? {
        val ch = server.getService(MeshGattConstants.SERVICE_UUID)?.getCharacteristic(chUuid) ?: return null
        return ServerCh(server, device, ch)
    }

    private fun writeEach(
        chunks: List<ByteArray>,
        write: (ByteArray) -> Unit,
    ) {
        for (chunk in chunks) {
            try {
                write(chunk)
            } catch (_: Exception) {
            }
        }
    }
}
