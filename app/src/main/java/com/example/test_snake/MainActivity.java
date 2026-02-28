package com.example.test_snake;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private Button btnStartGame, btnSettings, btnScores, btnExit;
    private Button btnEditUser, btnLogout;
    private Button btnTienda;
    private TextView tvUsername;

    private DatabaseReference databaseReference;
    private String currentUsername;
    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Inicializar Firebase
        databaseReference = FirebaseDatabase.getInstance().getReference();

        // Inicializar vistas
        initializeViews();

        // Cargar y mostrar username
        loadUsername();

        // Iniciar servicio de música
        startMusicService();

        // Cargar mensaje desde Firebase
        loadMessageFromFirebase();

        // Configurar listeners de botones
        setupButtonListeners();

        Log.d(TAG, "MainActivity creado - Servicio de música iniciado");
    }

    private void initializeViews() {
        tvUsername = findViewById(R.id.tvUsername);
        btnEditUser = findViewById(R.id.btnEditUser);
        btnLogout = findViewById(R.id.btnLogout);
        btnStartGame = findViewById(R.id.btnStartGame);
        btnSettings = findViewById(R.id.btnSettings);
        btnScores = findViewById(R.id.btnScores);
        btnExit = findViewById(R.id.btnExit);
        btnTienda = findViewById(R.id.btnTienda);
    }

    private void loadUsername() {
        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
        currentUsername = getIntent().getStringExtra("username");
        if (currentUsername == null || currentUsername.isEmpty()) {
            currentUsername = prefs.getString("username", "Invitado");
        }
        tvUsername.setText("Hola, " + currentUsername);
    }

    private void startMusicService() {
        try {
            Intent musicIntent = new Intent(this, MusicService.class);
            startService(musicIntent);
            Log.d(TAG, "Servicio de música iniciado");
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar servicio de música: " + e.getMessage());
        }
    }

    private void stopMusicService() {
        try {
            Intent musicIntent = new Intent(this, MusicService.class);
            stopService(musicIntent);
            Log.d(TAG, "Servicio de música detenido");
        } catch (Exception e) {
            Log.e(TAG, "Error al detener servicio de música: " + e.getMessage());
        }
    }

    private void loadMessageFromFirebase() {
        DatabaseReference messageRef = databaseReference.child("message");

        messageRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    String message = dataSnapshot.getValue(String.class);
                    if (message != null && !message.isEmpty()) {
                        if (currentUsername == null || currentUsername.equals("Invitado") || currentUsername.isEmpty()) {
                            currentUsername = message;
                            tvUsername.setText("Hola, " + currentUsername);
                            saveUsernameToPrefs(currentUsername);
                        }
                    }
                } else {
                    if (currentUsername == null || currentUsername.isEmpty()) {
                        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                        currentUsername = prefs.getString("username", "Invitado");
                        tvUsername.setText("Hola, " + currentUsername);
                    }
                    messageRef.setValue(currentUsername);
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.e(TAG, "Error Firebase: " + databaseError.getMessage());
                if (currentUsername == null || currentUsername.isEmpty()) {
                    SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                    currentUsername = prefs.getString("username", "Invitado");
                }
                tvUsername.setText("Hola, " + currentUsername);
            }
        });
    }

    private void saveUsernameToPrefs(String username) {
        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
        prefs.edit().putString("username", username).apply();
    }

    private void setupButtonListeners() {
        // Iniciar juego
        btnStartGame.setOnClickListener(v -> {
            Log.d(TAG, "Iniciando juego");
            startActivity(new Intent(MainActivity.this, GameActivity.class));
        });

        // Configuración
        btnSettings.setOnClickListener(v -> {
            Log.d(TAG, "Abriendo configuración");
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });

        // Puntuaciones
        btnScores.setOnClickListener(v -> {
            Log.d(TAG, "Abriendo puntuaciones");
            startActivity(new Intent(MainActivity.this, ScoresActivity.class));
        });

        // Salir - CORREGIDO: Detener música al salir
        btnExit.setOnClickListener(v -> {
            Log.d(TAG, "Saliendo de la aplicación");
            stopMusicService();
            finishAffinity(); // Cierra todas las actividades
        });

        // Editar usuario
        btnEditUser.setOnClickListener(v -> {
            Log.d(TAG, "Editando usuario");
            stopMusicService(); // Detener música antes de cambiar
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.putExtra("force_edit", true);
            startActivity(intent);
            finish();
        });

        // Logout
        btnLogout.setOnClickListener(v -> {
            Log.d(TAG, "Cerrando sesión");
            SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
            prefs.edit().remove("username").apply();
            stopMusicService(); // Detener música al hacer logout

            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // TIENDA
        btnTienda.setOnClickListener(v -> {
            Log.d(TAG, "Abriendo tienda");
            Intent intent = new Intent(MainActivity.this, StoreActivity.class);
            startActivity(intent);
        });
    }

    @Override
    public void onBackPressed() {
        Log.d(TAG, "Botón back presionado - Cerrando aplicación");
        super.onBackPressed();
        stopMusicService();
        finishAffinity(); // Cierra toda la aplicación
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity destruido");
    }
}