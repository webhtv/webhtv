package com.fongmi.android.tv.event;

import org.greenrobot.eventbus.EventBus;

public record ServerEvent(Type type, String text, String name) {

    public static void search(String text) {
        EventBus.getDefault().post(new ServerEvent(Type.SEARCH, text));
    }

    public static void push(String text) {
        EventBus.getDefault().post(new ServerEvent(Type.PUSH, text));
    }

    public static void push(String text, String name) {
        EventBus.getDefault().post(new ServerEvent(Type.PUSH, text, name));
    }
 
    public static void setting(String text) {
        EventBus.getDefault().post(new ServerEvent(Type.SETTING, text));
    }

    public static void setting(String text, String name) {
        EventBus.getDefault().post(new ServerEvent(Type.SETTING, text, name));
    }

    private ServerEvent(Type type, String text) {
        this(type, text, "");
    }

    public enum Type {
        SEARCH, PUSH, SETTING
    }
}
