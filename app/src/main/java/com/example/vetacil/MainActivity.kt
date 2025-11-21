package com.vetacil.app

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vetacil.app.model.VeterinaryClinic
import com.vetacil.app.network.OverpassApiService
import com.vetacil.app.repository.VeterinaryRepository
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import android.widget.ProgressBar
import android.widget.TextView
import org.osmdroid.views.overlay.infowindow.InfoWindow
import android.widget.ImageButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // Views
    lateinit var mapView: MapView  // private kaldırıldı
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var btnFilterOpen: MaterialButton
    private lateinit var btnSearch: MaterialButton
    private lateinit var tvInfo: TextView
    private lateinit var progressBar: ProgressBar

    // Location
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null


    // Fotoğraf için değişkenler
    private var currentPhotoPath: String? = null
    private var currentPhotoUri: Uri? = null
    private var currentClinicForPhoto: VeterinaryClinic? = null

    //Kamera launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && currentPhotoUri != null) {
            sendPhotoToVet()
        } else {
            Toast.makeText(this, "Fotoğraf çekilemedi", Toast.LENGTH_SHORT).show()
        }
    }

    //  Galeri launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            currentPhotoUri = it
            sendPhotoToVet()
        }
    }

    // Kamera izni launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showPhotoOptions()
        } else {
            Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
        }
    }

    // Data
    private val repository by lazy {
        VeterinaryRepository(OverpassApiService.create())
    }
    var allClinics = listOf<VeterinaryClinic>()  // private kaldırıldı
    private var isFilterActive = false

    // Permission launcher
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            getCurrentLocation()
        } else {
            Toast.makeText(this, "Konum izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(
            applicationContext,
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        )

        setContentView(R.layout.activity_main)

        initViews()
        setupMap()
        setupLocationClient()
        setupClickListeners()
        checkPermissionsAndStart()
    }

    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        btnFilterOpen = findViewById(R.id.btnFilterOpen)
        btnSearch = findViewById(R.id.btnSearch)
        tvInfo = findViewById(R.id.tvInfo)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        // Performans optimizasyonları
        mapView.isTilesScaledToDpi = true
        mapView.setUseDataConnection(true)
        mapView.isHorizontalMapRepetitionEnabled = false
        mapView.isVerticalMapRepetitionEnabled = false
        mapView.setScrollableAreaLimitDouble(null)

        mapView.controller.setZoom(6.0)
        mapView.controller.setCenter(GeoPoint(39.0, 35.0))

        mapView.setOnClickListener {
            InfoWindow.closeAllInfoWindowsOn(mapView)
            true
        }
    }

    private fun setupLocationClient() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
    }

    private fun setupClickListeners() {
        fabMyLocation.setOnClickListener {
            currentLocation?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.animateTo(geoPoint)
                mapView.controller.setZoom(16.0)
            } ?: run {
                getCurrentLocation()
            }
        }

        btnSearch.setOnClickListener {
            currentLocation?.let {
                searchNearbyVeterinaries(it.latitude, it.longitude, isFilterActive)
            } ?: run {
                Toast.makeText(this, "Konum alınıyor, lütfen bekleyin", Toast.LENGTH_SHORT).show()
                getCurrentLocation()
            }
        }

        btnFilterOpen.setOnClickListener {
            isFilterActive = !isFilterActive
            updateFilterButton()

            if (allClinics.isNotEmpty()) {
                displayClinics(
                    if (isFilterActive) allClinics.filter { it.isOpen }
                    else allClinics
                )
            } else {
                currentLocation?.let { location ->
                    searchNearbyVeterinaries(location.latitude, location.longitude, isFilterActive)
                }
            }
        }
    }

    private fun updateFilterButton() {
        if (isFilterActive) {
            btnFilterOpen.text = "Tüm Veterinerleri Göster"
            btnFilterOpen.setIconResource(android.R.drawable.ic_menu_close_clear_cancel)
        } else {
            btnFilterOpen.text = "Sadece Açık Olanları Göster"
            btnFilterOpen.setIconResource(android.R.drawable.ic_menu_search)
        }
    }

    private fun checkPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            getCurrentLocation()
        } else {
            locationPermissionLauncher.launch(permissions)
        }
    }

    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkPermissionsAndStart()
            return
        }

        showLoading(true)

        // GERÇEK KONUM KODU - AKTİF
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location: Location? ->
            showLoading(false)
            location?.let {
                currentLocation = it
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                mapView.controller.setCenter(geoPoint)
                mapView.controller.setZoom(15.0)

                // Kullanıcı konumunu işaretle
                addUserMarker(geoPoint)

                tvInfo.text = "Konum alındı! Arama yapabilirsiniz"
            } ?: run {
                Toast.makeText(this, "Konum alınamadı", Toast.LENGTH_SHORT).show()
                tvInfo.text = "Konum alınamadı, lütfen tekrar deneyin"
            }
        }.addOnFailureListener {
            showLoading(false)
            Toast.makeText(this, "Konum hatası: ${it.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addUserMarker(geoPoint: GeoPoint) {
        mapView.overlays.removeAll { it is Marker && it.title == "Konumunuz" }

        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.title = "Konumunuz"
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.icon = ContextCompat.getDrawable(this, R.drawable.ic_my_location)

        mapView.overlays.add(marker)
        mapView.invalidate()
    }

    private fun searchNearbyVeterinaries(
        latitude: Double,
        longitude: Double,
        onlyOpen: Boolean,
        radius: Int = 10000  // Varsayılan 10km
    ) {
        if (!isInternetAvailable()) {
            Toast.makeText(this, "İnternet bağlantısı yok!", Toast.LENGTH_LONG).show()
            tvInfo.text = "İnternet bağlantısı gerekli"
            return
        }

        showLoading(true)
        tvInfo.text = "Yakındaki veterinerler aranıyor... (${radius/1000} km)"

        lifecycleScope.launch {
            try {
                repository.searchNearbyVeterinaries(
                    latitude = latitude,
                    longitude = longitude,
                    radiusMeters = radius,
                    onlyOpen = onlyOpen
                ).fold(
                    onSuccess = { clinics ->
                        showLoading(false)
                        allClinics = clinics

                        if (clinics.isEmpty()) {
                            // BULUNAMADIYSA YARÇAPI ARTTIR
                            when (radius) {
                                10000 -> {
                                    tvInfo.text = "10 km'de bulunamadı, 25 km'de aranıyor..."
                                    searchNearbyVeterinaries(latitude, longitude, onlyOpen, 25000)
                                }
                                25000 -> {
                                    tvInfo.text = "25 km'de bulunamadı, 50 km'de aranıyor..."
                                    searchNearbyVeterinaries(latitude, longitude, onlyOpen, 50000)
                                }
                                50000 -> {
                                    tvInfo.text = "50 km'de bulunamadı, 100 km'de aranıyor..."
                                    searchNearbyVeterinaries(latitude, longitude, onlyOpen, 100000)
                                }
                                else -> {
                                    tvInfo.text = "100 km içinde veteriner bulunamadı"
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Çok geniş alanda arama yapıldı, veteriner bulunamadı",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        } else {
                            displayClinics(clinics)
                            tvInfo.text = "${clinics.size} veteriner bulundu (${radius/1000} km)"
                            Toast.makeText(
                                this@MainActivity,
                                "${clinics.size} veteriner bulundu",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onFailure = { error ->
                        showLoading(false)
                        val errorMessage = when {
                            error.message?.contains("timeout") == true ->
                                "Bağlantı zaman aşımı. WiFi yerine mobil veri deneyin."
                            error.message?.contains("Unable to resolve host") == true ->
                                "Sunucuya erişilemiyor."
                            else ->
                                "Hata: ${error.message}"
                        }
                        tvInfo.text = "Arama başarısız"

                        androidx.appcompat.app.AlertDialog.Builder(this@MainActivity)
                            .setTitle("Bağlantı Hatası")
                            .setMessage(errorMessage)
                            .setPositiveButton("Tekrar Dene") { _, _ ->
                                searchNearbyVeterinaries(latitude, longitude, onlyOpen, radius)
                            }
                            .setNegativeButton("İptal", null)
                            .show()
                    }
                )
            } catch (e: Exception) {
                showLoading(false)
                tvInfo.text = "Beklenmeyen hata"
                Toast.makeText(this@MainActivity, "Hata: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun displayClinics(clinics: List<VeterinaryClinic>) {
        mapView.overlays.removeAll {
            it is Marker && it.title != "Konumunuz"
        }

        clinics.forEach { clinic ->
            val marker = Marker(mapView)
            marker.position = GeoPoint(clinic.latitude, clinic.longitude)
            marker.title = clinic.name
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            marker.icon = if (clinic.isOpen) {
                ContextCompat.getDrawable(this, R.drawable.ic_vet_open)
            } else {
                ContextCompat.getDrawable(this, R.drawable.ic_vet_closed)
            }

            // BottomSheet ile göster
            marker.setOnMarkerClickListener { _, _ ->
                showClinicBottomSheet(clinic)
                true
            }

            mapView.overlays.add(marker)
        }

        mapView.invalidate()

        if (clinics.isNotEmpty()) {
            val firstClinic = clinics.first()
            mapView.controller.animateTo(
                GeoPoint(firstClinic.latitude, firstClinic.longitude)
            )
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnSearch.isEnabled = !show
        btnFilterOpen.isEnabled = !show
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDetach()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }


    private fun showClinicBottomSheet(clinic: VeterinaryClinic) {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_clinic, null)
        bottomSheet.setContentView(view)

        // View'leri bul
        val tvName = view.findViewById<TextView>(R.id.tvClinicName)
        val tvStatus = view.findViewById<TextView>(R.id.tvOpenStatus)
        val tvAddress = view.findViewById<TextView>(R.id.tvAddress)
        val tvPhone = view.findViewById<TextView>(R.id.tvPhone)
        val tvDistance = view.findViewById<TextView>(R.id.tvDistance)
        val btnCall = view.findViewById<Button>(R.id.btnCall)
        val btnDirections = view.findViewById<Button>(R.id.btnDirections)
        val btnSendPhoto = view.findViewById<Button>(R.id.btnSendPhoto)  // YENİ

        // Bilgileri doldur
        tvName.text = clinic.name

        if (clinic.isOpen) {
            tvStatus.text = "● Açık"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } else {
            tvStatus.text = "● Kapalı"
            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        }

        tvAddress.text = clinic.address ?: "Adres bilgisi yok"
        tvPhone.text = clinic.phone ?: "Telefon yok"
        tvDistance.text = String.format("%.2f km", clinic.distance ?: 0f)

        // Ara butonu
        btnCall.setOnClickListener {
            clinic.phone?.let { phone ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            } ?: run {
                Toast.makeText(this, "Telefon numarası yok", Toast.LENGTH_SHORT).show()
            }
            bottomSheet.dismiss()
        }

        // Yol tarifi butonu
        btnDirections.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:${clinic.latitude},${clinic.longitude}?q=${clinic.latitude},${clinic.longitude}(${clinic.name})")
            }
            startActivity(intent)
            bottomSheet.dismiss()
        }

        // Foto gönder butonu
        btnSendPhoto.setOnClickListener {
            currentClinicForPhoto = clinic
            bottomSheet.dismiss()
            checkCameraPermissionAndShowOptions()
        }

        // Haritayı hafif kaydır
        val clinicGeoPoint = GeoPoint(clinic.latitude, clinic.longitude)
        mapView.controller.animateTo(clinicGeoPoint)

        bottomSheet.show()
    }


    // FOTO GÖNDERME FONKSİYONLARI
    private fun checkCameraPermissionAndShowOptions() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            showPhotoOptions()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun showPhotoOptions() {
        val options = arrayOf(" Kamera ile Çek", " Galeriden Seç", " İptal")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Fotoğraf Gönder")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickFromGallery()
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            photoFile?.let {
                currentPhotoUri = FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    it
                )
                takePictureLauncher.launch(currentPhotoUri)
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Fotoğraf dosyası oluşturulamadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun pickFromGallery() {
        pickImageLauncher.launch("image/*")
    }

    @Throws(IOException::class)
    private fun createImageFile(): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "VETACIL_$timeStamp"
        val storageDir = getExternalFilesDir(null)

        return File.createTempFile(
            imageFileName,
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun sendPhotoToVet() {
        currentClinicForPhoto?.let { clinic ->
            currentPhotoUri?.let { photoUri ->
                val message = """
                VetAcil - Acil Durum
                
                Veteriner: ${clinic.name}
                Adres: ${clinic.address ?: "Belirtilmemiş"}
                Mesafe: ${String.format("%.2f km", clinic.distance ?: 0f)}
                
                Acil durum fotoğrafı ektedir.
            """.trimIndent()

                showSendOptions(clinic, message, photoUri)
            } ?: run {
                Toast.makeText(this, "Fotoğraf bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSendOptions(clinic: VeterinaryClinic, message: String, photoUri: Uri) {
        val options = arrayOf("WhatsApp ile Gönder", "Email ile Gönder", "İptal")

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Nasıl Göndermek İstersiniz?")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> sendViaWhatsApp(clinic, message, photoUri)
                    1 -> sendViaEmail(clinic, message, photoUri)
                    2 -> dialog.dismiss()
                }
            }
            .show()
    }

    private fun sendViaWhatsApp(clinic: VeterinaryClinic, message: String, photoUri: Uri) {
        clinic.phone?.let { phone ->
            try {
                val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                val whatsappPhone = if (cleanPhone.startsWith("0")) {
                    "90${cleanPhone.substring(1)}"
                } else if (!cleanPhone.startsWith("90")) {
                    "90$cleanPhone"
                } else {
                    cleanPhone
                }

                val intent = Intent(Intent.ACTION_SEND)
                intent.type = "image/*"
                intent.setPackage("com.whatsapp")
                intent.putExtra(Intent.EXTRA_TEXT, message)
                intent.putExtra(Intent.EXTRA_STREAM, photoUri)
                intent.putExtra("jid", "$whatsappPhone@s.whatsapp.net")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "WhatsApp açılamadı. Email deneyin.", Toast.LENGTH_LONG).show()
                sendViaEmail(clinic, message, photoUri)
            }
        } ?: run {
            Toast.makeText(this, "Telefon numarası yok, email ile gönderilecek", Toast.LENGTH_SHORT).show()
            sendViaEmail(clinic, message, photoUri)
        }
    }

    private fun sendViaEmail(clinic: VeterinaryClinic, message: String, photoUri: Uri) {
        val emailIntent = Intent(Intent.ACTION_SEND)
        emailIntent.type = "image/*"
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, "VetAcil - Acil Durum - ${clinic.name}")
        emailIntent.putExtra(Intent.EXTRA_TEXT, message)
        emailIntent.putExtra(Intent.EXTRA_STREAM, photoUri)
        emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        try {
            startActivity(Intent.createChooser(emailIntent, "Email Gönder"))
        } catch (e: Exception) {
            Toast.makeText(this, "Email uygulaması bulunamadı", Toast.LENGTH_SHORT).show()
        }
    }
}