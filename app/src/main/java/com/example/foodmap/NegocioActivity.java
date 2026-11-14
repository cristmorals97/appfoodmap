package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Registrar o editar el negocio: negocios/{uid}.
 * Un solo documento por negocio (el id es el uid), así que guardar nunca duplica.
 * La ubicación se elige tocando el mapa (SeleccionarUbicacionActivity).
 */
public class NegocioActivity extends AppCompatActivity {

    private EditText etNombreNegocio;
    private EditText etTelefono;
    private EditText etWhatsapp;
    private Spinner spCategoria;
    private EditText etDescripcion;
    private EditText etCatalogo;
    private TextView tvTituloNegocio;
    private TextView tvUbicacion;
    private Button btnUbicacion;
    private Button btnGuardarNegocio;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String[] opcionesCategoria;

    /** Ubicación que se guardará. null = todavía no hay una válida. */
    private Double latitud = null;
    private Double longitud = null;

    /** Recibe el punto elegido en SeleccionarUbicacionActivity. */
    private final ActivityResultLauncher<Intent> selectorUbicacion =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK
                                && data != null
                                && data.hasExtra(SeleccionarUbicacionActivity.EXTRA_LAT)
                                && data.hasExtra(SeleccionarUbicacionActivity.EXTRA_LNG)) {

                            latitud = data.getDoubleExtra(SeleccionarUbicacionActivity.EXTRA_LAT, 0);
                            longitud = data.getDoubleExtra(SeleccionarUbicacionActivity.EXTRA_LNG, 0);
                            mostrarUbicacion("📍 Ubicación seleccionada: ");
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_negocio);
        FoodUtils.aplicarInsets(findViewById(R.id.raizNegocio));

        etNombreNegocio = findViewById(R.id.etNombreNegocio);
        etTelefono = findViewById(R.id.etTelefono);
        etWhatsapp = findViewById(R.id.etWhatsapp);
        spCategoria = findViewById(R.id.spCategoria);
        etDescripcion = findViewById(R.id.etDescripcion);
        etCatalogo = findViewById(R.id.etCatalogo);
        tvTituloNegocio = findViewById(R.id.tvTituloNegocio);
        tvUbicacion = findViewById(R.id.tvUbicacion);
        btnUbicacion = findViewById(R.id.btnUbicacion);
        btnGuardarNegocio = findViewById(R.id.btnGuardarNegocio);
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesionNegocio);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        configurarCategorias();

        btnUbicacion.setOnClickListener(v -> abrirSelectorDeUbicacion());
        btnGuardarNegocio.setOnClickListener(v -> {
            if (validarCampos()) {
                guardarNegocio();
            }
        });
        btnCerrarSesion.setOnClickListener(v -> FoodUtils.cerrarSesion(this));

        // No se puede guardar hasta saber si ya existía un negocio (evita pisar datos)
        btnGuardarNegocio.setEnabled(false);
        cargarNegocioExistente();
    }

    // ------------------------------------------------------------------
    // CATEGORÍAS
    // ------------------------------------------------------------------

    private void configurarCategorias() {

        opcionesCategoria = Categorias.opcionesNegocio();

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                opcionesCategoria
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategoria.setAdapter(adapter);
    }

    private int posicionDeCategoria(String tipoGuardado) {
        Categorias.Categoria c = Categorias.deTipo(tipoGuardado);
        if (c != null) {
            for (int i = 0; i < opcionesCategoria.length; i++) {
                if (opcionesCategoria[i].equals(c.valor)) {
                    return i;
                }
            }
        }
        return opcionesCategoria.length - 1; // "Otro"
    }

    // ------------------------------------------------------------------
    // CARGAR (modo edición)
    // ------------------------------------------------------------------

    private void cargarNegocioExistente() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        db.collection("negocios")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (isFinishing()) {
                        return;
                    }

                    btnGuardarNegocio.setEnabled(true);

                    if (!doc.exists()) {
                        return; // negocio nuevo: formulario vacío
                    }

                    etNombreNegocio.setText(doc.getString("nombre"));
                    etTelefono.setText(doc.getString("telefono"));
                    etWhatsapp.setText(doc.getString("whatsapp"));
                    etDescripcion.setText(doc.getString("descripcion"));
                    etCatalogo.setText(doc.getString("catalogo"));
                    spCategoria.setSelection(posicionDeCategoria(doc.getString("tipoComida")));

                    Double lat = doc.getDouble("latitud");
                    Double lng = doc.getDouble("longitud");
                    if (lat != null && lng != null && latitud == null) {
                        latitud = lat;
                        longitud = lng;
                        mostrarUbicacion("📍 Ubicación guardada: ");
                    }

                    tvTituloNegocio.setText("Edita tu negocio");
                    btnGuardarNegocio.setText("GUARDAR CAMBIOS");
                })
                .addOnFailureListener(e -> {
                    if (isFinishing()) {
                        return;
                    }
                    // Se permite continuar (negocio nuevo), pero se avisa.
                    btnGuardarNegocio.setEnabled(true);
                    Toast.makeText(this,
                            "No se pudieron cargar los datos de tu negocio. Revisa tu conexión.",
                            Toast.LENGTH_LONG).show();
                });
    }

    // ------------------------------------------------------------------
    // UBICACIÓN (selector en mapa)
    // ------------------------------------------------------------------

    private void abrirSelectorDeUbicacion() {
        Intent intent = new Intent(this, SeleccionarUbicacionActivity.class);
        if (latitud != null && longitud != null) {
            intent.putExtra(SeleccionarUbicacionActivity.EXTRA_LAT, latitud.doubleValue());
            intent.putExtra(SeleccionarUbicacionActivity.EXTRA_LNG, longitud.doubleValue());
        }
        selectorUbicacion.launch(intent);
    }

    private void mostrarUbicacion(String prefijo) {
        tvUbicacion.setText(String.format(Locale.US, "%s%.5f, %.5f", prefijo, latitud, longitud));
    }

    // ------------------------------------------------------------------
    // VALIDAR Y GUARDAR
    // ------------------------------------------------------------------

    private boolean validarCampos() {

        String nombre = etNombreNegocio.getText().toString().trim();
        String telefono = etTelefono.getText().toString().trim();
        String whatsapp = etWhatsapp.getText().toString().trim();

        if (nombre.isEmpty()) {
            etNombreNegocio.setError("Ingresa el nombre del negocio");
            etNombreNegocio.requestFocus();
            return false;
        }

        if (spCategoria.getSelectedItemPosition() == 0) {
            Toast.makeText(this, "Selecciona una categoría", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (telefono.isEmpty()) {
            etTelefono.setError("Ingresa el teléfono");
            etTelefono.requestFocus();
            return false;
        }

        if (FoodUtils.telefonoMarcable(telefono).replace("+", "").length() < 7) {
            etTelefono.setError("El teléfono no parece válido");
            etTelefono.requestFocus();
            return false;
        }

        if (whatsapp.isEmpty()) {
            etWhatsapp.setError("Ingresa el WhatsApp");
            etWhatsapp.requestFocus();
            return false;
        }

        if (FoodUtils.numeroWhatsapp(whatsapp) == null) {
            etWhatsapp.setError("Ingresa un número de WhatsApp válido (ej. 987654321)");
            etWhatsapp.requestFocus();
            return false;
        }

        if (latitud == null || longitud == null) {
            Toast.makeText(this,
                    "Falta la ubicación. Toca \"Seleccionar ubicación en el mapa\".",
                    Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }

    private void guardarNegocio() {

        if (auth.getCurrentUser() == null) {
            FoodUtils.irALogin(this);
            return;
        }

        if (latitud == null || longitud == null) {
            return; // nunca guardar un establecimiento sin ubicación
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        String uid = auth.getCurrentUser().getUid();

        Map<String, Object> negocio = new HashMap<>();
        negocio.put("nombre", etNombreNegocio.getText().toString().trim());
        negocio.put("telefono", etTelefono.getText().toString().trim());
        negocio.put("whatsapp", etWhatsapp.getText().toString().trim());
        negocio.put("tipoComida", spCategoria.getSelectedItem().toString());
        negocio.put("descripcion", etDescripcion.getText().toString().trim());
        negocio.put("catalogo", etCatalogo.getText().toString().trim());
        negocio.put("usuarioId", uid);
        negocio.put("latitud", latitud);
        negocio.put("longitud", longitud);

        btnGuardarNegocio.setEnabled(false);

        // Siempre el mismo documento: negocios/{uid}
        db.collection("negocios")
                .document(uid)
                .set(negocio, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this,
                            "Negocio guardado correctamente",
                            Toast.LENGTH_LONG).show();

                    // Vuelve al panel (si ya estaba debajo en la pila, lo reutiliza y se refresca)
                    Intent intent = new Intent(this, PanelNegocioActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    btnGuardarNegocio.setEnabled(true);
                    Toast.makeText(this,
                            "No se pudo guardar el negocio. Revisa tu conexión e inténtalo de nuevo.",
                            Toast.LENGTH_LONG).show();
                });
    }
}
