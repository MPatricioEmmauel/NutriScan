package com.example.nutriscan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var scannedTextView: TextView
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var productNameTextView: TextView
    private lateinit var nutritionGradeTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        scannedTextView = findViewById(R.id.scannedText)
        productNameTextView = findViewById(R.id.productName)
        nutritionGradeTextView = findViewById(R.id.nutritionGrade)

        cameraExecutor = Executors.newSingleThreadExecutor()

        // Check camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                processImageProxy(imageProxy)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalysis)
                Log.d(TAG, "Camera bound to lifecycle")
            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        Log.d(TAG, "processImageProxy: Analyzing image")

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            val scanner: BarcodeScanner = BarcodeScanning.getClient()

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val barcodeValue = barcode.displayValue
                        Log.d(TAG, "Barcode detected: ${barcode.displayValue}")
                        runOnUiThread {
                            scannedTextView.text = "Código escaneado: ${barcode.displayValue}"
                            Toast.makeText(this, "Código escaneado: ${barcode.displayValue}", Toast.LENGTH_SHORT).show()
                        }
                        barcodeValue?.let { fetchProductInfo(it) }
                    }
                }
                .addOnFailureListener {
                    Log.d(TAG, "No barcode found")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun fetchProductInfo(barcode: String) {
        val api = RetroFitClient.instance
        api.getProductByBarcode(barcode).enqueue(object : Callback<ProductResponse> {
            override fun onResponse(call: Call<ProductResponse>, response: Response<ProductResponse>) {
                if (response.isSuccessful) {
                    val productResponse = response.body()
                    productResponse?.let {
                        Log.d(TAG, "API Response: $it")
                        if (it.status == 1) {
                            val productName = it.product?.product_name ?: "Unknown"
                            val nutriScore = it.product?.nutrition_grades ?: "Unknown"
                            Log.d(TAG, "Product Name: $productName, Nutri-score: $nutriScore")
                            runOnUiThread {
                                productNameTextView.text = "Nombre del producto: $productName"
                                nutritionGradeTextView.text = "Nutri-score: $nutriScore"
                            }
                        } else {
                            Log.d(TAG, "Product not found. Status: ${it.status}, Message: ${it.status_verbose}")
                            runOnUiThread {
                                productNameTextView.text = "Producto no encontrado"
                                nutritionGradeTextView.text = ""
                            }
                        }
                    }
                } else {
                    Log.d(TAG, "API response unsuccessful. Code: ${response.code()}, Message: ${response.message()}")
                    Toast.makeText(this@MainActivity, "Error en la respuesta de la API", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<ProductResponse>, t: Throwable) {
                Log.e("API_ERROR", "Error al llamar a la API", t)
                Toast.makeText(this@MainActivity, "Error al llamar a la API", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        val granted = ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "Permission $it granted: $granted")
        granted
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraXApp"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
