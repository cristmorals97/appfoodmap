package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;

public class RegistroActivity extends AppCompatActivity {

    private static final int MIN_CONTRASENA = 6;

    private EditText etCorreoRegistro;
    private EditText etContrasenaRegistro;
    private EditText etConfirmarContrasena;
    private Button btnRegistrarse;
    private TextView tvVolverLogin;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registro);

        FoodUtils.aplicarInsets(findViewById(R.id.raizRegistro));

        mAuth = FirebaseAuth.getInstance();

        etCorreoRegistro = findViewById(R.id.etCorreoRegistro);
        etContrasenaRegistro = findViewById(R.id.etContrasenaRegistro);
        etConfirmarContrasena = findViewById(R.id.etConfirmarContrasena);
        btnRegistrarse = findViewById(R.id.btnRegistrarse);
        tvVolverLogin = findViewById(R.id.tvVolverLogin);

        btnRegistrarse.setOnClickListener(v -> registrarUsuario());
        tvVolverLogin.setOnClickListener(v -> finish());
    }

    private void registrarUsuario() {

        String correo = etCorreoRegistro.getText().toString().trim();
        String contrasena = etContrasenaRegistro.getText().toString();
        String confirmar = etConfirmarContrasena.getText().toString();

        if (correo.isEmpty()) {
            etCorreoRegistro.setError("Ingresa tu correo");
            etCorreoRegistro.requestFocus();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreoRegistro.setError("El correo no tiene un formato válido");
            etCorreoRegistro.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            etContrasenaRegistro.setError("Ingresa una contraseña");
            etContrasenaRegistro.requestFocus();
            return;
        }

        if (contrasena.length() < MIN_CONTRASENA) {
            etContrasenaRegistro.setError(
                    "La contraseña debe tener al menos " + MIN_CONTRASENA + " caracteres");
            etContrasenaRegistro.requestFocus();
            return;
        }

        if (confirmar.isEmpty()) {
            etConfirmarContrasena.setError("Confirma tu contraseña");
            etConfirmarContrasena.requestFocus();
            return;
        }

        if (!contrasena.equals(confirmar)) {
            etConfirmarContrasena.setError("Las contraseñas no coinciden");
            etConfirmarContrasena.requestFocus();
            return;
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        btnRegistrarse.setEnabled(false);

        mAuth.createUserWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    if (task.isSuccessful()) {

                        Toast.makeText(this,
                                "Cuenta creada correctamente",
                                Toast.LENGTH_SHORT).show();

                        // Firebase deja la sesión iniciada: elegir perfil.
                        Intent intent = new Intent(this, PerfilActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();

                    } else {
                        btnRegistrarse.setEnabled(true);
                        Toast.makeText(this,
                                mensajeDeError(task.getException()),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String mensajeDeError(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "Sin conexión a Internet. Inténtalo de nuevo.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "Ya existe una cuenta con ese correo.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "La contraseña es demasiado débil.";
        }
        return "No se pudo crear la cuenta. Inténtalo de nuevo.";
    }
}
