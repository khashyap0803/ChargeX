package net.vonforst.evmap.fragment

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.tabs.TabLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentBookingVerificationBinding
import net.vonforst.evmap.security.VerificationResult
import net.vonforst.evmap.viewmodel.VerificationViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Booking Verification Fragment — Two-mode UI for offline booking verification.
 *
 * **User Mode**: Generate a digitally signed booking → display as QR code
 * **Station Mode**: Verify a booking QR code offline using ECDSA
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
    // USER MODE — Generate Demo Booking
    // ═══════════════════════════════════════════════════════

    private fun setupUserMode() {
        binding.btnGenerateBooking.setOnClickListener {
            val stationId = binding.etStationId.text?.toString() ?: "HYD_HITEC_01"
            val validity = binding.etValidity.text?.toString()?.toIntOrNull() ?: 60
            viewModel.generateDemoBooking(stationId, validity)
        }
    }

    // ═══════════════════════════════════════════════════════
    // STATION MODE — Verify Booking
    // ═══════════════════════════════════════════════════════

    private fun setupStationMode() {
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
                        }
                    }
                }
                launch {
                    viewModel.isProcessing.collect { processing ->
                        binding.progressBar.isVisible = processing
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
                binding.tvResultDetails.text = "Signature authentic • Ticket valid"
                showTicketDetails(result.ticket.bookingId, result.ticket.stationId,
                    result.ticket.userId, result.ticket.expiryTimestamp,
                    result.ticket.remainingValidityMinutes())
            }
            is VerificationResult.Expired -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#FFF3E0"))
                binding.tvResultIcon.text = "⏰"
                binding.tvResultTitle.text = "Booking Expired"
                binding.tvResultTitle.setTextColor(Color.parseColor("#E65100"))
                binding.tvResultDetails.text = "Signature is authentic but the ticket has expired"
                showTicketDetails(result.ticket.bookingId, result.ticket.stationId,
                    result.ticket.userId, result.ticket.expiryTimestamp, 0)
            }
            is VerificationResult.Tampered -> {
                binding.cardResult.setCardBackgroundColor(Color.parseColor("#FFEBEE"))
                binding.tvResultIcon.text = "🚫"
                binding.tvResultTitle.text = "TAMPERED — Invalid"
                binding.tvResultTitle.setTextColor(Color.parseColor("#C62828"))
                binding.tvResultDetails.text = "Digital signature verification FAILED.\nThis booking data has been modified or is fraudulent."
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

    private fun showTicketDetails(bookingId: String, stationId: String,
                                   userId: String, expiry: Long, remainingMins: Int) {
        binding.layoutTicketDetails.isVisible = true
        binding.tvBookingId.text = "Booking:  $bookingId"
        binding.tvStationId.text = "Station:  $stationId"
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
