package com.github.tvbox.osc.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.Setting;
import com.github.tvbox.osc.databinding.AdapterConfigBinding;
import com.github.tvbox.osc.databinding.DialogHistoryBinding;
import com.github.tvbox.osc.ui.custom.SpaceItemDecoration;
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
    private final Callback callback;

    public interface Callback {
        void onDanmuServerChanged(String url);
    }

    public static DanmuServerHistoryDialog create(Fragment fragment, Callback callback) {
        return new DanmuServerHistoryDialog(fragment, callback);
    }

    public DanmuServerHistoryDialog(Fragment fragment, Callback callback) {
        this.callback = callback;
        this.binding = DialogHistoryBinding.inflate(LayoutInflater.from(fragment.getContext()));
        this.dialog = new MaterialAlertDialogBuilder(fragment.getActivity()).setView(binding.getRoot()).create();
        this.adapter = new DanmuServerAdapter();
    }

    public void show() {
        setRecyclerView();
        setDialog();
    }

    private void setRecyclerView() {
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setAdapter(adapter);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
    }

    private void setDialog() {
        if (adapter.getItemCount() == 0) return;
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

        public DanmuServerAdapter() {
            this.list = getHistory();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            String url = list.get(position);
            holder.binding.text.setText(url);
            holder.binding.text.setOnClickListener(v -> {
                Setting.putDanmuHost(url);
                if (callback != null) callback.onDanmuServerChanged(url);
                dialog.dismiss();
            });
            holder.binding.delete.setOnClickListener(v -> {
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
            private final AdapterConfigBinding binding;

            ViewHolder(AdapterConfigBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }
}
