package com.github.tvbox.osc.ui.holder;

import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.bean.Vod;
import com.github.tvbox.osc.databinding.AdapterVodListBinding;
import com.github.tvbox.osc.ui.adapter.VodAdapter;
import com.github.tvbox.osc.ui.base.BaseVodHolder;
import com.github.tvbox.osc.utils.ImgUtil;

public class VodListHolder extends BaseVodHolder {

    private final VodAdapter.OnClickListener listener;
    private final AdapterVodListBinding binding;

    public VodListHolder(@NonNull AdapterVodListBinding binding, VodAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
    }

    @Override
    public void initView(Vod item) {
        binding.name.setText(item.getVodName());
        binding.remark.setText(item.getVodRemarks());
        binding.name.setVisibility(item.getNameVisible());
        binding.remark.setVisibility(item.getRemarkVisible());
        binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        binding.getRoot().setOnLongClickListener(v -> listener.onLongClick(item));
        ImgUtil.load(item.getVodName(), item.getVodPic(), binding.image, ImageView.ScaleType.FIT_CENTER, false);
    }
}
