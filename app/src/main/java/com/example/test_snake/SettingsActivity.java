package com.example.test_snake;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;


public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private SeekBar seekBarMusic, seekBarSFX, seekBarSpeed, seekBarSensitivity;
    private TextView tvMusicVolume, tvSFXVolume, tvSpeedValue, tvSensitivityValue;
    private RadioGroup rgDifficulty, rgControls;
    private SwitchCompat switchVibration, switchFPS, switchParticles, switchNightMode;
    private Spinner spinnerTheme;
    private Button btnChangeUser, btnClearData, btnAbout, btnResetDefaults, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);

        initializeViews();
        loadSettings();
        setupListeners();
    }

    private void initializeViews() {
        seekBarMusic = findViewById(R.id.seekBarMusic);
        seekBarSFX   = findViewById(R.id.seekBarSFX);
        tvMusicVolume = findViewById(R.id.tvMusicVolume);
        tvSFXVolume   = findViewById(R.id.tvSFXVolume);

        rgDifficulty = findViewById(R.id.rgDifficulty);
        seekBarSpeed = findViewById(R.id.seekBarSpeed);
        tvSpeedValue = findViewById(R.id.tvSpeedValue);
        switchVibration = findViewById(R.id.switchVibration);

        spinnerTheme    = findViewById(R.id.spinnerTheme);
        switchFPS       = findViewById(R.id.switchFPS);
        switchParticles = findViewById(R.id.switchParticles);
        switchNightMode = findViewById(R.id.switchNightMode);

        rgControls      = findViewById(R.id.rgControls);
        seekBarSensitivity = findViewById(R.id.seekBarSensitivity);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);

        btnChangeUser    = findViewById(R.id.btnChangeUser);
        btnClearData     = findViewById(R.id.btnClearData);
        btnAbout         = findViewById(R.id.btnAbout);
        btnResetDefaults = findViewById(R.id.btnResetDefaults);
        btnBack          = findViewById(R.id.btnBack);

        String[] themes = {"Clásico Verde", "Azul Neón", "Rojo Fuego", "Morado Oscuro", "Arcoíris"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, themes);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        spinnerTheme.setAdapter(adapter);
    }

    private void loadSettings() {
        seekBarMusic.setProgress(prefs.getInt("music_volume", 70));
        seekBarSFX.setProgress(prefs.getInt("sfx_volume", 85));
        tvMusicVolume.setText(prefs.getInt("music_volume", 70) + "%");
        tvSFXVolume.setText(prefs.getInt("sfx_volume", 85) + "%");

        String diff = prefs.getString("difficulty", "normal");
        if ("easy".equals(diff)) rgDifficulty.check(R.id.rbEasy);
        else if ("hard".equals(diff)) rgDifficulty.check(R.id.rbHard);
        else rgDifficulty.check(R.id.rbNormal);

        seekBarSpeed.setProgress(prefs.getInt("game_speed", 5));
        tvSpeedValue.setText(String.valueOf(prefs.getInt("game_speed", 5)));

        switchVibration.setChecked(prefs.getBoolean("vibration", true));

        spinnerTheme.setSelection(prefs.getInt("theme_index", 0));

        switchFPS.setChecked(prefs.getBoolean("show_fps", false));
        switchParticles.setChecked(prefs.getBoolean("particles", true));
        switchNightMode.setChecked(prefs.getBoolean("night_mode", true));

        String control = prefs.getString("control_type", "swipe");
        if ("buttons".equals(control)) rgControls.check(R.id.rbButtons);
        else if ("tilt".equals(control)) rgControls.check(R.id.rbTilt);
        else rgControls.check(R.id.rbSwipe);

        seekBarSensitivity.setProgress(prefs.getInt("sensitivity", 50));
        tvSensitivityValue.setText(prefs.getInt("sensitivity", 50) + "%");
    }

    private void setupListeners() {
        seekBarMusic.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvMusicVolume.setText(progress + "%");
                prefs.edit().putInt("music_volume", progress).apply();
                MusicService.updateVolume(SettingsActivity.this);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        seekBarSFX.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvSFXVolume.setText(progress + "%");
                prefs.edit().putInt("sfx_volume", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        rgDifficulty.setOnCheckedChangeListener((group, id) -> {
            String diff = "normal";
            if (id == R.id.rbEasy) diff = "easy";
            else if (id == R.id.rbHard) diff = "hard";
            prefs.edit().putString("difficulty", diff).apply();
            Toast.makeText(this, "Dificultad: " + diff, Toast.LENGTH_SHORT).show();
        });

        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvSpeedValue.setText(String.valueOf(progress));
                prefs.edit().putInt("game_speed", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        switchVibration.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("vibration", checked).apply());

        spinnerTheme.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View view, int position, long id) {
                prefs.edit().putInt("theme_index", position).apply();
                Toast.makeText(SettingsActivity.this,
                        "Tema: " + p.getItemAtPosition(position), Toast.LENGTH_SHORT).show();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        switchFPS.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("show_fps", checked).apply());

        switchParticles.setOnCheckedChangeListener((v, checked) ->
                prefs.edit().putBoolean("particles", checked).apply());

        switchNightMode.setOnCheckedChangeListener((v, checked) -> {
            prefs.edit().putBoolean("night_mode", checked).apply();
            Toast.makeText(this,
                    checked ? "Modo noche activado" : "Modo día activado",
                    Toast.LENGTH_SHORT).show();
        });

        rgControls.setOnCheckedChangeListener((group, id) -> {
            String ctrl = "swipe";
            if (id == R.id.rbButtons) ctrl = "buttons";
            else if (id == R.id.rbTilt) ctrl = "tilt";
            prefs.edit().putString("control_type", ctrl).apply();
            Toast.makeText(this, "Control: " + ctrl, Toast.LENGTH_SHORT).show();
        });

        seekBarSensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                tvSensitivityValue.setText(progress + "%");
                prefs.edit().putInt("sensitivity", progress).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        btnChangeUser.setOnClickListener(v -> {
            Intent i = new Intent(this, LoginActivity.class);
            i.putExtra("force_edit", true);
            startActivity(i);
            finish();
        });

        btnClearData.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ Confirmar")
                    .setMessage("¿Borrar TODOS los datos? Esta acción no se puede deshacer.")
                    .setPositiveButton("Borrar", (dialog, which) -> {
                        prefs.edit().clear().apply();
                        Toast.makeText(this, "Datos borrados", Toast.LENGTH_SHORT).show();
                        Intent i = new Intent(this, LoginActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        btnAbout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("🐍 Snake Game")
                    .setMessage("Versión 1.0.0\n\nDesarrollado por Grupo 5\n\n¡Disfruta del juego!")
                    .setPositiveButton("OK", null)
                    .show();
        });

        btnResetDefaults.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Restaurar configuración")
                    .setMessage("¿Restaurar todos los valores predeterminados?")
                    .setPositiveButton("Restaurar", (dialog, which) -> {
                        resetToDefaults();
                        Toast.makeText(this, "Configuración restaurada", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void resetToDefaults() {
        String username = prefs.getString("username", "");
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.putString("username", username);
        editor.putInt("music_volume", 70);
        editor.putInt("sfx_volume", 85);
        editor.putString("difficulty", "normal");
        editor.putInt("game_speed", 5);
        editor.putBoolean("vibration", true);
        editor.putInt("theme_index", 0);
        editor.putBoolean("show_fps", false);
        editor.putBoolean("particles", true);
        editor.putString("control_type", "swipe");
        editor.putInt("sensitivity", 50);
        editor.putBoolean("night_mode", true); // Por defecto en modo noche
        editor.apply();
        loadSettings();
    }
}
