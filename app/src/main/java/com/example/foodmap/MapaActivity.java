package com.example.foodmap;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.location.LocationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.Dash;
import com.google.android.gms.maps.model.Gap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mapa principal del cliente: exploración de lugares de comida cercanos.
 *
 * Google Maps solo pone el mapa base. Los establecimientos salen de Firestore ("negocios").
 * Todo lo visual (avatar del usuario, pines por categoría, anillos de exploración) está dibujado
 * con Canvas y círculos del propio mapa: no hay librerías ni imágenes externas.
 */
public class MapaActivity extends AppCompatActivity
        implements OnMapReadyCallback, SensorEventListener {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    /** Radio de búsqueda alrededor del usuario. Cámbialo aquí. */
    private static final double RADIO_KM = 5.0;

    /** Anillo interior de "zona cercana" (solo visual). */
    private static final double ZONA_CERCANA_M = 1000;

    /** Ubicación de respaldo (Lima Norte) si no hay GPS/permiso. */
    private static final double RESPALDO_LAT = -11.98;
    private static final double RESPALDO_LNG = -77.06;

    /** Cámara de exploración: cercana e inclinada para dar sensación de relieve. */
    private static final float ZOOM_EXPLORACION = 16f;
    private static final float ZOOM_RESPALDO = 15f;
    private static final float INCLINACION = 45f;

    /** Si el usuario se mueve más de esto, se recalcula qué lugares están en el radio. */
    private static final float METROS_PARA_REFRESCAR = 75f;

    /** Al volver al mapa, se recargan los negocios si pasó más de este tiempo. */
    private static final long MS_PARA_RECARGAR = 60_000L;

    /** Colores cálidos para los pines; se asigna uno por categoría. */
    private static final int[] PALETA = {
            0xFFE65100, 0xFFC62828, 0xFFEF6C00, 0xFFAD1457, 0xFF6D4C41,
            0xFFD84315, 0xFF2E7D32, 0xFF00838F, 0xFF8D6E63
    };

    private GoogleMap mMap;
    private FusedLocationProviderClient fusedLocationClient;
    private FirebaseFirestore db;
    private SensorManager sensorManager;
    private Sensor sensorRotacion;

    // Vistas
    private EditText etBuscar;
    private View btnLimpiarBusqueda;
    private ChipGroup chipGroup;
    private View scrollFiltros;
    private MaterialCardView cardFiltro;
    private TextView tvFiltro;
    private TextView tvInfoMapa;
    private TextView tvCardEmoji;
    private TextView tvCardNombre;
    private TextView tvCardCategoria;
    private TextView tvCardDistancia;
    private MaterialCardView cardNegocio;
    private Button btnVerDetalle;

    // Estado de ubicación
    private double refLat = RESPALDO_LAT;
    private double refLng = RESPALDO_LNG;
    private double ultimoRefrescoLat = RESPALDO_LAT;
    private double ultimoRefrescoLng = RESPALDO_LNG;
    private boolean usandoRespaldo = true;
    private boolean hayUbicacionReal = false;
    private boolean actualizandoUbicacion = false;
    private boolean negociosCargados = false;
    private long ultimaCargaMs = 0;

    // Elementos fijos del mapa (se crean al conocer la primera posición)
    private Marker avatarMarker;
    private Marker conoMarker;
    private Circle pulsoUsuario;
    private Circle radioCirculo;
    private Circle zonaCercana;

    // Negocios y marcadores
    private final List<NegocioMapa> negocios = new ArrayList<>();
    private final Map<String, Marker> marcadores = new HashMap<>();
    private final List<Marker> ordenMarcadores = new ArrayList<>();
    private final Map<String, IconoPin> cacheIconos = new HashMap<>();
    private BitmapDescriptor iconoAvatar;
    private BitmapDescriptor iconoCono;

    // Selección
    private NegocioMapa negocioSeleccionado;
    private Marker marcadorResaltado;
    private NegocioMapa negocioResaltado;
    private Circle anilloSeleccion;

    // Filtros
    private String claveFiltro = "";
    private String textoFiltroActivo = "🍴 Filtros";

    // Animaciones
    private ValueAnimator animPulso;
    private ValueAnimator animAparicion;

    // Rumbo (brújula)
    private final float[] matrizRot = new float[9];
    private final float[] matrizRemap = new float[9];
    private final float[] orientacion = new float[3];
    private float rumbo = 0f;
    private boolean rumboInicial = true;
    private long ultimoRumboMs = 0;

    private final LocationCallback callbackUbicacion = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult resultado) {
            Location l = resultado.getLastLocation();
            if (l == null || mMap == null) {
                return;
            }
            LatLng p = new LatLng(l.getLatitude(), l.getLongitude());
            usandoRespaldo = false;
            hayUbicacionReal = true;
            actualizarReferencia(p);

            float[] d = new float[1];
            Location.distanceBetween(ultimoRefrescoLat, ultimoRefrescoLng,
                    p.latitude, p.longitude, d);
            if (d[0] > METROS_PARA_REFRESCAR && negociosCargados) {
                ultimoRefrescoLat = p.latitude;
                ultimoRefrescoLng = p.longitude;
                aplicarFiltros(false);
            }
        }
    };

    // ------------------------------------------------------------------
    // CICLO DE VIDA
    // ------------------------------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_mapa);

        FoodUtils.aplicarInsets(findViewById(R.id.layoutSuperior));
        FoodUtils.aplicarInsets(findViewById(R.id.layoutInferior));

        etBuscar = findViewById(R.id.etBuscar);
        btnLimpiarBusqueda = findViewById(R.id.btnLimpiarBusqueda);
        chipGroup = findViewById(R.id.chipGroupCategorias);
        scrollFiltros = findViewById(R.id.scrollFiltros);
        cardFiltro = findViewById(R.id.cardFiltro);
        tvFiltro = findViewById(R.id.tvFiltro);
        tvInfoMapa = findViewById(R.id.tvInfoMapa);
        tvCardEmoji = findViewById(R.id.tvCardEmoji);
        tvCardNombre = findViewById(R.id.tvCardNombre);
        tvCardCategoria = findViewById(R.id.tvCardCategoria);
        tvCardDistancia = findViewById(R.id.tvCardDistancia);
        cardNegocio = findViewById(R.id.cardNegocio);
        btnVerDetalle = findViewById(R.id.btnVerDetalle);

        db = FirebaseFirestore.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            sensorRotacion = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        }

        crearChipsDeCategorias();
        configurarBusqueda();
        configurarTarjeta();

        cardFiltro.setOnClickListener(v -> alternarFiltros());
        findViewById(R.id.fabMiUbicacion).setOnClickListener(v -> volverAMiUbicacion());
        findViewById(R.id.fabVerTodos).setOnClickListener(v -> verTodosLosLugares());

        btnVerDetalle.setOnClickListener(v -> {
            if (negocioSeleccionado == null) {
                return;
            }
            Intent intent = new Intent(this, DetalleNegocioActivity.class);
            intent.putExtra("uidNegocio", negocioSeleccionado.uid);
            startActivity(intent);
        });

        // Atrás: primero cierra la tarjeta; si no hay tarjeta, sale del mapa
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (negocioSeleccionado != null) {
                    ocultarTarjeta();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sensorManager != null && sensorRotacion != null) {
            sensorManager.registerListener(this, sensorRotacion, SensorManager.SENSOR_DELAY_UI);
        }

        if (mMap != null) {
            iniciarAnimacionPulso();
            if (hayUbicacionReal) {
                iniciarActualizacionesDeUbicacion();
            }
            // Negocios nuevos o editados aparecen sin reiniciar la app
            if (negociosCargados
                    && System.currentTimeMillis() - ultimaCargaMs > MS_PARA_RECARGAR) {
                cargarNegocios(true);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        detenerActualizacionesDeUbicacion();
        detenerAnimaciones();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {

        mMap = googleMap;

        mMap.getUiSettings().setZoomControlsEnabled(false);
        mMap.getUiSettings().setCompassEnabled(false);
        mMap.getUiSettings().setMapToolbarEnabled(false);
        mMap.getUiSettings().setMyLocationButtonEnabled(false); // usamos nuestros controles

        // Estilo cálido y limpio; además oculta los negocios de Google
        try {
            mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.mapa_estilo));
        } catch (Exception ignorada) {
            // si falla el estilo, el mapa se ve normal
        }

        mMap.setOnMapClickListener(latLng -> {
            ocultarTarjeta();
            cerrarTeclado();
        });

        mMap.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof String) {
                seleccionarNegocio((String) tag, marker);
            }
            return true; // sin ventana de info de Google: usamos nuestra tarjeta
        });

        iniciarUbicacion();
    }

    // ------------------------------------------------------------------
    // FILTROS Y BÚSQUEDA
    // ------------------------------------------------------------------

    private void crearChipsDeCategorias() {

        LayoutInflater inflater = LayoutInflater.from(this);

        Chip todos = crearChip(inflater, "Todos", "", "🍴 Filtros");
        chipGroup.addView(todos);
        todos.setChecked(true);

        for (Categorias.Categoria c : Categorias.TODAS) {
            chipGroup.addView(crearChip(inflater,
                    c.emoji + " " + c.filtro, c.clave, c.emoji + " " + c.filtro));
        }
    }

    private Chip crearChip(LayoutInflater inflater, String texto, String clave, String textoBoton) {
        Chip chip = (Chip) inflater.inflate(R.layout.item_chip_categoria, chipGroup, false);
        chip.setId(View.generateViewId());
        chip.setText(texto);
        chip.setOnClickListener(v -> {
            claveFiltro = clave;
            textoFiltroActivo = textoBoton;
            actualizarBotonFiltro();
            ocultarTarjeta();
            aplicarFiltros(true);
        });
        return chip;
    }

    private void alternarFiltros() {
        boolean abrir = scrollFiltros.getVisibility() != View.VISIBLE;

        if (abrir) {
            scrollFiltros.setAlpha(0f);
            scrollFiltros.setTranslationY(-dp(12));
            scrollFiltros.setVisibility(View.VISIBLE);
            scrollFiltros.animate().alpha(1f).translationY(0f).setDuration(200).start();
        } else {
            scrollFiltros.animate().alpha(0f).translationY(-dp(12)).setDuration(160)
                    .withEndAction(() -> scrollFiltros.setVisibility(View.GONE)).start();
        }
    }

    /** El botón muestra la categoría activa (y se pinta de naranja) para que se note el filtro. */
    private void actualizarBotonFiltro() {
        boolean activo = !claveFiltro.isEmpty();
        tvFiltro.setText(activo ? textoFiltroActivo : "🍴 Filtros");
        tvFiltro.setTextColor(ContextCompat.getColor(this,
                activo ? R.color.white : R.color.foodmap_texto));
        cardFiltro.setCardBackgroundColor(ContextCompat.getColor(this,
                activo ? R.color.foodmap_naranja : R.color.white));
    }

    private void configurarBusqueda() {

        etBuscar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                btnLimpiarBusqueda.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                ocultarTarjeta();
                aplicarFiltros(false);
            }
        });

        etBuscar.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                cerrarTeclado();
                return true;
            }
            return false;
        });

        btnLimpiarBusqueda.setOnClickListener(v -> etBuscar.setText(""));
    }

    private void cerrarTeclado() {
        InputMethodManager imm =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etBuscar.getWindowToken(), 0);
        }
        etBuscar.clearFocus();
    }

    // ------------------------------------------------------------------
    // UBICACIÓN
    // ------------------------------------------------------------------

    private boolean tienePermisoUbicacion() {
        return ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void iniciarUbicacion() {

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

        obtenerUbicacion();
    }

    @SuppressLint("MissingPermission") // se comprueba en tienePermisoUbicacion()
    private void obtenerUbicacion() {

        if (mMap == null || !tienePermisoUbicacion()) {
            return;
        }

        if (!ubicacionDelSistemaActivada()) {
            usarRespaldo("La ubicación del teléfono está desactivada. Mostrando Lima Norte.");
            return;
        }

        CancellationTokenSource cancelacion = new CancellationTokenSource();

        fusedLocationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancelacion.getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        usarUbicacionReal(location);
                    } else {
                        fusedLocationClient.getLastLocation()
                                .addOnSuccessListener(ultima -> {
                                    if (ultima != null) {
                                        usarUbicacionReal(ultima);
                                    } else {
                                        usarRespaldo("No se pudo obtener tu ubicación. Mostrando Lima Norte.");
                                    }
                                })
                                .addOnFailureListener(e ->
                                        usarRespaldo("No se pudo obtener tu ubicación. Mostrando Lima Norte."));
                    }
                })
                .addOnFailureListener(e ->
                        usarRespaldo("No se pudo obtener tu ubicación. Mostrando Lima Norte."));
    }

    private boolean ubicacionDelSistemaActivada() {
        try {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            return lm == null || LocationManagerCompat.isLocationEnabled(lm);
        } catch (Exception e) {
            return true;
        }
    }

    private void usarUbicacionReal(Location location) {
        if (mMap == null) {
            return;
        }

        usandoRespaldo = false;
        hayUbicacionReal = true;

        LatLng p = new LatLng(location.getLatitude(), location.getLongitude());
        actualizarReferencia(p);
        ultimoRefrescoLat = p.latitude;
        ultimoRefrescoLng = p.longitude;

        moverCamara(p, ZOOM_EXPLORACION, true);

        cargarNegocios(false);
        iniciarActualizacionesDeUbicacion();
    }

    private void usarRespaldo(String motivo) {
        usandoRespaldo = true;
        hayUbicacionReal = false;

        LatLng p = new LatLng(RESPALDO_LAT, RESPALDO_LNG);
        actualizarReferencia(p);
        ultimoRefrescoLat = p.latitude;
        ultimoRefrescoLng = p.longitude;

        if (mMap != null) {
            moverCamara(p, ZOOM_RESPALDO, false);
        }

        if (motivo != null) {
            Toast.makeText(this, motivo, Toast.LENGTH_LONG).show();
        }

        cargarNegocios(false);
    }

    /** Seguimiento continuo: el avatar se mueve contigo mientras exploras. */
    @SuppressLint("MissingPermission") // se comprueba en tienePermisoUbicacion()
    private void iniciarActualizacionesDeUbicacion() {

        if (actualizandoUbicacion || !tienePermisoUbicacion()) {
            return;
        }

        try {
            LocationRequest pedido = new LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 4000L)
                    .setMinUpdateDistanceMeters(5f)
                    .build();

            fusedLocationClient.requestLocationUpdates(
                    pedido, callbackUbicacion, Looper.getMainLooper());
            actualizandoUbicacion = true;
        } catch (SecurityException ignorada) {
            // sin permiso: nos quedamos con la última posición
        }
    }

    private void detenerActualizacionesDeUbicacion() {
        if (actualizandoUbicacion && fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(callbackUbicacion);
        }
        actualizandoUbicacion = false;
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

        boolean concedido = false;
        for (int resultado : grantResults) {
            if (resultado == PackageManager.PERMISSION_GRANTED) {
                concedido = true;
                break;
            }
        }

        if (concedido) {
            obtenerUbicacion();
        } else {
            usarRespaldo("Sin permiso de ubicación. Mostrando Lima Norte.");
        }
    }

    // ------------------------------------------------------------------
    // CÁMARA Y CONTROLES FLOTANTES
    // ------------------------------------------------------------------

    private void moverCamara(LatLng destino, float zoom, boolean animar) {
        if (mMap == null) {
            return;
        }
        CameraPosition pos = new CameraPosition.Builder()
                .target(destino)
                .zoom(zoom)
                .tilt(INCLINACION)
                .bearing(0f)
                .build();
        if (animar) {
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(pos));
        } else {
            mMap.moveCamera(CameraUpdateFactory.newCameraPosition(pos));
        }
    }

    /** Botón "mi ubicación": vuelve al avatar con la cámara de exploración. */
    private void volverAMiUbicacion() {
        if (mMap == null) {
            return;
        }
        if (hayUbicacionReal) {
            moverCamara(new LatLng(refLat, refLng), ZOOM_EXPLORACION, true);
        } else {
            iniciarUbicacion(); // reintenta permiso / GPS
        }
    }

    /** Botón "ver todos": encuadra tu posición y todos los lugares visibles. */
    private void verTodosLosLugares() {
        if (mMap == null) {
            return;
        }
        if (ordenMarcadores.isEmpty()) {
            Toast.makeText(this,
                    "No hay lugares para mostrar con estos filtros",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        LatLngBounds.Builder limites = new LatLngBounds.Builder();
        limites.include(new LatLng(refLat, refLng));
        for (Marker m : ordenMarcadores) {
            limites.include(m.getPosition());
        }

        try {
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(limites.build(), (int) dp(90)));
        } catch (Exception ignorada) {
            // el mapa aún no tiene tamaño
        }
    }

    // ------------------------------------------------------------------
    // AVATAR DEL USUARIO, ANILLOS Y RUMBO
    // ------------------------------------------------------------------

    /** Crea (la primera vez) o mueve el avatar, el cono de rumbo y los anillos de exploración. */
    private void actualizarReferencia(LatLng p) {

        refLat = p.latitude;
        refLng = p.longitude;

        if (mMap == null) {
            return;
        }

        if (avatarMarker == null) {

            radioCirculo = mMap.addCircle(new CircleOptions()
                    .center(p)
                    .radius(RADIO_KM * 1000)
                    .strokeColor(0xAAE65100)
                    .strokeWidth(4f)
                    .strokePattern(Arrays.asList(new Dash(28f), new Gap(18f)))
                    .fillColor(0x0CF57C00)
                    .zIndex(0f));

            zonaCercana = mMap.addCircle(new CircleOptions()
                    .center(p)
                    .radius(ZONA_CERCANA_M)
                    .strokeColor(0x55E65100)
                    .strokeWidth(2f)
                    .fillColor(0x10F57C00)
                    .zIndex(0.2f));

            pulsoUsuario = mMap.addCircle(new CircleOptions()
                    .center(p)
                    .radius(30)
                    .strokeWidth(0f)
                    .fillColor(0x55F57C00)
                    .zIndex(0.5f));

            conoMarker = mMap.addMarker(new MarkerOptions()
                    .position(p)
                    .icon(iconoCono())
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .zIndex(1f)
                    .visible(false));

            avatarMarker = mMap.addMarker(new MarkerOptions()
                    .position(p)
                    .icon(iconoAvatar())
                    .anchor(0.5f, 0.5f)
                    .zIndex(2f));

            iniciarAnimacionPulso();

        } else {
            avatarMarker.setPosition(p);
            if (conoMarker != null) conoMarker.setPosition(p);
            if (pulsoUsuario != null) pulsoUsuario.setCenter(p);
            if (radioCirculo != null) radioCirculo.setCenter(p);
            if (zonaCercana != null) zonaCercana.setCenter(p);
        }
    }

    /** Un solo animador mueve el pulso del usuario y el anillo del lugar seleccionado. */
    private void iniciarAnimacionPulso() {

        if (animPulso != null || mMap == null) {
            return;
        }

        animPulso = ValueAnimator.ofFloat(0f, 1f);
        animPulso.setDuration(1800);
        animPulso.setRepeatCount(ValueAnimator.INFINITE);
        animPulso.setInterpolator(new LinearInterpolator());
        animPulso.addUpdateListener(a -> {

            if (mMap == null) {
                return;
            }

            float f = (float) a.getAnimatedValue();
            double mpp = metrosPorPixel();

            if (pulsoUsuario != null) {
                pulsoUsuario.setRadius((dp(16) + f * dp(38)) * mpp);
                pulsoUsuario.setFillColor(Color.argb((int) ((1f - f) * 95), 245, 124, 0));
            }

            if (anilloSeleccion != null) {
                anilloSeleccion.setRadius((dp(22) + f * dp(28)) * mpp);
                anilloSeleccion.setFillColor(Color.argb((int) ((1f - f) * 140), 249, 168, 37));
            }
        });
        animPulso.start();
    }

    private void detenerAnimaciones() {
        if (animPulso != null) {
            animPulso.cancel();
            animPulso = null;
        }
        if (animAparicion != null) {
            animAparicion.cancel();
            animAparicion = null;
        }
    }

    private double metrosPorPixel() {
        float zoom = mMap.getCameraPosition().zoom;
        return 156543.03392 * Math.cos(Math.toRadians(refLat)) / Math.pow(2, zoom);
    }

    // Brújula: gira el cono de visión del avatar -------------------------

    @Override
    public void onSensorChanged(SensorEvent event) {

        if (conoMarker == null || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR) {
            return;
        }

        long ahora = System.currentTimeMillis();
        if (ahora - ultimoRumboMs < 60) {
            return; // no saturar el mapa
        }

        try {
            SensorManager.getRotationMatrixFromVector(matrizRot, event.values);
            // Teléfono sostenido en vertical (como al caminar)
            SensorManager.remapCoordinateSystem(matrizRot,
                    SensorManager.AXIS_X, SensorManager.AXIS_Z, matrizRemap);
            SensorManager.getOrientation(matrizRemap, orientacion);
        } catch (Exception e) {
            return;
        }

        float azimut = (float) Math.toDegrees(orientacion[0]);
        if (azimut < 0) {
            azimut += 360f;
        }

        if (rumboInicial) {
            rumbo = azimut;
            rumboInicial = false;
        } else {
            float delta = ((azimut - rumbo + 540f) % 360f) - 180f; // camino más corto
            if (Math.abs(delta) < 2f) {
                return;
            }
            rumbo = (rumbo + delta * 0.3f + 360f) % 360f; // suavizado
        }

        ultimoRumboMs = ahora;
        conoMarker.setRotation(rumbo);
        if (!conoMarker.isVisible()) {
            conoMarker.setVisible(true);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    // ------------------------------------------------------------------
    // FIRESTORE
    // ------------------------------------------------------------------

    /** silencioso = recarga en segundo plano: sin avisos ni cerrar la tarjeta abierta. */
    private void cargarNegocios(boolean silencioso) {

        db.collection("negocios")
                .get()
                .addOnSuccessListener(snapshots -> {

                    negocios.clear();

                    for (DocumentSnapshot document : snapshots) {
                        NegocioMapa negocio = leerNegocio(document);
                        if (negocio != null) {
                            negocios.add(negocio);
                        }
                    }

                    negociosCargados = true;
                    ultimaCargaMs = System.currentTimeMillis();
                    if (!silencioso) {
                        ocultarTarjeta();
                    }
                    aplicarFiltros(!silencioso);
                })
                .addOnFailureListener(e -> {
                    if (silencioso) {
                        return; // ya hay datos en pantalla: no molestar
                    }
                    Toast.makeText(this,
                            FoodUtils.hayInternet(this)
                                    ? "No se pudieron cargar los establecimientos."
                                    : "Sin conexión a Internet. No se pudieron cargar los establecimientos.",
                            Toast.LENGTH_LONG).show();
                    tvInfoMapa.setText("⚠️ No se pudieron cargar los lugares");
                });
    }

    /** Devuelve null si el documento no tiene una ubicación válida. */
    private NegocioMapa leerNegocio(DocumentSnapshot document) {

        Double lat = document.getDouble("latitud");
        Double lng = document.getDouble("longitud");

        if (lat == null || lng == null
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return null;
        }

        String nombre = document.getString("nombre");
        String tipo = document.getString("tipoComida");
        String descripcion = document.getString("descripcion");
        String catalogo = document.getString("catalogo");

        if (FoodUtils.vacio(nombre)) {
            nombre = "Establecimiento de comida";
        }
        if (FoodUtils.vacio(tipo)) {
            tipo = "Comida";
        }

        return new NegocioMapa(document.getId(), nombre.trim(), tipo.trim(),
                descripcion == null ? "" : descripcion,
                catalogo == null ? "" : catalogo,
                lat, lng);
    }

    /** Distancia desde el avatar (o desde Lima Norte si no hay GPS), en km. */
    private float distanciaKm(NegocioMapa n) {
        float[] r = new float[1];
        Location.distanceBetween(refLat, refLng, n.latitud, n.longitud, r);
        return r[0] / 1000f;
    }

    // ------------------------------------------------------------------
    // MARCADORES
    // ------------------------------------------------------------------

    private void aplicarFiltros(boolean animar) {

        if (mMap == null) {
            return;
        }

        if (animAparicion != null) {
            animAparicion.cancel();
            animAparicion = null;
        }

        // Limpiar solo lo nuestro (el avatar y los anillos se quedan)
        String uidSeleccionado = negocioSeleccionado != null ? negocioSeleccionado.uid : null;

        if (anilloSeleccion != null) {
            anilloSeleccion.remove();
            anilloSeleccion = null;
        }
        marcadorResaltado = null;
        negocioResaltado = null;

        for (Marker m : marcadores.values()) {
            m.remove();
        }
        marcadores.clear();
        ordenMarcadores.clear();

        if (!negociosCargados) {
            return;
        }

        String consulta = etBuscar.getText().toString();

        // Los más cercanos primero: así "aparecen" desde ti hacia afuera
        List<NegocioMapa> ordenados = new ArrayList<>(negocios);
        Collections.sort(ordenados, (a, b) -> Float.compare(distanciaKm(a), distanciaKm(b)));

        int enRadio = 0;
        int mostrados = 0;

        for (NegocioMapa n : ordenados) {

            if (distanciaKm(n) > RADIO_KM) {
                continue;
            }
            enRadio++;

            if (!Categorias.coincideFiltro(n.tipoComida, claveFiltro)) {
                continue;
            }
            if (!Categorias.coincideBusqueda(n.nombre, n.tipoComida,
                    n.descripcion, n.catalogo, consulta)) {
                continue;
            }

            Marker marker = agregarMarcador(n);
            if (marker != null) {
                marcadores.put(n.uid, marker);
                ordenMarcadores.add(marker);
                mostrados++;
            }
        }

        String radio = String.format(Locale.US, "%.0f km", RADIO_KM);

        if (enRadio == 0) {
            tvInfoMapa.setText("🔎 Aún no hay lugares registrados a menos de " + radio);
        } else if (mostrados == 0) {
            tvInfoMapa.setText("🔎 Ningún lugar coincide. Prueba otra categoría o búsqueda");
        } else {
            tvInfoMapa.setText("🧭 " + mostrados
                    + (mostrados == 1 ? " lugar cerca de ti" : " lugares cerca de ti"));
        }

        // Si había uno seleccionado y sigue visible, se vuelve a resaltar
        if (uidSeleccionado != null) {
            Marker m = marcadores.get(uidSeleccionado);
            NegocioMapa n = buscarNegocio(uidSeleccionado);
            if (m != null && n != null) {
                resaltar(n, m);
            } else {
                ocultarTarjeta();
            }
        }

        if (animar && !ordenMarcadores.isEmpty()) {
            animarAparicion();
        }
    }

    private Marker agregarMarcador(NegocioMapa n) {

        IconoPin pin = iconoPin(n, false);

        Marker marker = mMap.addMarker(
                new MarkerOptions()
                        .position(new LatLng(n.latitud, n.longitud))
                        .title(n.nombre)
                        .icon(pin.icono)
                        .anchor(0.5f, pin.anclaY)
                        .zIndex(3f)
        );

        if (marker != null) {
            marker.setTag(n.uid);
        }
        return marker;
    }

    /** Los pines "aparecen" uno tras otro, del más cercano al más lejano. */
    private void animarAparicion() {

        final int total = ordenMarcadores.size();

        for (Marker m : ordenMarcadores) {
            m.setAlpha(0f);
        }

        animAparicion = ValueAnimator.ofFloat(0f, 1f);
        animAparicion.setDuration(900);
        animAparicion.setInterpolator(new DecelerateInterpolator());
        animAparicion.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < ordenMarcadores.size(); i++) {
                float inicio = total > 1 ? (i / (float) total) * 0.6f : 0f;
                float alfa = Math.max(0f, Math.min(1f, (t - inicio) / 0.4f));
                ordenMarcadores.get(i).setAlpha(alfa);
            }
        });
        animAparicion.start();
    }

    private NegocioMapa buscarNegocio(String uid) {
        for (NegocioMapa n : negocios) {
            if (n.uid.equals(uid)) {
                return n;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // ICONOS DIBUJADOS CON CANVAS
    // ------------------------------------------------------------------

    private static class IconoPin {
        final BitmapDescriptor icono;
        final float anclaY;

        IconoPin(BitmapDescriptor icono, float anclaY) {
            this.icono = icono;
            this.anclaY = anclaY;
        }
    }

    private int colorDe(String tipoComida) {
        Categorias.Categoria c = Categorias.deTipo(tipoComida);
        if (c == null) {
            return PALETA[0];
        }
        int i = Categorias.TODAS.indexOf(c);
        return PALETA[Math.max(i, 0) % PALETA.length];
    }

    /**
     * Pin en forma de gota: borde blanco con sombra, fondo del color de la categoría y su emoji.
     * El seleccionado es más grande y lleva un aro ámbar.
     */
    private IconoPin iconoPin(NegocioMapa n, boolean seleccionado) {

        String emoji = Categorias.emojiDe(n.tipoComida);
        int color = colorDe(n.tipoComida);
        String clave = emoji + "|" + color + "|" + seleccionado;

        IconoPin guardado = cacheIconos.get(clave);
        if (guardado != null) {
            return guardado;
        }

        IconoPin resultado;

        try {
            float d = dp(seleccionado ? 64 : 50);
            float pad = dp(10);
            float cola = dp(seleccionado ? 16 : 13);
            int w = (int) (d + 2 * pad);
            int h = (int) (d + cola + 2 * pad);

            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);

            float cx = w / 2f;
            float cy = pad + d / 2f;

            if (seleccionado) {
                pincel.setColor(0xFFF9A825);
                canvas.drawCircle(cx, cy, d / 2f + dp(4), pincel);
            }

            // Gota blanca (círculo + cola) con sombra
            pincel.setColor(Color.WHITE);
            pincel.setShadowLayer(dp(4), 0, dp(2), 0x66000000);
            canvas.drawCircle(cx, cy, d / 2f, pincel);

            Path colaPath = new Path();
            colaPath.moveTo(cx - d * 0.22f, cy + d * 0.38f);
            colaPath.lineTo(cx + d * 0.22f, cy + d * 0.38f);
            colaPath.lineTo(cx, pad + d + cola);
            colaPath.close();
            canvas.drawPath(colaPath, pincel);
            pincel.clearShadowLayer();

            // Centro del color de la categoría
            pincel.setColor(color);
            canvas.drawCircle(cx, cy, d / 2f - dp(3.5f), pincel);

            dibujarEmoji(canvas, emoji, cx, cy, d * 0.5f);

            resultado = new IconoPin(BitmapDescriptorFactory.fromBitmap(bitmap),
                    (pad + d + cola) / h);

        } catch (Exception e) {
            resultado = new IconoPin(
                    BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                    1f);
        }

        cacheIconos.put(clave, resultado);
        return resultado;
    }

    /** Avatar del usuario: círculo naranja con carita y borde blanco. */
    private BitmapDescriptor iconoAvatar() {

        if (iconoAvatar != null) {
            return iconoAvatar;
        }

        try {
            float d = dp(46);
            float pad = dp(8);
            int tam = (int) (d + 2 * pad);

            Bitmap bitmap = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);

            float c = tam / 2f;

            pincel.setColor(Color.WHITE);
            pincel.setShadowLayer(dp(4), 0, dp(2), 0x77000000);
            canvas.drawCircle(c, c, d / 2f, pincel);
            pincel.clearShadowLayer();

            pincel.setColor(0xFFE65100);
            canvas.drawCircle(c, c, d / 2f - dp(3), pincel);

            dibujarEmoji(canvas, "😋", c, c, d * 0.55f);

            iconoAvatar = BitmapDescriptorFactory.fromBitmap(bitmap);

        } catch (Exception e) {
            iconoAvatar = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE);
        }

        return iconoAvatar;
    }

    /** Cono translúcido que apunta hacia donde mira el teléfono (se rota con la brújula). */
    private BitmapDescriptor iconoCono() {

        if (iconoCono != null) {
            return iconoCono;
        }

        try {
            int tam = (int) dp(170);
            float c = tam / 2f;

            Bitmap bitmap = Bitmap.createBitmap(tam, tam, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Paint pincel = new Paint(Paint.ANTI_ALIAS_FLAG);

            pincel.setShader(new RadialGradient(c, c, c,
                    new int[]{0xCCF57C00, 0x00F57C00},
                    new float[]{0.25f, 1f},
                    Shader.TileMode.CLAMP));

            Path sector = new Path();
            sector.moveTo(c, c);
            sector.arcTo(new RectF(0, 0, tam, tam), -135f, 90f, false); // 90° hacia arriba
            sector.close();
            canvas.drawPath(sector, pincel);

            iconoCono = BitmapDescriptorFactory.fromBitmap(bitmap);

        } catch (Exception e) {
            iconoCono = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
        }

        return iconoCono;
    }

    private void dibujarEmoji(Canvas canvas, String emoji, float cx, float cy, float tamTexto) {
        Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
        texto.setTextSize(tamTexto);
        texto.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = texto.getFontMetrics();
        canvas.drawText(emoji, cx, cy - (fm.ascent + fm.descent) / 2f, texto);
    }

    // ------------------------------------------------------------------
    // SELECCIÓN Y TARJETA INFERIOR
    // ------------------------------------------------------------------

    private void seleccionarNegocio(String uid, Marker marker) {

        NegocioMapa n = buscarNegocio(uid);
        if (n == null) {
            return;
        }

        negocioSeleccionado = n;
        resaltar(n, marker);

        tvCardEmoji.setText(Categorias.emojiDe(n.tipoComida));
        Drawable fondo = tvCardEmoji.getBackground();
        if (fondo instanceof GradientDrawable) {
            ((GradientDrawable) fondo.mutate())
                    .setColor((colorDe(n.tipoComida) & 0x00FFFFFF) | 0x33000000);
        }

        tvCardNombre.setText(n.nombre);
        tvCardCategoria.setText(n.tipoComida);
        tvCardDistancia.setText(textoDistancia(distanciaKm(n)));

        mostrarTarjeta();

        mMap.animateCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
    }

    private String textoDistancia(float km) {
        String texto = km < 1f
                ? String.format(Locale.getDefault(), "📍 A %d m de ti", Math.round(km * 1000f / 10f) * 10)
                : String.format(Locale.getDefault(), "📍 A %.1f km de ti", km);
        if (usandoRespaldo) {
            texto += " (desde Lima Norte)";
        }
        return texto;
    }

    /** Pin grande + anillo pulsante en el lugar seleccionado. */
    private void resaltar(NegocioMapa n, Marker marker) {

        quitarResalte();

        IconoPin grande = iconoPin(n, true);
        marker.setIcon(grande.icono);
        marker.setAnchor(0.5f, grande.anclaY);
        marker.setZIndex(10f);
        marker.setAlpha(1f);

        marcadorResaltado = marker;
        negocioResaltado = n;

        anilloSeleccion = mMap.addCircle(new CircleOptions()
                .center(marker.getPosition())
                .radius(metrosPorPixel() * dp(24))
                .strokeWidth(0f)
                .fillColor(0x8CF9A825)
                .zIndex(0.8f));
    }

    private void quitarResalte() {

        if (marcadorResaltado != null && negocioResaltado != null) {
            IconoPin normal = iconoPin(negocioResaltado, false);
            marcadorResaltado.setIcon(normal.icono);
            marcadorResaltado.setAnchor(0.5f, normal.anclaY);
            marcadorResaltado.setZIndex(3f);
        }

        if (anilloSeleccion != null) {
            anilloSeleccion.remove();
            anilloSeleccion = null;
        }

        marcadorResaltado = null;
        negocioResaltado = null;
    }

    private void mostrarTarjeta() {

        tvInfoMapa.setVisibility(View.GONE);

        cardNegocio.animate().cancel();

        if (cardNegocio.getVisibility() != View.VISIBLE) {
            cardNegocio.setTranslationY(dp(280));
            cardNegocio.setAlpha(0f);
            cardNegocio.setVisibility(View.VISIBLE);
        }

        cardNegocio.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(280)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // El logo de Google y los controles se quedan por encima de la tarjeta
        cardNegocio.post(() -> {
            if (mMap != null && negocioSeleccionado != null) {
                mMap.setPadding(0, 0, 0, cardNegocio.getHeight() + (int) dp(16));
            }
        });
    }

    private void ocultarTarjeta() {

        quitarResalte();
        negocioSeleccionado = null;

        if (cardNegocio.getVisibility() != View.VISIBLE) {
            tvInfoMapa.setVisibility(View.VISIBLE);
            return;
        }

        cardNegocio.animate().cancel();
        cardNegocio.animate()
                .translationY(dp(280))
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    if (negocioSeleccionado == null) {
                        cardNegocio.setVisibility(View.GONE);
                        tvInfoMapa.setVisibility(View.VISIBLE);
                        if (mMap != null) {
                            mMap.setPadding(0, 0, 0, 0);
                        }
                    }
                })
                .start();
    }

    /** Arrastrar la parte superior de la tarjeta hacia abajo la cierra. */
    @SuppressLint("ClickableViewAccessibility")
    private void configurarTarjeta() {

        final float[] inicioY = {0f};

        findViewById(R.id.zonaArrastre).setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    inicioY[0] = e.getRawY();
                    cardNegocio.animate().cancel();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dy = e.getRawY() - inicioY[0];
                    if (dy > 0) {
                        cardNegocio.setTranslationY(dy);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (e.getRawY() - inicioY[0] > dp(80)) {
                        ocultarTarjeta();
                    } else {
                        cardNegocio.animate().translationY(0f).setDuration(150).start();
                    }
                    return true;

                default:
                    return false;
            }
        });
    }

    // ------------------------------------------------------------------
    // UTILIDADES Y MODELO
    // ------------------------------------------------------------------

    private float dp(float valor) {
        return valor * getResources().getDisplayMetrics().density;
    }

    private static class NegocioMapa {

        final String uid;
        final String nombre;
        final String tipoComida;
        final String descripcion;
        final String catalogo;
        final double latitud;
        final double longitud;

        NegocioMapa(String uid, String nombre, String tipoComida,
                    String descripcion, String catalogo,
                    double latitud, double longitud) {
            this.uid = uid;
            this.nombre = nombre;
            this.tipoComida = tipoComida;
            this.descripcion = descripcion;
            this.catalogo = catalogo;
            this.latitud = latitud;
            this.longitud = longitud;
        }
    }
}
