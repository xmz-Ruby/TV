package com.github.tvbox.osc.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.ActivitySettingDanmuBinding;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.utils.ResUtil;

public class SettingDanmuActivity extends BaseActivity {

    private ActivitySettingDanmuBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingDanmuBinding.inflate(getLayoutInflater());
    }

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingDanmuActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected void initView() {
        mBinding.danmuLoad.requestFocus();
        mBinding.danmuLoadText.setText(getSwitch(Setting.isDanmuLoad()));

        // 设置 Slider 初始值
        mBinding.danmuSpeedSlider.setValue(Setting.getDanmuSpeed());
        mBinding.danmuSizeSlider.setValue(Setting.getDanmuSize());
        mBinding.danmuLineSlider.setValue(Setting.getDanmuLine(3));
        mBinding.danmuAlphaSlider.setValue(Setting.getDanmuAlpha());

        // 更新标签显示
        updateSpeedLabel(Setting.getDanmuSpeed());
        updateSizeLabel(Setting.getDanmuSize());
        updateLineLabel(Setting.getDanmuLine(3));
        updateAlphaLabel(Setting.getDanmuAlpha());
    }

    @Override
    protected void initEvent() {
        mBinding.danmuLoad.setOnClickListener(this::setDanmuLoad);

        // 速度滑块监听
        mBinding.danmuSpeedSlider.addOnChangeListener((slider, value, fromUser) -> {
            int speed = (int) value;
            Setting.putDanmuSpeed(speed);
            updateSpeedLabel(speed);
        });

        // 大小滑块监听
        mBinding.danmuSizeSlider.addOnChangeListener((slider, value, fromUser) -> {
            float size = (float) (Math.round(value * 100.0) / 100.0);
            Setting.putDanmuSize(size);
            updateSizeLabel(size);
        });

        // 行数滑块监听
        mBinding.danmuLineSlider.addOnChangeListener((slider, value, fromUser) -> {
            int line = (int) value;
            Setting.putDanmuLine(line);
            updateLineLabel(line);
        });

        // 透明度滑块监听
        mBinding.danmuAlphaSlider.addOnChangeListener((slider, value, fromUser) -> {
            int alpha = (int) value;
            Setting.putDanmuAlpha(alpha);
            updateAlphaLabel(alpha);
        });
    }

    private void setDanmuLoad(View view) {
        Setting.putDanmuLoad(!Setting.isDanmuLoad());
        mBinding.danmuLoadText.setText(getSwitch(Setting.isDanmuLoad()));
    }

    private void updateSpeedLabel(int speed) {
        String[] speedTexts = {"慢", "正常", "快", "最快"};
        mBinding.danmuSpeedLabel.setText(getString(R.string.player_danmu_speed) + ": " + speedTexts[speed]);
    }

    private void updateSizeLabel(float size) {
        mBinding.danmuSizeLabel.setText(getString(R.string.player_danmu_size) + ": " + String.format("%.1f", size) + ResUtil.getString(R.string.times));
    }

    private void updateLineLabel(int line) {
        mBinding.danmuLineLabel.setText(getString(R.string.player_danmu_line) + ": " + line + ResUtil.getString(R.string.lines));
    }

    private void updateAlphaLabel(int alpha) {
        mBinding.danmuAlphaLabel.setText(getString(R.string.player_danmu_alpha) + ": " + alpha + "%");
    }


}
