package com.onedongua.smartbrightness.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.onedongua.smartbrightness.databinding.ViewLogsBinding;
import com.onedongua.smartbrightness.databinding.ViewSettingsBinding;

import java.util.List;

public class MainPagerAdapter extends RecyclerView.Adapter<MainPagerAdapter.ViewHolder> {

    private final Context context;
    private final List<View> views;
    private final ViewSettingsBinding settingsBinding;
    private final ViewLogsBinding logsBinding;

    public MainPagerAdapter(Context context) {
        this.context = context;
        settingsBinding = ViewSettingsBinding.inflate(LayoutInflater.from(context), null, false);
        logsBinding = ViewLogsBinding.inflate(LayoutInflater.from(context), null, false);
        views = List.of(settingsBinding.getRoot(), logsBinding.getRoot());
    }

    public List<ViewBinding> getViewBindings() {
        return List.of(settingsBinding, logsBinding);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        FrameLayout container = new FrameLayout(context);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        return new ViewHolder(container);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        FrameLayout container = (FrameLayout) holder.itemView;

        // 先移除旧View
        container.removeAllViews();

        View view = views.get(position);

        // 如果已有父布局，需要先移除
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            ((ViewGroup) parent).removeView(view);
        }

        container.addView(view);
    }

    @Override
    public int getItemCount() {
        return views.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(View itemView) {
            super(itemView);
        }
    }
}