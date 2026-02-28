package com.example.test_snake;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText etUsername;
    private Button btnPlay;
    private Button btnGuest;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar Firebase Auth
        mAuth = FirebaseAuth.getInstance();

        // Autenticar anónimamente al inicio
        authenticateAnonymously();

        final SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);

        etUsername = findViewById(R.id.etUsername);
        btnPlay = findViewById(R.id.btnPlay);
        btnGuest = findViewById(R.id.btnGuest);

        // Si ya habías escrito un nombre antes, lo mostramos para que no lo tengas que reescribir
        String savedUsername = prefs.getString("username", "");
        if (!TextUtils.isEmpty(savedUsername)) {
            etUsername.setText(savedUsername);
            etUsername.setSelection(savedUsername.length()); // Dejar el cursor al final, cómodo para editar
        }

        btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String username = etUsername.getText().toString().trim();

                // Si no pones nombre, entras como Invitado
                if (TextUtils.isEmpty(username)) {
                    username = "Invitado";
                }

                // Validación sencilla: máximo 20 caracteres
                if (username.length() > 20) {
                    etUsername.setError("Máximo 20 caracteres");
                    Toast.makeText(LoginActivity.this, "El nombre no puede superar 20 caracteres", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Guardamos tu nombre para recordarlo la próxima vez
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username", username);
                editor.apply();

                Toast.makeText(LoginActivity.this, "Bienvenido " + username, Toast.LENGTH_SHORT).show();

                // Vamos al menú principal y le pasamos el nombre por si la pantalla lo necesita ahora mismo
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("username", username);
                startActivity(intent);
                finish();
            }
        });

        btnGuest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Si quieres jugar rápido sin nombre, entras como 'Invitado'
                String guest = "Invitado";

                // Guardamos 'Invitado' para mantener sincronía si usamos Firebase
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("username", guest);
                editor.apply();

                Toast.makeText(LoginActivity.this, "Entrando como Invitado", Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                intent.putExtra("username", guest);
                startActivity(intent);
                finish();
            }
        });
    }

    /**
     * Autentica al usuario de forma anónima con Firebase.
     * Esto permite usar reglas de seguridad en Firebase sin requerir email/password.
     */
    private void authenticateAnonymously() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser != null) {
            // Ya está autenticado
            Log.d(TAG, "Usuario ya autenticado: " + currentUser.getUid());
            return;
        }

        // Autenticar anónimamente
        mAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser user = mAuth.getCurrentUser();
                        Log.d(TAG, "Autenticación anónima exitosa: " + (user != null ? user.getUid() : "null"));
                    } else {
                        Log.e(TAG, "Error en autenticación anónima: " + task.getException());
                        Toast.makeText(LoginActivity.this, "Error de conexión", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

