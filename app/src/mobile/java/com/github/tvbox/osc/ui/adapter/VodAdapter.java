package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

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

import java.util.ArrayList;
import java.util.List;

public class VodAdapter extends RecyclerView.Adapter<BaseVodHolder> {

    private final OnClickListener mListener;
    private final List<Vod> mItems;
    private final Style style;
    private final int[] size;

    public VodAdapter(OnClickListener listener, Style style, int[] size) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
        this.style = style;
        this.size = size;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        boolean onLongClick(Vod item);
    }

    public Style getStyle() {
        return style;
    }

    public void addAll(List<Vod> items) {
        int position = mItems.size() + 1;
        mItems.addAll(items);
        notifyItemRangeInserted(position, items.size());
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return style.getViewType();
    }

    @Override
    public void onBindViewHolder(@NonNull BaseVodHolder holder, int position) {
        holder.initView(mItems.get(position));
    }

    @NonNull
    @Override
    public BaseVodHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        switch (viewType) {
            case ViewType.LIST:
                return new VodListHolder(AdapterVodListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener);
            case ViewType.OVAL:
                return new VodOvalHolder(AdapterVodOvalBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener).size(size);
            default:
                return new VodRectHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), mListener).size(size);
        }
    }
}
