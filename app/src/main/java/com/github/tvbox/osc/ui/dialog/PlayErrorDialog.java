package com.github.tvbox.osc.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.databinding.DialogPlayErrorBinding;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class PlayErrorDialog extends BaseDialog {

    private DialogPlayErrorBinding binding;
    private Listener listener;
    private String errorMsg;
    private boolean hasMultiQuality;

    public static PlayErrorDialog create() {
        return new PlayErrorDialog();
    }

    public PlayErrorDialog() {
    }

    public PlayErrorDialog message(String msg) {
        this.errorMsg = msg;
        return this;
    }

    public PlayErrorDialog hasMultiQuality(boolean hasMulti) {
        this.hasMultiQuality = hasMulti;
        return this;
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) {
            if (f instanceof BottomSheetDialogFragment) {
                ((BottomSheetDialogFragment) f).dismiss();
            }
        }
        show(activity.getSupportFragmentManager(), null);
        this.listener = (Listener) activity;
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogPlayErrorBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (errorMsg != null && !errorMsg.isEmpty()) {
            binding.message.setText(errorMsg);
        }
        binding.btnQuality.setVisibility(hasMultiQuality ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void initEvent() {
        binding.btnRetry.setOnClickListener(v -> onRetry());
        binding.btnQuality.setOnClickListener(v -> onQuality());
        binding.btnAutoSwitch.setOnClickListener(v -> onAutoSwitch());
    }

    private void onRetry() {
        listener.onPlayErrorRetry();
        dismiss();
    }

    private void onQuality() {
        listener.onPlayErrorSwitchQuality();
        dismiss();
    }

    private void onAutoSwitch() {
        listener.onPlayErrorAutoSwitch();
        dismiss();
    }

    public interface Listener {
        void onPlayErrorRetry();
        void onPlayErrorSwitchQuality();
        void onPlayErrorAutoSwitch();
    }

    @Override
    public void onResume() {
        super.onResume();
        getDialog().getWindow().setLayout(ResUtil.dp2px(400), -1);
    }
}
