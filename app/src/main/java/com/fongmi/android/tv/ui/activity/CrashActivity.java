package com.fongmi.android.tv.ui.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityCrashBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.AppVersion;
import com.fongmi.android.tv.utils.CrashLogSession;
import com.fongmi.android.tv.utils.CrashLogText;
import com.fongmi.android.tv.utils.CrashRestartMode;
import com.fongmi.android.tv.utils.PreviousProcessCrashRecovery;
import com.github.catvod.utils.Util;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

public class CrashActivity extends AppCompatActivity {

    private static final long ACK_JOIN_MS = 1500L;

    private ActivityCrashBinding mBinding;
    private String logUrl;
    private String localUrl;
    private volatile boolean closing;
    private volatile Thread ackWorker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityCrashBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackInvoked();
            }
        });
        initEvent();
        mBinding.restart.requestFocus();
        // Confirming the event touches the file-locked state; it must never run on the UI thread.
        ackWorker = new Thread(() -> PreviousProcessCrashRecovery.acknowledgeShown(this, getIntent()), "crash-ack");
        ackWorker.setDaemon(true);
        ackWorker.start();
    }

    /** A restart kills this process, so the pending confirmation has to land first. */
    private void awaitAck() {
        Thread worker = ackWorker;
        if (worker == null) return;
        try {
            worker.join(ACK_JOIN_MS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void initEvent() {
        mBinding.details.setOnClickListener(v -> showError());
        mBinding.restart.setOnClickListener(v -> restartApplication());
        mBinding.silentRestart.setOnClickListener(v -> {
            try {
                CrashRestartMode.arm();
            } catch (Throwable ignored) {
                showToast(R.string.crash_action_failed);
                return;
            }
            restartApplication();
        });
        mBinding.copyLogAddress.setOnClickListener(v -> copyLogAddress());
        mBinding.openLog.setOnClickListener(v -> openLog());
        mBinding.logAddress.setText(R.string.crash_log_preparing);
        setLogActionsEnabled(false);
        Thread worker = new Thread(this::startLogService, "crash-log-service");
        worker.setDaemon(true);
        worker.start();
    }

    protected void onBackInvoked() {
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    @Override
    protected void onDestroy() {
        if (isFinishing()) {
            closing = true;
            shutdownLogService();
        }
        super.onDestroy();
    }

    private void shutdownLogService() {
        try {
            Server.get().shutdown();
        } catch (Throwable ignored) {
        }
    }

    private void startLogService() {
        try {
            try {
                CrashLogSession.prepare(this);
            } catch (Throwable ignored) {
            }
            if (closing) return;
            Server.get().start();
            if (closing) {
                shutdownLogService();
                return;
            }
            if (!Server.get().isRunning()) {
                post(this::showLogFailure);
                return;
            }
            String host;
            try {
                host = Util.getIp();
            } catch (Throwable ignored) {
                host = "";
            }
            String lan = Server.get().getAddress(false) + "/debug/logs";
            String local = Server.get().getAddress("/debug/logs");
            String lanHost = host;
            post(() -> bindLogAddress(lanHost, lan, local));
        } catch (Throwable ignored) {
            post(this::showLogFailure);
        }
    }

    private void bindLogAddress(String host, String lan, String local) {
        localUrl = local;
        if (host == null || host.isEmpty()) {
            logUrl = local;
            mBinding.logAddress.setText(getString(R.string.crash_log_no_lan, CrashLogText.wrapUrlForDisplay(local)));
        } else {
            logUrl = lan;
            mBinding.logAddress.setText(CrashLogText.wrapUrlForDisplay(lan));
        }
        mBinding.logHint.setText(R.string.crash_log_hint);
        setLogActionsEnabled(true);
    }

    private void showLogFailure() {
        logUrl = null;
        localUrl = null;
        mBinding.logAddress.setText(R.string.crash_log_service_failed);
        mBinding.logHint.setText(R.string.crash_log_unavailable);
        setLogActionsEnabled(false);
    }

    private void setLogActionsEnabled(boolean enabled) {
        mBinding.copyLogAddress.setEnabled(enabled);
        mBinding.openLog.setEnabled(enabled);
    }

    private void copyLogAddress() {
        if (logUrl == null) return;
        try {
            ClipboardManager manager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager == null) return;
            manager.setPrimaryClip(ClipData.newPlainText(getString(R.string.crash_log_title), logUrl));
            showToast(R.string.crash_log_copied);
        } catch (Throwable ignored) {
            showToast(R.string.crash_log_unavailable);
        }
    }

    private void openLog() {
        if (localUrl == null) return;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(localUrl)));
        } catch (Throwable ignored) {
            showToast(R.string.debug_log_no_browser);
        }
    }

    private void showToast(int text) {
        try {
            Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        } catch (Throwable ignored) {
        }
    }

    private void post(Runnable action) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                action.run();
            } catch (Throwable ignored) {
            }
        });
    }

    private void restartApplication() {
        awaitAck();
        try {
            CaocConfig config;
            try {
                config = CustomActivityOnCrash.getConfigFromIntent(getIntent());
            } catch (Throwable ignored) {
                config = null;
            }
            if (config == null) config = new CaocConfig();
            Intent restart = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (restart == null) restart = new Intent(this, HomeActivity.class);
            CustomActivityOnCrash.restartApplicationWithIntent(this, restart, config);
        } catch (Throwable ignored) {
            showToast(R.string.crash_action_failed);
        }
    }

    private String errorDetails() {
        try {
            String recovery = PreviousProcessCrashRecovery.getRecoveryDetails(getIntent());
            if (recovery != null) return recovery;
        } catch (Throwable ignored) {
        }
        try {
            return CustomActivityOnCrash.getAllErrorDetailsFromIntent(this, getIntent());
        } catch (Throwable ignored) {
            try {
                String stack = CustomActivityOnCrash.getStackTraceFromIntent(getIntent());
                if (stack != null) return stack;
            } catch (Throwable unavailable) {
            }
            return getString(R.string.crash_log_unavailable);
        }
    }

    private void showError() {
        String version;
        try {
            version = AppVersion.fullName();
        } catch (Throwable ignored) {
            version = "unknown";
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.crash_details_title)
                .setMessage(getString(R.string.crash_details_message, version, errorDetails()))
                .setPositiveButton(R.string.crash_details_close, null)
                .show();
    }
}