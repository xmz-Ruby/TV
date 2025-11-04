package com.github.tvbox.osc.ui.presenter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.DanmakuAnime;

public class DanmakuAnimePresenter extends Presenter {

    public interface OnClickListener {
        void onClick(DanmakuAnime item);
    }

    private final OnClickListener listener;

    public DanmakuAnimePresenter(OnClickListener listener) {
        this.listener = listener;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_danmaku_anime, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        DanmakuAnime anime = (DanmakuAnime) item;
        TextView text = viewHolder.view.findViewById(R.id.text);
        text.setText(anime.getAnimeTitle());
        viewHolder.view.setOnClickListener(v -> {
            if (listener != null) listener.onClick(anime);
        });
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
    }
}
