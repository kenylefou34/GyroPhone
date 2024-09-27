package com.example.gyro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.Image
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.Data
import android.util.Log
import android.util.Range
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    // private val tcpClient = TcpClient
    // private val udpClient = UdpClient
    private var socket: DatagramSocket? = null
    // private const val IP = "192.168.1.100" // Replace with server IP
    private val IP = "PORT-KEN"
    private val PORT = 5000 // Replace with server port

    private lateinit var textViewError : TextView
    private lateinit var textViewInfo : TextView

    private var minValues = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var maxValues = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    private val initialGravity = floatArrayOf(0f, 0f, 0f)
    private var resetGravity = true
    private var isFrontCameraActive = false
    private var isDisplayEnabled = false

    private var mutex = Mutex() // Mutex lock

    private var frameCount = 0 // limited to 65535 - 2 octets

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
        // tcpClient.close()
        // udpClient.close()
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

            // Permission already granted, proceed with camera usage
            cameraExecutor = Executors.newSingleThreadExecutor()

            startCamera()
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

        // tcpClient.close()
        // udpClient.close()
        socket?.close()
        socket = null

        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "App"
        private const val DTAG = "DebugApp"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Setting preview camera
            val previewBuilder = Preview.Builder()
            // Manage Frame Rate
            previewBuilder.setTargetFrameRate(Range(20,25))

            try {

                bindPreviewImage(cameraProvider)

                val displayCameraButton: Button = findViewById(R.id.enableCameraDisplay)
                displayCameraButton.setOnClickListener {
                    if (isDisplayEnabled) {
                        Toast.makeText(applicationContext, "Enable display", Toast.LENGTH_SHORT).show()
                        displayFrontOrBackCamera(cameraProvider, previewBuilder)
                    }
                    else {
                        Toast.makeText(applicationContext, "Sending images", Toast.LENGTH_SHORT).show()
                        bindPreviewImage(cameraProvider)
                    }
                    isDisplayEnabled = !isDisplayEnabled
                }

                val switchButton: Button = findViewById(R.id.switchCamera)
                switchButton.setOnClickListener {
                    isFrontCameraActive = !isFrontCameraActive
                    if (isDisplayEnabled) {
                        displayFrontOrBackCamera(cameraProvider, previewBuilder)
                    }
                }

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun displayFrontOrBackCamera(cameraProvider: ProcessCameraProvider, previewBuilder: Preview.Builder)  {
        cameraProvider.unbindAll()
        // Select back camera as a default
        val cameraSelectorBack = CameraSelector.DEFAULT_BACK_CAMERA
        val cameraSelectorFront = CameraSelector.DEFAULT_FRONT_CAMERA

        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(viewBinding.viewCamera.surfaceProvider)
        }

        if (isFrontCameraActive) {
            cameraProvider.bindToLifecycle(this, cameraSelectorBack, preview)
        }
        else {
            cameraProvider.bindToLifecycle(
                this, cameraSelectorFront, preview)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToNv21(imageProxy: ImageProxy): ByteArray? {
        val image = imageProxy.image ?: return null

        // Convert the YUV_420_888 Image to NV21 format
        return yuvToNv21(image)
    }

    // Converts YUV_420_888 image to NV21 format byte array
    private fun yuvToNv21(image: Image): ByteArray {
        val yBuffer: ByteBuffer = image.planes[0].buffer // Y
        val uBuffer: ByteBuffer = image.planes[1].buffer // U
        val vBuffer: ByteBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        return nv21
    }

    private fun bindPreviewImage(cameraProvider: ProcessCameraProvider) {
        // Unbind use cases before rebinding
        cameraProvider.unbindAll()

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(
            Executors.newSingleThreadExecutor(), ImageAnalysis.Analyzer { imageProxy: ImageProxy ->
                textViewError.text = buildString {
                    append(imageProxy.width.toString())
                    append("x")
                    append(imageProxy.height.toString())
                }

                // Convert ImageProxy to NV21 ByteArray (YUV data)
                val yuvBytes = imageProxyToNv21(imageProxy)
                if (yuvBytes != null) {
                    sendFramePackets(yuvBytes, imageProxy.width, imageProxy.height)
                }

                // Free memory here!
                imageProxy.close()  // Close the image to free resources
        })

        // Select back camera as a default
        val cameraSelector =
            if (isFrontCameraActive) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA

        // Bind the ImageAnalysis use case
        cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis)
    }

    private fun sendFramePackets(frameData: ByteArray,
                                 width: Int,
                                 height: Int) = runBlocking {
        mutex.withLock {
            try {
                val startTime = System.nanoTime()

                // Create a socket
                if (socket == null) {
                    socket = DatagramSocket()
                    // socket?.setSendBufferSize(65536) // Set send buffer size to 64 KB
                }

                // Make frame header data packet
                var mtu = width + 1 + 1 + 2 + 2
                val headerBytes = buildHeaderByteArray(mtu, width, height)
                // Send header
                // Sending packet
                val headerPacket = DatagramPacket(headerBytes, headerBytes.size, InetAddress.getByName(IP), PORT)
                socket?.send(headerPacket)

                // Create a DatagramPacket with the byteArray of the whole frame and send it
                mtu = 5 + width
                val line = ByteArray(mtu)
                var row = 0
                // For each row of the image
                while (row < height) {
                    // Make data packet
                    var destPos = 0
                    mapByteArray(lastByteValue(0xFF), line, destPos, 1)
                    destPos++
                    mapByteArray(lastTwoBytesValue(frameCount), line, destPos, 2)
                    destPos += 2
                    mapByteArray(lastTwoBytesValue(row), line, destPos, 2)
                    destPos += 2
                    val offset = row * width
                    mapByteArray(frameData.copyOfRange(offset, offset + width), line, destPos, width)
                    // Send data
                    // Sending packet
                    val dataPacket = DatagramPacket(line, line.size, InetAddress.getByName(IP), PORT)
                    socket?.send(dataPacket)
                    // or
                    // udpClient.send(line)
                    // Increment row
                    row++
                }

                val endTime = System.nanoTime()
                val duration = (endTime - startTime) / 1_000_000
                Log.d(DTAG, "bindPreviewImage: $duration")
            } catch (e: Exception) {
                e.printStackTrace()
                textViewError.text = e.message
            }
        }
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

    private fun mapByteArray(source: ByteArray, destination: ByteArray, destPos: Int, length: Int) {
        require(destPos >= 0 &&
                destPos + length <= destination.size) {
            "Invalid destination position or length"
        }
        require(length <= source.size) { "Source array is too small" }
        System.arraycopy(source, 0, destination, destPos, length)
    }

    private fun buildHeaderByteArray(mtu: Int, width: Int, height: Int) : ByteArray {

        val byteArray = ByteArray(mtu)

        // Frame width/height are limited to 65535x65535 pixels in header encoding
        val beginFrameCode = 184 // 0xB8
        frameCount = if (frameCount > 0xffff) 0 else frameCount + 1
        val yuvCode = 422 // 0x1A6
        val ecc = (beginFrameCode * beginFrameCode + yuvCode) / (width * 3 - height ) * 100
        val headerHexStr =
            byteArrayToHexString(lastByteValue(beginFrameCode)) +
                    byteArrayToHexString(lastTwoBytesValue(yuvCode)) +
                    byteArrayToHexString(lastTwoBytesValue(width)) +
                    byteArrayToHexString(lastTwoBytesValue(height)) +
                    byteArrayToHexString(lastTwoBytesValue(frameCount)) +
                    byteArrayToHexString(lastTwoBytesValue(ecc))

        // Log.i(TAG, "Sending header: $headerHexStr")
        val headerByteArray = hexStringToByteArray(headerHexStr)
        mapByteArray(headerByteArray, byteArray, 0, headerByteArray.size) // Line start 0xff

        return byteArray
    }
}