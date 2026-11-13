package com.fongmi.android.tv.service;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;
import androidx.media3.common.Player;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.setting.AppBranding;
import com.fongmi.android.tv.dlna.CastAction;
import com.fongmi.android.tv.dlna.DLNAAvTransportImpl;
import com.fongmi.android.tv.dlna.DLNARenderingControlImpl;
import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;
import com.fongmi.android.tv.dlna.RenderState;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Util;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.binding.annotations.AnnotationLocalServiceBinder;
import org.jupnp.model.DefaultServiceManager;
import org.jupnp.model.meta.DeviceDetails;
import org.jupnp.model.meta.DeviceIdentity;
import org.jupnp.model.meta.LocalDevice;
import org.jupnp.model.meta.LocalService;
import org.jupnp.model.meta.ManufacturerDetails;
import org.jupnp.model.meta.ModelDetails;
import org.jupnp.model.types.UDADeviceType;
import org.jupnp.model.types.UDN;
import org.jupnp.support.avtransport.lastchange.AVTransportLastChangeParser;
import org.jupnp.support.connectionmanager.ConnectionManagerService;
import org.jupnp.support.lastchange.LastChangeAwareServiceManager;
import org.jupnp.support.renderingcontrol.lastchange.RenderingControlLastChangeParser;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class DLNARendererService extends AndroidUpnpServiceImpl implements ServiceConnection {

    private final IBinder binder = new LocalBinder();

    private volatile PlayerManager player;
    private volatile boolean isDlnaActive;

    private DLNARenderingControlImpl renderingControlImpl;
    private DLNAAvTransportImpl avTransportImpl;
    private PlaybackService playbackService;
    private Player currentListenerPlayer;
    private boolean bound;

    public static void start(Context context) {
        context.startService(new Intent(context, DLNARendererService.class));
    }

    public static void stop(Context context) {
        context.stopService(new Intent(context, DLNARendererService.class));
    }

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new DLNAServiceConfiguration();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Notification notification = new NotificationCompat.Builder(this, Notify.DEFAULT).setSmallIcon(AppBranding.notificationIcon()).setContentTitle(getString(R.string.app_name)).setSilent(true).build();
        startForeground(Notify.ID + 1, notification);
        upnpService.startup();
        registerLocalDevice();
    }

    private void registerLocalDevice() {
        LocalService<DLNAAvTransportImpl> avTransport = createAvTransport();
        LocalService<ConnectionManagerService> connManager = createConnectionManager();
        LocalService<DLNARenderingControlImpl> renderControl = createRenderingControl();
        DeviceIdentity identity = new DeviceIdentity(new UDN(UUID.nameUUIDFromBytes((Build.MANUFACTURER + Build.MODEL + "-MediaRenderer").getBytes(StandardCharsets.UTF_8))));
        UDADeviceType type = new UDADeviceType("MediaRenderer", 1);
        DeviceDetails details = new DeviceDetails(Util.getDeviceName(), new ManufacturerDetails(Build.MANUFACTURER), new ModelDetails(Build.MODEL, "DLNA Renderer", "1.0"));
        try {
            LocalDevice device = new LocalDevice(identity, type, details, new LocalService[]{avTransport, connManager, renderControl});
            upnpService.getRegistry().addDevice(device);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private LocalService<DLNAAvTransportImpl> createAvTransport() {
        avTransportImpl = new DLNAAvTransportImpl(this);
        LocalService<DLNAAvTransportImpl> service = new AnnotationLocalServiceBinder().read(DLNAAvTransportImpl.class);
        service.setManager(new LastChangeAwareServiceManager<>(service, new AVTransportLastChangeParser()) {
            @Override
            protected DLNAAvTransportImpl createServiceInstance() {
                return avTransportImpl;
            }
        });
        return service;
    }

    @SuppressWarnings("unchecked")
    private LocalService<ConnectionManagerService> createConnectionManager() {
        LocalService<ConnectionManagerService> service = new AnnotationLocalServiceBinder().read(ConnectionManagerService.class);
        service.setManager(new DefaultServiceManager<>(service, ConnectionManagerService.class));
        return service;
    }

    @SuppressWarnings("unchecked")
    private LocalService<DLNARenderingControlImpl> createRenderingControl() {
        renderingControlImpl = new DLNARenderingControlImpl(this);
        LocalService<DLNARenderingControlImpl> service = new AnnotationLocalServiceBinder().read(DLNARenderingControlImpl.class);
        service.setManager(new LastChangeAwareServiceManager<>(service, new RenderingControlLastChangeParser()) {
            @Override
            protected DLNARenderingControlImpl createServiceInstance() {
                return renderingControlImpl;
            }
        });
        return service;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        unbindPlaybackService();
        super.onDestroy();
    }

    private void bindPlaybackService() {
        if (bound) return;
        bound = true;
        bindService(new Intent(this, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION), this, BIND_AUTO_CREATE);
    }

    private void cleanupPlaybackRefs() {
        App.removeCallbacks(positionUpdater);
        if (currentListenerPlayer != null) {
            currentListenerPlayer.removeListener(listener);
            currentListenerPlayer = null;
        }
        if (playbackService != null) {
            playbackService.removePlayerCallback(playerCallback);
            playbackService = null;
        }
        player = null;
        if (avTransportImpl != null) avTransportImpl.setPlayerManager(null);
    }

    private void unbindPlaybackService() {
        if (!bound) return;
        bound = false;
        cleanupPlaybackRefs();
        unbindService(this);
    }

    public void setDlnaActive(boolean active) {
        isDlnaActive = active;
        if (avTransportImpl != null) avTransportImpl.setDlnaActive(active);
        if (active) bindPlaybackService();
        else {
            if (avTransportImpl != null) avTransportImpl.reset();
            unbindPlaybackService();
        }
    }

    public long consumePendingSeekMs() {
        return avTransportImpl != null ? avTransportImpl.consumePendingSeekMs() : -1;
    }

    public CastAction consumeNext() {
        return avTransportImpl != null ? avTransportImpl.popNext() : null;
    }

    public void notifyError() {
        if (avTransportImpl != null) avTransportImpl.fireStateChange(RenderState.STOPPED);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        if (!isDlnaActive) {
            unbindPlaybackService();
            return;
        }
        playbackService = ((PlaybackService.LocalBinder) binder).getService();
        playbackService.addPlayerCallback(playerCallback);
        player = playbackService.player();
        avTransportImpl.setPlayerManager(player);
        currentListenerPlayer = player.getPlayer();
        currentListenerPlayer.addListener(listener);
        App.post(positionUpdater, 1000);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        bound = false;
        cleanupPlaybackRefs();
    }

    private void notifyState() {
        if (avTransportImpl == null || player == null || !isDlnaActive) return;
        int state = player.getPlaybackState();
        if (state == Player.STATE_IDLE) return;
        long duration = player.getDuration();
        avTransportImpl.updatePositionCache(player.getPosition(), duration > 0 ? duration : -1);
        RenderState renderState = switch (state) {
            case Player.STATE_BUFFERING -> RenderState.PREPARING;
            case Player.STATE_READY -> player.isPlaying() ? RenderState.PLAYING : RenderState.PAUSED;
            case Player.STATE_ENDED -> avTransportImpl.hasNext() ? RenderState.PREPARING : RenderState.STOPPED;
            default -> null;
        };
        if (renderState != null) avTransportImpl.fireStateChange(renderState);
    }

    private final Runnable positionUpdater = new Runnable() {
        @Override
        public void run() {
            if (player != null && avTransportImpl != null && player.isPlaying()) avTransportImpl.updatePositionCache(player.getPosition(), player.getDuration());
            if (player != null) App.post(this, 1000);
        }
    };

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onPlaybackStateChanged(int playbackState) {
            notifyState();
        }

        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            notifyState();
        }
    };

    private final PlaybackService.PlayerCallback playerCallback = new PlaybackService.PlayerCallback() {
        @Override
        public void onPlayerRebuild(Player newPlayer, boolean resetVideoSurface) {
            if (currentListenerPlayer != null) currentListenerPlayer.removeListener(listener);
            currentListenerPlayer = newPlayer;
            newPlayer.addListener(listener);
        }
    };

    public class LocalBinder extends Binder {

        public DLNARendererService getService() {
            return DLNARendererService.this;
        }
    }
}
