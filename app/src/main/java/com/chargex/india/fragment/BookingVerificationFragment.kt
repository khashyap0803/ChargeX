package com.chargex.india.fragment

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import com.chargex.india.R
import com.chargex.india.databinding.FragmentBookingVerificationBinding
import com.chargex.india.security.VerificationResult
import com.chargex.india.viewmodel.VerificationViewModel
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*

/**
 * Booking Verification Fragment — Two-mode UI for offline booking verification.
 *
 * **User Mode**: Generate a digitally signed booking → display as QR code
 * **Station Mode**: Verify a booking QR code offline using ECDSA
 *   - Camera scanning (real-time QR decode)
 *   - Image upload (decode QR from gallery photo)
 *   - Manual paste (for testing)
 *
 * This fragment demonstrates the complete PKI workflow:
 * 1. Key pair generation (ECDSA secp256r1)
 * 2. Ticket signing (SHA256withECDSA)
 * 3. QR code encoding
 * 4. Offline signature verification
 * 5. Tamper detection
 * 6. Expiry validation
 */
class BookingVerificationFragment : Fragment() {

    private var _binding: FragmentBookingVerificationBinding? = null
    private val binding get() = _binding!!
    private val viewModel: VerificationViewModel by viewModels()

    // Camera capture launcher
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            decodeQrFromBitmap(bitmap)
        } else {
            Toast.makeText(requireContext(), "Camera capture cancelled", Toast.LENGTH_SHORT).show()
        }
    }

    // Gallery image picker launcher
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    decodeQrFromBitmap(bitmap)
                } else {
                    Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            Toast.makeText(requireContext(), "Camera permission is required to scan QR codes", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookingVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupModeToggle()
        setupUserMode()
        setupStationMode()
        setupBackButton()
        observeViewModel()
    }

    // ═══════════════════════════════════════════════════════
    // MODE TOGGLE
    // ═══════════════════════════════════════════════════════

    private fun setupModeToggle() {
        binding.tabMode.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showUserMode()
                    1 -> showStationMode()
                }
                viewModel.clearResult()
                binding.cardResult.isVisible = false
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showUserMode() {
        binding.layoutUserMode.isVisible = true
        binding.layoutStationMode.isVisible = false
    }

    private fun showStationMode() {
        binding.layoutUserMode.isVisible = false
        binding.layoutStationMode.isVisible = true
    }

    // ═══════════════════════════════════════════════════════
    // USER MODE — Generate Booking
    // ═══════════════════════════════════════════════════════

    private fun setupUserMode() {
        binding.btnGenerateBooking.setOnClickListener {
            val creditInput = binding.etCreditAmount.text?.toString() ?: "300"
            val credit = creditInput.toIntOrNull() ?: 300
            val validity = binding.etValidity.text?.toString()?.toIntOrNull() ?: 60
            viewModel.generateDemoBooking(credit, validity)
        }
    }

    // ═══════════════════════════════════════════════════════
    // STATION MODE — Verify Booking (Camera, Gallery, Manual)
    // ═══════════════════════════════════════════════════════

    private fun setupStationMode() {
        // Camera scan
        binding.btnScanQr.setOnClickListener {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
                cameraLauncher.launch(null)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Gallery upload
        binding.btnUploadQrImage.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Manual paste verify
        binding.btnVerifyManual.setOnClickListener {
            val qrData = binding.etManualQr.text?.toString()
            if (!qrData.isNullOrBlank()) {
                viewModel.verifyScannedCode(qrData)
            }
        }

        binding.btnVerifyLastGenerated.setOnClickListener {
            viewModel.verifyLastGenerated()
        }

        binding.btnTestTampered.setOnClickListener {
            viewModel.testTamperedVerification()
        }

        // Charging timer Start/Stop
        binding.btnStartCharging.setOnClickListener {
            viewModel.startCharging()
            binding.btnStartCharging.isVisible = false
            binding.btnStopCharging.isVisible = true
            binding.tvSessionSummary.isVisible = false
        }

        binding.btnStopCharging.setOnClickListener {
            val isOnline = isNetworkAvailable()
            viewModel.stopCharging(isOnline)
            binding.btnStartCharging.isVisible = true
            binding.btnStopCharging.isVisible = false
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Decodes a QR code from a Bitmap using ZXing's BinaryBitmap + HybridBinarizer.
     *
     * ### Algorithm:
     * 1. Convert Bitmap to int[] pixel array
     * 2. Create RGBLuminanceSource from pixels  
     * 3. Apply HybridBinarizer for adaptive thresholding
     * 4. Decode using MultiFormatReader
     * 5. Pass decoded text to VerificationViewModel
     */
    private fun decodeQrFromBitmap(bitmap: Bitmap) {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val hints = mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )

            val result = MultiFormatReader().decode(binaryBitmap, hints)
            val decodedText = result.text

            if (decodedText.isNullOrBlank()) {
                Toast.makeText(requireContext(), "No QR code found in image", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "QR decoded! Verifying...", Toast.LENGTH_SHORT).show()
                viewModel.verifyScannedCode(decodedText)
            }
        } catch (e: NotFoundException) {
            Toast.makeText(requireContext(), "No QR code found in this image", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "QR decode error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupBackButton() {
        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }
    }

    // ═══════════════════════════════════════════════════════
    // OBSERVE VIEWMODEL
    // ═══════════════════════════════════════════════════════

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.generatedQrPayload.collect { payload ->
                        if (payload != null) {
                            showQrCode(payload)
                        }
                    }
                }
                launch {
                    viewModel.verificationResult.collect { result ->
                        if (result != null) {
                            showVerificationResult(result)
                            // Show charging timer card on successful verification
                            if (result is VerificationResult.Valid) {
                                binding.cardChargingTimer.isVisible = true
                            }
                        }
                    }
                }
                launch {
                    viewModel.isProcessing.collect { processing ->
                        binding.progressBar.isVisible = processing
                    }
                }
                launch {
                    viewModel.offlineCreditInr.collect { credit ->
                        binding.tvCreditBalance.text = "₹$credit"
                    }
                }
                launch {
                    viewModel.estimatedChargingHours.collect { hours ->
                        binding.tvChargingHours.text = "~${"%.1f".format(hours)} hrs"
                    }
                }
                // Charging timer observers
                launch {
                    viewModel.elapsedSeconds.collect { elapsed ->
                        binding.tvTimerDisplay.text = viewModel.formatElapsed(elapsed)
                    }
                }
                launch {
                    viewModel.sessionCost.collect { cost ->
                        binding.tvRunningCost.text = "₹${"%.2f".format(cost)}"
                    }
                }
                launch {
                    viewModel.sessionSummary.collect { summary ->
                        if (summary != null) {
                            binding.tvSessionSummary.text = summary
                            binding.tvSessionSummary.isVisible = true
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // QR CODE GENERATION (using ZXing)
    // ═══════════════════════════════════════════════════════

    private fun showQrCode(payload: String) {
        binding.cardQrCode.isVisible = true
        binding.tvQrPayload.text = payload

        try {
            val size = 512
            val hints = mapOf(EncodeHintType.MARGIN to 1)
            val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            binding.ivQrCode.setImageResource(R.drawable.ic_map)
        }
    }

    // ═══════════════════════════════════════════════════════
    // VERIFICATION RESULT DISPLAY
    // ═══════════════════════════════════════════════════════

    private fun showVerificationResult(result: VerificationResult) {
        binding.cardResult.isVisible = true

        when (result) {
            is VerificationResult.Valid -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#E8F5E9"))
                binding.tvResultIcon.text = "✅"
                binding.tvResultTitle.text = "Booking Verified"
                binding.tvResultTitle.setTextColor(Color.parseColor("#2E7D32"))
                binding.tvResultDetails.text = "ECDSA Signature authentic • Trust Token valid"
                showTicketDetails(result.ticket.tokenId, result.ticket.maxCreditInr.toString(),
                    result.ticket.userId, result.ticket.expiryTimestamp,
                    result.ticket.remainingValidityMinutes())
            }
            is VerificationResult.Expired -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                binding.tvResultIcon.text = "⏰"
                binding.tvResultTitle.text = "Token Expired"
                binding.tvResultTitle.setTextColor(Color.parseColor("#E65100"))
                binding.tvResultDetails.text = "Signature is authentic but the trust token has expired"
                showTicketDetails(result.ticket.tokenId, result.ticket.maxCreditInr.toString(),
                    result.ticket.userId, result.ticket.expiryTimestamp, 0)
            }
            is VerificationResult.Tampered -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.tvResultIcon.text = "🚫"
                binding.tvResultTitle.text = "TAMPERED — Invalid"
                binding.tvResultTitle.setTextColor(Color.parseColor("#C62828"))
                binding.tvResultDetails.text = "ECDSA signature verification FAILED.\nThis data has been modified or is fraudulent."
                binding.layoutTicketDetails.isVisible = false
            }
            is VerificationResult.InvalidFormat -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#F3E5F5"))
                binding.tvResultIcon.text = "❓"
                binding.tvResultTitle.text = "Invalid Format"
                binding.tvResultTitle.setTextColor(Color.parseColor("#6A1B9A"))
                binding.tvResultDetails.text = "Reason: ${result.reason}"
                binding.layoutTicketDetails.isVisible = false
            }
            is VerificationResult.Error -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#ECEFF1"))
                binding.tvResultIcon.text = "⚠️"
                binding.tvResultTitle.text = "Error"
                binding.tvResultTitle.setTextColor(Color.parseColor("#37474F"))
                binding.tvResultDetails.text = result.message
                binding.layoutTicketDetails.isVisible = false
            }
        }
    }

    private fun showTicketDetails(tokenId: String, maxCreditInr: String,
                                   userId: String, expiry: Long, remainingMins: Int) {
        binding.layoutTicketDetails.isVisible = true
        binding.tvBookingId.text = "Token:    $tokenId"
        binding.tvStationId.text = "Credit:   ₹$maxCreditInr"
        binding.tvUserId.text = "User:     $userId"

        val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        val expiryStr = dateFormat.format(Date(expiry * 1000))
        val remainingStr = if (remainingMins > 0) " ($remainingMins min remaining)" else " (EXPIRED)"
        binding.tvExpiry.text = "Expires:  $expiryStr$remainingStr"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
