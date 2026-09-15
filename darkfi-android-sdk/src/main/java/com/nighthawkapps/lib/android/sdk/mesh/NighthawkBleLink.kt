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
import com.nighthawkapps.lib.android.spackle.Twig
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Dual-role BLE radio. Packet semantics stay in Rust / [MeshWireCodec].
 * Does not log Bluetooth addresses.
 */
@RequiresApi(Build.VERSION_CODES.S)
class NighthawkBleLink(
    private val context: Context,
    private val sink: MeshLinkSink,
) {
    private val peers = MeshPeerTable()
    private val inboxAssemblers = ConcurrentHashMap<String, AttFrameAssembler>()
    private val clientGatts = ConcurrentHashMap<String, BluetoothGatt>()
    private val serverDevices = ConcurrentHashMap<String, BluetoothDevice>()
    private val sessionHint = ByteArray(8)
    private val meshToAtt = ConcurrentHashMap<String, String>()
    private val attToMesh = ConcurrentHashMap<String, ByteArray>()

    var onEngineFlush: (() -> Unit)? = null

    private var thread: HandlerThread? = null
    private var radio: Handler? = null
    private var adapter: BluetoothAdapter? = null
    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var scanning = false
    private var advertising = false
    private var started = false
    private var foreground = true
    private var charging = false
    private var gatewayArmed = false
    private var alwaysOn = true
    private var mtu = MeshAttPolicy.DEFAULT_ATT_MTU

    private val adapterReceiver =
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
            context.registerReceiver(adapterReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        } catch (e: Exception) {
            Twig.warn { "mesh: adapter receiver not registered" }
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
    private fun startRadioLocked() {
        val mgr = context.getSystemService(BluetoothManager::class.java) ?: return
        val ad = mgr.adapter ?: return
        if (!ad.isEnabled) {
            Twig.warn { "mesh: bluetooth adapter off" }
            return
        }
        adapter = ad
        openGattServerLocked(mgr)
        startAdvertisingLocked(ad)
        scanner = ad.bluetoothLeScanner
        applyDutyLocked()
    }

    @SuppressLint("MissingPermission")
    private fun tearDownRadio() {
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
    private fun openGattServerLocked(mgr: BluetoothManager) {
        if (gattServer != null) return
        val server = mgr.openGattServer(context, serverCallback) ?: return
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
        if (!server.addService(service)) {
            server.close()
            return
        }
        gattServer = server
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertisingLocked(ad: BluetoothAdapter) {
        val adv = ad.bluetoothLeAdvertiser ?: return
        advertiser = adv
        val pid = MeshNative.peerId()
        if (pid != null && pid.size == 8) {
            System.arraycopy(pid, 0, sessionHint, 0, 8)
        } else {
            SecureRandom().nextBytes(sessionHint)
        }
        val settings =
            AdvertiseSettings.Builder()
                .setAdvertiseMode(
                    if (foreground) {
                        AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
                    } else {
                        AdvertiseSettings.ADVERTISE_MODE_LOW_POWER
                    },
                )
                .setConnectable(true)
                .setTimeout(0)
                .build()
        val data =
            AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(MeshGattConstants.SERVICE_UUID))
                .setIncludeDeviceName(false)
                .build()
        val scan =
            AdvertiseData.Builder()
                .addServiceData(ParcelUuid(MeshGattConstants.SERVICE_UUID), sessionHint)
                .build()
        try {
            adv.startAdvertising(settings, data, scan, advertiseCallback)
        } catch (e: Exception) {
            Twig.warn { "mesh: advertise failed" }
        }
    }

    private val advertiseCallback =
        object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                advertising = true
            }

            override fun onStartFailure(errorCode: Int) {
                advertising = false
                Twig.warn { "mesh: advertise error $errorCode" }
            }
        }

    private fun applyDutyLocked() {
        val duty =
            MeshPowerPolicy.scanDuty(
                foreground = foreground,
                hasPeers = peers.size > 0,
                charging = charging,
                gatewayArmed = gatewayArmed,
                alwaysOn = alwaysOn,
            )
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

    @SuppressLint("MissingPermission")
    private fun startScanLocked() {
        val sc = scanner ?: return
        if (scanning) return
        val filter =
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(MeshGattConstants.SERVICE_UUID))
                .build()
        val settings =
            ScanSettings.Builder()
                .setScanMode(
                    if (foreground) {
                        ScanSettings.SCAN_MODE_BALANCED
                    } else {
                        ScanSettings.SCAN_MODE_LOW_POWER
                    },
                )
                .build()
        try {
            sc.startScan(listOf(filter), settings, scanCallback)
            scanning = true
        } catch (e: Exception) {
            Twig.warn { "mesh: scan start failed" }
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanLocked() {
        if (!scanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
        scanning = false
    }

    private val scanCallback =
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

    @SuppressLint("MissingPermission")
    private fun considerConnectLocked(
        device: BluetoothDevice,
        advertisedMeshId: ByteArray?,
    ) {
        val id = device.address ?: return
        if (id == adapter?.address) return
        if (clientGatts.containsKey(id) || peers.contains(id)) return
        val evicted = peers.admit(id, System.currentTimeMillis())
        if (evicted != null) dropPeerLocked(evicted)
        if (advertisedMeshId != null && advertisedMeshId.size == 8) {
            rememberMeshLocked(id, advertisedMeshId)
        }
        try {
            val gatt = device.connectGatt(context, false, clientCallback, BluetoothDevice.TRANSPORT_LE)
            clientGatts[id] = gatt
        } catch (e: Exception) {
            peers.forget(id)
        }
    }

    @SuppressLint("MissingPermission")
    private fun dropPeerLocked(id: String) {
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

    private val clientCallback =
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
                    gatt.getService(MeshGattConstants.SERVICE_UUID)
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

    private val serverCallback =
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
                    if (responseNeeded) {
                        try {
                            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, offset, null)
                        } catch (_: Exception) {
                        }
                    }
                    return
                }
                val bytes = value ?: ByteArray(0)
                radio?.post { ingestAttLocked(device.address, bytes) }
                if (responseNeeded) {
                    try {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
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
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
                    } catch (_: Exception) {
                    }
                }
            }
        }

    private fun ingestAttLocked(
        peerId: String,
        chunk: ByteArray,
    ) {
        val asm = inboxAssemblers.getOrPut(peerId) { AttFrameAssembler() }
        for (frame in asm.ingest(chunk)) {
            MeshWireCodec.decode(frame)?.sender?.let { rememberMeshLocked(peerId, it) }
            sink.onLinkFrame(frame)
        }
    }

    private fun rememberMeshLocked(
        attKey: String,
        meshId: ByteArray,
    ) {
        if (meshId.size != 8) return
        attToMesh[attKey] = meshId
        meshToAtt[meshHex(meshId)] = attKey
    }

    private fun meshHex(id: ByteArray): String =
        id.joinToString("") { b -> "%02x".format(b.toInt() and 0xff) }

    @SuppressLint("MissingPermission")
    private fun sendLocked(frame: ByteArray) {
        val chunks = AttFrameSplitter.split(frame, MeshAttPolicy.writePayloadCap(mtu))
        val dest = MeshWireCodec.decode(frame)?.recipient
        if (dest == null || dest.size != 8) return
        val att = meshToAtt[meshHex(dest)] ?: return
        writeChunksToAttLocked(att, chunks)
    }

    @SuppressLint("MissingPermission")
    private fun writeChunksToAttLocked(
        att: String,
        chunks: List<ByteArray>,
    ) {
        val chUuid = MeshGattConstants.CHARACTERISTIC_UUID
        clientGatts[att]?.let { gatt ->
            val ch = gatt.getService(MeshGattConstants.SERVICE_UUID)?.getCharacteristic(chUuid)
            if (ch != null) {
                for (chunk in chunks) {
                    try {
                        ch.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                        ch.value = chunk
                        gatt.writeCharacteristic(ch)
                    } catch (_: Exception) {
                    }
                }
                return
            }
        }
        val server = gattServer ?: return
        val device = serverDevices[att] ?: return
        val localCh =
            server.getService(MeshGattConstants.SERVICE_UUID)?.getCharacteristic(chUuid) ?: return
        for (chunk in chunks) {
            try {
                localCh.value = chunk
                server.notifyCharacteristicChanged(device, localCh, false)
            } catch (_: Exception) {
            }
        }
    }
}
