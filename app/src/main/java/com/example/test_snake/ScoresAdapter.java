package com.example.test_snake;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ScoresAdapter extends RecyclerView.Adapter<ScoresAdapter.ScoreViewHolder> {

    private List<Score> scores;

    public ScoresAdapter(List<Score> scores) {
        this.scores = scores;
    }

    @NonNull
    @Override
    public ScoreViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_score, parent, false);
        return new ScoreViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ScoreViewHolder holder, int position) {
        Score score = scores.get(position);
        holder.tvRank.setText(String.valueOf(score.getRank()));
        holder.tvName.setText(score.getName());
        holder.tvScore.setText(String.valueOf(score.getScore()));
    }

    @Override
    public int getItemCount() {
        return scores.size();
    }

    public static class ScoreViewHolder extends RecyclerView.ViewHolder {
        TextView tvRank, tvName, tvScore;

        public ScoreViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRank = itemView.findViewById(R.id.tvRank);
            tvName = itemView.findViewById(R.id.tvName);
            tvScore = itemView.findViewById(R.id.tvScore);
        }
    }
}
