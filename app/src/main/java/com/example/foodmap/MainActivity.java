package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

/**
 * Pantalla de inicio de sesión.
 * Si ya hay una sesión activa en Firebase, no pide credenciales: abre directamente
 * la pantalla que corresponde según usuarios/{uid}.tipoPerfil.
 */
public class MainActivity extends AppCompatActivity {

    private EditText etCorreo;
    private EditText etContrasena;
    private Button btnIniciarSesion;
    private TextView tvRegistrarse;
    private TextView tvOlvidoContrasena;
    private View layoutFormulario;
    private View progresoSesion;

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FoodUtils.aplicarInsets(findViewById(R.id.main));

        mAuth = FirebaseAuth.getInstance();

        etCorreo = findViewById(R.id.etCorreo);
        etContrasena = findViewById(R.id.etContrasena);
        btnIniciarSesion = findViewById(R.id.btnIniciarSesion);
        tvRegistrarse = findViewById(R.id.tvRegistrarse);
        tvOlvidoContrasena = findViewById(R.id.tvOlvidoContrasena);
        layoutFormulario = findViewById(R.id.layoutFormulario);
        progresoSesion = findViewById(R.id.progresoSesion);

        btnIniciarSesion.setOnClickListener(v -> iniciarSesion());

        tvOlvidoContrasena.setOnClickListener(v -> recuperarContrasena());

        tvRegistrarse.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, RegistroActivity.class)));

        // Persistencia de sesión
        FirebaseUser actual = mAuth.getCurrentUser();
        if (actual != null) {
            comprobarSesionExistente();
        }
    }

    private void comprobarSesionExistente() {
        mostrarCargando(true);

        FoodUtils.abrirSegunPerfil(this, mensaje -> {
            // No se pudo leer el perfil: mostramos el formulario para poder reintentar.
            mostrarCargando(false);
            Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
        });
    }

    private void mostrarCargando(boolean cargando) {
        progresoSesion.setVisibility(cargando ? View.VISIBLE : View.GONE);
        layoutFormulario.setVisibility(cargando ? View.GONE : View.VISIBLE);
    }

    private void iniciarSesion() {

        String correo = etCorreo.getText().toString().trim();
        String contrasena = etContrasena.getText().toString();

        if (correo.isEmpty()) {
            etCorreo.setError("Ingresa tu correo");
            etCorreo.requestFocus();
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("El correo no tiene un formato válido");
            etCorreo.requestFocus();
            return;
        }

        if (contrasena.isEmpty()) {
            etContrasena.setError("Ingresa tu contraseña");
            etContrasena.requestFocus();
            return;
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        btnIniciarSesion.setEnabled(false);

        mAuth.signInWithEmailAndPassword(correo, contrasena)
                .addOnCompleteListener(this, task -> {

                    if (!task.isSuccessful()) {
                        btnIniciarSesion.setEnabled(true);
                        Toast.makeText(this,
                                mensajeDeErrorLogin(task.getException()),
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Login correcto: decidir a qué pantalla ir
                    mostrarCargando(true);
                    FoodUtils.abrirSegunPerfil(this, mensaje -> {
                        mostrarCargando(false);
                        btnIniciarSesion.setEnabled(true);
                        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
                    });
                });
    }

    /** Envía el correo de recuperación de Firebase al correo escrito. */
    private void recuperarContrasena() {

        String correo = etCorreo.getText().toString().trim();

        if (correo.isEmpty()
                || !android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            etCorreo.setError("Escribe tu correo para enviarte el enlace");
            etCorreo.requestFocus();
            return;
        }

        if (!FoodUtils.exigirInternet(this)) {
            return;
        }

        tvOlvidoContrasena.setEnabled(false);

        mAuth.sendPasswordResetEmail(correo)
                .addOnCompleteListener(this, task -> {
                    tvOlvidoContrasena.setEnabled(true);

                    if (task.getException() instanceof FirebaseNetworkException) {
                        Toast.makeText(this,
                                "Sin conexión a Internet. Inténtalo de nuevo.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    // Mismo mensaje exista o no la cuenta (no revela qué correos están registrados)
                    Toast.makeText(this,
                            "Si ese correo tiene una cuenta, te enviamos un enlace para restablecer la contraseña. Revisa también tu spam.",
                            Toast.LENGTH_LONG).show();
                });
    }

    private String mensajeDeErrorLogin(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "Sin conexión a Internet. Inténtalo de nuevo.";
        }
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) {
            return "Correo o contraseña incorrectos";
        }
        return "No se pudo iniciar sesión. Inténtalo de nuevo.";
    }
}
