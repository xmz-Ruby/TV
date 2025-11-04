package com.github.tvbox.osc.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DanmakuEpisode;

public class DanmakuEpisodePresenter extends Presenter {

    public interface OnClickListener {
        void onClick(DanmakuEpisode item);
    }

    private final OnClickListener listener;

    public DanmakuEpisodePresenter(OnClickListener listener) {
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_danmaku_episode, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        DanmakuEpisode episode = (DanmakuEpisode) item;
        TextView text = viewHolder.view.findViewById(R.id.text);
        text.setText(episode.getDisplayName());

        // 设置选中状态
        viewHolder.view.setActivated(episode.isActivated());

        viewHolder.view.setOnClickListener(v -> {
            if (listener != null) listener.onClick(episode);
        });
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
