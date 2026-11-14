package com.example.foodmap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Categorías de FOODMAP en un solo lugar:
 * - opciones del Spinner del negocio,
 * - filtros del mapa,
 * - emoji de cada marcador,
 * - palabras relacionadas para el buscador (ej. "pollo" encuentra pollerías).
 */
public final class Categorias {

    private Categorias() {
    }

    public static final class Categoria {
        /** Texto del chip de filtro (ej. "Pollerías"). */
        public final String filtro;
        /** Texto guardado en Firestore y mostrado en el Spinner (ej. "Pollería"). */
        public final String valor;
        /** Raíz sin tildes ni mayúsculas usada para comparar (ej. "polleria"). */
        public final String clave;
        public final String emoji;
        public final List<String> sinonimos;

        Categoria(String filtro, String valor, String clave,
                  String emoji, String... sinonimos) {
            this.filtro = filtro;
            this.valor = valor;
            this.clave = clave;
            this.emoji = emoji;
            this.sinonimos = Arrays.asList(sinonimos);
        }
    }

    public static final String EMOJI_OTRO = "🍴";

    /** Categorías "reales" (sin "Todos" ni "Otro"). */
    public static final List<Categoria> TODAS = Collections.unmodifiableList(Arrays.asList(
            new Categoria("Pollerías", "Pollería", "polleria", "🍗",
                    "pollo", "pollos", "brasa"),
            new Categoria("Hamburguesas", "Hamburguesas", "hamburguesa", "🍔",
                    "burger", "hamburguesas"),
            new Categoria("Pizzerías", "Pizzería", "pizzeria", "🍕",
                    "pizza", "pizzas"),
            new Categoria("Cafeterías", "Cafetería", "cafeteria", "☕",
                    "cafe", "cafes", "capuchino", "postre", "postres"),
            new Categoria("Juguerías", "Juguería", "jugueria", "🥤",
                    "jugo", "jugos", "batido", "batidos", "fruta"),
            new Categoria("Sangucherías", "Sanguchería", "sangucheria", "🥪",
                    "sanguche", "sanguches", "sandwich"),
            new Categoria("Chifas", "Chifa", "chifa", "🥡",
                    "chaufa", "wantan", "tallarin saltado", "comida china"),
            new Categoria("Parrillas", "Parrilla", "parrilla", "🥩",
                    "anticucho", "anticuchos", "parrillada", "carne", "carnes"),
            new Categoria("Comida criolla", "Comida criolla", "comida criolla", "🍛",
                    "criolla", "criollo", "lomo saltado", "aji de gallina", "menu"),
            new Categoria("Comida rápida", "Comida rápida", "comida rapida", "🍟",
                    "rapida", "fast food", "salchipapa", "salchipapas", "hot dog"),
            new Categoria("Sushi", "Sushi", "sushi", "🍣",
                    "maki", "makis", "roll", "rolls", "japonesa", "japones"),
            new Categoria("Comida mexicana", "Comida mexicana", "comida mexicana", "🌮",
                    "mexicana", "mexicano", "taco", "tacos", "burrito", "nachos"),
            new Categoria("Restaurantes", "Restaurante", "restaurante", "🍽️",
                    "almuerzo", "cena", "plato", "platos"),
            new Categoria("Pastelerías", "Pastelería", "pasteleria", "🍰",
                    "torta", "tortas", "pastel", "pasteles", "keke"),
            new Categoria("Heladerías", "Heladería", "heladeria", "🍦",
                    "helado", "helados", "paleta", "paletas"),
            new Categoria("Panaderías", "Panadería", "panaderia", "🥖",
                    "pan", "panes", "empanada", "empanadas"),
            new Categoria("Mariscos", "Mariscos", "marisco", "🦐",
                    "ceviche", "cebiche", "pescado", "camaron", "camarones"),
            new Categoria("Comida saludable", "Comida saludable", "comida saludable", "🥗",
                    "saludable", "ensalada", "ensaladas", "vegano", "vegetariano")
    ));

    /** Opciones del Spinner del formulario de negocio. */
    public static String[] opcionesNegocio() {
        List<String> lista = new ArrayList<>();
        lista.add("Selecciona una categoría");
        for (Categoria c : TODAS) {
            lista.add(c.valor);
        }
        lista.add("Otro");
        return lista.toArray(new String[0]);
    }

    /** Devuelve la categoría que corresponde al texto guardado en Firestore, o null si es "Otro". */
    public static Categoria deTipo(String tipoComida) {
        String t = FoodUtils.normalizar(tipoComida);
        if (t.isEmpty()) {
            return null;
        }
        for (Categoria c : TODAS) {
            if (t.contains(c.clave)) {
                return c;
            }
        }
        return null;
    }

    public static String emojiDe(String tipoComida) {
        Categoria c = deTipo(tipoComida);
        return c != null ? c.emoji : EMOJI_OTRO;
    }

    /** ¿El tipo guardado pertenece al filtro? clave vacía = "Todos". */
    public static boolean coincideFiltro(String tipoComida, String claveFiltro) {
        if (claveFiltro == null || claveFiltro.isEmpty()) {
            return true;
        }
        return FoodUtils.normalizar(tipoComida).contains(claveFiltro);
    }

    /**
     * Búsqueda por nombre, categoría, descripción, catálogo y palabras relacionadas.
     * Con varias palabras ("pizza familiar"), TODAS deben aparecer en alguno de esos campos.
     * Ignora tildes y mayúsculas; "poll" ya encuentra "pollo".
     */
    public static boolean coincideBusqueda(String nombre, String tipoComida,
                                           String descripcion, String catalogo,
                                           String consulta) {
        String q = FoodUtils.normalizar(consulta);
        if (q.isEmpty()) {
            return true;
        }

        StringBuilder texto = new StringBuilder();
        texto.append(FoodUtils.normalizar(nombre)).append(' ')
                .append(FoodUtils.normalizar(tipoComida)).append(' ')
                .append(FoodUtils.normalizar(descripcion)).append(' ')
                .append(FoodUtils.normalizar(catalogo));

        Categoria c = deTipo(tipoComida);
        if (c != null) {
            for (String s : c.sinonimos) {
                texto.append(' ').append(s);
            }
        }

        String pajar = texto.toString();

        for (String palabra : q.split("\\s+")) {
            if (!palabra.isEmpty() && !pajar.contains(palabra)) {
                return false;
            }
        }
        return true;
    }
}
