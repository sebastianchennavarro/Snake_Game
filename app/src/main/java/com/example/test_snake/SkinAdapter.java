package com.example.test_snake;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

public class SkinAdapter extends ArrayAdapter<Skin> {

    public interface OnSkinActionListener {
        void onSkinAction(Skin skin);
        int getCoinBalance();
    }

    private final LayoutInflater inflater;
    private final OnSkinActionListener listener;

    public SkinAdapter(@NonNull Context context, @NonNull List<Skin> objects, @NonNull OnSkinActionListener listener) {
        super(context, 0, objects);
        this.inflater = LayoutInflater.from(context);
        this.listener = listener;
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.list_item_skin, parent, false);
            holder = new ViewHolder();
            holder.imageView = convertView.findViewById(R.id.skinImage);
            holder.nameView = convertView.findViewById(R.id.skinName);
            holder.costView = convertView.findViewById(R.id.skinCost);
            holder.actionButton = convertView.findViewById(R.id.skinButton);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final Skin skin = getItem(position);
        if (skin != null) {
            holder.imageView.setImageResource(skin.getImageResId());
            holder.nameView.setText(skin.getName());

            if (skin.isPurchased()) {
                holder.costView.setText("Comprado");
                holder.actionButton.setText("Equipar");
                holder.actionButton.setEnabled(true);
                holder.actionButton.setAlpha(1f);
            } else {
                holder.costView.setText("Costo: " + skin.getCost());
                holder.actionButton.setText("Comprar");
                boolean canBuy = listener.getCoinBalance() >= skin.getCost();
                holder.actionButton.setEnabled(canBuy);
                holder.actionButton.setAlpha(canBuy ? 1f : 0.5f);
            }

            holder.actionButton.setOnClickListener(v -> listener.onSkinAction(skin));
        }

        return convertView;
    }

    private static class ViewHolder {
        ImageView imageView;
        TextView nameView;
        TextView costView;
        Button actionButton;
    }
}
