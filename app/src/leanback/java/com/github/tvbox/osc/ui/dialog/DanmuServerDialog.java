package com.github.tvbox.osc.ui.dialog;

import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.DialogDanmuServerBinding;
import com.github.tvbox.osc.event.ServerEvent;
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.ui.custom.CustomTextListener;
import com.github.tvbox.osc.utils.QRCode;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class DanmuServerDialog implements DialogInterface.OnDismissListener {

    public interface Callback {
        void onDanmuServerChanged(String url);
    }

    private final DialogDanmuServerBinding binding;
    private final Callback callback;
    private final AlertDialog dialog;
    private boolean append;

    public static DanmuServerDialog create(FragmentActivity activity, Callback callback) {
        return new DanmuServerDialog(activity, callback);
    }

    public DanmuServerDialog(FragmentActivity activity, Callback callback) {
        this.callback = callback;
        this.binding = DialogDanmuServerBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.append = true;
    }

    public void show() {
        initDialog();
        initView();
        initEvent();
    }

    private void initDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.55f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.setOnDismissListener(this);
        dialog.show();
    }

    private void initView() {
        String url = Setting.getDanmuHost();
        binding.text.setText(url);
        binding.text.setSelection(TextUtils.isEmpty(url) ? 0 : url.length());
        binding.code.setImageBitmap(QRCode.getBitmap(Server.get().getAddress(3), 200, 0));
        binding.info.setText(ResUtil.getString(R.string.push_info, Server.get().getAddress()).replace("，", "\n"));
    }

    private void initEvent() {
        EventBus.getDefault().register(this);
        binding.positive.setOnClickListener(this::onPositive);
        binding.negative.setOnClickListener(this::onNegative);
        binding.text.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.text.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) binding.positive.performClick();
            return true;
        });
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.text.append("ttp://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.length() == 0) {
            append = true;
        }
    }

    private void onPositive(View view) {
        String text = binding.text.getText().toString().trim();
        Setting.putDanmuHost(text);
        if (!TextUtils.isEmpty(text)) {
            DanmuServerHistoryDialog.addHistory(text);
        }
        if (callback != null) callback.onDanmuServerChanged(text);
        dialog.dismiss();
    }

    private void onNegative(View view) {
        dialog.dismiss();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.getType() != ServerEvent.Type.DANMU_SERVER) return;
        binding.text.setText(event.getText());
        binding.text.setSelection(binding.text.getText().length());
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        EventBus.getDefault().unregister(this);
    }
}
