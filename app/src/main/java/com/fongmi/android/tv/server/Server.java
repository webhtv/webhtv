package com.fongmi.android.tv.server;

import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.player.PlaybackRouteRegistry;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Util;

public class Server {

    private volatile PlaybackService service;
    private volatile Nano nano;
    private volatile boolean manage;
    private PlaybackRouteRegistry.Registration routeRegistration;

    private static class Loader {
        static volatile Server INSTANCE = new Server();
    }

    public static Server get() {
        return Loader.INSTANCE;
    }

    public PlaybackService getService() {
        return service;
    }

    public synchronized void setService(PlaybackService service) {
        this.service = service;
    }

    public synchronized void clearService(PlaybackService expected) {
        if (service == expected) service = null;
    }

    public boolean isRunning() {
        return nano != null;
    }

    public String getAddress() {
        return getAddress(false);
    }

    public String getAddress(int tab) {
        return getAddress(false) + "?tab=" + tab;
    }

    public String getAddress(String path) {
        return getAddress(true) + path;
    }

    public String getAddress(boolean local) {
        // 自愈：./ 与 clan:// 链接经 UrlUtil.convert 走到这里拼端口。端口无效说明服务
        // 没起来（如启动早期 start 失败被吞），此时现场补启动一次，避免拼出
        // http://127.0.0.1:-1/... 坏地址（PQ 源 jar/ext 由此加载失败）。start 幂等。
        if (Proxy.getPort() <= 0) start();
        return "http://" + (local ? "127.0.0.1" : Util.getIp()) + ":" + Proxy.getPort();
    }

    public synchronized void startManage() {
        manage = true;
        start();
    }

    public synchronized void stopManage() {
        manage = false;
        if (service == null) stop();
    }

    public synchronized void start() {
        if (nano != null) return;
        for (int i = 9978; i < 9999; i++) {
            try {
                nano = new Nano(i);
                nano.start(500);
                Proxy.set(i);
                if (routeRegistration != null) routeRegistration.close();
                routeRegistration = PlaybackRouteRegistry.registerAppService(i, PlaybackRouteRegistry.AppOwner.MAIN_SERVER);
                SpiderDebug.log("server", "started port=%d", i);
                break;
            } catch (Throwable e) {
                nano = null;
                // 静默吞异常曾让端口一直是 -1 且无从排查；至少把失败原因写进调试日志。
                SpiderDebug.log("server", "start failed port=%d error=%s", i, e.toString());
            }
        }
        if (nano == null) SpiderDebug.log("server", "start failed all ports 9978-9998");
    }

    public void stop() {
        Nano expected = nano;
        Task.execute(() -> {
            synchronized (this) {
                if (manage || service != null || nano != expected) return;
                stopNow();
            }
        });
    }

    public synchronized void shutdown() {
        manage = false;
        service = null;
        stopNow();
    }

    private void stopNow() {
        if (nano != null) nano.stop();
        nano = null;
        if (routeRegistration != null) routeRegistration.close();
        routeRegistration = null;
    }
}
