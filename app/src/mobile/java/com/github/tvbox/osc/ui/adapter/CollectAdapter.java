package com.github.tvbox.osc.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Collect;
import com.github.tvbox.osc.bean.Vod;
import com.github.tvbox.osc.databinding.AdapterCollectBinding;

import java.util.ArrayList;
import java.util.List;

public class CollectAdapter extends RecyclerView.Adapter<CollectAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Collect> mItems;

    public CollectAdapter(OnClickListener listener) {
        this.mListener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Collect item);
    }

    public void clear() {
        mItems.clear();
        mItems.add(Collect.all());
        notifyDataSetChanged();
    }

    public void add(Collect item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void addToAll(List<Vod> items, int exactMatchCount) {
        Collect all = mItems.get(0);
        all.getList().addAll(items);
        all.setExactMatchCount(all.getExactMatchCount() + exactMatchCount);
        notifyItemChanged(0);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isActivated()) return i;
        return 0;
    }

    public Collect getActivated() {
        return mItems.get(getPosition());
    }

    public void setActivated(int position) {
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setActivated(i == position);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterCollectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = mItems.get(position);
        holder.binding.getRoot().setActivated(item.isActivated());
        holder.binding.text.setActivated(item.isActivated());
        holder.binding.text.setText(item.getSite().getName());
        holder.binding.badge.setVisibility(item.getCount() > 0 || position == 0 ? View.VISIBLE : View.GONE);
        holder.binding.badge.setText(String.valueOf(item.getCount()));
        int badgeBg = item.hasExactMatch() ? R.color.search_badge_red_bg : R.color.search_badge_gray_bg;
        int badgeText = item.hasExactMatch() ? R.color.search_badge_red_text : R.color.search_badge_gray_text;
        holder.binding.badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(holder.itemView.getContext(), badgeBg)));
        holder.binding.badge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), badgeText));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(position, item));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCollectBinding binding;

        ViewHolder(@NonNull AdapterCollectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
