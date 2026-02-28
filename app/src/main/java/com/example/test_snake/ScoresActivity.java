package com.example.test_snake;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ScoresActivity extends AppCompatActivity {

    private static final String TAG = "ScoresActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 100;

    private RecyclerView rvScores;
    private ScoresAdapter adapter;
    private DatabaseReference globalScoresRef;
    private DatabaseReference countryScoresRef;
    private List<Score> scoresList;
    private Button btnGlobal, btnLocal;
    private TextView tvTitle;
    private String username;
    private boolean showingGlobal = true;

    // Geolocalización
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String userCountry = "Unknown";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scores);

        // Tomamos el username guardado para poder mostrar tus puntuaciones locales
        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Invitado");

        // Intentar obtener país guardado
        userCountry = prefs.getString("userCountry", "Unknown");

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtener ubicación actualizada
        requestLocationPermissionAndGetCountry();

        // Vistas
        tvTitle = findViewById(R.id.tvTitle);
        rvScores = findViewById(R.id.rvScores);
        btnGlobal = findViewById(R.id.btnGlobal);
        btnLocal = findViewById(R.id.btnLocal);

        rvScores.setLayoutManager(new LinearLayoutManager(this));

        // Preparamos la lista y el adaptador vacíos
        scoresList = new ArrayList<>();
        adapter = new ScoresAdapter(scoresList);
        rvScores.setAdapter(adapter);

        // Botones para cambiar entre Top global y ranking local (por país)
        setupButtons();

        // Por defecto mostramos el TOP 10 global
        loadGlobalScores();
    }

    private void requestLocationPermissionAndGetCountry() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Solicitar permiso
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Ya tenemos permiso, obtener ubicación
            getUserCountry();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getUserCountry();
            } else {
                Log.w(TAG, "Permiso de ubicación denegado. País por defecto: Unknown");
                Toast.makeText(this, "Permiso de ubicación denegado. No se puede mostrar ranking local", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void getUserCountry() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.d(TAG, "Obteniendo ubicación del usuario...");

        // Primero intentar con la última ubicación conocida
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        Log.d(TAG, "Última ubicación obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                        getCountryFromLocation(location);
                    } else {
                        // Si no hay última ubicación, solicitar una actualización
                        Log.d(TAG, "No hay última ubicación, solicitando actualización...");
                        requestNewLocationForScores();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo última ubicación: " + e.getMessage());
                    // Intentar solicitar nueva ubicación
                    requestNewLocationForScores();
                });
    }

    private void requestNewLocationForScores() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Crear LocationRequest para obtener ubicación actualizada
        LocationRequest locationRequest = new LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(5000)
                .setMaxUpdateDelayMillis(10000)
                .build();

        // Crear callback para recibir la ubicación
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    Log.d(TAG, "Nueva ubicación obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                    getCountryFromLocation(location);
                    // Detener actualizaciones después de obtener la primera
                    fusedLocationClient.removeLocationUpdates(locationCallback);
                }
            }
        };

        // Solicitar actualizaciones de ubicación
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

        // Configurar timeout para detener las actualizaciones después de 30 segundos
        new android.os.Handler().postDelayed(() -> {
            Log.w(TAG, "Timeout: No se pudo obtener ubicación actualizada. Usando país guardado: " + userCountry);
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }, 30000);
    }

    private void getCountryFromLocation(Location location) {
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1
            );

            if (addresses != null && !addresses.isEmpty()) {
                String country = addresses.get(0).getCountryName();
                if (country != null && !country.isEmpty()) {
                    userCountry = country;
                    Log.d(TAG, "País detectado: " + userCountry);

                    // Guardar país en SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                    prefs.edit().putString("userCountry", userCountry).apply();

                    // Si estamos mostrando local, recargar con el país actualizado
                    if (!showingGlobal) {
                        loadLocalScores();
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error en Geocoder: " + e.getMessage());
        }
    }

    private void setupButtons() {
        // Botón GLOBAL: si no está activo, lo activamos
        btnGlobal.setOnClickListener(v -> {
            if (!showingGlobal) {
                showingGlobal = true;
                updateButtonStyles();
                loadGlobalScores();
            }
        });

        // Botón LOCAL: ranking del país donde estás (TOP 50)
        btnLocal.setOnClickListener(v -> {
            if (showingGlobal) {
                showingGlobal = false;
                updateButtonStyles();
                loadLocalScores();
            }
        });

        // Estilo inicial de botones
        updateButtonStyles();
    }

    private void updateButtonStyles() {
        if (showingGlobal) {
            // GLOBAL activo (más visible)
            btnGlobal.setBackgroundColor(0xFF9D4EDD); // Morado brillante
            btnGlobal.setTextColor(0xFFFFFFFF); // Blanco

            // LOCAL inactivo
            btnLocal.setBackgroundColor(0xFF6A0DAD); // Morado oscuro
            btnLocal.setTextColor(0xFF00FF00); // Verde
        } else {
            // LOCAL activo
            btnLocal.setBackgroundColor(0xFF9D4EDD); // Morado brillante
            btnLocal.setTextColor(0xFFFFFFFF); // Blanco

            // GLOBAL inactivo
            btnGlobal.setBackgroundColor(0xFF6A0DAD); // Morado oscuro
            btnGlobal.setTextColor(0xFF00FF00); // Verde
        }
    }

    private void loadGlobalScores() {
        tvTitle.setText("TOP 10 GLOBAL");
        Log.d(TAG, "Cargando scores globales...");

        globalScoresRef = FirebaseDatabase.getInstance()
                .getReference("global_scores");

        // Escuchar cambios en el nodo global_scores y actualizar UI
        globalScoresRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Global - onDataChange. Hijos: " + dataSnapshot.getChildrenCount());

                scoresList.clear();

                if (!dataSnapshot.exists() || dataSnapshot.getChildrenCount() == 0) {
                    showNoScoresMessage();
                    return;
                }

                List<Score> tempList = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long scoreValue = snapshot.child("score").getValue(Long.class);
                    String username = snapshot.child("username").getValue(String.class);
                    String country = snapshot.child("country").getValue(String.class);

                    if (scoreValue != null && username != null) {
                        Score score = new Score(0, username, scoreValue.intValue(),
                                country != null ? country : "");
                        tempList.add(score);
                    }
                }

                if (tempList.isEmpty()) {
                    showNoScoresMessage();
                    return;
                }

                // Ordenar desc y quedar con los top 10 para mostrar
                tempList.sort((s1, s2) -> Integer.compare(s2.getScore(), s1.getScore()));

                for (int i = 0; i < Math.min(10, tempList.size()); i++) {
                    Score score = tempList.get(i);
                    score.setPosition(i + 1);
                    scoresList.add(score);
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Si Firebase falla, mostramos un mensaje amigable y una sugerencia
                handleFirebaseError(databaseError);
            }
        });
    }

    private void loadLocalScores() {
        if (userCountry.equals("Unknown")) {
            tvTitle.setText("RANKING LOCAL - País Desconocido");
            scoresList.clear();
            scoresList.add(new Score(1, "No se pudo detectar tu ubicación", 0));
            scoresList.add(new Score(2, "Activa la ubicación e intenta nuevamente", 0));
            adapter.notifyDataSetChanged();
            Toast.makeText(this, "Por favor, activa la ubicación para ver el ranking local", Toast.LENGTH_LONG).show();
            return;
        }

        tvTitle.setText("TOP 50 - " + userCountry.toUpperCase());
        Log.d(TAG, "Cargando scores locales para: " + userCountry);

        countryScoresRef = FirebaseDatabase.getInstance()
                .getReference("country_scores")
                .child(userCountry);

        // Escuchamos el ranking del país
        countryScoresRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Local - onDataChange. Hijos: " + dataSnapshot.getChildrenCount());

                scoresList.clear();

                if (!dataSnapshot.exists() || dataSnapshot.getChildrenCount() == 0) {
                    showNoCountryScoresMessage();
                    return;
                }

                List<Score> tempList = new ArrayList<>();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long scoreValue = snapshot.child("score").getValue(Long.class);
                    String username = snapshot.child("username").getValue(String.class);
                    String country = snapshot.child("country").getValue(String.class);

                    if (scoreValue != null && username != null) {
                        Score score = new Score(0, username, scoreValue.intValue(),
                                country != null ? country : userCountry);
                        tempList.add(score);
                    }
                }

                if (tempList.isEmpty()) {
                    showNoCountryScoresMessage();
                    return;
                }

                // Ordenar por puntuación descendente
                tempList.sort((s1, s2) -> Integer.compare(s2.getScore(), s1.getScore()));

                // Mostrar top 50
                for (int i = 0; i < Math.min(50, tempList.size()); i++) {
                    Score score = tempList.get(i);
                    score.setPosition(i + 1);
                    scoresList.add(score);
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                handleFirebaseError(databaseError);
            }
        });
    }

    private void handleFirebaseError(DatabaseError databaseError) {
        Log.e(TAG, "Error de Firebase: " + databaseError.getMessage());

        String errorMsg = "Error al cargar scores";

        if (databaseError.getCode() == DatabaseError.PERMISSION_DENIED) {
            errorMsg = "Permiso denegado. Configura las reglas de Firebase";
            Toast.makeText(this,
                    "⚠️ Error de permisos de Firebase",
                    Toast.LENGTH_LONG).show();
        } else if (databaseError.getCode() == DatabaseError.NETWORK_ERROR) {
            errorMsg = "Error de conexión. Verifica tu internet";
        }

        // Mostrar un mensaje claro en la lista para que el usuario entienda qué pasó
        showErrorMessage(errorMsg);
    }

    private void showNoScoresMessage() {
        scoresList.clear();
        scoresList.add(new Score(1, "No hay puntuaciones aún", 0));
        scoresList.add(new Score(2, "¡Sé el primero!", 0));
        adapter.notifyDataSetChanged();
    }

    private void showNoCountryScoresMessage() {
        scoresList.clear();
        scoresList.add(new Score(1, "No hay puntuaciones en " + userCountry, 0));
        scoresList.add(new Score(2, "¡Sé el primero de tu país!", 0));
        adapter.notifyDataSetChanged();
    }

    private void showErrorMessage(String errorMsg) {
        scoresList.clear();
        scoresList.add(new Score(1, errorMsg, 0));
        scoresList.add(new Score(2, "Intenta más tarde", 0));
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpiar las actualizaciones de ubicación para evitar memory leaks
        if (locationCallback != null && fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
