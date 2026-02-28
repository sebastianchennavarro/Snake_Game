package com.example.test_snake;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class GameActivity extends AppCompatActivity {

    private static final String TAG = "GameActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 101;

    private GameView gameView;
    private TextView scoreText, coinsText;
    private Button btnUp, btnDown, btnLeft, btnRight;

    // Firebase: referencias a nodos que usamos (puntuaciones y monedas)
    private DatabaseReference scoresRef;
    private DatabaseReference globalScoresRef;
    private DatabaseReference countryScoresRef;
    private DatabaseReference coinsRef;
    private String username;
    private int lastSavedScore = 0;

    // Geolocalización
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private String userCountry = "Unknown";
    private boolean locationObtained = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        Log.d(TAG, "GameActivity creada");

        // Tomamos el username guardado (si no hay, aparece 'Invitado')
        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Invitado");

        // Intentar obtener país guardado previamente
        userCountry = prefs.getString("userCountry", "Unknown");
        Log.d(TAG, "País inicial desde SharedPreferences: " + userCountry);

        // Inicializar cliente de geolocalización
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Obtener ubicación actualizada
        getUserLocation();

        // Conectamos a Firebase para los diferentes nodos que necesitamos
        scoresRef = FirebaseDatabase.getInstance()
                .getReference("user_scores")
                .child(username);
        globalScoresRef = FirebaseDatabase.getInstance()
                .getReference("global_scores");
        coinsRef = FirebaseDatabase.getInstance()
                .getReference("user_coins")
                .child(username);

        // Cargar las monedas desde Firebase (si hay)
        loadCoinsFromFirebase();

        try {
            // Inicializar vistas
            gameView = findViewById(R.id.gameView);
            scoreText = findViewById(R.id.scoreText);
            coinsText = findViewById(R.id.coinsText);
            btnUp = findViewById(R.id.btnUp);
            btnDown = findViewById(R.id.btnDown);
            btnLeft = findViewById(R.id.btnLeft);
            btnRight = findViewById(R.id.btnRight);

            if (gameView == null) {
                Log.e(TAG, "GameView es null!");
                finish();
                return;
            }

            if (scoreText == null || coinsText == null || btnUp == null || btnDown == null || btnLeft == null || btnRight == null) {
                Log.e(TAG, "Alguna vista es null!");
            }

            // Configurar controles
            setupControls();

            // Configurar toque para reiniciar
            gameView.setOnTouchListener((v, event) -> {
                if ((gameView.isGameOver() || gameView.isGameWon()) && event.getAction() == MotionEvent.ACTION_DOWN) {
                    // Guardamos el score actual antes de reiniciar la partida
                    saveScoreToFirebase(gameView.getScore());
                    gameView.restartGame();
                    updateScore();
                    updateCoins();
                    return true;
                }
                return false;
            });

            // Iniciar actualización de score
            startScoreUpdate();

            Log.d(TAG, "GameActivity inicializada correctamente");

        } catch (Exception e) {
            Log.e(TAG, "Error en onCreate: " + e.getMessage());
            e.printStackTrace();
            finish();
        }
    }

    private void loadCoinsFromFirebase() {
        coinsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Integer firebaseCoins = dataSnapshot.getValue(Integer.class);
                    if (firebaseCoins != null) {
                        // Sincronizamos las monedas con las que guardamos localmente
                        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                        prefs.edit().putInt("coins", firebaseCoins).apply();

                        // Avisamos al GameView por si necesita mostrarlas
                        if (gameView != null) {
                            gameView.updateCoinsFromFirebase(firebaseCoins);
                        }
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Si falla Firebase, seguimos con las monedas locales (no rompemos la partida)
                Log.e(TAG, "Error cargando monedas de Firebase: " + databaseError.getMessage());
            }
        });
    }

    private void setupControls() {
        if (btnUp != null && gameView != null) {
            btnUp.setOnClickListener(v -> {
                Log.d(TAG, "Botón UP presionado");
                gameView.setDirectionUp();
            });
        }

        if (btnDown != null && gameView != null) {
            btnDown.setOnClickListener(v -> {
                Log.d(TAG, "Botón DOWN presionado");
                gameView.setDirectionDown();
            });
        }

        if (btnLeft != null && gameView != null) {
            btnLeft.setOnClickListener(v -> {
                Log.d(TAG, "Botón LEFT presionado");
                gameView.setDirectionLeft();
            });
        }

        if (btnRight != null && gameView != null) {
            btnRight.setOnClickListener(v -> {
                Log.d(TAG, "Botón RIGHT presionado");
                gameView.setDirectionRight();
            });
        }
    }

    private void startScoreUpdate() {
        new Thread(() -> {
            while (!isFinishing()) {
                try {
                    Thread.sleep(100);
                    runOnUiThread(() -> {
                        updateScore();
                        updateCoins();
                        // Si terminó la partida, guardamos el score (una sola vez por cambio)
                        if (gameView != null && gameView.isGameOver() && gameView.getScore() > 0 && lastSavedScore != gameView.getScore()) {
                            saveScoreToFirebase(gameView.getScore());
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error en hilo de score: " + e.getMessage());
                    break;
                }
            }
        }).start();
    }

    private void updateScore() {
        if (scoreText != null && gameView != null) {
            scoreText.setText("SCORE: " + gameView.getScore() + "/250");
        }
    }

    private void updateCoins() {
        if (coinsText != null) {
            SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
            int coins = prefs.getInt("coins", 0);
            coinsText.setText("MONEDAS: " + coins);
        }
    }

    private void saveScoreToFirebase(final int score) {
        if (score == 0 || score == lastSavedScore) return;

        Log.d(TAG, "Guardando score: " + score + " para usuario: " + username + " en país: " + userCountry);

        lastSavedScore = score;
        final long timestamp = System.currentTimeMillis();

        // Guardar en el historial personal del usuario
        String scoreId = scoresRef.push().getKey();
        if (scoreId != null) {
            Map<String, Object> scoreData = new HashMap<>();
            scoreData.put("score", score);
            scoreData.put("timestamp", timestamp);
            scoreData.put("username", username);
            scoreData.put("country", userCountry);

            scoresRef.child(scoreId).setValue(scoreData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Score personal guardado exitosamente"))
                    .addOnFailureListener(e -> Log.e(TAG, "Error guardando score personal: " + e.getMessage()));
        }

        // Verificar si entra al top 10 global y, si es así, guardarlo allí también
        checkAndSaveGlobalScore(score, timestamp);

        // Verificar si entra al top 50 del país y guardarlo
        checkAndSaveCountryScore(score, timestamp);
    }

    private void checkAndSaveGlobalScore(final int score, final long timestamp) {
        Log.d(TAG, "Verificando si score " + score + " califica para top 10");

        // Leemos los scores globales actuales para decidir si el nuevo entra al top 10
        globalScoresRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Scores globales actuales: " + dataSnapshot.getChildrenCount());

                List<ScoreEntry> allScores = new ArrayList<>();

                // Recolectar todos los scores que hay
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long scoreValue = snapshot.child("score").getValue(Long.class);
                    if (scoreValue != null) {
                        allScores.add(new ScoreEntry(snapshot.getKey(), scoreValue.intValue()));
                        Log.d(TAG, "Score existente: " + scoreValue);
                    }
                }

                // Añadimos el score nuevo a la lista para comparar
                allScores.add(new ScoreEntry(null, score));

                // Ordenamos de mayor a menor
                Collections.sort(allScores, (s1, s2) -> Integer.compare(s2.score, s1.score));

                // Determinar si el nuevo score queda en el top 10 y eliminar los que sobren
                boolean isTop10 = false;

                for (int i = 0; i < allScores.size(); i++) {
                    if (i < 10 && allScores.get(i).key == null) {
                        // Nuestro nuevo score está dentro del top 10
                        isTop10 = true;
                        Log.d(TAG, "Score " + score + " está en posición " + (i + 1) + " del top 10");
                    } else if (i >= 10 && allScores.get(i).key != null) {
                        // Este score ya no pertenece al top 10, lo borramos
                        String keyToRemove = allScores.get(i).key;
                        Log.d(TAG, "Eliminando score fuera del top 10: " + allScores.get(i).score);
                        globalScoresRef.child(keyToRemove).removeValue();
                    }
                }

                // Si entra al top 10, lo guardamos con los datos necesarios
                if (isTop10) {
                    String scoreId = globalScoresRef.push().getKey();
                    if (scoreId != null) {
                        Map<String, Object> globalScoreData = new HashMap<>();
                        globalScoreData.put("score", score);
                        globalScoreData.put("timestamp", timestamp);
                        globalScoreData.put("username", username);
                        globalScoreData.put("country", userCountry);

                        Log.d(TAG, "Guardando en top 10 global: " + score + " con país: " + userCountry);
                        globalScoresRef.child(scoreId).setValue(globalScoreData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Score guardado en top 10 global exitosamente");
                                    runOnUiThread(() -> {
                                        Toast.makeText(GameActivity.this,
                                                "¡Top 10 Global! Score: " + score,
                                                Toast.LENGTH_SHORT).show();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error guardando score global: " + e.getMessage());
                                });
                    }
                } else {
                    Log.d(TAG, "Score " + score + " NO califica para top 10");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error verificando scores globales: " + databaseError.getMessage());
            }
        });
    }

    private void checkAndSaveCountryScore(final int score, final long timestamp) {
        // Si el país es Unknown, no guardamos en ranking local
        if (userCountry.equals("Unknown")) {
            Log.w(TAG, "País desconocido, no se guarda en ranking local");
            return;
        }

        Log.d(TAG, "Verificando si score " + score + " califica para top 50 de " + userCountry);

        // Obtener referencia al ranking del país
        countryScoresRef = FirebaseDatabase.getInstance()
                .getReference("country_scores")
                .child(userCountry);

        // Leer los scores actuales del país
        countryScoresRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                Log.d(TAG, "Scores del país " + userCountry + " actuales: " + dataSnapshot.getChildrenCount());

                List<ScoreEntry> allScores = new ArrayList<>();

                // Recolectar todos los scores que hay
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Long scoreValue = snapshot.child("score").getValue(Long.class);
                    if (scoreValue != null) {
                        allScores.add(new ScoreEntry(snapshot.getKey(), scoreValue.intValue()));
                    }
                }

                // Añadir el nuevo score
                allScores.add(new ScoreEntry(null, score));

                // Ordenar de mayor a menor
                Collections.sort(allScores, (s1, s2) -> Integer.compare(s2.score, s1.score));

                // Determinar si el nuevo score queda en el top 50
                boolean isTop50 = false;

                for (int i = 0; i < allScores.size(); i++) {
                    if (i < 50 && allScores.get(i).key == null) {
                        // Nuestro nuevo score está dentro del top 50
                        isTop50 = true;
                        Log.d(TAG, "Score " + score + " está en posición " + (i + 1) + " del top 50 de " + userCountry);
                    } else if (i >= 50 && allScores.get(i).key != null) {
                        // Este score ya no pertenece al top 50, lo borramos
                        String keyToRemove = allScores.get(i).key;
                        Log.d(TAG, "Eliminando score fuera del top 50: " + allScores.get(i).score);
                        countryScoresRef.child(keyToRemove).removeValue();
                    }
                }

                // Si entra al top 50, lo guardamos
                if (isTop50) {
                    String scoreId = countryScoresRef.push().getKey();
                    if (scoreId != null) {
                        Map<String, Object> countryScoreData = new HashMap<>();
                        countryScoreData.put("score", score);
                        countryScoreData.put("timestamp", timestamp);
                        countryScoreData.put("username", username);
                        countryScoreData.put("country", userCountry);

                        Log.d(TAG, "Guardando en top 50 de " + userCountry + ": " + score);
                        countryScoresRef.child(scoreId).setValue(countryScoreData)
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Score guardado en top 50 del país exitosamente");
                                    runOnUiThread(() -> {
                                        Toast.makeText(GameActivity.this,
                                                "¡Top 50 de " + userCountry + "! Score: " + score,
                                                Toast.LENGTH_SHORT).show();
                                    });
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error guardando score del país: " + e.getMessage());
                                });
                    }
                } else {
                    Log.d(TAG, "Score " + score + " NO califica para top 50 de " + userCountry);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e(TAG, "Error verificando scores del país: " + databaseError.getMessage());
            }
        });
    }

    // Pequeña clase auxiliar para manejar pares (key, score)
    private static class ScoreEntry {
        String key;
        int score;

        ScoreEntry(String key, int score) {
            this.key = key;
            this.score = score;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "GameActivity en pausa");
        if (gameView != null) {
            gameView.stopGame();
            gameView.pauseBackgroundMusic();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "GameActivity en resume");
        if (gameView != null && !gameView.isGameWon()) {
            gameView.resumeBackgroundMusic();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "GameActivity destruida");
        if (gameView != null) {
            gameView.stopGame();
        }
        // Limpiar las actualizaciones de ubicación para evitar memory leaks
        if (locationCallback != null && fusedLocationClient != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    // ==================== MÉTODOS DE GEOLOCALIZACIÓN ====================

    private void getUserLocation() {
        // Verificar si tenemos permisos de ubicación
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Solicitar permisos
            Log.d(TAG, "Solicitando permisos de ubicación");
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            // Ya tenemos permisos, obtener ubicación
            requestLocation();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permiso de ubicación concedido");
                requestLocation();
            } else {
                Log.w(TAG, "Permiso de ubicación denegado. Usando país guardado: " + userCountry);
                Toast.makeText(this, "Sin ubicación, tus scores se guardarán como: " + userCountry,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Log.d(TAG, "Obteniendo ubicación...");

        // Primero intentar con la última ubicación conocida
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        Log.d(TAG, "Última ubicación obtenida: " + location.getLatitude() + ", " + location.getLongitude());
                        getCountryFromLocation(location);
                    } else {
                        // Si no hay última ubicación, solicitar una actualización
                        Log.d(TAG, "No hay última ubicación, solicitando actualización...");
                        requestNewLocation();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error obteniendo última ubicación: " + e.getMessage());
                    // Intentar solicitar nueva ubicación
                    requestNewLocation();
                });
    }

    private void requestNewLocation() {
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
            if (!locationObtained) {
                Log.w(TAG, "Timeout: No se pudo obtener ubicación actualizada. Usando país guardado: " + userCountry);
                fusedLocationClient.removeLocationUpdates(locationCallback);
                locationObtained = true;
            }
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
                    locationObtained = true;
                    Log.d(TAG, "País detectado: " + userCountry);

                    // Guardar país en SharedPreferences
                    SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                    prefs.edit().putString("userCountry", userCountry).apply();

                    Toast.makeText(this, "Ubicación detectada: " + userCountry, Toast.LENGTH_SHORT).show();
                } else {
                    Log.w(TAG, "No se pudo obtener el nombre del país");
                    locationObtained = true;
                }
            } else {
                Log.w(TAG, "No se encontraron direcciones para la ubicación");
                locationObtained = true;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error en Geocoder: " + e.getMessage());
            locationObtained = true;
        }
    }
}