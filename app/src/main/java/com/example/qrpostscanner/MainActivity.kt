
package com.example.qrpostscanner

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.qrpostscanner.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocation: FusedLocationProviderClient
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val records = mutableListOf<ScanRecord>()
    private lateinit var adapter: ScansAdapter

    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private val createCsvLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let { writeCsv(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocation = LocationServices.getFusedLocationProviderClient(this)

        adapter = ScansAdapter(records)
        binding.rvScans.layoutManager = LinearLayoutManager(this)
        binding.rvScans.adapter = adapter

        binding.btnExport.setOnClickListener {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(java.util.Date())
            val fname = "leituras_qr_" + ts + ".csv"
            createCsvLauncher.launch(fname)
        }
        binding.btnClear.setOnClickListener {
            records.clear(); adapter.notifyDataSetChanged()
        }

        if (hasAllPermissions()) startCamera() else requestPerms()
    }

    private fun hasAllPermissions(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPerms() {
        requestPermissionsLauncher.launch(permissions)
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants.values.all { it }
        if (granted) startCamera() else Toast.makeText(this, "Permissões necessárias.", Toast.LENGTH_LONG).show()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor, BarcodeAnalyzer { qr ->
                        onQrScanned(qr)
                    })
                }

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, selector, preview, analyzer)
            } catch (e: Exception) {
                Toast.makeText(this, "Erro a iniciar câmara: ${e.message}", Toast.LENGTH_LONG).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    @Volatile private var lastQr: String? = null
    @Volatile private var lastTime: Long = 0

    private fun onQrScanned(qr: String) {
        // bloquear duplicados durante 2s
        val now = System.currentTimeMillis()
        if (qr == lastQr && (now - lastTime) < 2000) return
        lastQr = qr; lastTime = now

        getCurrentLocation { lat, lon ->
            val ts = isoUtc(now)
            records.add(0, ScanRecord(qr, ts, lat, lon))
            runOnUiThread { adapter.notifyItemInserted(0) }
        }
    }

    private fun getCurrentLocation(callback: (Double?, Double?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null, null); return
        }
        val cts = CancellationTokenSource()
        fusedLocation.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) callback(loc.latitude, loc.longitude) else callback(null, null)
            }
            .addOnFailureListener { callback(null, null) }
    }

    private fun isoUtc(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(ms))
    }

    private fun writeCsv(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { os ->
                OutputStreamWriter(os, Charsets.UTF_8).use { w ->
                    w.appendLine("qr_code,timestamp_utc,latitude,longitude")
                    records.asReversed().forEach { r ->
                        val lat = r.latitude?.toString() ?: ""
                        val lon = r.longitude?.toString() ?: ""
                        // CSV simples; escapar vírgulas e aspas no QR
                        val safeQr = '"' + r.qr.replace(""", """") + '"'
                        w.appendLine("$safeQr,${r.timestampUtcIso},$lat,$lon")
                    }
                }
            }
            Toast.makeText(this, "CSV exportado.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Falha ao exportar: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

// --- RecyclerView Adapter ---
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.qrpostscanner.databinding.ItemScanBinding

class ScansAdapter(private val items: List<ScanRecord>) : RecyclerView.Adapter<ScansAdapter.VH>() {
    class VH(val b: ItemScanBinding) : RecyclerView.ViewHolder(b.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inf = LayoutInflater.from(parent.context)
        return VH(ItemScanBinding.inflate(inf, parent, false))
    }
    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.b.tvQr.text = it.qr
        val meta = buildString {
            append(it.timestampUtcIso)
            if (it.latitude != null && it.longitude != null) {
                append("  |  ")
                append(String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude))
            }
        }
        holder.b.tvMeta.text = meta
    }
}
