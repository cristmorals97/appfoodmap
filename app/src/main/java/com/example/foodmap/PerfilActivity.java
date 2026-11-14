package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

/** Selección de perfil: guarda usuarios/{uid} = { correo, tipoPerfil }. */
public class PerfilActivity extends AppCompatActivity {

    private Button btnCliente;
    private Button btnNegocio;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_perfil);
        FoodUtils.aplicarInsets(findViewById(R.id.raizPerfil));

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnCliente = findViewById(R.id.btnCliente);
        btnNegocio = findViewById(R.id.btnNegocio);

        btnCliente.setOnClickListener(v -> guardarPerfil("cliente"));
        btnNegocio.setOnClickListener(v -> guardarPerfil("negocio"));
    }

    private void guardarPerfil(String tipoPerfil) {

        FirebaseUser usuarioActual = mAuth.getCurrentUser();

        if (usuarioActual == null) {
            FoodUtils.irALogin(this);
            return;
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        setBotonesHabilitados(false);

        Map<String, Object> usuario = new HashMap<>();
        usuario.put("correo", usuarioActual.getEmail());
        usuario.put("tipoPerfil", tipoPerfil);

        db.collection("usuarios")
                .document(usuarioActual.getUid())
                .set(usuario)
                .addOnSuccessListener(unused -> {

                    Intent intent = new Intent(
                            this,
                            "negocio".equals(tipoPerfil)
                                    ? NegocioActivity.class
                                    : ClienteActivity.class
                    );

                    startActivity(intent);
                    finish();
                })
                .addOnFailureListener(e -> {
                    setBotonesHabilitados(true);
                    Toast.makeText(this,
                            "No se pudo guardar el perfil. Inténtalo de nuevo.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private void setBotonesHabilitados(boolean habilitados) {
        btnCliente.setEnabled(habilitados);
        btnNegocio.setEnabled(habilitados);
    }
}
