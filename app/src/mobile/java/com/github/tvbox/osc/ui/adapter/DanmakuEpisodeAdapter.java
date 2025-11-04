package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DanmakuEpisode;

import java.util.ArrayList;
import java.util.List;

public class DanmakuEpisodeAdapter extends RecyclerView.Adapter<DanmakuEpisodeAdapter.ViewHolder> {

    private List<DanmakuEpisode> data = new ArrayList<>();
    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(DanmakuEpisode episode);
    }

    public DanmakuEpisodeAdapter(OnItemClickListener listener) {
        this.listener = listener;
    }

    public void setData(List<DanmakuEpisode> data) {
        this.data = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    public List<DanmakuEpisode> getData() {
        return data;
    }

    public void clear() {
        data.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_danmaku_episode, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DanmakuEpisode episode = data.get(position);
        holder.text.setText(episode.getDisplayName());
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(episode);
            }
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }
    }
}
