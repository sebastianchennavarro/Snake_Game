package com.example.test_snake;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DatabaseReference;

import java.util.List;

public class SkinAdapter extends RecyclerView.Adapter<SkinAdapter.SkinViewHolder> {

    private final Context context;
    private final List<Skin> skins;
    private final SharedPreferences prefs;
    private final DatabaseReference coinsRef;

    public SkinAdapter(Context context, List<Skin> skins, DatabaseReference coinsRef) {
        this.context = context;
        this.skins = skins;
        this.prefs = context.getSharedPreferences("SnakePrefs", Context.MODE_PRIVATE);
        this.coinsRef = coinsRef;
    }

    @NonNull
    @Override
    public SkinViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_skin, parent, false);
        return new SkinViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SkinViewHolder holder, int position) {
        Skin skin = skins.get(position);

        holder.imageView.setImageResource(skin.getImageResId());
        holder.nameText.setText(skin.getName());
        holder.priceText.setText("Precio: " + skin.getPrice());

        // Estado de compra desde SharedPreferences
        boolean purchased = prefs.getBoolean("skin_purchased_" + skin.getId(), skin.isPurchased());
        skin.setPurchased(purchased);

        // Skin equipada actual
        String equippedSkin = prefs.getString("equipped_skin", "skin_default");

        // Texto del botón según estado
        if (!skin.isPurchased() && !skin.getId().equals("skin_default")) {
            holder.actionButton.setText("Comprar");
        } else {
            holder.actionButton.setText(
                    skin.getId().equals(equippedSkin) ? "Equipada" : "Equipar"
            );
        }

        holder.actionButton.setOnClickListener(v -> {
            int coins = prefs.getInt("coins", 0);

            // Si no está comprada y no es la default -> comprar
            if (!skin.isPurchased() && !skin.getId().equals("skin_default")) {
                if (coins >= skin.getPrice()) {
                    int newCoins = coins - skin.getPrice();

                    // Descontar monedas y marcar como comprada
                    prefs.edit()
                            .putInt("coins", newCoins)
                            .putBoolean("skin_purchased_" + skin.getId(), true)
                            .apply();

                    // Sincronizar con Firebase
                    if (coinsRef != null) {
                        coinsRef.setValue(newCoins);
                    }

                    skin.setPurchased(true);
                    notifyItemChanged(position);
                    Toast.makeText(context,
                            "Compraste " + skin.getName(),
                            Toast.LENGTH_SHORT).show();
                } else {
                    // No tiene fondos suficientes (requisito del enunciado)
                    Toast.makeText(context,
                            "No tienes suficientes monedas",
                            Toast.LENGTH_SHORT).show();
                }
            } else {
                // Ya comprada o es default -> equipar
                prefs.edit()
                        .putString("equipped_skin", skin.getId())
                        .apply();
                notifyDataSetChanged(); // Refrescar texto de todos los botones
                Toast.makeText(context,
                        "Skin equipada: " + skin.getName(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return skins.size();
    }

    static class SkinViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView nameText;
        TextView priceText;
        Button actionButton;

        SkinViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.skinImage);
            nameText = itemView.findViewById(R.id.skinName);
            priceText = itemView.findViewById(R.id.skinPrice);
            actionButton = itemView.findViewById(R.id.skinButton);
        }
    }
}
