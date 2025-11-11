package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.DialogHistoryBinding;
import com.github.tvbox.osc.ui.custom.SpaceItemDecoration;
import com.github.tvbox.osc.utils.ResUtil;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;

public class DanmuServerHistoryDialog {

    private static final String KEY_HISTORY = "danmu_server_history";
    private static final Gson gson = new Gson();

    private final DialogHistoryBinding binding;
    private final AlertDialog dialog;
    private final DanmuServerAdapter adapter;
    private final Activity activity;

    public static DanmuServerHistoryDialog create(Activity activity) {
        return new DanmuServerHistoryDialog(activity);
    }

    public DanmuServerHistoryDialog(Activity activity) {
        this.activity = activity;
        this.binding = DialogHistoryBinding.inflate(LayoutInflater.from(activity));
        this.dialog = new MaterialAlertDialogBuilder(activity).setView(binding.getRoot()).create();
        this.adapter = new DanmuServerAdapter(activity);
    }

    public void show() {
        setRecyclerView();
        setDialog();
        binding.recycler.requestFocus();
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 16));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
        WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
        params.width = (int) (ResUtil.getScreenWidth() * 0.4f);
        dialog.getWindow().setAttributes(params);
        dialog.getWindow().setDimAmount(0);
        dialog.show();
    }

    public static void addHistory(String url) {
        if (TextUtils.isEmpty(url)) return;
        List<String> history = getHistory();
        if (!history.contains(url)) {
            history.add(0, url);
            if (history.size() > 20) {
                history = history.subList(0, 20);
            }
            saveHistory(history);
        }
    }

    private static List<String> getHistory() {
        try {
            String json = Prefers.getString(KEY_HISTORY);
            if (TextUtils.isEmpty(json)) return new ArrayList<>();
            return gson.fromJson(json, new TypeToken<List<String>>(){}.getType());
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static void saveHistory(List<String> history) {
        try {
            Prefers.put(KEY_HISTORY, gson.toJson(history));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void removeHistory(String url) {
        List<String> history = getHistory();
        history.remove(url);
        saveHistory(history);
    }

    private class DanmuServerAdapter extends RecyclerView.Adapter<DanmuServerAdapter.ViewHolder> {

        private final List<String> list;
        private final Activity activity;

        public DanmuServerAdapter(Activity activity) {
            this.activity = activity;
            this.list = getHistory();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_config, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String url = list.get(position);
            holder.text.setText(url);
            holder.text.setOnClickListener(v -> {
                Setting.putDanmuHost(url);
                if (activity instanceof DanmuServerDialog.Callback) {
                    ((DanmuServerDialog.Callback) activity).onDanmuServerChanged(url);
                }
                dialog.dismiss();
            });
            holder.delete.setOnClickListener(v -> {
                removeHistory(url);
                list.remove(position);
                notifyItemRemoved(position);
                if (list.isEmpty()) dialog.dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            private final TextView text;
            private final View delete;

            ViewHolder(View view) {
                super(view);
                text = view.findViewById(R.id.text);
                delete = view.findViewById(R.id.delete);
            }
        }
    }
}
