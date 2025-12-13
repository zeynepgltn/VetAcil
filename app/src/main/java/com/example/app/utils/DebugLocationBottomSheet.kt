package com.vetacil.app.utils

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.vetacil.app.R
import com.vetacil.app.model.DebugLocation
import com.vetacil.app.model.DebugLocations
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import org.osmdroid.util.GeoPoint

/**
 * Debug menüsü için Bottom Sheet
 * Test konumları seçme ve manuel koordinat girişi sağlar
 */
class DebugLocationBottomSheet(
    private val onLocationSelected: (GeoPoint) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var etLatitude: TextInputEditText
    private lateinit var etLongitude: TextInputEditText
    private lateinit var btnSetCustomLocation: MaterialButton
    private lateinit var btnUseRealLocation: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_debug_location, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupViews(view)
        setupRecyclerView()
        setupCustomLocationInput()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog

        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }

        return dialog
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.rvDebugLocations)
        etLatitude = view.findViewById(R.id.etLatitude)
        etLongitude = view.findViewById(R.id.etLongitude)
        btnSetCustomLocation = view.findViewById(R.id.btnSetCustomLocation)
        btnUseRealLocation = view.findViewById(R.id.btnUseRealLocation)
    }

    private fun setupRecyclerView() {
        val adapter = DebugLocationAdapter(DebugLocations.getAllLocations()) { location ->
            onLocationSelected(location.geoPoint)
            Toast.makeText(
                requireContext(),
                "Test konumu: ${location.name}",
                Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }

        recyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            this.adapter = adapter
        }
    }

    private fun setupCustomLocationInput() {
        btnSetCustomLocation.setOnClickListener {
            val latText = etLatitude.text?.toString()
            val lngText = etLongitude.text?.toString()

            if (latText.isNullOrBlank() || lngText.isNullOrBlank()) {
                Toast.makeText(
                    requireContext(),
                    "Lütfen koordinatları girin",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            try {
                val lat = latText.toDouble()
                val lng = lngText.toDouble()

                if (lat < -90 || lat > 90) {
                    Toast.makeText(
                        requireContext(),
                        "Geçersiz enlem (-90 ile 90 arası olmalı)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                if (lng < -180 || lng > 180) {
                    Toast.makeText(
                        requireContext(),
                        "Geçersiz boylam (-180 ile 180 arası olmalı)",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }

                onLocationSelected(GeoPoint(lat, lng))
                Toast.makeText(
                    requireContext(),
                    "Manuel konum ayarlandı",
                    Toast.LENGTH_SHORT
                ).show()
                dismiss()

            } catch (e: NumberFormatException) {
                Toast.makeText(
                    requireContext(),
                    "Geçersiz koordinat formatı",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnUseRealLocation.setOnClickListener {
            onLocationSelected(GeoPoint(0.0, 0.0)) // Gerçek konumu kullanmak için özel değer
            Toast.makeText(
                requireContext(),
                "Gerçek konum kullanılacak",
                Toast.LENGTH_SHORT
            ).show()
            dismiss()
        }
    }

    companion object {
        const val TAG = "DebugLocationBottomSheet"
    }
}

/**
 * Debug konumları için RecyclerView Adapter
 */
class DebugLocationAdapter(
    private val locations: List<DebugLocation>,
    private val onLocationClick: (DebugLocation) -> Unit
) : RecyclerView.Adapter<DebugLocationAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvLocationName: android.widget.TextView = view.findViewById(R.id.tvLocationName)
        private val tvLocationDescription: android.widget.TextView = view.findViewById(R.id.tvLocationDescription)
        private val tvLocationCoordinates: android.widget.TextView = view.findViewById(R.id.tvLocationCoordinates)

        fun bind(location: DebugLocation) {
            tvLocationName.text = location.name
            tvLocationDescription.text = location.description
            tvLocationCoordinates.text = String.format(
                "%.4f, %.4f",
                location.geoPoint.latitude,
                location.geoPoint.longitude
            )

            itemView.setOnClickListener {
                onLocationClick(location)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_debug_location, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(locations[position])
    }

    override fun getItemCount() = locations.size
}