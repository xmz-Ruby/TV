package com.github.tvbox.osc.ui.base;

import android.view.View;

import androidx.leanback.widget.Presenter;

import com.github.tvbox.osc.bean.Vod;

public abstract class BaseVodHolder extends Presenter.ViewHolder {

    public BaseVodHolder(View view) {
        super(view);
    }

    public abstract void initView(Vod item);
}
