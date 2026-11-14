package com.example.foodmap;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Locale;

/** Detalle de negocios/{uid}. Recibe el extra "uidNegocio". */
public class DetalleNegocioActivity extends AppCompatActivity {

    private TextView tvNombre;
    private TextView tvTipoComida;
    private TextView tvDescripcion;
    private TextView tvTelefono;
    private TextView tvWhatsapp;
    private TextView tvCatalogo;

    private Button btnLlamar;
    private Button btnWhatsapp;
    private Button btnComoLlegar;

    private FirebaseFirestore db;

    /** Valores crudos tal como están en Firestore ("" = no registrado). */
    private String telefono = "";
    private String whatsapp = "";
    private String nombreNegocio = "";
    private Double latitud = null;
    private Double longitud = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_detalle_negocio);
        FoodUtils.aplicarInsets(findViewById(R.id.raizDetalle));

        tvNombre = findViewById(R.id.tvNombre);
        tvTipoComida = findViewById(R.id.tvTipoComida);
        tvDescripcion = findViewById(R.id.tvDescripcion);
        tvTelefono = findViewById(R.id.tvTelefono);
        tvWhatsapp = findViewById(R.id.tvWhatsapp);
        tvCatalogo = findViewById(R.id.tvCatalogo);

        btnLlamar = findViewById(R.id.btnLlamar);
        btnWhatsapp = findViewById(R.id.btnWhatsapp);
        btnComoLlegar = findViewById(R.id.btnComoLlegar);

        // Hasta que carguen los datos no hay nada que llamar
        btnLlamar.setEnabled(false);
        btnWhatsapp.setEnabled(false);

        db = FirebaseFirestore.getInstance();

        String uidNegocio = getIntent().getStringExtra("uidNegocio");

        if (FoodUtils.vacio(uidNegocio)) {
            Toast.makeText(this, "No se encontró el establecimiento", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btnLlamar.setOnClickListener(v -> llamarNegocio());
        btnWhatsapp.setOnClickListener(v -> abrirWhatsapp());
        btnComoLlegar.setOnClickListener(v -> comoLlegar());

        cargarNegocio(uidNegocio);
    }

    private void cargarNegocio(String uidNegocio) {

        db.collection("negocios")
                .document(uidNegocio)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        Toast.makeText(this,
                                "El establecimiento ya no existe",
                                Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }

                    mostrarDatos(documentSnapshot);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this,
                            FoodUtils.hayInternet(this)
                                    ? "No se pudo cargar la información"
                                    : "Sin conexión a Internet. No se pudo cargar la información",
                            Toast.LENGTH_LONG).show();
                    finish();
                });
    }

    private void mostrarDatos(DocumentSnapshot document) {

        String nombre = document.getString("nombre");
        String tipoComida = document.getString("tipoComida");
        String descripcion = document.getString("descripcion");
        String catalogo = document.getString("catalogo");

        telefono = valorOVacio(document.getString("telefono"));
        whatsapp = valorOVacio(document.getString("whatsapp"));

        tvNombre.setText(FoodUtils.vacio(nombre) ? "Establecimiento de comida" : nombre.trim());

        tvTipoComida.setText(FoodUtils.vacio(tipoComida)
                ? "Comida"
                : Categorias.emojiDe(tipoComida) + " " + tipoComida.trim());

        tvDescripcion.setText(FoodUtils.vacio(descripcion)
                ? "Este establecimiento todavía no tiene una descripción."
                : descripcion.trim());

        tvCatalogo.setText(FoodUtils.vacio(catalogo)
                ? "El establecimiento todavía no ha registrado su catálogo."
                : catalogo.trim());

        tvTelefono.setText("📞 Teléfono: " + (telefono.isEmpty() ? "No registrado" : telefono));
        tvWhatsapp.setText("💬 WhatsApp: " + (whatsapp.isEmpty() ? "No registrado" : whatsapp));

        btnLlamar.setEnabled(!FoodUtils.telefonoMarcable(telefono).isEmpty());
        btnWhatsapp.setEnabled(FoodUtils.numeroWhatsapp(whatsapp) != null);

        nombreNegocio = FoodUtils.vacio(nombre) ? "Establecimiento" : nombre.trim();
        latitud = document.getDouble("latitud");
        longitud = document.getDouble("longitud");
        btnComoLlegar.setEnabled(latitud != null && longitud != null);
    }

    private String valorOVacio(String valor) {
        return valor == null ? "" : valor.trim();
    }

    // ------------------------------------------------------------------
    // CONTACTO
    // ------------------------------------------------------------------

    /** Abre el marcador del teléfono con el número ya escrito. No llama automáticamente. */
    private void llamarNegocio() {

        String numero = FoodUtils.telefonoMarcable(telefono);

        if (numero.isEmpty()) {
            Toast.makeText(this,
                    "Este negocio no tiene teléfono registrado",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            startActivity(new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No se encontró una aplicación para hacer llamadas",
                    Toast.LENGTH_SHORT).show();
        }
    }

    /** Abre la app de mapas con el destino. Si no hay ninguna, usa el navegador. */
    private void comoLlegar() {

        if (latitud == null || longitud == null) {
            Toast.makeText(this,
                    "Este negocio no tiene ubicación registrada",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        String coords = String.format(Locale.US, "%.6f,%.6f", latitud, longitud);

        Uri destino = Uri.parse("geo:" + coords + "?q=" + coords
                + "(" + Uri.encode(nombreNegocio) + ")");

        try {
            startActivity(new Intent(Intent.ACTION_VIEW, destino));
            return;
        } catch (ActivityNotFoundException ignorada) {
            // sin app de mapas: probamos con el navegador
        }

        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=" + coords)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this,
                    "No se encontró una aplicación de mapas",
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Abre un chat de WhatsApp (wa.me). Si WhatsApp no está instalado, avisa. */
    private void abrirWhatsapp() {

        String numero = FoodUtils.numeroWhatsapp(whatsapp);

        if (numero == null) {
            Toast.makeText(this,
                    "Este negocio no tiene un WhatsApp válido registrado",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        Uri enlace = Uri.parse("https://wa.me/" + numero);

        // WhatsApp normal y WhatsApp Business. Forzamos el paquete para que no se abra el navegador.
        String[] paquetes = {"com.whatsapp", "com.whatsapp.w4b"};

        for (String paquete : paquetes) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, enlace);
                intent.setPackage(paquete);
                startActivity(intent);
                return;
            } catch (ActivityNotFoundException ignorada) {
                // probar con el siguiente paquete
            }
        }

        Toast.makeText(this,
                "WhatsApp no está instalado en este teléfono",
                Toast.LENGTH_LONG).show();
    }
}
