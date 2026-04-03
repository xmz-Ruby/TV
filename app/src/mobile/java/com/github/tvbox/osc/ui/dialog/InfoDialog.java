package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import com.github.tvbox.osc.databinding.DialogInfoBinding;
import com.github.tvbox.osc.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Map;

public class InfoDialog {

    private final DialogInfoBinding binding;
    private final Listener callback;
    private AlertDialog dialog;
    private CharSequence title;
    private String header;
    private String url;
    private boolean copyable = true;

    public static InfoDialog create(Activity activity) {
        return new InfoDialog(activity);
    }

    public InfoDialog(Activity activity) {
        this.binding = DialogInfoBinding.inflate(LayoutInflater.from(activity));
        this.callback = (Listener) activity;
    }

    public InfoDialog title(CharSequence title) {
        this.title = title;
        return this;
    }

    public InfoDialog headers(Map<String, String> headers) {
        StringBuilder sb = new StringBuilder();
        for (String key : headers.keySet()) sb.append(key).append(" : ").append(headers.get(key)).append("\n");
        this.header = Util.substring(sb.toString());
        return this;
    }

    public InfoDialog url(String url) {
        this.url = url;
        return this;
    }

    public InfoDialog copyable(boolean copyable) {
        this.copyable = copyable;
        return this;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext()).setView(binding.getRoot()).create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        binding.title.setText(title);
        binding.url.setText(fixUrl());
        binding.header.setText(header);
        binding.url.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        binding.header.setVisibility(TextUtils.isEmpty(header) ? View.GONE : View.VISIBLE);
    }

    private void initEvent() {
        binding.url.setOnClickListener(copyable ? this::onShare : null);
        binding.url.setOnLongClickListener(copyable ? v -> onCopy(url) : null);
        binding.header.setOnLongClickListener(copyable ? v -> onCopy(header) : null);
        binding.url.setClickable(copyable);
        binding.url.setLongClickable(copyable);
        binding.header.setLongClickable(copyable);
    }

    private String fixUrl() {
        return TextUtils.isEmpty(url) ? "" : url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
    }

    private void onShare(View view) {
        callback.onShare(title);
        dialog.dismiss();
    }

    private boolean onCopy(String text) {
        Util.copy(text);
        return true;
    }

    public interface Listener {

        void onShare(CharSequence title);
    }
}
