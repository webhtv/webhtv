package com.fongmi.android.tv.ui.adapter;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.impl.Diffable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public abstract class BaseDiffAdapter<T extends Diffable<T>, VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {

    protected final AsyncListDiffer<T> differ;

    /**
     * 列表数量变化后的回调。
     *
     * <p><b>为什么放在这里。</b>本仓库反复出现过同一类缺陷（共 5 处）：删除/清空之后宿主忘了
     * 驱动状态机，于是列表空了但页面留白——连空态都不出现。根因是"驱动状态机"这件事被留给了
     * 每个调用点，而调用点很容易漏（单条删除、删除全部、删除后又刷新，各是一个入口）。
     *
     * <p>所有变更最终都经过 {@code setItems(...)}，而它的回调在 diff **应用完成**后才触发
     * （{@code AsyncListDiffer} 是异步的，只有那时 {@code getItemCount()} 才是新值）。
     * 因此把回调挂在这里：宿主只需注册一次，之后无论从哪条路径改动列表，
     * 状态都会跟着列表走——不可能再忘。
     */
    public interface OnCountChanged {
        void onCountChanged(int count);
    }

    private OnCountChanged onCountChanged;

    public BaseDiffAdapter() {
        this.differ = new AsyncListDiffer<>(this, new BaseItemCallback<T>());
    }

    /** 注册列表数量变化回调；传 {@code null} 取消注册。 */
    public void setOnCountChanged(OnCountChanged listener) {
        this.onCountChanged = listener;
    }

    private void afterUpdate(Runnable runnable) {
        if (runnable != null) runnable.run();
        if (onCountChanged != null) onCountChanged.onCountChanged(getItemCount());
    }

    private boolean listsAreSame(List<T> oldList, List<T> newList) {
        if (oldList.size() != newList.size()) return false;
        for (int i = 0; i < oldList.size(); i++) {
            T oldItem = oldList.get(i);
            T newItem = newList.get(i);
            if (!oldItem.isSameItem(newItem) || !oldItem.isSameContent(newItem)) return false;
        }
        return true;
    }

    public T getItem(int position) {
        return differ.getCurrentList().get(position);
    }

    public List<T> getItems() {
        return differ.getCurrentList();
    }

    public void setItems(List<T> items) {
        setItems(items, () -> {});
    }

    public void setItems(List<T> items, Runnable runnable) {
        differ.submitList(Objects.requireNonNullElseGet(items, ArrayList::new), () -> afterUpdate(runnable));
    }

    public void setItems(List<T> items, Callback callback) {
        List<T> oldItems = getItems();
        List<T> newItems = Objects.requireNonNullElseGet(items, ArrayList::new);
        boolean hasChange = !listsAreSame(oldItems, newItems);
        if (!hasChange) {
            callback.onUpdateFinished(false);
            afterUpdate(null);
        } else differ.submitList(newItems, () -> {
            callback.onUpdateFinished(true);
            afterUpdate(null);
        });
    }

    public void add(T item) {
        add(item, null);
    }

    public void add(T item, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        current.add(item);
        setItems(current, runnable);
    }

    public void addAll(List<T> items) {
        addAll(items, null);
    }

    public void addAll(List<T> items, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        current.addAll(items);
        setItems(current, runnable);
    }

    public void sort(T item) {
        sort(item, null);
    }

    public void sort(T item, Runnable runnable) {
        List<T> current = Stream.concat(getItems().stream(), Stream.of(item)).distinct().sorted().toList();
        setItems(current, runnable);
    }

    public void sort(List<T> items) {
        sort(items, null);
    }

    public void sort(List<T> items, Runnable runnable) {
        List<T> current = Stream.concat(getItems().stream(), items.stream()).distinct().sorted().toList();
        setItems(current, runnable);
    }

    public void remove(T item) {
        remove(item, null);
    }

    public void remove(T item, Runnable runnable) {
        List<T> current = new ArrayList<>(getItems());
        if (current.remove(item)) setItems(current, runnable);
    }

    public void clear() {
        clear(null);
    }

    public void clear(Runnable runnable) {
        setItems(new ArrayList<>(), runnable);
    }

    @Override
    public int getItemCount() {
        return differ.getCurrentList().size();
    }

    @NonNull
    @Override
    public abstract VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType);

    @Override
    public abstract void onBindViewHolder(@NonNull VH holder, int position);

    public interface Callback {

        void onUpdateFinished(boolean hasChange);
    }
}
