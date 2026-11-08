package androidx.media3.mpvplayer;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.player.cache.DiskCacheCapacityPolicy;
import com.fongmi.android.tv.utils.FileUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide coordinator for the MPV/IJK HLS proxy cache.
 *
 * <p>The coordinator only guards local cache state. Network requests are deliberately kept
 * outside its lock. A reservation accounts for the bytes that have not yet reached the temp
 * file, while the directory scan accounts for bytes already present on disk.</p>
 */
public final class MpvHlsCacheCoordinator {

    private static final long META_RESERVATION_BYTES = 256;
    private static final long DEFAULT_CIRCUIT_RETRY_DELAY_MS = 30_000;
    private static final long DEFAULT_ORPHAN_AGE_MS = 5 * 60_000;
    private static final String CACHE_SUFFIX = ".bin";
    private static final String META_SUFFIX = ".meta";
    private static final String TEMP_SUFFIX = ".tmp";

    private static final Map<String, MpvHlsCacheCoordinator> SHARED = new ConcurrentHashMap<>();
    private static final AtomicLong NEXT_CLIENT_ID = new AtomicLong();
    private static final AtomicLong NEXT_TEMP_ID = new AtomicLong();

    private final File directory;
    private final StorageProbe storageProbe;
    private final Clock clock;
    private final long circuitRetryDelayMs;
    private final long orphanAgeMs;
    private final Object lock = new Object();
    private final Map<String, WriteReservation> writers = new HashMap<>();
    private final Map<String, Integer> readers = new HashMap<>();
    private final Map<Long, ClientLease> clients = new HashMap<>();
    private final Map<Long, Runnable> circuitListeners = new HashMap<>();

    private boolean circuitOpen;
    private long circuitOpenedAtMs;

    public static MpvHlsCacheCoordinator shared(File directory) {
        String key = canonicalPath(directory);
        return SHARED.computeIfAbsent(key, ignored -> new MpvHlsCacheCoordinator(directory));
    }

    public MpvHlsCacheCoordinator(File directory) {
        this(directory, MpvHlsCacheCoordinator::probeStorage, System::currentTimeMillis,
                DEFAULT_CIRCUIT_RETRY_DELAY_MS, DEFAULT_ORPHAN_AGE_MS);
    }

    MpvHlsCacheCoordinator(File directory, StorageProbe storageProbe, Clock clock,
                           long circuitRetryDelayMs, long orphanAgeMs) {
        this.directory = directory;
        this.storageProbe = storageProbe;
        this.clock = clock;
        this.circuitRetryDelayMs = Math.max(0, circuitRetryDelayMs);
        this.orphanAgeMs = Math.max(0, orphanAgeMs);
        recoverOrphans();
    }

    public ClientLease registerClient(long configuredCapacityBytes, @Nullable Runnable onCircuitOpen) {
        long id = NEXT_CLIENT_ID.incrementAndGet();
        ClientLease lease = new ClientLease(id, Math.max(0, configuredCapacityBytes));
        synchronized (lock) {
            clients.put(id, lease);
            if (onCircuitOpen != null) circuitListeners.put(id, onCircuitOpen);
        }
        return lease;
    }

    public ReservationDecision tryReserve(String key, File file, long expectedBytes,
                                          long configuredCapacityBytes, WriterType type) {
        if (key == null || key.isEmpty() || file == null || expectedBytes <= 0) {
            return denied(DenyReason.INVALID_REQUEST);
        }
        synchronized (lock) {
            long configured = effectiveConfiguredCapacityLocked(configuredCapacityBytes);
            if (configured <= 0) return denied(DenyReason.CACHE_DISABLED);
            if (file.isFile() && file.length() > 0) return denied(DenyReason.ALREADY_CACHED);
            if (file.isFile()) {
                // A zero-length final file is an incomplete artifact from an older writer.
                // It is never exposed as a readable cache object.
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                deleteMeta(file);
            }
            if (writers.containsKey(key)) return denied(DenyReason.DUPLICATE_KEY);
            if (!ensureDirectoryLocked()) return denied(DenyReason.DIRECTORY_UNAVAILABLE);

            if (circuitOpen && !circuitCanRecoverLocked(configured, expectedBytes)) {
                return denied(DenyReason.CIRCUIT_OPEN);
            }

            for (int attempt = 0; attempt < 3; attempt++) {
                CapacitySnapshot snapshot = snapshotLocked(configured);
                if (snapshot.policy().state() == DiskCacheCapacityPolicy.State.UNAVAILABLE) {
                    return denied(DenyReason.STORAGE_UNAVAILABLE, snapshot);
                }
                if (snapshot.policy().state() == DiskCacheCapacityPolicy.State.DISABLED) {
                    return denied(DenyReason.CACHE_DISABLED, snapshot);
                }
                long reservationBytes = reservationBytes(expectedBytes);
                long writable = subtractNonNegative(snapshot.policy().newWriteBudgetBytes(), snapshot.reservedBytes());
                long need = Math.max(0, reservationBytes - writable);
                long reclaim = Math.max(snapshot.policy().reclaimBytes(), need);
                if (reclaim > 0) {
                    long deleted = pruneLocked(reclaim);
                    if (deleted > 0) continue;
                    return denied(snapshot.policy().reclaimBytes() > 0 ? DenyReason.RECLAIM_REQUIRED : DenyReason.NO_GROWTH, snapshot);
                }
                if (writable < reservationBytes) {
                    return denied(DenyReason.NO_GROWTH, snapshot);
                }
                File temp = tempFileLocked(file);
                WriteReservation reservation = new WriteReservation(this, key, file, temp,
                        expectedBytes, reservationBytes, type);
                writers.put(key, reservation);
                if (circuitOpen) {
                    circuitOpen = false;
                    circuitOpenedAtMs = 0;
                }
                return new ReservationDecision(reservation, DenyReason.NONE, snapshot);
            }
            return denied(DenyReason.NO_GROWTH);
        }
    }

    public boolean canStartPreload(long configuredCapacityBytes) {
        return preloadSnapshot(configuredCapacityBytes).allowed();
    }

    public PreloadCapacitySnapshot preloadSnapshot(long configuredCapacityBytes) {
        synchronized (lock) {
            long configured = effectiveConfiguredCapacityLocked(configuredCapacityBytes);
            CapacitySnapshot snapshot = snapshotLocked(configured);
            if (configured <= 0 || circuitOpen) {
                return new PreloadCapacitySnapshot(snapshot, false);
            }
            if (snapshot.policy().state() == DiskCacheCapacityPolicy.State.UNAVAILABLE
                    || snapshot.policy().state() == DiskCacheCapacityPolicy.State.DISABLED) {
                return new PreloadCapacitySnapshot(snapshot, false);
            }
            boolean allowed = snapshot.policy().newWriteBudgetBytes()
                    > snapshot.reservedBytes() || hasEvictableFileLocked();
            return new PreloadCapacitySnapshot(snapshot, allowed);
        }
    }

    public boolean isKeyBusyOrCached(String key, File file) {
        synchronized (lock) {
            return writers.containsKey(key) || (file != null && file.isFile() && file.length() > 0);
        }
    }

    @Nullable
    public ReadLease openRead(File file) throws IOException {
        if (file == null) return null;
        synchronized (lock) {
            if (!file.isFile() || file.length() <= 0) return null;
            String key = file.getName();
            FileInputStream input;
            try {
                input = new FileInputStream(file);
            } catch (IOException error) {
                return null;
            }
            readers.put(key, readers.getOrDefault(key, 0) + 1);
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(clock.now());
            return new ReadLease(key, input);
        }
    }

    public void prune(long configuredCapacityBytes) {
        synchronized (lock) {
            long configured = effectiveConfiguredCapacityLocked(configuredCapacityBytes);
            if (configured <= 0) return;
            CapacitySnapshot snapshot = snapshotLocked(configured);
            if (snapshot.policy().state() == DiskCacheCapacityPolicy.State.UNAVAILABLE
                    || snapshot.policy().state() == DiskCacheCapacityPolicy.State.DISABLED) return;
            if (snapshot.policy().reclaimBytes() > 0) pruneLocked(snapshot.policy().reclaimBytes());
        }
    }

    public long cacheBytes() {
        synchronized (lock) {
            return physicalBytesLocked();
        }
    }

    public boolean clearIfIdle() {
        synchronized (lock) {
            if (!writers.isEmpty() || !readers.isEmpty() || !clients.isEmpty()) return false;
            File[] files = directory.listFiles();
            if (files == null) return !directory.exists();
            boolean success = true;
            for (File file : files) {
                if (!file.isFile()) continue;
                if (!file.delete()) success = false;
            }
            if (success) circuitOpen = false;
            return success;
        }
    }

    public long effectiveCapacityBytes(long configuredCapacityBytes) {
        synchronized (lock) {
            long configured = effectiveConfiguredCapacityLocked(configuredCapacityBytes);
            return snapshotLocked(configured).policy().effectiveCapacityBytes();
        }
    }

    public boolean isCircuitOpen() {
        synchronized (lock) {
            return circuitOpen;
        }
    }

    public CapacitySnapshot snapshot(long configuredCapacityBytes) {
        synchronized (lock) {
            return snapshotLocked(effectiveConfiguredCapacityLocked(configuredCapacityBytes));
        }
    }

    private ReservationDecision denied(DenyReason reason) {
        synchronized (lock) {
            return denied(reason, snapshotLocked(effectiveConfiguredCapacityLocked(0)));
        }
    }

    private ReservationDecision denied(DenyReason reason, CapacitySnapshot snapshot) {
        return new ReservationDecision(null, reason, snapshot);
    }

    private CapacitySnapshot snapshotLocked(long configuredCapacityBytes) {
        StorageFacts storage = storageProbe.probe(directory);
        long physical = physicalBytesLocked();
        long reserved = reservedBytesLocked();
        DiskCacheCapacityPolicy.Decision policy = DiskCacheCapacityPolicy.resolve(
                storage.available(), configuredCapacityBytes, physical,
                storage.availableBytes(), storage.totalBytes());
        return new CapacitySnapshot(policy, physical, reserved, circuitOpen);
    }

    private long effectiveConfiguredCapacityLocked(long requested) {
        long configured = Math.max(0, requested);
        long minimum = Long.MAX_VALUE;
        boolean hasPositiveClient = false;
        for (ClientLease client : clients.values()) {
            long value = client.configuredCapacityBytes;
            if (value > 0) {
                hasPositiveClient = true;
                minimum = Math.min(minimum, value);
            }
        }
        if (hasPositiveClient) configured = configured == 0 ? minimum : Math.min(configured, minimum);
        return configured;
    }

    private boolean circuitCanRecoverLocked(long configured, long expectedBytes) {
        if (!circuitOpen) return true;
        if (clock.now() - circuitOpenedAtMs < circuitRetryDelayMs) return false;
        CapacitySnapshot snapshot = snapshotLocked(configured);
        if (snapshot.policy().state() == DiskCacheCapacityPolicy.State.UNAVAILABLE
                || snapshot.policy().state() == DiskCacheCapacityPolicy.State.DISABLED) return false;
        long writable = subtractNonNegative(snapshot.policy().newWriteBudgetBytes(), snapshot.reservedBytes());
        return writable >= reservationBytes(expectedBytes) || hasEvictableFileLocked();
    }

    private boolean ensureDirectoryLocked() {
        return directory.exists() || directory.mkdirs();
    }

    private long pruneLocked(long bytes) {
        if (bytes <= 0) return 0;
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(CACHE_SUFFIX));
        if (files == null || files.length == 0) return 0;
        List<File> candidates = new ArrayList<>();
        for (File file : files) {
            String key = file.getName();
            if (readers.getOrDefault(key, 0) > 0 || writers.containsKey(key)) continue;
            candidates.add(file);
        }
        candidates.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        long deleted = 0;
        for (File file : candidates) {
            if (deleted >= bytes) break;
            long length = Math.max(0, file.length());
            if (file.delete()) {
                deleteMeta(file);
                deleted = saturatedAdd(deleted, length);
            }
        }
        return deleted;
    }

    private boolean hasEvictableFileLocked() {
        File[] files = directory.listFiles(file -> file.isFile() && file.getName().endsWith(CACHE_SUFFIX));
        if (files == null) return false;
        for (File file : files) {
            String key = file.getName();
            if (readers.getOrDefault(key, 0) == 0 && !writers.containsKey(key)) return true;
        }
        return false;
    }

    private long physicalBytesLocked() {
        File[] files = directory.listFiles(File::isFile);
        long total = 0;
        if (files != null) {
            for (File file : files) total = saturatedAdd(total, Math.max(0, file.length()));
        }
        return total;
    }

    private long reservedBytesLocked() {
        long total = 0;
        for (WriteReservation reservation : writers.values()) {
            total = saturatedAdd(total, reservation.remainingReservationBytes);
        }
        return total;
    }

    private File tempFileLocked(File file) {
        File temp;
        do {
            String suffix = "." + NEXT_TEMP_ID.incrementAndGet() + TEMP_SUFFIX;
            temp = new File(directory, file.getName() + suffix);
        } while (temp.exists());
        return temp;
    }

    private void releaseReservationLocked(WriteReservation reservation) {
        if (writers.get(reservation.key) == reservation) writers.remove(reservation.key);
    }

    private boolean recordWritten(WriteReservation reservation, long bytes) {
        synchronized (lock) {
            if (writers.get(reservation.key) != reservation || reservation.closed || circuitOpen) return false;
            if (bytes < 0 || bytes > reservation.remainingDataBytes) return false;
            reservation.writtenBytes = saturatedAdd(reservation.writtenBytes, bytes);
            reservation.remainingDataBytes -= bytes;
            reservation.remainingReservationBytes -= bytes;
            return true;
        }
    }

    private boolean canWrite(WriteReservation reservation, long bytes) {
        synchronized (lock) {
            return writers.get(reservation.key) == reservation && !reservation.closed
                    && !circuitOpen && bytes >= 0 && bytes <= reservation.remainingDataBytes;
        }
    }

    private boolean commit(WriteReservation reservation, String mime) throws IOException {
        synchronized (lock) {
            if (writers.get(reservation.key) != reservation || reservation.closed) return false;
            if (reservation.remainingDataBytes != 0 || !reservation.tempFile.isFile()) return false;
            if (reservation.finalFile.isFile() && reservation.finalFile.length() > 0) {
                reservation.closed = true;
                releaseReservationLocked(reservation);
                //noinspection ResultOfMethodCallIgnored
                reservation.tempFile.delete();
                return false;
            }
            if (reservation.finalFile.isFile()) {
                //noinspection ResultOfMethodCallIgnored
                reservation.finalFile.delete();
                deleteMeta(reservation.finalFile);
            }
            if (!reservation.tempFile.renameTo(reservation.finalFile)) {
                reservation.closed = true;
                releaseReservationLocked(reservation);
                //noinspection ResultOfMethodCallIgnored
                reservation.tempFile.delete();
                throw new IOException("cache commit failed");
            }
            writeMetaLocked(reservation.finalFile, mime);
            reservation.closed = true;
            reservation.remainingReservationBytes = 0;
            releaseReservationLocked(reservation);
            return true;
        }
    }

    private void writeMetaLocked(File file, String mime) {
        File meta = metaFile(file);
        File temp = new File(meta.getParentFile(), meta.getName() + "." + NEXT_TEMP_ID.incrementAndGet() + TEMP_SUFFIX);
        String value = mime == null || mime.isEmpty() ? "application/octet-stream" : mime;
        try (OutputStream output = new java.io.FileOutputStream(temp)) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            output.write(bytes, 0, Math.min(bytes.length, (int) META_RESERVATION_BYTES));
            //noinspection ResultOfMethodCallIgnored
            meta.delete();
            //noinspection ResultOfMethodCallIgnored
            temp.renameTo(meta);
        } catch (Throwable ignored) {
            // The binary cache remains valid; MIME falls back to the URL when metadata is absent.
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    private void abort(WriteReservation reservation, @Nullable Throwable error) {
        List<Runnable> callbacks = List.of();
        boolean notify = false;
        synchronized (lock) {
            if (reservation.closed) return;
            reservation.closed = true;
            reservation.remainingReservationBytes = 0;
            releaseReservationLocked(reservation);
            if (isNoSpace(error) && !circuitOpen) {
                circuitOpen = true;
                circuitOpenedAtMs = clock.now();
                long configured = effectiveConfiguredCapacityLocked(0);
                if (configured > 0) {
                    CapacitySnapshot snapshot = snapshotLocked(configured);
                    if (snapshot.policy().state() != DiskCacheCapacityPolicy.State.UNAVAILABLE
                            && snapshot.policy().state() != DiskCacheCapacityPolicy.State.DISABLED
                            && snapshot.policy().reclaimBytes() > 0) {
                        pruneLocked(snapshot.policy().reclaimBytes());
                    }
                }
                callbacks = new ArrayList<>(circuitListeners.values());
                notify = true;
            }
        }
        //noinspection ResultOfMethodCallIgnored
        reservation.tempFile.delete();
        if (notify) for (Runnable callback : callbacks) runCallback(callback);
    }

    private void runCallback(Runnable callback) {
        try {
            callback.run();
        } catch (Throwable ignored) {
        }
    }

    private void releaseRead(String key) {
        synchronized (lock) {
            int count = readers.getOrDefault(key, 0);
            if (count <= 1) readers.remove(key);
            else readers.put(key, count - 1);
        }
    }

    private void recoverOrphans() {
        if (!directory.exists()) return;
        long cutoff = clock.now() - orphanAgeMs;
        File[] files = directory.listFiles(File::isFile);
        if (files == null) return;
        Set<String> complete = new HashSet<>();
        for (File file : files) if (file.getName().endsWith(CACHE_SUFFIX)) complete.add(file.getName());
        for (File file : files) {
            if (file.lastModified() > cutoff) continue;
            if (file.getName().endsWith(TEMP_SUFFIX)
                    || (file.getName().endsWith(META_SUFFIX)
                    && !complete.contains(file.getName().substring(0, file.getName().length() - META_SUFFIX.length())))) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    private static void deleteMeta(File file) {
        File meta = new File(file.getParentFile(), file.getName() + META_SUFFIX);
        //noinspection ResultOfMethodCallIgnored
        meta.delete();
    }

    private static File metaFile(File file) {
        return new File(file.getParentFile(), file.getName() + META_SUFFIX);
    }

    private static StorageFacts probeStorage(File directory) {
        FileUtil.StorageSpace storage = FileUtil.getStorageSpace(directory);
        return new StorageFacts(storage.available(), storage.availableBytes(), storage.totalBytes());
    }

    private static String canonicalPath(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException ignored) {
            return file.getAbsolutePath();
        }
    }

    private static long reservationBytes(long expectedBytes) {
        return saturatedAdd(expectedBytes, META_RESERVATION_BYTES);
    }

    private static long subtractNonNegative(long value, long subtraction) {
        return value <= subtraction ? 0 : value - subtraction;
    }

    private static long saturatedAdd(long first, long second) {
        return second > 0 && first > Long.MAX_VALUE - second ? Long.MAX_VALUE : first + second;
    }

    private static boolean isNoSpace(@Nullable Throwable error) {
        Set<Throwable> visited = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) continue;
            String value = message.toLowerCase(java.util.Locale.ROOT);
            if (value.contains("enospc") || value.contains("no space left on device") || value.contains("errno 28")) return true;
        }
        return false;
    }

    public enum WriterType {
        FOREGROUND,
        PREFETCH
    }

    public enum DenyReason {
        NONE,
        INVALID_REQUEST,
        CACHE_DISABLED,
        ALREADY_CACHED,
        DUPLICATE_KEY,
        DIRECTORY_UNAVAILABLE,
        STORAGE_UNAVAILABLE,
        CIRCUIT_OPEN,
        RECLAIM_REQUIRED,
        NO_GROWTH
    }

    @FunctionalInterface
    interface StorageProbe {
        StorageFacts probe(File directory);
    }

    @FunctionalInterface
    interface Clock {
        long now();
    }

    public record StorageFacts(boolean available, long availableBytes, long totalBytes) {
    }

    public record CapacitySnapshot(DiskCacheCapacityPolicy.Decision policy, long physicalBytes,
                                   long reservedBytes, boolean circuitOpen) {
    }

    public record PreloadCapacitySnapshot(CapacitySnapshot capacity, boolean allowed) {
    }

    public record ReservationDecision(@Nullable WriteReservation reservation, DenyReason reason,
                                      CapacitySnapshot snapshot) {

        public boolean granted() {
            return reservation != null;
        }
    }

    public final class ClientLease implements AutoCloseable {

        private final long id;
        private long configuredCapacityBytes;
        private boolean closed;

        private ClientLease(long id, long configuredCapacityBytes) {
            this.id = id;
            this.configuredCapacityBytes = configuredCapacityBytes;
        }

        public void update(long configuredCapacityBytes) {
            synchronized (lock) {
                if (!closed) {
                    this.configuredCapacityBytes = Math.max(0, configuredCapacityBytes);
                    clients.put(id, this);
                }
            }
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (closed) return;
                closed = true;
                clients.remove(id);
                circuitListeners.remove(id);
            }
        }
    }

    public final class WriteReservation implements AutoCloseable {

        private final MpvHlsCacheCoordinator owner;
        private final String key;
        private final File finalFile;
        private final File tempFile;
        private final long expectedBytes;
        private final WriterType type;
        private long remainingDataBytes;
        private long remainingReservationBytes;
        private long writtenBytes;
        private boolean closed;

        private WriteReservation(MpvHlsCacheCoordinator owner, String key, File finalFile, File tempFile,
                                  long expectedBytes, long reservationBytes, WriterType type) {
            this.owner = owner;
            this.key = key;
            this.finalFile = finalFile;
            this.tempFile = tempFile;
            this.expectedBytes = expectedBytes;
            this.remainingDataBytes = expectedBytes;
            this.remainingReservationBytes = reservationBytes;
            this.type = type;
        }

        public File tempFile() {
            return tempFile;
        }

        public File finalFile() {
            return finalFile;
        }

        public long expectedBytes() {
            return expectedBytes;
        }

        public long writtenBytes() {
            synchronized (lock) {
                return writtenBytes;
            }
        }

        public WriterType type() {
            return type;
        }

        public boolean canWrite(long bytes) {
            return owner.canWrite(this, bytes);
        }

        public boolean recordWritten(long bytes) {
            return owner.recordWritten(this, bytes);
        }

        public boolean commit(String mime) throws IOException {
            return owner.commit(this, mime);
        }

        public void abort() {
            owner.abort(this, null);
        }

        public void fail(@Nullable Throwable error) {
            owner.abort(this, error);
        }

        @Override
        public void close() {
            abort();
        }
    }

    public final class ReadLease extends FilterInputStream {

        private final String key;
        private boolean released;

        private ReadLease(String key, InputStream input) {
            super(input);
            this.key = key;
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value == -1) release();
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int value = super.read(buffer, offset, length);
            if (value == -1) release();
            return value;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                release();
            }
        }

        private synchronized void release() {
            if (released) return;
            released = true;
            releaseRead(key);
        }
    }
}
