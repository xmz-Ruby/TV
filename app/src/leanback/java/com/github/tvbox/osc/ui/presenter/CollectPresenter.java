package com.github.tvbox.osc.ui.presenter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.leanback.widget.Presenter;
import androidx.core.content.ContextCompat;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Collect;
import com.github.tvbox.osc.databinding.AdapterCollectBinding;

public class CollectPresenter extends Presenter {

    @Override
    public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        return new ViewHolder(AdapterCollectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object object) {
        Collect item = (Collect) object;
        ViewHolder holder = (ViewHolder) viewHolder;
        holder.binding.name.setText(item.getSite().getName());
        holder.binding.badge.setText(String.valueOf(item.getCount()));
        holder.binding.badge.setVisibility(android.view.View.VISIBLE);
        int badgeBg = item.hasExactMatch() ? R.color.search_badge_red_bg : R.color.search_badge_gray_bg;
        int badgeText = item.hasExactMatch() ? R.color.search_badge_red_text : R.color.search_badge_gray_text;
        holder.binding.badge.getBackground().setTint(ContextCompat.getColor(holder.view.getContext(), badgeBg));
        holder.binding.badge.setTextColor(ContextCompat.getColor(holder.view.getContext(), badgeText));
        setOnClickListener(holder, null);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
    }

    public static class ViewHolder extends Presenter.ViewHolder {

        private final AdapterCollectBinding binding;

        public ViewHolder(@NonNull AdapterCollectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
