package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.Diffable;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.UrlUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.gson.annotations.SerializedName;

import org.jupnp.model.meta.RemoteDevice;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Entity(indices = @Index(value = {"uuid", "name"}, unique = true))
public class Device implements Diffable<Device>, Comparable<Device> {

    @PrimaryKey(autoGenerate = true)
    @SerializedName("id")
    private Integer id;
    @SerializedName("uuid")
    private String uuid;
    @SerializedName("name")
    private String name;
    @SerializedName("ip")
    private String ip;
    @SerializedName("type")
    private int type;

    @Ignore
    @SerializedName("serial")
    private String serial;
    @Ignore
    @SerializedName("eth")
    private String eth;
    @Ignore
    @SerializedName("wlan")
    private String wlan;
    @Ignore
    @SerializedName("time")
    private long time;

    // uuid / 名字 / 序列号 / 有线无线 MAC / 设备形态在进程生命周期内不会变化，
    // 但原来每次 Device.get() 都要重新探测一遍：NetworkInterface.getByName("eth0"/"wlan0")
    // 两次、Settings.Secure 一次、Build 字段一次，再由 Server.getAddress() 遍历全部网卡。
    // 播放中的进度 tick 每秒都会经 PlaybackRecord.clientKey() 走到这里，
    // 于是主线程每秒被这几项设备探测各扫一次。这里只把稳定身份量缓存起来，
    // 会随网络变化的 ip（以及 time）仍然每次实时取值。
    private static final Object IDENTITY_LOCK = new Object();
    private static volatile boolean identityReady;
    private static String identityUuid;
    private static String identityName;
    private static String identitySerial;
    private static String identityEth;
    private static String identityWlan;
    private static int identityType;

    private static void ensureIdentity() {
        if (identityReady) return;
        synchronized (IDENTITY_LOCK) {
            if (identityReady) return;
            identitySerial = Util.getSerial();
            identityEth = Util.getMac("eth0");
            identityWlan = Util.getMac("wlan0");
            identityUuid = Util.getAndroidId();
            identityName = Util.getDeviceName();
            identityType = Product.getDeviceType();
            identityReady = true;
        }
    }

    /**
     * 把首次设备身份探测的成本提前付掉：ensureIdentity() 里的 Util.getSerial() 会 spawn
     * 一个 shell 子进程取 ro.serialno，另外还有两次网卡枚举和一次 Settings.Secure 读取，
     * 都是阻塞调用。它过去第一次发生在播放页进场窗口里（设备实测在主线程 tid 上抓到该
     * shell 命令），这里由 App 启动时的后台线程先走一遍，用户点进播放页时这份成本已经付完。
     * 只是提前触发同一段双检锁缓存，不改变任何取值逻辑与返回值语义。
     */
    public static void warmIdentity() {
        ensureIdentity();
    }

    /** 稳定设备身份里的 uuid，语义与 get().getUuid() 一致（空值归一为空串），但不做网络探测。 */
    public static String identityUuid() {
        ensureIdentity();
        return identityUuid == null ? "" : identityUuid;
    }

    public static Device get() {
        ensureIdentity();
        Device device = new Device();
        device.setTime(App.time());
        device.setSerial(identitySerial);
        device.setEth(identityEth);
        device.setWlan(identityWlan);
        device.setUuid(identityUuid);
        device.setName(identityName);
        device.setIp(Server.get().getAddress());
        device.setType(identityType);
        return device;
    }

    public static Device get(RemoteDevice item) {
        Device device = new Device();
        device.setUuid(item.getIdentity().getUdn().getIdentifierString());
        device.setName(item.getDetails().getFriendlyName());
        device.setType(2);
        return device;
    }

    public static Device objectFrom(String str) {
        return App.gson().fromJson(str, Device.class);
    }

    public static List<Device> getAll() {
        return AppDatabase.get().getDeviceDao().findAll();
    }

    public static void delete() {
        AppDatabase.get().getDeviceDao().delete();
    }

    public static void delete(Device device) {
        if (device != null) AppDatabase.get().getDeviceDao().delete(device.getUuid());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUuid() {
        return TextUtils.isEmpty(uuid) ? "" : uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return TextUtils.isEmpty(ip) ? "" : ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public void setEth(String eth) {
        this.eth = eth;
    }

    public void setWlan(String wlan) {
        this.wlan = wlan;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isLeanback() {
        return getType() == 0;
    }

    public boolean isMobile() {
        return getType() == 1;
    }

    public boolean isDLNA() {
        return getType() == 2;
    }

    public boolean isApp() {
        return isLeanback() || isMobile();
    }

    public String getHost() {
        return isDLNA() ? getUuid() : UrlUtil.host(getIp());
    }

    public Device save() {
        AppDatabase.get().getDeviceDao().insertOrUpdate(this);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Device it)) return false;
        return Objects.equals(getUuid(), it.getUuid());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getUuid());
    }

    @NonNull
    @Override
    public String toString() {
        return App.gson().toJson(this);
    }

    @Override
    public boolean isSameItem(Device other) {
        return equals(other);
    }

    @Override
    public boolean isSameContent(Device other) {
        return getName().equals(other.getName()) && getIp().equals(other.getIp()) && getType() == other.getType();
    }

    @Override
    public int compareTo(Device other) {
        return Comparator.comparingInt(Device::getType).thenComparing(Device::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Device::getUuid).compare(this, other);
    }
}
