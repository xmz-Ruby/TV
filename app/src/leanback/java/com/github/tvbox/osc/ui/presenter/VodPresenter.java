package com.github.tvbox.osc.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.leanback.widget.Presenter;

import com.github.tvbox.osc.Product;
import com.github.tvbox.osc.bean.Style;
import com.github.tvbox.osc.bean.Vod;
import com.github.tvbox.osc.databinding.AdapterVodListBinding;
import com.github.tvbox.osc.databinding.AdapterVodOvalBinding;
import com.github.tvbox.osc.databinding.AdapterVodRectBinding;
import com.github.tvbox.osc.ui.base.BaseVodHolder;
import com.github.tvbox.osc.ui.base.ViewType;
import com.github.tvbox.osc.ui.holder.VodListHolder;
import com.github.tvbox.osc.ui.holder.VodOvalHolder;
import com.github.tvbox.osc.ui.holder.VodRectHolder;

public class VodPresenter extends Presenter {

    private final OnClickListener mListener;
    private final Style style;
    private final int[] size;

    public VodPresenter(OnClickListener listener) {
        this(listener, Style.rect());
    }

    public VodPresenter(OnClickListener listener, Style style) {
        this.mListener = listener;
        this.style = style;
        this.size = Product.getSpec(style);
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        boolean onLongClick(Vod item);
    }

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        switch (style.getViewType()) {
            case ViewType.LIST:
                return new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
            case ViewType.OVAL:
                return new VodOvalHolder(AdapterVodOvalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener).size(size);
            default:
                return new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener).size(size);
        }
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object object) {
        ((BaseVodHolder) viewHolder).initView((Vod) object);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }
}