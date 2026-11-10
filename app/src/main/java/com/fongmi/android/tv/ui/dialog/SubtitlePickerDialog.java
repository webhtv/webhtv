package com.fongmi.android.tv.ui.dialog;
 
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
 
import java.util.List;
 
/** 射手字幕候选列表选择器。纯 AlertDialog，不带布局文件。 */
public final class SubtitlePickerDialog {
 
    public interface Listener {
        void onPick(ShooterSubtitleHelper.Candidate candidate);
        void onEmpty();
    }
 
    /** 弹候选列表。空列表时自动回调 onEmpty 并 return。 */
    public static void show(Context context, List<ShooterSubtitleHelper.Candidate> items, Listener listener) {
        if (context == null || items == null || items.isEmpty()) {
            if (listener != null) listener.onEmpty();
            return;
        }
        String[] names = new String[items.size()];
        for (int i = 0; i < items.size(); i++) names[i] = items.get(i).displayName;
        new AlertDialog.Builder(context)
                .setTitle("射手字幕")
                .setItems(names, (DialogInterface.OnClickListener) (dialog, which) -> {
                    if (listener != null && which >= 0 && which < items.size())
                        listener.onPick(items.get(which));
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
