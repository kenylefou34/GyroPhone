package com.example.gyro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.camera.core.CameraSelector
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
import java.util.TreeMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.experimental.xor

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

    private lateinit var mediaCodec: MediaCodec
    private lateinit var inputSurface: Surface

    private var frameCount: Int = 0

    private val fps: Int = 24
    private val frameIntervalMs: Int = 1000 / fps

    private var videoFrameWidth = 1680 // 640 800 1024 1680 1280 1920
    private var videoFrameHeight = 720 // 480 600 768  720  960  864

    private lateinit var streamingExecutor: ExecutorService
    private val atomicStopDecoding = AtomicBoolean(false)

    private val sendFrameMutex = Mutex()

    /* SENSORS STUFFS */
    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometerSensor: Sensor

    private var linearAccelerationX = 0F
    private var linearAccelerationY = 0F
    private var linearAccelerationZ = 0F

    /* VIEW STUFFS */
    private lateinit var textViewError : TextView

    private lateinit var viewTextViewMinX : TextView
    private lateinit var viewTextViewMinY : TextView
    private lateinit var viewTextViewMinZ : TextView

    private lateinit var viewTextViewX : TextView
    private lateinit var viewTextViewY : TextView
    private lateinit var viewTextViewZ : TextView

    private lateinit var viewTextViewMaxX : TextView
    private lateinit var viewTextViewMaxY : TextView
    private lateinit var viewTextViewMaxZ : TextView

    private lateinit var mediaCodecResolutionSpinner : Spinner
    private lateinit var resolutionOptions: Array<String>

    /* SOCKET STUFFS */
    private var socketFrames: DatagramSocket? = null

    private val serverPortVideoData = 50000

    private val serverAddressName = "PORT-KEN" // "192.168.1.100"

    private val mtuSize = 1200

    private var sps = ByteArray(0)
    private var pps = ByteArray(0)

    /* SENSORS STUFFS */
    private var minValues = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var maxValues = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    private val initialGravity = floatArrayOf(0f, 0f, 0f)
    private var resetGravity = true

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
           if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
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
                   maxValues = floatArrayOf(-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

                   resetGravity = false
               }

               linearAccelerationX = event.values[0] - initialGravity[0]
               linearAccelerationY = event.values[1] - initialGravity[1]
               linearAccelerationZ = event.values[2] - initialGravity[2]

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

    // In onResume() method of your Activity or Fragment:
    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted(applicationContext)) {
            sensorManager.registerListener(
                sensorListener,
                accelerometerSensor,
                10_000 /*SensorManager.SENSOR_DELAY_GAME*/
            )
        }
    }

    // In onPause() method:
    override fun onPause() {
        super.onPause()
        if(allPermissionsGranted(applicationContext)) {
            sensorManager.unregisterListener(sensorListener)
        }

        closeData()
    }

    override fun onDestroy() {
        super.onDestroy()

        closeData()
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

        mediaCodecResolutionSpinner = findViewById(R.id.mediaCodecResolutionSpinner)

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

            runSensorsAndCamera()
        }
    }

    private fun runSensorsAndCamera() {
        // Show camera formats
        getCameraVideoFormats(applicationContext)

        // Permission already granted, proceed with camera usage
        // Start camera preview with CameraX
        startCameraPreview()

        // Initialize MediaCodec for H.264 encoding
        initMediaCodec()
    }

    private fun closeData() {
        socketFrames?.close()
        socketFrames = null

        if(::mediaCodec.isInitialized) {
            mediaCodec.stop()
            mediaCodec.release()
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

    private fun getCameraVideoFormats(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val mutableOptions: MutableList<String> = mutableListOf()
        mutableOptions.add("${videoFrameWidth}x${videoFrameHeight}") // default resolution

        try {
            // Check all cameras
            val cameraIds = cameraManager.cameraIdList

            for (cameraId in cameraIds) {
                val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)

                // Check if camera is the back camera
                if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    Log.d("CameraVideoFormats", "Back camera found: $cameraId")
                } else {
                    continue
                }

                // Get the camera configuration fo streaming (including video formats)
                val map: StreamConfigurationMap? = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                map?.let {
                    // Get sizes for MediaCodec
                    val codecSizes: Array<Size> = it.getOutputSizes(MediaCodec::class.java)
                    Log.d("CameraVideoFormats", "Video resolutions (MediaCodec):")
                    for (size in codecSizes) {
                        val option = "${size.width}x${size.height}"
                        Log.d("CameraVideoFormats", option)
                        mutableOptions.add(option)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("CameraVideoFormats", "Error while retrieving video formats : ${e.message}")
        }

        resolutionOptions = mutableOptions.toTypedArray()
        // Create an adaptor for the spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item, // Default layout for displaying
            resolutionOptions
        )
        // Specify a layout for the dropdown menu when opening
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Bind spinner with adapter
        mediaCodecResolutionSpinner.adapter = adapter
        // Manage elements selection
        mediaCodecResolutionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedResolution = resolutionOptions[position]
                setResolution(selectedResolution)

                // Display a toast to validate the selection
                Toast.makeText(this@MainActivity, "You have chosen resolution: $selectedResolution", Toast.LENGTH_LONG).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Action to do when nothing is selected
            }
        }
    }

    private fun setResolution(resolution: String) {
        // Split the string by "x"
        val dimensions = resolution.split("x")
        if (dimensions.size != 2) {
            return
        }

        // Convert the strings to integers
        videoFrameWidth = dimensions[0].toInt()
        videoFrameHeight = dimensions[1].toInt()

        if(::streamingExecutor.isInitialized) {
            // Shutdown thread to be sure
            atomicStopDecoding.set(true)
            stopStreamingGracefully()
        }

        // Reinit media codec with new resolution
        initMediaCodec()

        atomicStopDecoding.set(false)
        // Start streaming thread
        streamingExecutor = Executors.newSingleThreadExecutor()
        streamingExecutor.execute {
            processFrame()
        }
    }

    // Terminate the thread gracefully
    private fun stopStreamingGracefully() {
        streamingExecutor.shutdown()
        try {
            if (!streamingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                streamingExecutor.shutdownNow() // Force shutdown if not terminated in time
            }
        } catch (ex: InterruptedException) {
            streamingExecutor.shutdownNow()
            Thread.currentThread().interrupt() // Restore interrupted status
        }
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
            // Ensure to stop and release media codec before creating a new one
            if(::mediaCodec.isInitialized) {
                mediaCodec.flush()
                mediaCodec.stop()
                mediaCodec.release()
            }

            // Define a TreeMap with video frame height as keys and bitrates as values
            val bitrateMap = TreeMap<Int, Int>().apply {
                put(480, 1_500_000)
                put(720, 4_000_000)
                put(1080, 10_000_000)
                put(Int.MAX_VALUE, 50_000_000) // Default for values above 1080
            }
            // Find the smallest key that is greater than or equal to `height`
            val nearestHeight = bitrateMap.ceilingKey(videoFrameHeight)
            val adjustedBitrate = bitrateMap[nearestHeight!!] ?: error("Bitrate not found")

            Log.d(TAG, "With ${fps}FPS and a resolution of ${videoFrameHeight}p, the bitrate is $adjustedBitrate")

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoFrameWidth, videoFrameHeight)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, adjustedBitrate) // Adjusted (computed) bitrate
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps) // FPS
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10)
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // Get the input surface for the encoder (used as the preview output)
            inputSurface = mediaCodec.createInputSurface()
            mediaCodec.start()

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Error initializing MediaCodec", e)
        }
    }

    private fun processFrame() {
        Log.d(TAG, "processFrame with size ${videoFrameWidth}x${videoFrameHeight}")

        while (!atomicStopDecoding.get()) {
            val startTime = System.nanoTime()

            val outputBufferInfo = MediaCodec.BufferInfo()
            var outputBufferId = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 10000)
            // Log.d(TAG, "Buffer media codec Id is $outputBufferId")
            while (outputBufferId >= 0) {
                val outputBuffer = mediaCodec.getOutputBuffer(outputBufferId)
                val encodedData = ByteArray(outputBufferInfo.size)
                outputBuffer?.get(encodedData)

                // This indicated that the buffer marked as such contains
                // codec initialization / codec specific data instead of media data.
                // if (outputBufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {

                // This test is used to get SPS and PPS infos
                if(encodedData.size <= 32)
                {
                    Log.i(TAG, "Getting SPS and PPS Infos")
                    val target = byteArrayOf(0, 0, 0, 1) // LUN
                    val positions = mutableListOf<Int>()
                    for (i in encodedData.indices) {
                        if (i + target.size <= encodedData.size && encodedData.sliceArray(i until i + target.size).contentEquals(target)) {
                            positions.add(i)
                        }
                    }
                    sps = ByteArray(positions[1])
                    mapByteArray(encodedData, sps, 0, 0, sps.size)
                    pps = ByteArray(encodedData.size - positions[1])
                    mapByteArray(encodedData, pps, positions[1], 0, pps.size)
                }

                // Send frame only when SPS and PPS have been mapped
                if(sps.isNotEmpty() && pps.isNotEmpty()) {
                    sendFrame(encodedData)
                }

                mediaCodec.releaseOutputBuffer(outputBufferId, false)
                outputBufferId = mediaCodec.dequeueOutputBuffer(outputBufferInfo, 0)
            }

            // Wait for end of frame with respect to FPS (frame rate)
            var durationMs = 0
            do {
                val endTime = System.nanoTime()
                durationMs = ((endTime - startTime) / 1_000_000).toInt()
            } while (durationMs < frameIntervalMs && !atomicStopDecoding.get())
        }
        Log.d(TAG, "processFrame terminated")
    }

    private fun sendFramesPacket(data: ByteArray, sleepFor: Long = 0) {
        // Create a socket
        if (socketFrames == null) {
            socketFrames = DatagramSocket()
        }
        val packet = DatagramPacket(data, data.size, InetAddress.getByName(serverAddressName), serverPortVideoData)
        socketFrames?.send(packet)
        if(sleepFor > 0) {
            sleepNano(sleepFor)
        }
    }

    private fun sendFrame(h264Data: ByteArray) = runBlocking {
        // Handle the H.264 data (e.g., save it, stream it, etc.)
        // Log.d(TAG, "Encoded H.264 data of size: ${h264Data.size}, width: $width, height: $height")
        val totalSize = h264Data.size

        // For example, you could save the h264Data to a file
        sendFrameMutex.withLock {
            try {
                var sentSize = 0

                // Sending frame header
                // Make frame header data packet
                val headerBytes = buildHeaderByteArray(videoFrameWidth, videoFrameHeight, totalSize)
                // Sending packet
                sendFramesPacket(headerBytes)

                // Send sps and pps
                sendFramesPacket(sps)
                sendFramesPacket(pps)

                // Sending accelerometer data
                // Create a ByteBuffer to hold the bytes
                val byteBuffer = ByteBuffer.allocate(Float.SIZE_BYTES * 3)
                // Append the float values to the ByteBuffer
                byteBuffer.putFloat(linearAccelerationX)
                byteBuffer.putFloat(linearAccelerationY)
                byteBuffer.putFloat(linearAccelerationZ)
                // Convert ByteBuffer to ByteArray
                val byteArray = byteBuffer.array()
                sendFramesPacket(byteArray);

                // Sending H.264 data
                val mtuSize = 1280
                while (sentSize < totalSize) {
                    var datalength = mtuSize
                    if(sentSize + datalength > totalSize) {
                        datalength = totalSize - sentSize
                    }

                    val data = ByteArray(datalength)
                    mapByteArray(h264Data, data, sentSize, 0, datalength)

                    // Sending packet
                    sendFramesPacket(data)
                    sentSize += datalength

                }
            } catch (e: Exception) {
                e.printStackTrace()
                textViewError.text = e.message

                socketFrames?.close()
                socketFrames = null
            }
        }
    }

    private fun calculateECC(
        code: Int,
        frameEncoding: Int,
        width: Int,
        height: Int,
        frameId: Int,
        encodedTotalSize: Int
    ): Byte {
        var ecc = code.toByte()

        // XOR each byte of the fields to accumulate parity
        ecc = ecc xor (frameEncoding and 0xFF).toByte()
        ecc = ecc xor ((frameEncoding shr 8) and 0xFF).toByte()
        ecc = ecc xor ((frameEncoding shr 16) and 0xFF).toByte()
        ecc = ecc xor ((frameEncoding shr 24) and 0xFF).toByte()

        ecc = ecc xor (width and 0xFF).toByte()
        ecc = ecc xor ((width shr 8) and 0xFF).toByte()
        ecc = ecc xor ((width shr 16) and 0xFF).toByte()
        ecc = ecc xor ((width shr 24) and 0xFF).toByte()

        ecc = ecc xor (height and 0xFF).toByte()
        ecc = ecc xor ((height shr 8) and 0xFF).toByte()
        ecc = ecc xor ((height shr 16) and 0xFF).toByte()
        ecc = ecc xor ((height shr 24) and 0xFF).toByte()

        ecc = ecc xor (frameId and 0xFF).toByte()
        ecc = ecc xor ((frameId shr 8) and 0xFF).toByte()
        ecc = ecc xor ((frameId shr 16) and 0xFF).toByte()
        ecc = ecc xor ((frameId shr 24) and 0xFF).toByte()

        ecc = ecc xor (encodedTotalSize and 0xFF).toByte()
        ecc = ecc xor ((encodedTotalSize shr 8) and 0xFF).toByte()
        ecc = ecc xor ((encodedTotalSize shr 16) and 0xFF).toByte()
        ecc = ecc xor ((encodedTotalSize shr 24) and 0xFF).toByte()

        return ecc
    }

    private fun buildHeaderByteArray(width: Int, height: Int, totalSize: Int) : ByteArray {
        // Frame width/height are limited to 65535x65535 pixels in header encoding
        val beginFrameCode = 184 // 0xB8
        frameCount = if (frameCount > 0xffff) 0 else frameCount + 1
        val h264Code = 128 // 0x80
        val ecc = calculateECC(beginFrameCode, h264Code, width, height, frameCount, totalSize)
        val headerHexStr =
            byteArrayToHexString(lastByteValue(beginFrameCode)) +
                    byteArrayToHexString(lastTwoBytesValue(h264Code)) +
                    byteArrayToHexString(lastTwoBytesValue(width)) +
                    byteArrayToHexString(lastTwoBytesValue(height)) +
                    byteArrayToHexString(lastTwoBytesValue(frameCount)) +
                    byteArrayToHexString(intToBytesBigEndian(totalSize)) +
                    byteArrayToHexString(lastByteValue(ecc.toInt()))

        // Log.d(TAG, "Sending header: $headerHexStr")
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