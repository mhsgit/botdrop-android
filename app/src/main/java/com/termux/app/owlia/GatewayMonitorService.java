package com.termux.app.owlia;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.shared.logger.Logger;

/**
 * Foreground service that monitors and keeps the OpenClaw gateway alive.
 * 
 * Features:
 * - Runs as a foreground service with persistent notification
 * - Starts gateway if not running
 * - Monitors gateway process and restarts if it dies
 * - Handles Android Doze mode with partial wake lock
 * - Shows gateway status in notification
 */
public class GatewayMonitorService extends Service {

    private static final String LOG_TAG = "GatewayMonitorService";
    private static final int NOTIFICATION_ID = 1001;
    private static final int MONITOR_INTERVAL_MS = 30000; // 30 seconds
    private static final int RESTART_DELAY_MS = 5000; // 5 seconds

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mMonitorRunnable;
    private PowerManager.WakeLock mWakeLock;
    private OwliaService mOwliaService;
    private boolean mIsMonitoring = false;
    private String mCurrentStatus = "Starting...";

    @Override
    public void onCreate() {
        super.onCreate();
        Logger.logInfo(LOG_TAG, "Service created");

        // Initialize Owlia service for command execution
        mOwliaService = new OwliaService();

        // Acquire partial wake lock to handle Doze mode
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "BotDrop::GatewayMonitor"
            );
            mWakeLock.acquire(10*60*1000L /*10 minutes*/);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.logInfo(LOG_TAG, "Service started");

        // Start foreground service with notification
        Notification notification = buildNotification("BotDrop is running");
        startForeground(NOTIFICATION_ID, notification);

        // Start monitoring if not already monitoring
        if (!mIsMonitoring) {
            startMonitoring();
        }

        // START_STICKY ensures the service is restarted if killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Logger.logInfo(LOG_TAG, "Service destroyed");

        // Stop monitoring
        stopMonitoring();

        // Release wake lock
        if (mWakeLock != null && mWakeLock.isHeld()) {
            mWakeLock.release();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // We don't provide binding
    }

    /**
     * Start monitoring the gateway
     */
    private void startMonitoring() {
        mIsMonitoring = true;
        Logger.logInfo(LOG_TAG, "Starting gateway monitoring");

        mMonitorRunnable = new Runnable() {
            @Override
            public void run() {
                checkAndRestartGateway();
                if (mIsMonitoring) {
                    mHandler.postDelayed(this, MONITOR_INTERVAL_MS);
                }
            }
        };

        // Start immediately, then repeat at intervals
        mHandler.post(mMonitorRunnable);
    }

    /**
     * Stop monitoring the gateway
     */
    private void stopMonitoring() {
        mIsMonitoring = false;
        if (mMonitorRunnable != null) {
            mHandler.removeCallbacks(mMonitorRunnable);
        }
    }

    /**
     * Check if gateway is running and restart if needed
     */
    private void checkAndRestartGateway() {
        mOwliaService.isGatewayRunning(result -> {
            boolean isRunning = result.success && result.stdout.trim().equals("running");

            if (isRunning) {
                // Gateway is running - update status
                updateStatus("Running");
            } else {
                // Gateway is not running - restart it
                Logger.logInfo(LOG_TAG, "Gateway is not running, attempting restart");
                updateStatus("Restarting...");
                restartGateway();
            }
        });
    }

    /**
     * Restart the gateway
     */
    private void restartGateway() {
        mOwliaService.executeCommand("openclaw gateway start", result -> {
            if (result.success) {
                Logger.logInfo(LOG_TAG, "Gateway started successfully");
                mHandler.postDelayed(() -> updateStatus("Running"), RESTART_DELAY_MS);
            } else {
                Logger.logError(LOG_TAG, "Failed to start gateway: " + result.stderr);
                updateStatus("Failed to start");
                
                // Try again after delay
                mHandler.postDelayed(this::restartGateway, RESTART_DELAY_MS);
            }
        });
    }

    /**
     * Update the notification with current status
     */
    private void updateStatus(String status) {
        mCurrentStatus = status;
        Notification notification = buildNotification("Gateway: " + status);
        
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    /**
     * Build notification for foreground service
     */
    private Notification buildNotification(String contentText) {
        // Intent to open DashboardActivity when notification is tapped
        Intent notificationIntent = new Intent(this, DashboardActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
            this, DashboardActivity.NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle("💧 BotDrop")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Cannot be dismissed
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false);

        // For Android 14+, specify foreground service type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }
}
