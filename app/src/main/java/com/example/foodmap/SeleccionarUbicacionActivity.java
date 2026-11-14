package com.example.foodmap;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;

import java.util.Locale;

/**
 * El dueño toca el mapa donde está su negocio.
 * Entrada (opcional): EXTRA_LAT / EXTRA_LNG = punto ya guardado.
 * Salida (RESULT_OK): EXTRA_LAT / EXTRA_LNG = punto elegido.
 */
public class SeleccionarUbicacionActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LAT = "latitud";
    public static final String EXTRA_LNG = "longitud";

    private static final int LOCATION_PERMISSION_REQUEST = 3001;

    private static final double RESPALDO_LAT = -11.98;
    private static final double RESPALDO_LNG = -77.06;
    private static final float ZOOM_NEGOCIO = 17f;
    private static final float ZOOM_ZONA = 14.5f;

    private GoogleMap mMap;
    private Marker marcador;
    private LatLng elegido;

    private Button btnConfirmar;
    private TextView tvCoordenadas;
    private FusedLocationProviderClient fusedLocationClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_seleccionar_ubicacion);

        FoodUtils.aplicarInsets(findViewById(R.id.layoutSuperiorSel));
        FoodUtils.aplicarInsets(findViewById(R.id.layoutInferiorSel));

        btnConfirmar = findViewById(R.id.btnConfirmarUbicacion);
        tvCoordenadas = findViewById(R.id.tvCoordenadasSel);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Punto previo (modo edición)
        Intent entrada = getIntent();
        if (entrada != null && entrada.hasExtra(EXTRA_LAT) && entrada.hasExtra(EXTRA_LNG)) {
            elegido = new LatLng(
                    entrada.getDoubleExtra(EXTRA_LAT, RESPALDO_LAT),
                    entrada.getDoubleExtra(EXTRA_LNG, RESPALDO_LNG));
        }

        btnConfirmar.setOnClickListener(v -> confirmar());
        findViewById(R.id.fabCentrarUsuario).setOnClickListener(v -> centrarEnUsuario(true));

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapSeleccion);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false);

        mMap.setOnMapClickListener(latLng -> colocarMarcador(latLng, false));

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(@NonNull Marker marker) {
            }

            @Override
            public void onMarkerDrag(@NonNull Marker marker) {
            }

            @Override
            public void onMarkerDragEnd(@NonNull Marker marker) {
                elegido = marker.getPosition();
                actualizarTextoYBoton();
            }
        });

        if (elegido != null) {
            colocarMarcador(elegido, true);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(elegido, ZOOM_NEGOCIO));
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(RESPALDO_LAT, RESPALDO_LNG), ZOOM_ZONA));
            centrarEnUsuario(false); // solo mueve la cámara, NO elige el punto
        }
    }

    private void colocarMarcador(LatLng punto, boolean silencioso) {

        elegido = punto;

        if (marcador == null) {
            marcador = mMap.addMarker(new MarkerOptions()
                    .position(punto)
                    .draggable(true)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        } else {
            marcador.setPosition(punto);
        }

        if (!silencioso) {
            mMap.animateCamera(CameraUpdateFactory.newLatLng(punto));
        }

        actualizarTextoYBoton();
    }

    private void actualizarTextoYBoton() {
        if (elegido == null) {
            tvCoordenadas.setText("Aún no has elegido un punto");
            btnConfirmar.setEnabled(false);
        } else {
            tvCoordenadas.setText(String.format(Locale.US,
                    "📍 %.5f, %.5f", elegido.latitude, elegido.longitude));
            btnConfirmar.setEnabled(true);
        }
    }

    private void confirmar() {

        if (elegido == null) {
            Toast.makeText(this, "Toca el mapa para elegir la ubicación", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent resultado = new Intent();
        resultado.putExtra(EXTRA_LAT, elegido.latitude);
        resultado.putExtra(EXTRA_LNG, elegido.longitude);
        setResult(RESULT_OK, resultado);
        finish();
    }

    // ------------------------------------------------------------------
    // Centrar el mapa en el dispositivo (ayuda para encontrar la zona)
    // ------------------------------------------------------------------

    private boolean tienePermisoUbicacion() {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission") // se comprueba en tienePermisoUbicacion()
    private void centrarEnUsuario(boolean avisarSiFalla) {

        if (mMap == null) {
            return;
        }

        if (!tienePermisoUbicacion()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST
            );
            return;
        }

        try {
            mMap.setMyLocationEnabled(true);
        } catch (SecurityException ignorada) {
            return;
        }

        CancellationTokenSource cancelacion = new CancellationTokenSource();

        fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null && mMap != null) {
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                                new LatLng(location.getLatitude(), location.getLongitude()),
                                ZOOM_NEGOCIO));
                    } else if (avisarSiFalla) {
                        avisoSinUbicacion();
                    }
                })
                .addOnFailureListener(e -> {
                    if (avisarSiFalla) {
                        avisoSinUbicacion();
                    }
                });
    }

    private void avisoSinUbicacion() {
        Toast.makeText(this,
                "No se pudo obtener tu ubicación. Busca la zona en el mapa y toca tu negocio.",
                Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != LOCATION_PERMISSION_REQUEST) {
            return;
        }

        for (int resultado : grantResults) {
            if (resultado == PackageManager.PERMISSION_GRANTED) {
                centrarEnUsuario(false);
                return;
            }
        }
        // Sin permiso no pasa nada: se puede elegir el punto tocando el mapa.
    }
}
