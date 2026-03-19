package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.databinding.DialogAudioChannelBinding;
import com.github.tvbox.osc.impl.AudioChannelCallback;
import com.github.tvbox.osc.ui.adapter.AudioChannelAdapter;
import com.github.tvbox.osc.ui.custom.SpaceItemDecoration;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AudioChannelDialog implements AudioChannelAdapter.OnClickListener {

    private final DialogAudioChannelBinding binding;
    private final AudioChannelCallback callback;
    private final AlertDialog dialog;
    private final AudioChannelAdapter adapter;

    public static AudioChannelDialog create(Activity activity) {
        return new AudioChannelDialog(activity);
    }

    public AudioChannelDialog index(int index) {
        adapter.setSelect(index);
        return this;
    }

    public AudioChannelDialog(Activity activity) {
        this.callback = (AudioChannelCallback) activity;
        this.binding = DialogAudioChannelBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new AudioChannelAdapter(this);
    }

    public void show() {
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(adapter.getSelect()));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onItemClick(int item) {
        callback.setDefaultAudioChannel(item);
        dialog.dismiss();
    }
}
