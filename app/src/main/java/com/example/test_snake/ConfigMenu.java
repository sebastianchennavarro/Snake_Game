package com.example.test_snake;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class ConfigMenu extends AppCompatActivity {

    private Switch switchMusica;
    private SeekBar seekBarVolumen;
    private Button btnSalir, btnCreditos, btnReanudar;
    private SharedPreferences prefs;
    private static final String TAG = "ConfigMenu";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config_menu);

        // Inicializar SharedPreferences
        prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);

        // Inicializar vistas
        switchMusica = findViewById(R.id.sw_musica);
        seekBarVolumen = findViewById(R.id.sbar_Volumen);
        btnSalir = findViewById(R.id.btn_salir);
        btnCreditos = findViewById(R.id.btn_creditos);
        btnReanudar = findViewById(R.id.btn_reanudar);

        // Cargar configuración guardada
        loadSavedSettings();

        // Configurar listeners
        setupButtonListeners();
        setupSeekBarListener();
        setupSwitchListener();

        Log.d(TAG, "ConfigMenu creado - Control de volumen activo");
    }

    private void loadSavedSettings() {
        // Cargar volumen guardado (0-100)
        int savedVolume = prefs.getInt("music_volume", 70);
        boolean musicEnabled = prefs.getBoolean("music_enabled", true);

        // Aplicar configuración a las vistas
        seekBarVolumen.setProgress(savedVolume);
        switchMusica.setChecked(musicEnabled);

        Log.d(TAG, "Configuración cargada - Volumen: " + savedVolume + "%, Música: " + musicEnabled);
    }

    private void setupButtonListeners() {
        btnSalir.setOnClickListener(v -> {
            Log.d(TAG, "Saliendo de configuración");
            finish();
        });

        btnReanudar.setOnClickListener(v -> {
            Log.d(TAG, "Reanudando");
            finish();
        });

        btnCreditos.setOnClickListener(v -> {
            Log.d(TAG, "Mostrando créditos");
            Toast.makeText(ConfigMenu.this, "Desarrollado por Yadir Zúñiga", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupSeekBarListener() {
        seekBarVolumen.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // Actualizar volumen en tiempo real
                    updateMusicVolume(progress);
                    Log.d(TAG, "Volumen cambiado a: " + progress + "%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Opcional: pausar música momentáneamente si es necesario
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Guardar volumen cuando el usuario suelta la barra
                int volume = seekBar.getProgress();
                saveVolumeSetting(volume);
                Log.d(TAG, "Volumen guardado: " + volume + "%");
            }
        });
    }

    private void setupSwitchListener() {
        switchMusica.setOnClickListener(v -> {
            boolean musicaActivada = switchMusica.isChecked();

            if (musicaActivada) {
                // Activar música
                MusicService.resumeMusic();
                Toast.makeText(ConfigMenu.this, "Música activada", Toast.LENGTH_SHORT).show();
            } else {
                // Desactivar música
                MusicService.pauseMusic();
                Toast.makeText(ConfigMenu.this, "Música desactivada", Toast.LENGTH_SHORT).show();
            }

            // Guardar preferencia
            saveMusicEnabledSetting(musicaActivada);
            Log.d(TAG, "Música " + (musicaActivada ? "activada" : "desactivada"));
        });
    }

    private void updateMusicVolume(int volume) {
        // Guardar en SharedPreferences
        saveVolumeSetting(volume);

        // Actualizar volumen en el servicio de música
        MusicService.updateVolume(this);

        // Opcional: mostrar feedback visual
        if (volume == 0) {
            Toast.makeText(this, "Volumen: Mute", Toast.LENGTH_SHORT).show();
        } else if (volume % 25 == 0) { // Mostrar cada 25%
            Toast.makeText(this, "Volumen: " + volume + "%", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveVolumeSetting(int volume) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("music_volume", volume);
        editor.apply();
    }

    private void saveMusicEnabledSetting(boolean enabled) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("music_enabled", enabled);
        editor.apply();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "ConfigMenu resumido");

        // Actualizar volumen al volver (por si cambió en otro lugar)
        int currentVolume = prefs.getInt("music_volume", 70);
        seekBarVolumen.setProgress(currentVolume);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "ConfigMenu pausado");

        //Guardar CAMBIOS
        saveVolumeSetting(seekBarVolumen.getProgress());
        saveMusicEnabledSetting(switchMusica.isChecked());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ConfigMenu destruido");
    }
}