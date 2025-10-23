package com.example.barscanner.ui.view

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio 
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.barscanner.R
import com.example.barscanner.ui.view.BarcodeBoxView
import com.google.android.material.card.MaterialCardView
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService 
import java.util.concurrent.Executors 

class BarcodeFragment : Fragment() 
{

    private enum class ScanState 
    {
        AWAITING_ITEM,
        AWAITING_LOCATION,
        AWAITING_CONFIRMATION
    }

    private lateinit var previewView: PreviewView
    private lateinit var tvResult: TextView
    private lateinit var resultCard: MaterialCardView
    private lateinit var barcodeBoxView: BarcodeBoxView 

    private lateinit var cameraExecutor: ExecutorService

    private val scanner by lazy{
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
            .build()
        BarcodeScanning.getClient(options)
    }

    private var currentState = ScanState.AWAITING_ITEM
    private var itemScanRaw: String? = null
    private var locationScanRaw: String? = null
    private var scanStartTime: Long = 0L
    private val PAIR_WINDOW_MS = 30_000L
    private var lastShown: String = ""
    private var pendingValue: String? = null
    private var pendingStartTime: Long = 0
    private val HOLD_TIME_MS = 50L
    private val COL_OK_TXT get() = Color.parseColor("#69F0AE")
    private val COL_BAD_TXT get() = Color.parseColor("#FF8A80")

    private fun pairsOf(vararg p: Pair<String, String>): Map<String, Set<String>> 
    {
        val m = mutableMapOf<String, MutableSet<String>>()
        fun add(a: String, b: String) 
        {
            m.getOrPut(a) { mutableSetOf() }.add(b)
            m.getOrPut(b) { mutableSetOf() }.add(a)
        }

        p.forEach { (a, b) -> add(a, b) }
        return m
    }

    private val PAIRS: Map<String, Set<String>> = pairsOf(
        "R211C12" to "R211C11",
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View 
    {
        val v = inflater.inflate(R.layout.fragment_home, container, false)
        previewView = v.findViewById(R.id.previewView)
        resultCard = v.findViewById(R.id.resultCard)
        tvResult = v.findViewById(R.id.tvResult)
        barcodeBoxView = v.findViewById(R.id.barcodeBoxView)
        
        resultCard.setOnLongClickListener{
            hardResetUiAndState()
            Toast.makeText(requireContext(), "Cleared", Toast.LENGTH_SHORT).show()
            true
        }
        return v
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) 
    {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    override fun onDestroyView() 
    {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }

    private fun startCamera() 
    {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9) 
                .build()
                .also{
                    it.setSurfaceProvider(previewView.surfaceProvider) 
                }

            val analysis = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9) 
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also{
                    it.setAnalyzer(cameraExecutor, this::processImageProxy) 
                }

            try 
            {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } 
            catch (exc: Exception) 
            {
                Log.e("BarcodeFragment", "Use case binding failed", exc)
                Toast.makeText(requireContext(), "Camera setup failed", Toast.LENGTH_SHORT).show()
            }

        },
        ContextCompat.getMainExecutor(requireContext()))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy)
    {
        val mediaImage = imageProxy.image ?: run{
            imageProxy.close(); 
            return
        }
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        val rotation = imageProxy.imageInfo.rotationDegrees
        val rotated = rotation == 90 || rotation == 270 
        val sourceWidth = if (rotated) imageProxy.height else imageProxy.width
        val sourceHeight = if (rotated) imageProxy.width else imageProxy.height

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                barcodeBoxView.updateView(barcodes, sourceWidth, sourceHeight)

                if (barcodes.isNotEmpty()) 
                {
                    val raw = barcodes.first().rawValue.orEmpty()
                    if (raw.isNotEmpty() && raw != lastShown) 
                    {
                        val now = System.currentTimeMillis()
                        if (pendingValue == raw) 
                        {
                            if (now - pendingStartTime >= HOLD_TIME_MS) 
                            {
                                lastShown = raw
                                onStableScan(raw) 
                                pendingValue = null
                            }
                        } 
                        else 
                        {
                            pendingValue = raw
                            pendingStartTime = now
                        }
                    }
                } 
                else 
                {
                    pendingValue = null
                }
            }
            .addOnFailureListener{
                Log.e("BarcodeFragment", "Barcode scanning failed", it)
                barcodeBoxView.updateView(emptyList(), 1, 1)
            }
            .addOnCompleteListener{
                imageProxy.close() 
            }
    }

    private fun onStableScan(raw: String) 
    {
        vibrate(35)
        beep() 
        resultCard.visibility = View.VISIBLE
        resultCard.bringToFront()

        val now = System.currentTimeMillis()
        if (currentState != ScanState.AWAITING_ITEM && now - scanStartTime > PAIR_WINDOW_MS) 
        {
            hardResetUiAndState()
            Toast.makeText(requireContext(), "Timed out, please start over.", Toast.LENGTH_SHORT).show()
            return
        }

        when (currentState) 
        {
            ScanState.AWAITING_ITEM -> 
            {
                itemScanRaw = raw
                scanStartTime = now
                currentState = ScanState.AWAITING_LOCATION
                tvResult.setTextColor(Color.WHITE)
                tvResult.text = "Item: $raw\n\nScan LOCATION barcode"
            }

            ScanState.AWAITING_LOCATION -> 
            {
                if (arePair(itemScanRaw!!, raw)) 
                {
                    locationScanRaw = raw
                    currentState = ScanState.AWAITING_CONFIRMATION
                    tvResult.setTextColor(Color.WHITE)
                    tvResult.text = "Location: $raw\n\nRescan ITEM barcode"
                } 
                else 
                {
                    tvResult.setTextColor(COL_BAD_TXT)
                    barcodeBoxView.updateView(emptyList(), 1, 1)
                    tvResult.text = "WRONG LOCATION\n\nItem: $itemScanRaw\nScan correct location"

                    hardResetUiAndState(delayMs = 5000)
                }
            }

            ScanState.AWAITING_CONFIRMATION -> 
            {
                if (raw == itemScanRaw) 
                {
                    val successText = "SUCCESS ✅\n$itemScanRaw placed at $locationScanRaw"
                    tvResult.setTextColor(COL_OK_TXT)
                    tvResult.text = successText
                    
                    barcodeBoxView.updateView(emptyList(), 1, 1)
                    hardResetUiAndState(delayMs = 5000)
                } 
                else 
                {
                    tvResult.setTextColor(COL_BAD_TXT)
                    tvResult.text = "WRONG ITEM\nExpected: $itemScanRaw Scanned: $raw"
                }
            }
        }
    }

    private fun arePair(a: String, b: String): Boolean 
    {
        return PAIRS[a]?.contains(b) == true || PAIRS[b]?.contains(a) == true
    }

    private fun hardResetUiAndState(delayMs: Long = 0) 
    {
        val resetAction = 
        {
            currentState = ScanState.AWAITING_ITEM
            itemScanRaw = null
            locationScanRaw = null
            scanStartTime = 0L
            pendingValue = null
            pendingStartTime = 0L
            lastShown = ""
            tvResult.text = ""
            tvResult.setTextColor(Color.WHITE)
            resultCard.visibility = View.GONE
            barcodeBoxView.updateView(emptyList(), 1, 1)
        }

        if (delayMs > 0) 
        {
            view?.postDelayed(resetAction, delayMs)
        } 
        else 
        {
            resetAction.invoke()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ms: Long) 
    {
        val vib = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        if (android.os.Build.VERSION.SDK_INT >= 26)
        {
            vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        else 
        {
            vib.vibrate(ms)
        }
    }

    private fun beep() 
    {
        try 
        {
            val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            toneGen.startTone(ToneGenerator.TONE_PROP_ACK, 200)
            toneGen.release()
        } 
        catch (e: Exception) 
        {
            Log.e("BarcodeFragment", "Error playing beep sound", e)
        }
    }
}