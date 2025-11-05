package com.github.tvbox.osc.ui.dialog;

import android.os.Handler;
import android.os.Looper;
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
    private Handler countdownHandler;
    private Runnable countdownRunnable;
    private int countdown = 5;

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

        // 初始化倒计时
        countdownHandler = new Handler(Looper.getMainLooper());
        updateCountdownText();
        startCountdown();
    }

    private void startCountdown() {
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                countdown--;
                if (countdown > 0) {
                    updateCountdownText();
                    countdownHandler.postDelayed(this, 1000);
                } else {
                    // 倒计时结束，自动换源
                    onAutoSwitch();
                }
            }
        };
        countdownHandler.postDelayed(countdownRunnable, 1000);
    }

    private void stopCountdown() {
        if (countdownHandler != null && countdownRunnable != null) {
            countdownHandler.removeCallbacks(countdownRunnable);
        }
    }

    private void updateCountdownText() {
        if (binding != null && binding.countdown != null) {
            binding.countdown.setText(countdown + "秒后自动换源...");
        }
    }

    @Override
    protected void initEvent() {
        binding.btnRetry.setOnClickListener(v -> onRetry());
        binding.btnQuality.setOnClickListener(v -> onQuality());
        binding.btnAutoSwitch.setOnClickListener(v -> onAutoSwitch());
    }

    private void onRetry() {
        stopCountdown();
        listener.onPlayErrorRetry();
        dismiss();
    }

    private void onQuality() {
        stopCountdown();
        listener.onPlayErrorSwitchQuality();
        dismiss();
    }

    private void onAutoSwitch() {
        stopCountdown();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopCountdown();
    }
}
