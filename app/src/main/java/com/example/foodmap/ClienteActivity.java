package com.example.foodmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

/** Bienvenida del cliente antes de entrar al mapa. */
public class ClienteActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!FoodUtils.requiereSesion(this)) {
            return;
        }

        setContentView(R.layout.activity_cliente);
        FoodUtils.aplicarInsets(findViewById(R.id.raizCliente));

        Button btnVerMapa = findViewById(R.id.btnVerMapa);
        Button btnCerrarSesion = findViewById(R.id.btnCerrarSesionCliente);

        btnVerMapa.setOnClickListener(v ->
                startActivity(new Intent(ClienteActivity.this, MapaActivity.class)));

        btnCerrarSesion.setOnClickListener(v -> FoodUtils.cerrarSesion(this));
    }
}
