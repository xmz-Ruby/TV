package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.databinding.DialogMenuBinding;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.KeepActivity;
import com.github.tvbox.osc.ui.activity.LiveActivity;
import com.github.tvbox.osc.ui.activity.PushActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.MenuAdapter;
import com.github.tvbox.osc.ui.custom.SpaceItemDecoration;
import com.github.tvbox.osc.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MenuDialog implements MenuAdapter.OnClickListener {
    private final DialogMenuBinding binding;
    private final MenuAdapter adapter;
    private final AlertDialog dialog;

    private final Activity activity;

    public static MenuDialog create(Activity activity) {
        return new MenuDialog(activity);
    }

    public MenuDialog(Activity activity) {
        String[] items = ResUtil.getStringArray(R.array.select_home_menu_key);
        List<String> mItems = new ArrayList<>(Arrays.asList(items));
        mItems.remove(0);
        this.adapter = new MenuAdapter(this, mItems);
        this.activity = activity;
        this.binding = DialogMenuBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
    }

    public void show() {
        initView();
    }

    private int getCount() {
        return 2;
    }

    private float getWidth() {
        return 0.4f + (getCount() - 1) * 0.2f;
    }

    private void initView() {
        setRecyclerView();
        setDialog();
        binding.recycler.requestFocus();
    }

    private void setRecyclerView() {
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(getCount(), 16));
        binding.recycler.setLayoutManager(new GridLayoutManager(dialog.getContext(), getCount()));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(0));

    }

    private void setDialog() {
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * getWidth());
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    @Override
    public void onItemClick(int position) {
        if (dialog != null) dialog.dismiss();
        if (position == 0 && activity instanceof HomeActivity) SiteDialog.create(activity).show();
        else if (position == 1 && activity instanceof HomeActivity) HistoryDialog.create(activity).type(0).show();
        else if (position == 2) LiveActivity.start(activity);
        else if (position == 3) HistoryActivity.start(activity);
        else if (position == 4) SearchActivity.start(activity);
        else if (position == 5) PushActivity.start(activity);
        else if (position == 6) KeepActivity.start(activity);
        else if (position == 7) SettingActivity.start(activity);
    }

}
