package com.example.gyro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import com.example.gyro.databinding.ActivityMainBinding
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val MAIN_CODE_PERMISSIONS = 100
private val REQUIRED_PERMISSIONS =
    arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
        Manifest.permission.INTERNET,
        Manifest.permission.CAMERA)

const val TAG: String = "GyroMainActivity"

private fun allPermissionsGranted(context: Context) = REQUIRED_PERMISSIONS.all { permission ->
    val permissionResult = ContextCompat.checkSelfPermission(
        context,
        permission
    )
    Log.i(TAG, String.format(Locale.getDefault(), "Requesting permission %d", permissionResult))
    permissionResult == PackageManager.PERMISSION_GRANTED
}

class MainActivity : AppCompatActivity() {
    /* CAMERA STUFFS */
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var mediaCodec: MediaCodec
    private lateinit var inputSurface: Surface

    private var frameCount: Int = 0

    /* SENSORS STUFFS */
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor

    private lateinit var viewTextViewMinX : TextView
    private lateinit var viewTextViewMinY : TextView
    private lateinit var viewTextViewMinZ : TextView

    private lateinit var viewTextViewX : TextView
    private lateinit var viewTextViewY : TextView
    private lateinit var viewTextViewZ : TextView

    private lateinit var viewTextViewMaxX : TextView
    private lateinit var viewTextViewMaxY : TextView
    private lateinit var viewTextViewMaxZ : TextView

    /* VIEW STUFFS */
    private lateinit var textViewError : TextView
    private lateinit var textViewInfo : TextView

    /* SOCKET STUFFS */
    private var mutex = Mutex() // Mutex lock
    private var socket: DatagramSocket? = null
    // private const val IP = "192.168.1.100" // Replace with server IP
    private val IP = "PORT-KEN"

    private val PORT = 50000 // Replace with server port

    private var minValues = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var maxValues = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    private val initialGravity = floatArrayOf(0f, 0f, 0f)
    private var resetGravity = true

    /* METHODS */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MAIN_CODE_PERMISSIONS) {
            when {
                allPermissionsGranted(applicationContext) -> {
                    // Permissions granted, proceed with using the sensors
                    Toast.makeText(
                        applicationContext,
                        "Great all permission are granted ! Enjoy...",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                else -> {
                    // Permissions denied, handle accordingly (e.g., show a message)
                    Toast.makeText(
                        applicationContext,
                        "Sorry all permission are not granted ! Try again...",
                        Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
           if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
               val linearAccelerationX = event.values[0] - initialGravity[0]
               val linearAccelerationY = event.values[1] - initialGravity[1]
               val linearAccelerationZ = event.values[2] - initialGravity[2]

               if (resetGravity) {
                   initialGravity[0] = event.values[0]
                   initialGravity[1] = event.values[1]
                   initialGravity[2] = event.values[2]

                   viewTextViewMinX.text = getString(R.string.zero)
                   viewTextViewMinY.text = getString(R.string.zero)
                   viewTextViewMinZ.text = getString(R.string.zero)

                   viewTextViewMaxX.text = getString(R.string.zero)
                   viewTextViewMaxY.text = getString(R.string.zero)
                   viewTextViewMaxZ.text = getString(R.string.zero)

                   minValues = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
                   maxValues = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

                   resetGravity = false
               }

               if (linearAccelerationX > maxValues[0]) {
                   maxValues[0] = linearAccelerationX
                   viewTextViewMaxX.text = String.format(Locale.getDefault(),
                       "%.4f", maxValues[0])
               }
               if (linearAccelerationX < minValues[0]) {
                   minValues[0] = linearAccelerationX
                   viewTextViewMinX.text = String.format(Locale.getDefault(),
                       "%.4f", minValues[0])
               }
               if (linearAccelerationY > maxValues[1]) {
                   maxValues[1] = linearAccelerationY
                   viewTextViewMaxY.text = String.format(Locale.getDefault(),
                       "%.4f", maxValues[1])
               }
               if (linearAccelerationY < minValues[1]) {
                   minValues[1] = linearAccelerationY
                   viewTextViewMinY.text = String.format(Locale.getDefault(),
                       "%.4f", minValues[1])
               }
               if (linearAccelerationZ > maxValues[2]) {
                   maxValues[2] = linearAccelerationZ
                   viewTextViewMaxZ.text = String.format(Locale.getDefault(),
                       "%.4f", maxValues[2])
               }
               if (linearAccelerationZ < minValues[2]) {
                   minValues[2] = linearAccelerationZ
                   viewTextViewMinZ.text = String.format(Locale.getDefault(),
                       "%.4f", minValues[2])
               }

               // Update UI elements to display the gyroscope values
               // For example, using TextViews:
               viewTextViewX.text = String.format(Locale.getDefault(),
                   "X: %.4f", linearAccelerationX)
               viewTextViewY.text = String.format(Locale.getDefault(),
                   "Y: %.4f", linearAccelerationY)
               viewTextViewZ.text = String.format(Locale.getDefault(),
                   "Z: %.4f", linearAccelerationZ)
           }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // TODO("Not yet implemented")
        }
    }

    // In onResume() method of your Activity or Fragment:
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted(applicationContext)) {
            sensorManager.registerListener(
                sensorListener,
                accelerometerSensor,
                25000 /*SensorManager.SENSOR_DELAY_GAME*/
            )
        }
    }

    // In onPause() method:
    override fun onPause() {
        super.onPause()
        if(allPermissionsGranted(applicationContext)) {
            sensorManager.unregisterListener(sensorListener)
        }

        socket?.close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        viewTextViewMinX = findViewById(R.id.textViewMinX)
        viewTextViewMinY = findViewById(R.id.textViewMinY)
        viewTextViewMinZ = findViewById(R.id.textViewMinZ)

        viewTextViewX = findViewById(R.id.textViewX)
        viewTextViewY = findViewById(R.id.textViewY)
        viewTextViewZ = findViewById(R.id.textViewZ)

        viewTextViewMaxX = findViewById(R.id.textViewMaxX)
        viewTextViewMaxY = findViewById(R.id.textViewMaxY)
        viewTextViewMaxZ = findViewById(R.id.textViewMaxZ)

        textViewError = findViewById(R.id.textViewError)
        textViewInfo  = findViewById(R.id.textViewInfo)

        val buttonRazSensors = findViewById<Button>(R.id.buttonRazSensors)
        buttonRazSensors.setOnClickListener {
            resetGravity = true
        }

        // Check all permissions granted
        if (!allPermissionsGranted(applicationContext)) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                MAIN_CODE_PERMISSIONS
            )
        } else {
            Toast.makeText(applicationContext,
                "Permission succeeded !", Toast.LENGTH_SHORT).show()

            // Permission already granted, proceed with sensors usage
            sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) as Sensor
            sensorManager.registerListener(
                sensorListener,
                accelerometerSensor,
                20000 /*SensorManager.SENSOR_DELAY_GAME*/
            )

            val ip = getLocalIpAddress()
            val ipAddressText = findViewById<TextView>(R.id.textViewIpAddress)
            ipAddressText.text = ip

            // Initialize the executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            // Permission already granted, proceed with camera usage
            // Start camera preview with CameraX
            startCameraPreview()

            // Initialize MediaCodec for H.264 encoding
            initMediaCodec()
        }
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        // Return the IPv4 address if available
                        val hostAddress = address.hostAddress
                        if (hostAddress != null) {
                            if (hostAddress.indexOf(':') < 0) {
                                return hostAddress
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            textViewError.text = e.message
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaCodec.stop()
        mediaCodec.release()

        cameraExecutor.shutdown()
    }

    private fun startCameraPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build()

            preview.setSurfaceProvider { surfaceRequest ->
                // Use the surface from MediaCodec as the camera's preview output
                surfaceRequest.provideSurface(inputSurface, ContextCompat.getMainExecutor(this)) {
                    // Handle when surface is no longer valid
                    Log.e(TAG, "Surface is not valid")
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this as LifecycleOwner, cameraSelector, preview)
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun initMediaCodec() {
        try {
            val width = 640
            val height = 480

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 4000000) // Bitrate
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 15) // FPS
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // Get the input surface for the encoder (used as the preview output)
            inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

            // Start a background thread to handle encoded output
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                processFrame(width, height)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error initializing MediaCodec", e)
        }
    }

    private fun processFrame(width: Int, height: Int) {
        Log.d(TAG, "processFrame with size $width x $height")

        while (true) {
            val outputBufferInfo = MediaCodec.BufferInfo()
            var outputBufferId = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 10000)
            Log.d(TAG, "Buffer media codec Id is $outputBufferId")
            while (outputBufferId >= 0) {
                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferId)
                val encodedData = ByteArray(outputBufferInfo.size)
                outputBuffer?.get(encodedData)
                Log.d(TAG, "Data length is ${encodedData.size}")

                Log.d(TAG, "Data are is ${byteArrayToHexString(encodedData)}")

                sendFrame(encodedData, width, height)

                mediaCodec.releaseOutputBuffer(outputBufferId, false)
                outputBufferId = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 0)
            }
            sleepNano(10000L)
        }
    }

    private fun sendFrame(h264Data: ByteArray, width: Int, height: Int) = runBlocking {
        // Handle the H.264 data (e.g., save it, stream it, etc.)
        Log.d(TAG, "Encoded H.264 data of size: ${h264Data.size}, width: $width, height: $height")

        val mtuSize = 1200 // Adjust this value based on your requirements
        val totalSize = h264Data.size

        // For example, you could save the h264Data to a file
        mutex.withLock {
            try {
                var sentSize = 0
                val startTime = System.nanoTime()

                // Sending frame header
                // Make frame header data packet
                val mtu = width + 1 + 1 + 2 + 2
                val headerBytes = buildHeaderByteArray(mtu, width, height)
                // Send header
                // Sending packet
                val headerPacket = DatagramPacket(headerBytes, headerBytes.size, InetAddress.getByName(IP), PORT)
                socket?.send(headerPacket)

                while (sentSize < totalSize) {
                    // Create a socket
                    if (socket == null) {
                        socket = DatagramSocket()
                    }

                    var datalength = mtuSize
                    if(sentSize + datalength > totalSize) {
                        datalength = totalSize - sentSize
                    }

                    val data = ByteArray(datalength)
                    mapByteArray(h264Data, data, sentSize, 0, datalength)

                    val packet =
                        DatagramPacket(data, data.size, InetAddress.getByName(IP), PORT)
                    sentSize += datalength

                    socket?.send(packet)
                }

                val endTime = System.nanoTime()
                val duration = (endTime - startTime) / 1_000_000
                Log.d(TAG, "bindPreviewImage: $duration")
            } catch (e: Exception) {
                e.printStackTrace()
                textViewError.text = e.message

                socket?.close()
                socket = null
            }
        }
    }

    private fun buildHeaderByteArray(mtu: Int, width: Int, height: Int) : ByteArray {
        // Frame width/height are limited to 65535x65535 pixels in header encoding
        val beginFrameCode = 184 // 0xB8
        frameCount = if (frameCount > 0xffff) 0 else frameCount + 1
        val h264Code = 128 // 0x80
        val ecc = (beginFrameCode * beginFrameCode + h264Code) / (width * 3 - height ) * 100
        val headerHexStr =
            byteArrayToHexString(lastByteValue(beginFrameCode)) +
                    byteArrayToHexString(lastTwoBytesValue(h264Code)) +
                    byteArrayToHexString(lastTwoBytesValue(width)) +
                    byteArrayToHexString(lastTwoBytesValue(height)) +
                    byteArrayToHexString(lastTwoBytesValue(frameCount)) +
                    byteArrayToHexString(lastTwoBytesValue(ecc))

        Log.d(TAG, "Sending header: $headerHexStr")
        return hexStringToByteArray(headerHexStr)
    }

    private fun sleepNano(delayNano: Long) {
        val startSleepTime = System.nanoTime()
        while (System.nanoTime() - startSleepTime < delayNano) {
            // Sleep ZZzzzZZzzz
        }
    }

    private fun hexStringToByteArray(hexString: String): ByteArray {
        require(hexString.length % 2 == 0) { "Hex string must have an even length" }
        val result = ByteArray(hexString.length / 2)
        for (i in result.indices) {
            val highNibble = hexString[i * 2].digitToIntOrNull(16) ?: error("Invalid hex digit")
            val lowNibble = hexString[i * 2 + 1].digitToIntOrNull(16) ?: error("Invalid hex digit")
            result[i] = ((highNibble shl 4) or lowNibble).toByte()
        }
        return result
    }

    private fun intToBytesBigEndian(value: Int): ByteArray {
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(value)
        return buffer.array()
    }

    private fun lastTwoBytesValue(value: Int): ByteArray {
        val originalBytes = intToBytesBigEndian(value)
        require(originalBytes.size == Int.SIZE_BYTES) { "Input array must have exactly 4 bytes bytes" }
        return byteArrayOf(originalBytes[2], originalBytes[3])
    }

    private fun lastByteValue(value: Int): ByteArray {
        val originalBytes = intToBytesBigEndian(value)
        require(originalBytes.size == Int.SIZE_BYTES) { "Input array must have exactly 4 bytes bytes" }
        return byteArrayOf(originalBytes[3])
    }

    private fun byteArrayToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { String.format("%02x", it) }
    }

    private fun mapByteArray(source: ByteArray, destination: ByteArray, srcPos: Int, destPos: Int, length: Int) {
        require(destPos >= 0 &&
                destPos + length <= destination.size) {
            "Invalid destination position or length"
        }
        require(length <= source.size) { "Source array is too small" }
        System.arraycopy(source, srcPos, destination, destPos, length)
    }
}