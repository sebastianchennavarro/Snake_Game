package com.example.test_snake;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class StoreActivity extends AppCompatActivity implements SkinAdapter.OnSkinActionListener {

    private static final String PREFS_NAME = "snake_game_prefs";
    private static final String PREF_COINS = "coin_count";
    private static final String PREF_SELECTED_SKIN = "selected_skin";

    private SharedPreferences prefs;
    private TextView txtCoinCount;
    private List<Skin> skinList;
    private SkinAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_store);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        txtCoinCount = findViewById(R.id.txtCoinCount);
        ListView listView = findViewById(R.id.listViewSkins);
        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        // Configurar la lista de skins
        skinList = new ArrayList<>();
        skinList.add(new Skin("default","Clásica",0,R.drawable.skin_default,true));
        skinList.add(new Skin("roja","Serpiente Roja",20,R.drawable.skin_red,  isSkinPurchased("roja")));
        skinList.add(new Skin("azul","Serpiente Azul",40,R.drawable.skin_blue, isSkinPurchased("azul")));

        adapter = new SkinAdapter(this, skinList, this);
        listView.setAdapter(adapter);

        updateCoinDisplay();
    }

    @Override
    public int getCoinBalance() {
        return prefs.getInt(PREF_COINS, 0);
    }

    @Override
    public void onSkinAction(Skin skin) {
        int coins = getCoinBalance();
        if (!skin.isPurchased()) {
            if (coins >= skin.getCost()) {
                coins -= skin.getCost();
                prefs.edit()
                        .putInt(PREF_COINS, coins)
                        .putBoolean(getSkinPurchasedKey(skin.getId()), true)
                        .apply();
                skin.setPurchased(true);
                Toast.makeText(this, "Has comprado " + skin.getName(), Toast.LENGTH_SHORT).show();
                updateCoinDisplay();
                adapter.notifyDataSetChanged();
            } else {
                Toast.makeText(this, "No tienes suficientes monedas para comprar " + skin.getName(), Toast.LENGTH_SHORT).show();
            }
        } else {
            prefs.edit().putString(PREF_SELECTED_SKIN, skin.getId()).apply();
            Toast.makeText(this, skin.getName() + " seleccionada!", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isSkinPurchased(String id) {
        return "default".equals(id) || prefs.getBoolean(getSkinPurchasedKey(id), false);
    }

    private String getSkinPurchasedKey(String id) {
        return "skin_purchased_" + id;
    }

    private void updateCoinDisplay() {
        txtCoinCount.setText(String.valueOf(getCoinBalance()));
    }
}
