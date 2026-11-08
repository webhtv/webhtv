package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Trans;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Group {

    @SerializedName("channel")
    private List<Channel> channel;
    @SerializedName("name")
    private String name;
    @SerializedName("pass")
    private String pass;

    private boolean selected;
    private int position;
    private int width;

    /**
     * 界面语言下的分组显示名。
     * <p>
     * {@link #name} 同时是 keep 键（{@code Live.keep()} 会拼成 {@code 组名@@@频道名@@@线路}）、
     * 浏览树 id（{@code LG:组名}）与 {@link #equals} 的依据，必须与界面语言无关；简繁转换只写到
     * 本字段，由 {@link #getDisplayName()} 供 UI 展示。加 transient 是为了不进入 gson 序列化
     * （分组来自直播源自带的 JSON，导出时不应夹带译文）。
     */
    private transient String displayName;

    public Group(String name) {
        this(name, false);
    }

    public Group(String name, boolean pass) {
        this.name = name;
        this.position = -1;
        if (name.contains("_")) parse(pass);
        if (name.isEmpty()) setName(ResUtil.getString(R.string.setting_live));
    }

    public static List<Group> arrayFrom(String str) {
        Type listType = TypeToken.getParameterized(List.class, Group.class).getType();
        List<Group> items = App.gson().fromJson(str, listType);
        return items == null ? Collections.emptyList() : items;
    }

    public static Group create() {
        return create(R.string.setting_live);
    }

    public static Group create(@StringRes int resId) {
        return new Group(ResUtil.getString(resId));
    }

    public static Group create(String name, boolean pass) {
        return new Group(name, pass);
    }

    private void parse(boolean pass) {
        String[] splits = name.split("_", 2);
        setName(splits[0]);
        if (pass || splits.length == 1) return;
        setPass(splits[1]);
    }

    public List<Channel> getChannel() {
        return channel = channel == null ? new ArrayList<>() : channel;
    }

    public void setChannel(List<Channel> channel) {
        this.channel = channel;
    }

    public String getName() {
        return TextUtils.isEmpty(name) ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * 界面语言下的展示名；未做简繁转换时回退到 {@link #getName()}。
     */
    public String getDisplayName() {
        return TextUtils.isEmpty(displayName) ? getName() : displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPass() {
        return TextUtils.isEmpty(pass) ? "" : pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public boolean isHidden() {
        return !TextUtils.isEmpty(getPass());
    }

    public boolean isKeep() {
        return getName().equals(ResUtil.getString(R.string.keep));
    }

    public boolean isEmpty() {
        return getChannel().isEmpty();
    }

    public boolean skip() {
        return isKeep();
    }

    public int find(int number) {
        return getChannel().lastIndexOf(Channel.create(number));
    }

    public int find(String name) {
        return getChannel().lastIndexOf(Channel.create(name));
    }

    /**
     * 该分组是否就是给定的身份名。
     * <p>
     * 旧版本把简繁转换后的组名写进了 keep 键与浏览树 id，所以除了当前身份名，还要把候选名与
     * 配置值都做一次简繁归一后再比，避免升级后「上次频道」静默退回第一组第一个频道。
     * 归一显式传 {@code pass=false} 强制转换，否则简体态会短路返回原文。
     */
    public boolean matchesName(String name) {
        if (TextUtils.isEmpty(name)) return false;
        if (getName().equals(name) || getDisplayName().equals(name)) return true;
        return Trans.s2t(false, getName()).equals(Trans.s2t(false, name));
    }

    public void add(Channel channel) {
        Channel exist = getChannel().stream().filter(item -> item.equals(channel)).findFirst().orElse(null);
        if (exist != null) exist.getUrls().addAll(channel.getUrls());
        else getChannel().add(Channel.create(channel));
    }

    public Channel find(Channel channel) {
        Channel exist = getChannel().stream().filter(item -> item.equals(channel)).findFirst().orElse(null);
        if (exist != null) return exist;
        getChannel().add(channel);
        return channel;
    }

    public Channel current() {
        return getChannel().get(getPosition()).group(this);
    }

    /**
     * 只把简繁转换结果写到展示名，绝不改写 {@link #name}。
     * <p>
     * 旧实现直接改写 name，导致 keep 键（组名@@@频道名@@@线路）、浏览树 id（LG:组名）与
     * {@link #equals} 全部随界面语言漂移：切到繁体后「上次频道」静默回落到第一组第一个频道，
     * 切回简体又按另一套组名再找一遍。
     */
    public Group trans() {
        if (Trans.pass()) return this;
        this.displayName = Trans.s2t(name);
        return this;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (!(obj instanceof Group it)) return false;
        return getName().equals(it.getName()) && getChannel().size() == it.getChannel().size();
    }
}
