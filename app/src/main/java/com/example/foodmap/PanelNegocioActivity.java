package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/**
 * Panel del negocio ya registrado (negocios/{uid} existe).
 * Permite editar datos, cambiar la ubicación, ver la ficha pública y cerrar sesión.
 */
public class PanelNegocioActivity extends AppCompatActivity {

    private TextView tvEstado;
    private TextView tvNombre;
    private TextView tvCategoria;
    private TextView tvContacto;
    private TextView tvUbicacion;
    private Button btnEditar;
    private Button btnUbicacion;
    private Button btnVer;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private Double latitud = null;
    private Double longitud = null;

    private final ActivityResultLauncher<Intent> selectorUbicacion =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Intent data = result.getData();
                        if (result.getResultCode() == RESULT_OK
                                && data != null
                                && data.hasExtra(SeleccionarUbicacionActivity.EXTRA_LAT)
                                && data.hasExtra(SeleccionarUbicacionActivity.EXTRA_LNG)) {

                            guardarUbicacion(
                                    data.getDoubleExtra(SeleccionarUbicacionActivity.EXTRA_LAT, 0),
                                    data.getDoubleExtra(SeleccionarUbicacionActivity.EXTRA_LNG, 0));
                        }
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_panel_negocio);
        FoodUtils.aplicarInsets(findViewById(R.id.raizPanel));

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        tvEstado = findViewById(R.id.tvPanelEstado);
        tvNombre = findViewById(R.id.tvPanelNombre);
        tvCategoria = findViewById(R.id.tvPanelCategoria);
        tvContacto = findViewById(R.id.tvPanelContacto);
        tvUbicacion = findViewById(R.id.tvPanelUbicacion);
        btnEditar = findViewById(R.id.btnEditarNegocio);
        btnUbicacion = findViewById(R.id.btnModificarUbicacion);
        btnVer = findViewById(R.id.btnVerMiNegocio);
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesionPanel);

        btnEditar.setOnClickListener(v ->
                startActivity(new Intent(this, NegocioActivity.class)));

        btnUbicacion.setOnClickListener(v -> {
            Intent intent = new Intent(this, SeleccionarUbicacionActivity.class);
            if (latitud != null && longitud != null) {
                intent.putExtra(SeleccionarUbicacionActivity.EXTRA_LAT, latitud.doubleValue());
                intent.putExtra(SeleccionarUbicacionActivity.EXTRA_LNG, longitud.doubleValue());
            }
            selectorUbicacion.launch(intent);
        });

        btnVer.setOnClickListener(v -> {
            if (auth.getCurrentUser() == null) {
                return;
            }
            Intent intent = new Intent(this, DetalleNegocioActivity.class);
            intent.putExtra("uidNegocio", auth.getCurrentUser().getUid());
            startActivity(intent);
        });

        btnCerrarSesion.setOnClickListener(v -> FoodUtils.cerrarSesion(this));
    }

    /** Se recarga al volver de editar, para mostrar siempre los datos actuales. */
    @Override
    protected void onResume() {
        super.onResume();
        if (auth != null && auth.getCurrentUser() != null) {
            cargarNegocio();
        }
    }

    private void cargarNegocio() {

        String uid = auth.getCurrentUser().getUid();

        db.collection("negocios")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {

                    if (isFinishing()) {
                        return;
                    }

                    if (!doc.exists()) {
                        // El negocio ya no existe: volver a registrarlo
                        startActivity(new Intent(this, NegocioActivity.class));
                        finish();
                        return;
                    }

                    mostrar(doc);
                })
                .addOnFailureListener(e -> {
                    if (isFinishing()) {
                        return;
                    }
                    tvEstado.setText(FoodUtils.hayInternet(this)
                            ? "No se pudo cargar tu negocio. Inténtalo de nuevo más tarde."
                            : "Sin conexión a Internet. No se pudo cargar tu negocio.");
                });
    }

    private void mostrar(DocumentSnapshot doc) {

        String nombre = doc.getString("nombre");
        String tipo = doc.getString("tipoComida");
        String telefono = doc.getString("telefono");
        String whatsapp = doc.getString("whatsapp");

        latitud = doc.getDouble("latitud");
        longitud = doc.getDouble("longitud");

        tvEstado.setText("✅ Tu negocio está registrado y visible para los clientes en FOODMAP.");

        tvNombre.setText(FoodUtils.vacio(nombre) ? "Sin nombre" : nombre.trim());

        tvCategoria.setText(FoodUtils.vacio(tipo)
                ? "Sin categoría"
                : Categorias.emojiDe(tipo) + " " + tipo.trim());

        tvContacto.setText("📞 " + (FoodUtils.vacio(telefono) ? "—" : telefono.trim())
                + "     💬 " + (FoodUtils.vacio(whatsapp) ? "—" : whatsapp.trim()));

        if (latitud != null && longitud != null) {
            tvUbicacion.setText(String.format(Locale.US,
                    "📍 Ubicación: %.5f, %.5f", latitud, longitud));
        } else {
            tvUbicacion.setText("📍 Ubicación: sin registrar");
        }

        btnEditar.setEnabled(true);
        btnUbicacion.setEnabled(true);
        btnVer.setEnabled(true);
    }

    /** Actualiza solo latitud y longitud de negocios/{uid} (no crea otro documento). */
    private void guardarUbicacion(double lat, double lng) {

        if (auth.getCurrentUser() == null) {
            FoodUtils.irALogin(this);
            return;
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        btnUbicacion.setEnabled(false);

        db.collection("negocios")
                .document(auth.getCurrentUser().getUid())
                .update("latitud", lat, "longitud", lng)
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Ubicación actualizada", Toast.LENGTH_SHORT).show();
                    cargarNegocio();
                })
                .addOnFailureListener(e -> {
                    btnUbicacion.setEnabled(true);
                    Toast.makeText(this,
                            "No se pudo actualizar la ubicación. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG).show();
                });
    }
}
