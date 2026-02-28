package com.example.test_snake;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.util.Log;

public class MusicService extends Service {

    private static MediaPlayer mediaPlayer;
    private static final String TAG = "MusicService";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Servicio de música creado");

        if (mediaPlayer == null) {
            try {
                mediaPlayer = MediaPlayer.create(this, R.raw.snake_music);
                if (mediaPlayer != null) {
                    mediaPlayer.setLooping(true);

                    // Configurar volumen inicial
                    SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                    int vol = prefs.getInt("music_volume", 70);
                    float volume = vol / 100f;
                    mediaPlayer.setVolume(volume, volume);

                    Log.d(TAG, "MediaPlayer creado - Volumen: " + vol + "%");
                } else {
                    Log.e(TAG, "Error: No se pudo crear MediaPlayer");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error al crear MediaPlayer: " + e.getMessage());
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Iniciando servicio de música");

        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            Log.d(TAG, "Música iniciada");
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Destruyendo servicio de música");

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // Método estático para actualizar volumen - CORREGIDO
    public static void updateVolume(Context context) {
        if (mediaPlayer != null) {
            SharedPreferences prefs = context.getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE);
            int vol = prefs.getInt("music_volume", 70);
            float volume = vol / 100f;

            // DEBUG: Verificar que el volumen se está actualizando
            Log.d(TAG, "Actualizando volumen a: " + vol + "% (" + volume + ")");

            mediaPlayer.setVolume(volume, volume);

            // Si el volumen es 0, pausar la música
            if (vol == 0) {
                pauseMusic();
            } else if (!mediaPlayer.isPlaying()) {
                resumeMusic();
            }
        }
    }

    public static void pauseMusic() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            Log.d(TAG, "Música pausada");
        }
    }

    public static void resumeMusic() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            Log.d(TAG, "Música reanudada");
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        Log.d(TAG, "App removida del recents - Deteniendo música");

        stopSelf();
    }
}