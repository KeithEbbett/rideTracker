package com.example.ridetracker.data.sensor

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BleManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val adapter = bluetoothManager.adapter

    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    
    private val CSC_SERVICE_UUID = UUID.fromString("00001816-0000-1000-8000-00805f9b34fb")
    private val CSC_MEASUREMENT_CHAR_UUID = UUID.fromString("00002a5b-0000-1000-8000-00805f9b34fb")

    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    enum class SensorType { HEART_RATE, CYCLING_SPEED_CADENCE, UNKNOWN }

    data class ScannedSensor(
        val device: BluetoothDevice,
        val type: SensorType
    )

    data class SensorData(
        val heartRate: Int? = null,
        val cadence: Int? = null,
        val wheelRevolutions: Long? = null,
        val lastWheelEventTime: Int? = null,
        val crankRevolutions: Int? = null,
        val lastCrankEventTime: Int? = null,
        val rawValue: ByteArray? = null
    )

    @SuppressLint("MissingPermission")
    fun scanAndConnect(onDeviceFound: (ScannedSensor) -> Unit) {
        val scanner = adapter.bluetoothLeScanner
        
        val filters = listOf(
            ScanFilter.Builder().setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID)).build(),
            ScanFilter.Builder().setServiceUuid(ParcelUuid(CSC_SERVICE_UUID)).build()
        )
        
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val serviceUuids = result.scanRecord?.serviceUuids
                val type = when {
                    serviceUuids?.contains(ParcelUuid(HEART_RATE_SERVICE_UUID)) == true -> SensorType.HEART_RATE
                    serviceUuids?.contains(ParcelUuid(CSC_SERVICE_UUID)) == true -> SensorType.CYCLING_SPEED_CADENCE
                    else -> SensorType.UNKNOWN
                }
                onDeviceFound(ScannedSensor(result.device, type))
            }
        }
        scanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Flow<SensorData> = callbackFlow {
        val descriptorQueue = mutableListOf<BluetoothGattDescriptor>()
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Timber.d("GATT Connection State: $newState, Status: $status")
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices()
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    close()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                Timber.d("GATT Services Discovered: $status")
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    prepareNotifications(gatt, descriptorQueue)
                    processNextDescriptor(gatt, descriptorQueue)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                @Suppress("DEPRECATION")
                val value = characteristic.value
                val data = when (characteristic.uuid) {
                    HEART_RATE_MEASUREMENT_CHAR_UUID -> parseHeartRate(value).copy(rawValue = value)
                    CSC_MEASUREMENT_CHAR_UUID -> parseCsc(value).copy(rawValue = value)
                    else -> null
                }
                data?.let { trySend(it) }
            }

            // Android 13+ support
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val data = when (characteristic.uuid) {
                    HEART_RATE_MEASUREMENT_CHAR_UUID -> parseHeartRate(value).copy(rawValue = value)
                    CSC_MEASUREMENT_CHAR_UUID -> parseCsc(value).copy(rawValue = value)
                    else -> null
                }
                data?.let { trySend(it) }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                Timber.d("Descriptor Written: ${descriptor.uuid}, Status: $status")
                processNextDescriptor(gatt, descriptorQueue)
            }
        }

        val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        awaitClose {
            Timber.d("Closing GATT connection for ${device.address}")
            gatt.disconnect()
            gatt.close()
        }
    }

    @SuppressLint("MissingPermission")
    private fun prepareNotifications(gatt: BluetoothGatt, queue: MutableList<BluetoothGattDescriptor>) {
        gatt.services.forEach { service ->
            service.characteristics.forEach { char ->
                if (char.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID || char.uuid == CSC_MEASUREMENT_CHAR_UUID) {
                    gatt.setCharacteristicNotification(char, true)
                    char.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)?.let { descriptor ->
                        queue.add(descriptor)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun processNextDescriptor(gatt: BluetoothGatt, queue: MutableList<BluetoothGattDescriptor>) {
        if (queue.isEmpty()) return
        val descriptor = queue.removeAt(0)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun parseHeartRate(value: ByteArray): SensorData {
        if (value.size < 2) return SensorData()
        val flag = value[0].toInt()
        val isUint16 = (flag and 0x01) != 0
        val heartRate = if (isUint16) {
            if (value.size < 3) return SensorData()
            ((value[1].toInt() and 0xFF) or ((value[2].toInt() and 0xFF) shl 8))
        } else {
            value[1].toInt() and 0xFF
        }
        return SensorData(heartRate = heartRate)
    }

    private fun parseCsc(value: ByteArray): SensorData {
        if (value.size < 1) return SensorData()
        val flags = value[0].toInt()
        var offset = 1
        
        var wheelRevolutions: Long? = null
        var lastWheelEventTime: Int? = null
        var crankRevolutions: Int? = null
        var lastCrankEventTime: Int? = null
        
        if (flags and 0x01 != 0) {
            if (value.size >= offset + 6) {
                // Cumulative Wheel Revolutions (uint32)
                val lsb = ((value[offset].toInt() and 0xFF) or ((value[offset+1].toInt() and 0xFF) shl 8)).toLong()
                val msb = ((value[offset+2].toInt() and 0xFF) or ((value[offset+3].toInt() and 0xFF) shl 8)).toLong()
                wheelRevolutions = (msb shl 16) or lsb
                
                // Last Wheel Event Time (uint16)
                lastWheelEventTime = (value[offset+4].toInt() and 0xFF) or ((value[offset+5].toInt() and 0xFF) shl 8)
                offset += 6
            }
        }
        
        if (flags and 0x02 != 0) {
            if (value.size >= offset + 4) {
                // Cumulative Crank Revolutions (uint16)
                crankRevolutions = (value[offset].toInt() and 0xFF) or ((value[offset+1].toInt() and 0xFF) shl 8)
                // Last Crank Event Time (uint16)
                lastCrankEventTime = (value[offset+2].toInt() and 0xFF) or ((value[offset+3].toInt() and 0xFF) shl 8)
            }
        }
        
        return SensorData(
            wheelRevolutions = wheelRevolutions,
            lastWheelEventTime = lastWheelEventTime,
            crankRevolutions = crankRevolutions,
            lastCrankEventTime = lastCrankEventTime
        )
    }
}
