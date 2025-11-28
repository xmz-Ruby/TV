package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.android.cast.dlna.dmc.DLNACastManager;
import com.android.cast.dlna.dmc.control.DeviceControl;
import com.android.cast.dlna.dmc.control.ServiceActionCallback;
import com.github.tvbox.osc.App;
import com.github.tvbox.osc.databinding.ActivityCastControlBinding;
import com.github.tvbox.osc.server.Server;
import com.github.tvbox.osc.ui.base.BaseActivity;
import com.github.tvbox.osc.utils.DLNADevice;
import com.github.tvbox.osc.utils.Notify;

import org.fourthline.cling.support.model.MediaInfo;
import org.fourthline.cling.support.model.PositionInfo;

import kotlin.Unit;

public class CastControlActivity extends BaseActivity {

    private ActivityCastControlBinding binding;
    private DeviceControl control;
    private Runnable progressUpdateTask;
    private boolean isUserSeeking = false;
    private boolean isPaused = false;
    private int consecutiveZeroDurationCount = 0;
    private static final int MAX_ZERO_DURATION_COUNT = 3; // 连续3次检测到时长为0则认为播放已停止

    public static void start(Context context, DeviceControl control) {
        Intent intent = new Intent(context, CastControlActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        // 将 control 存储到静态变量中传递
        currentControl = control;
        context.startActivity(intent);
    }

    private static DeviceControl currentControl;

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityCastControlBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        control = currentControl;
        currentControl = null;

        if (control == null) {
            Notify.show("投屏连接已断开");
            finish();
            return;
        }

        // 设置视频信息
        binding.title.setText(Server.get().getCurrentTitle());
        binding.episode.setText(Server.get().getCurrentEpisode());
        binding.url.setText(Server.get().getCastUrl());

        // 初始化进度条
        binding.progress.setMax(100);
        binding.progress.setProgress(0);
        binding.position.setText("00:00");
        binding.duration.setText("00:00");

        // 初始化播放按钮状态
        updatePlayPauseButton();

        // 启动进度更新
        startProgressUpdate();
    }

    @Override
    protected void initEvent() {
        // 返回按钮
        binding.back.setOnClickListener(v -> finish());

        // 播放/暂停按钮
        binding.playPause.setOnClickListener(v -> togglePlayPause());

        // 停止投屏按钮
        binding.stop.setOnClickListener(v -> stopCasting());

        // 进度条拖动
        binding.progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    long duration = Server.get().getCastDuration();
                    long position = (long) (duration * progress / 100.0);
                    binding.position.setText(formatTime(position));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                long duration = Server.get().getCastDuration();
                long position = (long) (duration * seekBar.getProgress() / 100.0);
                seekTo(position);
            }
        });
    }

    /**
     * 切换播放/暂停
     */
    private void togglePlayPause() {
        if (isPaused) {
            // 恢复播放
            control.play("1", new ServiceActionCallback<Unit>() {
                @Override
                public void onSuccess(Unit result) {
                    isPaused = false;
                    updatePlayPauseButton();
                    Notify.show("已恢复播放");
                }

                @Override
                public void onFailure(@NonNull String error) {
                    Notify.show("操作失败: " + error);
                }
            });
        } else {
            // 暂停
            control.pause(new ServiceActionCallback<Unit>() {
                @Override
                public void onSuccess(Unit result) {
                    isPaused = true;
                    updatePlayPauseButton();
                    Notify.show("已暂停");
                }

                @Override
                public void onFailure(@NonNull String error) {
                    Notify.show("操作失败: " + error);
                }
            });
        }
    }

    /**
     * 更新播放/暂停按钮图标
     */
    private void updatePlayPauseButton() {
        binding.playPause.setText(isPaused ? "播放" : "暂停");
    }

    /**
     * 跳转到指定位置
     */
    private void seekTo(long position) {
        control.seek(position, new ServiceActionCallback<Unit>() {
            @Override
            public void onSuccess(Unit result) {
                android.util.Log.d("CastControl", "Seek successful to: " + position);
            }

            @Override
            public void onFailure(@NonNull String error) {
                Notify.show("跳转失败: " + error);
            }
        });
    }

    /**
     * 停止投屏
     */
    private void stopCasting() {
        stopProgressUpdate();
        Server.get().setCasting(false, null);

        if (control != null) {
            control.stop(new ServiceActionCallback<Unit>() {
                @Override
                public void onSuccess(Unit result) {
                    android.util.Log.d("CastControl", "Stop successful");
                }

                @Override
                public void onFailure(@NonNull String error) {
                    android.util.Log.e("CastControl", "Stop failed: " + error);
                }
            });
        }

        // 断开 DLNA 设备连接
        DLNADevice.get().disconnect();
        Notify.show("已停止投屏");
        finish();
    }

    /**
     * 启动定期更新播放进度
     */
    private void startProgressUpdate() {
        progressUpdateTask = new Runnable() {
            @Override
            public void run() {
                if (control != null) {
                    // 查询当前播放位置
                    control.getPositionInfo(new ServiceActionCallback<PositionInfo>() {
                        @Override
                        public void onSuccess(PositionInfo positionInfo) {
                            // 查询总时长
                            control.getMediaInfo(new ServiceActionCallback<MediaInfo>() {
                                @Override
                                public void onSuccess(MediaInfo mediaInfo) {
                                    // 从 PositionInfo 和 MediaInfo 中提取时间
                                    long position = parseTime(positionInfo.getRelTime());
                                    long duration = parseTime(mediaInfo.getMediaDuration());

                                    // 检测播放是否已停止
                                    if (duration == 0) {
                                        consecutiveZeroDurationCount++;
                                        android.util.Log.d("CastControl", "Detected zero duration, count: " + consecutiveZeroDurationCount);

                                        if (consecutiveZeroDurationCount >= MAX_ZERO_DURATION_COUNT) {
                                            android.util.Log.d("CastControl", "Receiver stopped playback, closing control activity");
                                            runOnUiThread(() -> {
                                                Notify.show("投屏已停止");
                                                finish();
                                            });
                                            return;
                                        }
                                    } else {
                                        // 重置计数器
                                        consecutiveZeroDurationCount = 0;
                                    }

                                    // 更新到 Server
                                    Server.get().updateCastProgress(position, duration);

                                    // 更新 UI
                                    runOnUiThread(() -> updateProgress(position, duration));
                                }

                                @Override
                                public void onFailure(@NonNull String error) {
                                    android.util.Log.e("CastControl", "Failed to get duration: " + error);
                                }
                            });
                        }

                        @Override
                        public void onFailure(@NonNull String error) {
                            android.util.Log.e("CastControl", "Failed to get position: " + error);
                        }
                    });

                    // 每 1 秒更新一次
                    App.post(progressUpdateTask, 1000);
                }
            }
        };

        // 首次延迟 500ms 后开始更新
        App.post(progressUpdateTask, 500);
    }

    /**
     * 解析时间字符串 (格式: HH:MM:SS 或 HH:MM:SS.mmm)
     */
    private long parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty() || "NOT_IMPLEMENTED".equals(timeStr)) {
            return 0;
        }

        try {
            // 移除毫秒部分
            if (timeStr.contains(".")) {
                timeStr = timeStr.substring(0, timeStr.indexOf("."));
            }

            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(parts[2]);
                return (hours * 3600 + minutes * 60 + seconds) * 1000;
            }
        } catch (Exception e) {
            android.util.Log.e("CastControl", "Failed to parse time: " + timeStr, e);
        }

        return 0;
    }

    /**
     * 停止定期更新播放进度
     */
    private void stopProgressUpdate() {
        if (progressUpdateTask != null) {
            App.removeCallbacks(progressUpdateTask);
            progressUpdateTask = null;
        }
    }

    /**
     * 更新进度显示
     */
    private void updateProgress(long position, long duration) {
        binding.position.setText(formatTime(position));
        binding.duration.setText(formatTime(duration));

        // 只在用户不拖动时更新进度条
        if (!isUserSeeking && duration > 0) {
            int progress = (int) (position * 100 / duration);
            binding.progress.setProgress(progress);
        }
    }

    /**
     * 格式化时间显示
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        seconds = seconds % 60;
        minutes = minutes % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // 停止进度更新
        stopProgressUpdate();

        // 停止 DLNA 播放
        if (control != null) {
            android.util.Log.d("CastControl", "Stopping DLNA playback on activity destroy");
            try {
                control.stop(new ServiceActionCallback<Unit>() {
                    @Override
                    public void onSuccess(Unit result) {
                        android.util.Log.d("CastControl", "DLNA playback stopped successfully");
                    }

                    @Override
                    public void onFailure(@NonNull String error) {
                        android.util.Log.e("CastControl", "Failed to stop DLNA playback: " + error);
                    }
                });
            } catch (Exception e) {
                android.util.Log.e("CastControl", "Error stopping DLNA playback", e);
            }
        }

        // 断开所有 DLNA 连接
        try {
            DLNADevice.get().disconnect();
            android.util.Log.d("CastControl", "DLNA devices disconnected");
        } catch (Exception e) {
            android.util.Log.e("CastControl", "Error disconnecting DLNA devices", e);
        }

        // 清除投屏状态
        Server.get().setCasting(false, null);

        android.util.Log.d("CastControl", "CastControlActivity destroyed, resources cleaned up");
    }
}
