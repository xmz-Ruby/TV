package com.github.tvbox.osc.ui.dialog;

import android.view.LayoutInflater;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.DialogDanmuSettingBinding;
import com.github.tvbox.osc.impl.DanmuSettingCallback;
import com.github.tvbox.osc.utils.KeyUtil;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class DanmuSettingDialog {

    private final DialogDanmuSettingBinding binding;
    private final DanmuSettingCallback callback;
    private final AlertDialog dialog;

    public static DanmuSettingDialog create(FragmentActivity activity) {
        return new DanmuSettingDialog(activity);
    }

    public DanmuSettingDialog(FragmentActivity activity) {
        this.callback = (DanmuSettingCallback) activity;
        this.binding = DialogDanmuSettingBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.show();
    }

    private void initView() {
        // 设置弹幕开关状态
        updateDanmuLoadText();

        // 设置当前值
        binding.speedSlider.setValue(Setting.getDanmuSpeed());
        binding.sizeSlider.setValue(Setting.getDanmuSize());
        binding.lineSlider.setValue(Setting.getDanmuLine(3));
        binding.alphaSlider.setValue(Setting.getDanmuAlpha());

        // 更新标签显示
        updateSpeedLabel(Setting.getDanmuSpeed());
        updateSizeLabel(Setting.getDanmuSize());
        updateLineLabel(Setting.getDanmuLine(3));
        updateAlphaLabel(Setting.getDanmuAlpha());
    }

    private void initEvent() {
        // 速度滑块监听
        binding.speedSlider.addOnChangeListener((slider, value, fromUser) -> {
            int speed = (int) value;
            Setting.putDanmuSpeed(speed);
            updateSpeedLabel(speed);
            callback.onDanmuSettingChanged();
        });

        // 大小滑块监听
        binding.sizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            float size = (float) (Math.round(value * 100.0) / 100.0);
            Setting.putDanmuSize(size);
            updateSizeLabel(size);
            callback.onDanmuSettingChanged();
        });

        // 行数滑块监听
        binding.lineSlider.addOnChangeListener((slider, value, fromUser) -> {
            int line = (int) value;
            Setting.putDanmuLine(line);
            updateLineLabel(line);
            callback.onDanmuSettingChanged();
        });

        // 透明度滑块监听
        binding.alphaSlider.addOnChangeListener((slider, value, fromUser) -> {
            int alpha = (int) value;
            Setting.putDanmuAlpha(alpha);
            updateAlphaLabel(alpha);
            callback.onDanmuSettingChanged();
        });

        // 弹幕开关按钮
        binding.danmuLoadButton.setOnClickListener(v -> toggleDanmuLoad());
        binding.danmuLoadButton.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && KeyUtil.isEnterKey(event)) {
                toggleDanmuLoad();
                return true;
            }
            return false;
        });

        // 关闭按钮
        binding.closeButton.setOnClickListener(v -> dialog.dismiss());
        binding.closeButton.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == android.view.KeyEvent.ACTION_DOWN && KeyUtil.isEnterKey(event)) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
    }

    private void toggleDanmuLoad() {
        boolean wasDisabled = !Setting.isDanmuLoad();
        Setting.putDanmuLoad(!Setting.isDanmuLoad());
        updateDanmuLoadText();
        callback.onDanmuSettingChanged();

        // 如果从关到开，关闭弹层并打开弹幕搜索框
        if (wasDisabled && Setting.isDanmuLoad()) {
            dialog.dismiss();
            callback.onDanmuLoadEnabled();
        }
    }

    private void updateDanmuLoadText() {
        binding.danmuLoadText.setText(Setting.isDanmuLoad() ? "开" : "关");
    }

    private void updateSpeedLabel(int speed) {
        String[] speedTexts = {"慢", "正常", "快", "最快"};
        binding.speedLabel.setText("速度: " + speedTexts[speed]);
    }

    private void updateSizeLabel(float size) {
        binding.sizeLabel.setText("大小: " + String.format("%.1f", size));
    }

    private void updateLineLabel(int line) {
        binding.lineLabel.setText("行数: " + line + ResUtil.getString(R.string.lines));
    }

    private void updateAlphaLabel(int alpha) {
        binding.alphaLabel.setText("透明度: " + alpha + "%");
    }
}
