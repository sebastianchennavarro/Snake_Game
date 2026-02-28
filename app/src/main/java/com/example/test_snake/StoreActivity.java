package com.example.test_snake;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class StoreActivity extends AppCompatActivity {

    private TextView coinsAmount;
    private RecyclerView recyclerView;
    private SkinAdapter adapter;
    private List<Skin> skinList;
    private DatabaseReference coinsRef;
    private String username;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        coinsAmount = findViewById(R.id.coinsAmount);
        recyclerView = findViewById(R.id.skinsRecyclerView);

        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
        username = prefs.getString("username", "Invitado");

        // Inicializar Firebase
        coinsRef = FirebaseDatabase.getInstance()
                .getReference("user_coins")
                .child(username);

        // Cargar monedas desde Firebase
        loadCoinsFromFirebase();

        // Crear lista de skins
        skinList = new ArrayList<>();

        // Skin por defecto (siempre equipada / gratis)
        skinList.add(new Skin(
                "skin_default",
                "Clásica",
                0,
                getResources().getIdentifier("skin_default", "drawable", getPackageName()),
                true
        ));

        // Skin azul
        skinList.add(new Skin(
                "skin_blue",
                "Azul",
                10, // precio en monedas
                getResources().getIdentifier("skin_blue", "drawable", getPackageName()),
                prefs.getBoolean("skin_purchased_skin_blue", false)
        ));

        // Skin roja
        skinList.add(new Skin(
                "skin_red",
                "Roja",
                15, // precio en monedas
                getResources().getIdentifier("skin_red", "drawable", getPackageName()),
                prefs.getBoolean("skin_purchased_skin_red", false)
        ));

        // Configurar RecyclerView
        adapter = new SkinAdapter(this, skinList, coinsRef);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void loadCoinsFromFirebase() {
        coinsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    Integer firebaseCoins = dataSnapshot.getValue(Integer.class);
                    if (firebaseCoins != null) {
                        // Sincronizar con SharedPreferences
                        SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                        prefs.edit().putInt("coins", firebaseCoins).apply();

                        // Actualizar UI
                        coinsAmount.setText(String.valueOf(firebaseCoins));
                    }
                } else {
                    // Si no existe en Firebase, usar local
                    SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                    int coins = prefs.getInt("coins", 0);
                    coinsAmount.setText(String.valueOf(coins));
                    // Guardar en Firebase
                    coinsRef.setValue(coins);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Error al cargar, usar local
                SharedPreferences prefs = getSharedPreferences("SnakePrefs", MODE_PRIVATE);
                int coins = prefs.getInt("coins", 0);
                coinsAmount.setText(String.valueOf(coins));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Las monedas se actualizan automáticamente con el listener de Firebase
    }
}
