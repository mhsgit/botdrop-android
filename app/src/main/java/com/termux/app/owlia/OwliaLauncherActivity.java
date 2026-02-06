package com.termux.app.owlia;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxInstaller;
import com.termux.shared.logger.Logger;

import org.json.JSONObject;

/**
 * Launcher activity that routes to the appropriate screen based on installation state.
 *
 * Routing logic:
 * 1. If bootstrap not extracted -> Wait for TermuxInstaller (show progress)
 * 2. If OpenClaw not installed -> SetupActivity (auto-install)
 * 3. If OpenClaw not configured -> SetupActivity (auth + channel setup)
 * 4. All ready -> DashboardActivity (TODO: implement later, for now go to TermuxActivity)
 */
public class OwliaLauncherActivity extends Activity {

    private static final String LOG_TAG = "OwliaLauncherActivity";
    private static final int REQUEST_CODE_NOTIFICATIONS = 1001;
    private static final int REQUEST_CODE_BATTERY_OPTIMIZATION = 1002;
    
    private TextView mStatusText;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mPermissionsRequested = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_owlia_launcher);

        mStatusText = findViewById(R.id.launcher_status_text);
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Request permissions when activity is in foreground (visible to user)
        if (!mPermissionsRequested) {
            mPermissionsRequested = true;
            // Delay slightly to ensure activity is fully visible
            mHandler.postDelayed(this::requestAllPermissions, 300);
        } else {
            // Already requested permissions, proceed with routing
            mHandler.postDelayed(this::checkAndRoute, 500);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cancel all pending callbacks to prevent memory leak
        mHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Request all required permissions upfront when activity is visible
     */
    private void requestAllPermissions() {
        // Step 1: Request notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Logger.logInfo(LOG_TAG, "Requesting notification permission");
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_NOTIFICATIONS);
                return; // Wait for result before continuing
            }
        }
        
        // Step 2: Request battery optimization exemption
        requestBatteryOptimizationExemption();
    }

    /**
     * Request exemption from battery optimization to allow background running
     */
    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Logger.logInfo(LOG_TAG, "Requesting battery optimization exemption");
                try {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, REQUEST_CODE_BATTERY_OPTIMIZATION);
                    return; // Wait for result before continuing
                } catch (Exception e) {
                    Logger.logError(LOG_TAG, "Failed to request battery optimization exemption: " + e.getMessage());
                }
            }
        }
        
        // All permissions handled, proceed with routing
        mHandler.postDelayed(this::checkAndRoute, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_CODE_NOTIFICATIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Logger.logInfo(LOG_TAG, "Notification permission granted");
            } else {
                Logger.logWarn(LOG_TAG, "Notification permission denied");
            }
            // Continue to battery optimization request
            requestBatteryOptimizationExemption();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_CODE_BATTERY_OPTIMIZATION) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Logger.logInfo(LOG_TAG, "Battery optimization exemption granted");
            } else {
                Logger.logWarn(LOG_TAG, "Battery optimization exemption denied");
            }
            // Proceed with routing regardless of result
            mHandler.postDelayed(this::checkAndRoute, 500);
        }
    }

    private void checkAndRoute() {
        // Check 1: Bootstrap installed?
        if (!OwliaService.isBootstrapInstalled()) {
            Logger.logInfo(LOG_TAG, "Bootstrap not ready, waiting for TermuxInstaller");
            mStatusText.setText("Setting up environment...");

            // Wait for bootstrap, then re-check
            TermuxInstaller.setupBootstrapIfNeeded(this, this::checkAndRoute);
            return;
        }

        // Check 2: OpenClaw installed?
        if (!OwliaService.isOpenclawInstalled()) {
            Logger.logInfo(LOG_TAG, "OpenClaw not installed, routing to SetupActivity");
            mStatusText.setText("Preparing installation...");

            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra(SetupActivity.EXTRA_START_STEP, SetupActivity.STEP_INSTALL);
            startActivity(intent);
            finish();
            return;
        }

        // Check 3: OpenClaw configured?
        if (!OwliaService.isOpenclawConfigured()) {
            Logger.logInfo(LOG_TAG, "OpenClaw not configured, routing to SetupActivity");
            mStatusText.setText("Setup required...");

            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra(SetupActivity.EXTRA_START_STEP, SetupActivity.STEP_API_KEY);
            startActivity(intent);
            finish();
            return;
        }

        // Check 4: Channel configured?
        if (!hasChannelConfigured()) {
            Logger.logInfo(LOG_TAG, "No channel configured, routing to channel setup");
            mStatusText.setText("Channel setup required...");

            Intent intent = new Intent(this, SetupActivity.class);
            intent.putExtra(SetupActivity.EXTRA_START_STEP, SetupActivity.STEP_CHANNEL);
            startActivity(intent);
            finish();
            return;
        }

        // All ready - go to DashboardActivity
        Logger.logInfo(LOG_TAG, "All ready, routing to dashboard");
        mStatusText.setText("Starting...");

        Intent intent = new Intent(this, DashboardActivity.class);
        startActivity(intent);
        finish();
    }

    /**
     * Check if a channel is configured
     */
    private boolean hasChannelConfigured() {
        try {
            JSONObject config = OwliaConfig.readConfig();
            if (config.has("channels")) {
                JSONObject channels = config.getJSONObject("channels");
                // Check if either telegram or discord is configured
                return channels.has("telegram") || channels.has("discord");
            }
            return false;
        } catch (Exception e) {
            Logger.logError(LOG_TAG, "Failed to check channel config: " + e.getMessage());
            return false;
        }
    }
}
