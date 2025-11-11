package com.github.tvbox.osc.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.DialogConfigBinding;
import com.github.tvbox.osc.ui.custom.CustomTextListener;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmuServerDialog {

    private final DialogConfigBinding binding;
    private final Callback callback;
    private final Fragment fragment;
    private AlertDialog dialog;
    private boolean append;

    public interface Callback {
        void onDanmuServerChanged(String url);
    }

    public static DanmuServerDialog create(Fragment fragment, Callback callback) {
        return new DanmuServerDialog(fragment, callback);
    }

    public DanmuServerDialog(Fragment fragment, Callback callback) {
        this.fragment = fragment;
        this.callback = callback;
        this.binding = DialogConfigBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog = new MaterialAlertDialogBuilder(binding.getRoot().getContext())
                .setTitle(R.string.setting_danmu_host)
                .setView(binding.getRoot())
                .setPositiveButton(R.string.dialog_positive, this::onPositive)
                .setNegativeButton(R.string.dialog_negative, this::onNegative)
                .create();
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    private void initView() {
        String host = Setting.getDanmuHost();
        binding.input.setVisibility(android.view.View.GONE);
        binding.url.setText(host);
        binding.choose.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
        binding.url.setSelection(TextUtils.isEmpty(host) ? 0 : host.length());
    }

    private void initEvent() {
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
            return true;
        });
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.length() == 0) {
            append = true;
        }
    }

    private void onPositive(DialogInterface dialog, int which) {
        String text = binding.url.getText().toString().trim();
        Setting.putDanmuHost(text);
        if (!TextUtils.isEmpty(text)) {
            DanmuServerHistoryDialog.addHistory(text);
        }
        if (callback != null) callback.onDanmuServerChanged(text);
        dialog.dismiss();
    }

    private void onNegative(DialogInterface dialog, int which) {
        dialog.dismiss();
    }
}
