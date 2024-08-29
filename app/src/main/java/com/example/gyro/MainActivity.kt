package com.example.gyro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Range
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.gyro.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


private const val MAIN_CODE_PERMISSIONS = 100
private val REQUIRED_PERMISSIONS =
    arrayOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.CHANGE_WIFI_STATE,
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

    private var minValues = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE)
    private var maxValues = floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE, Float.MIN_VALUE)

    private val initialGravity = floatArrayOf(0f, 0f, 0f)
    private var resetGravity = true
    private var isFrontCameraActive = false

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

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            // Handle accuracy changes if needed
        }
    }

    // In onResume() method of your Activity or Fragment:
    override fun onResume() {
        super.onResume()
        sensorManager.registerListener(
            sensorListener,
            accelerometerSensor,
            25000 /*SensorManager.SENSOR_DELAY_GAME*/
        )
    }

    // In onPause() method:
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorListener)
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

            // Permission already granted, proceed with camera usage
            cameraExecutor = Executors.newSingleThreadExecutor()

            startCamera()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
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
        val lifecycleOwner = this

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Setting preview camera
            val previewBuilder = Preview.Builder()
            // Manage Frame Rate
            previewBuilder.setTargetFrameRate(Range(15,25))

            val preview = previewBuilder.build().also {
                    it.setSurfaceProvider(viewBinding.viewCamera.surfaceProvider)
                }

            // Select back camera as a default
            val cameraSelectorBack = CameraSelector.DEFAULT_BACK_CAMERA
            val cameraSelectorFront = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelectorBack, preview)
                isFrontCameraActive = false

                val switchButton: Button = findViewById(R.id.switchCamera)
                switchButton.setOnClickListener {
                    cameraProvider.unbindAll()
                    if (isFrontCameraActive) {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelectorBack, preview)
                    }
                    else {
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelectorFront, preview)
                    }
                    isFrontCameraActive = !isFrontCameraActive
                }
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }
}