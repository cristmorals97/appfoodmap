package com.example.foodmap;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.View;
import android.widget.Toast;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.Normalizer;
import java.util.Locale;

/** Utilidades comunes: sesión, navegación, red, texto y teléfonos. */
public final class FoodUtils {

    private FoodUtils() {
    }

    // ------------------------------------------------------------------
    // TEXTO
    // ------------------------------------------------------------------

    /** minúsculas + sin tildes. "Pollería" -> "polleria". Nunca devuelve null. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String s = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD);
        s = s.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return s.toLowerCase(Locale.ROOT);
    }

    public static boolean vacio(String s) {
        return s == null || s.trim().isEmpty();
    }

    // ------------------------------------------------------------------
    // TELÉFONOS (Perú)
    // ------------------------------------------------------------------

    /** Deja solo dígitos y un "+" inicial opcional, apto para tel:. Devuelve "" si no hay dígitos. */
    public static String telefonoMarcable(String telefono) {
        if (telefono == null) {
            return "";
        }
        String t = telefono.trim();
        boolean mas = t.startsWith("+");
        String digitos = t.replaceAll("[^0-9]", "");
        if (digitos.isEmpty()) {
            return "";
        }
        return mas ? "+" + digitos : digitos;
    }

    /**
     * Número para wa.me en formato internacional sin "+".
     * 987654321 -> 51987654321 | 51987654321 -> igual | +51 987 654 321 -> 51987654321
     * 0987654321 -> 51987654321 | 0051987654321 -> 51987654321
     * Devuelve null si no parece un número válido.
     */
    public static String numeroWhatsapp(String texto) {
        if (texto == null) {
            return null;
        }
        String n = texto.replaceAll("[^0-9]", "");
        if (n.startsWith("00")) {
            n = n.substring(2);
        }
        if (n.startsWith("0")) {
            n = n.substring(1);
        }
        if (n.length() == 9) {
            return "51" + n;           // celular peruano sin código de país
        }
        if (n.length() >= 11 && n.length() <= 15) {
            return n;                  // ya trae código de país
        }
        return null;
    }

    // ------------------------------------------------------------------
    // RED
    // ------------------------------------------------------------------

    public static boolean hayInternet(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true; // no podemos saberlo: dejamos que Firebase intente
            }
            Network red = cm.getActiveNetwork();
            if (red == null) {
                return false;
            }
            NetworkCapabilities caps = cm.getNetworkCapabilities(red);
            return caps != null
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    /** Muestra un aviso y devuelve false si no hay Internet. */
    public static boolean exigirInternet(Context context) {
        if (hayInternet(context)) {
            return true;
        }
        Toast.makeText(context,
                "Sin conexión a Internet. Revisa tu conexión e inténtalo de nuevo.",
                Toast.LENGTH_LONG).show();
        return false;
    }

    public static boolean esErrorDeRed(Exception e) {
        return e instanceof FirebaseNetworkException;
    }

    // ------------------------------------------------------------------
    // SESIÓN Y NAVEGACIÓN
    // ------------------------------------------------------------------

    /**
     * Para pantallas privadas: si no hay sesión, vuelve a MainActivity y devuelve false.
     * Uso: if (!FoodUtils.requiereSesion(this)) return;  (al inicio de onCreate)
     */
    public static boolean requiereSesion(Activity activity) {
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            return true;
        }
        irALogin(activity);
        return false;
    }

    public static void irALogin(Activity activity) {
        Intent intent = new Intent(activity, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    public static void cerrarSesion(Activity activity) {
        FirebaseAuth.getInstance().signOut();
        irALogin(activity);
    }

    public interface AlFallar {
        void alFallar(String mensaje);
    }

    /**
     * Lee usuarios/{uid}.tipoPerfil y abre la pantalla que corresponde:
     * cliente -> ClienteActivity, negocio -> NegocioActivity, sin perfil -> PerfilActivity.
     * Si falla la lectura, llama a alFallar y NO navega.
     */
    public static void abrirSegunPerfil(Activity activity, AlFallar alFallar) {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            alFallar.alFallar("No hay una sesión iniciada.");
            return;
        }
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();

        FirebaseFirestore.getInstance()
                .collection("usuarios")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    String tipo = doc.exists() ? doc.getString("tipoPerfil") : null;

                    if ("cliente".equals(tipo)) {
                        abrir(activity, ClienteActivity.class);
                    } else if ("negocio".equals(tipo)) {
                        abrirPanelONegocio(activity, uid, alFallar);
                    } else {
                        abrir(activity, PerfilActivity.class);
                    }
                })
                .addOnFailureListener(e -> fallar(activity, e, alFallar));
    }

    /** Negocio: si ya existe negocios/{uid} -> PanelNegocioActivity; si no -> NegocioActivity. */
    private static void abrirPanelONegocio(Activity activity, String uid, AlFallar alFallar) {
        FirebaseFirestore.getInstance()
                .collection("negocios")
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (activity.isFinishing() || activity.isDestroyed()) {
                        return;
                    }
                    abrir(activity, doc.exists()
                            ? PanelNegocioActivity.class
                            : NegocioActivity.class);
                })
                .addOnFailureListener(e -> fallar(activity, e, alFallar));
    }

    private static void abrir(Activity activity, Class<? extends Activity> destino) {
        activity.startActivity(new Intent(activity, destino));
        activity.finish();
    }

    private static void fallar(Activity activity, Exception e, AlFallar alFallar) {
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        alFallar.alFallar(esErrorDeRed(e) || !hayInternet(activity)
                ? "Sin conexión a Internet. No se pudo verificar tu perfil."
                : "No se pudo consultar tu perfil. Inténtalo de nuevo.");
    }

    // ------------------------------------------------------------------
    // PANTALLA COMPLETA (edge-to-edge)
    // ------------------------------------------------------------------

    /**
     * Con targetSdk 35+ la app se dibuja bajo las barras del sistema.
     * Esto suma el alto de las barras al padding original de la vista.
     */
    public static void aplicarInsets(View vista) {
        final int izq = vista.getPaddingLeft();
        final int arr = vista.getPaddingTop();
        final int der = vista.getPaddingRight();
        final int aba = vista.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(vista, (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(izq + barras.left, arr + barras.top,
                    der + barras.right, aba + barras.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(vista);
    }
}
