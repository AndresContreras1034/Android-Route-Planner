package com.edupilot.laboratorio_planear_recorrido_con_google_maps;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements LocationListener {

    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final int REQ_LOCATION = 100;

    private static class Punto {
        final String nombre;
        final double lat;
        final double lng;

        Punto(String nombre, double lat, double lng) {
            this.nombre = nombre;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private double origenLat = 4.632339710;
    private double origenLng = -74.065350;
    private final List<Punto> puntos = new ArrayList<>();

    private TextView tvUbicacion;
    private TextView tvResultado;
    private RadioGroup rgModo;
    private LocationManager locationManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvUbicacion = findViewById(R.id.tvUbicacion);
        tvResultado = findViewById(R.id.tvResultado);
        rgModo = findViewById(R.id.rgModo);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        cargarPuntos();
        crearBotonesDePuntos();

        findViewById(R.id.btnGps).setOnClickListener(v -> obtenerUbicacion());
        findViewById(R.id.btnRutaEficiente).setOnClickListener(v -> planearRuta(true));
        findViewById(R.id.btnRutaOriginal).setOnClickListener(v -> planearRuta(false));
    }

    private void cargarPuntos() {
        puntos.add(new Punto("Estudiante - Usaquen", 4.6950, -74.0306));
        puntos.add(new Punto("Estudiante - Kennedy", 4.6285, -74.1527));
        puntos.add(new Punto("Docente - Chapinero", 4.6486, -74.0628));
        puntos.add(new Punto("Estudiante - Suba", 4.7410, -74.0830));
        puntos.add(new Punto("Docente - Teusaquillo", 4.6400, -74.0870));
        puntos.add(new Punto("Estudiante - La Candelaria", 4.5970, -74.0730));
        puntos.add(new Punto("Estudiante - Fontibon", 4.6700, -74.1430));
    }

    private void crearBotonesDePuntos() {
        LinearLayout contenedor = findViewById(R.id.contenedorPuntos);
        for (Punto punto : puntos) {
            Button boton = new Button(this);
            boton.setText("Ver " + punto.nombre);
            boton.setOnClickListener(v -> mostrarUbicacion(punto));
            contenedor.addView(boton);
        }
    }

    private void mostrarUbicacion(Punto punto) {
        Uri uri = Uri.parse("geo:" + punto.lat + "," + punto.lng
                + "?q=" + punto.lat + "," + punto.lng + "(" + Uri.encode(punto.nombre) + ")");
        abrirMaps(new Intent(Intent.ACTION_VIEW, uri));
    }

    private void planearRuta(boolean eficiente) {
        List<Punto> orden = eficiente
                ? ordenarPorCercania(origenLat, origenLng, puntos)
                : new ArrayList<>(puntos);
        double km = distanciaTotalKm(origenLat, origenLng, orden);

        StringBuilder resumen = new StringBuilder();
        resumen.append(eficiente ? "Ruta eficiente:\n" : "Ruta en orden asignado:\n");
        for (int i = 0; i < orden.size(); i++) {
            resumen.append(i + 1).append(". ").append(orden.get(i).nombre).append("\n");
        }
        resumen.append(String.format(Locale.US,
                "\nDistancia aprox. (linea recta): %.1f km", km));

        if (eficiente) {
            double kmOriginal = distanciaTotalKm(origenLat, origenLng, puntos);
            resumen.append(String.format(Locale.US,
                    "\nOrden asignado: %.1f km -> ahorro estimado: %.1f km",
                    kmOriginal, kmOriginal - km));
        }
        tvResultado.setText(resumen.toString());

        String url = construirUrlRuta(orden, modoSeleccionado());
        Log.d("Directions", url);
        abrirMaps(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
    }

    private List<Punto> ordenarPorCercania(double lat, double lng, List<Punto> original) {
        List<Punto> pendientes = new ArrayList<>(original);
        List<Punto> resultado = new ArrayList<>();
        double actualLat = lat;
        double actualLng = lng;

        while (!pendientes.isEmpty()) {
            Punto masCercano = null;
            float menor = Float.MAX_VALUE;
            for (Punto punto : pendientes) {
                float distancia = distanciaMetros(actualLat, actualLng, punto.lat, punto.lng);
                if (distancia < menor) {
                    menor = distancia;
                    masCercano = punto;
                }
            }
            resultado.add(masCercano);
            pendientes.remove(masCercano);
            actualLat = masCercano.lat;
            actualLng = masCercano.lng;
        }
        return resultado;
    }

    private double distanciaTotalKm(double lat, double lng, List<Punto> orden) {
        double total = 0;
        double actualLat = lat;
        double actualLng = lng;
        for (Punto punto : orden) {
            total += distanciaMetros(actualLat, actualLng, punto.lat, punto.lng);
            actualLat = punto.lat;
            actualLng = punto.lng;
        }
        return total / 1000.0;
    }

    private float distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        float[] resultado = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, resultado);
        return resultado[0];
    }

    private String modoSeleccionado() {
        int id = rgModo.getCheckedRadioButtonId();
        if (id == R.id.rbBicycling) return "bicycling";
        if (id == R.id.rbWalking) return "walking";
        if (id == R.id.rbTransit) return "transit";
        return "driving";
    }

    private String construirUrlRuta(List<Punto> orden, String modo) {
        Punto destino = orden.get(orden.size() - 1);
        StringBuilder waypoints = new StringBuilder();
        for (int i = 0; i < orden.size() - 1; i++) {
            if (i > 0) waypoints.append("|");
            waypoints.append(orden.get(i).lat).append(",").append(orden.get(i).lng);
        }

        Uri.Builder builder = new Uri.Builder()
                .scheme("https")
                .authority("www.google.com")
                .appendPath("maps")
                .appendPath("dir")
                .appendPath("")
                .appendQueryParameter("api", "1")
                .appendQueryParameter("origin", origenLat + "," + origenLng)
                .appendQueryParameter("destination", destino.lat + "," + destino.lng)
                .appendQueryParameter("travelmode", modo);
        if (waypoints.length() > 0) {
            builder.appendQueryParameter("waypoints", waypoints.toString());
        }
        return builder.build().toString();
    }

    private void abrirMaps(Intent intent) {
        intent.setPackage(MAPS_PACKAGE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                intent.setPackage(null);
                startActivity(intent);
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Instala una aplicacion de mapas",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private void obtenerUbicacion() {
        boolean fine = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }

        try {
            Location mejor = null;
            for (String proveedor : locationManager.getProviders(true)) {
                Location ubicacion = locationManager.getLastKnownLocation(proveedor);
                if (ubicacion != null
                        && (mejor == null || ubicacion.getAccuracy() < mejor.getAccuracy())) {
                    mejor = ubicacion;
                }
            }
            if (mejor != null) {
                actualizarOrigen(mejor);
                return;
            }

            String proveedor = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            if (!locationManager.isProviderEnabled(proveedor)) {
                Toast.makeText(this, "Activa el GPS del dispositivo",
                        Toast.LENGTH_LONG).show();
                return;
            }
            Toast.makeText(this, "Buscando ubicacion...", Toast.LENGTH_SHORT).show();
            locationManager.requestLocationUpdates(proveedor, 0, 0, this);
        } catch (SecurityException e) {
            Toast.makeText(this, "Permiso de ubicacion denegado",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void actualizarOrigen(Location ubicacion) {
        origenLat = ubicacion.getLatitude();
        origenLng = ubicacion.getLongitude();
        tvUbicacion.setText(String.format(Locale.US,
                "Origen: mi ubicacion (%.5f, %.5f)", origenLat, origenLng));
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        actualizarOrigen(location);
        locationManager.removeUpdates(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && (grantResults[0] == PackageManager.PERMISSION_GRANTED
                || (grantResults.length > 1
                && grantResults[1] == PackageManager.PERMISSION_GRANTED))) {
            obtenerUbicacion();
        } else if (requestCode == REQ_LOCATION) {
            Toast.makeText(this, "Sin permiso se usara el origen por defecto",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        locationManager.removeUpdates(this);
    }
}
