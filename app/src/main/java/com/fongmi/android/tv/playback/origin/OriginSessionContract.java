package com.fongmi.android.tv.playback.origin;

import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

public final class OriginSessionContract {
    public static final int VERSION = 1;
    public static final int MAX_CONTROL_BYTES = 64 * 1024;
    public static final String READY = "READY", PLAY_READY = "PLAY_READY", UNAVAILABLE = "UNAVAILABLE",
            BLOCKED = "BLOCKED", CANCELLED = "CANCELLED", STALE = "STALE";
    public static final String CONFIG_MISSING = "CONFIG_MISSING", CONFIG_LOAD_FAILED = "CONFIG_LOAD_FAILED",
            SITE_MISSING = "SITE_MISSING", DETAIL_FAILED = "DETAIL_FAILED", EPISODE_MISSING = "EPISODE_MISSING",
            ISOLATION_ERROR = "ISOLATION_ERROR", ORIGIN_AUTH_REQUIRED = "ORIGIN_AUTH_REQUIRED",
            PORT_OWNERSHIP_AMBIGUOUS = "PORT_OWNERSHIP_AMBIGUOUS", SNAPSHOT_INVALID = "SNAPSHOT_INVALID",
            WORKER_DIED = "WORKER_DIED", PARSE_FAILED = "PARSE_FAILED", PLAYER_FAILED = "PLAYER_FAILED";

    private OriginSessionContract() { }

    public static boolean shouldFallback(String reason) {
        return CONFIG_MISSING.equals(reason) || CONFIG_LOAD_FAILED.equals(reason) || SITE_MISSING.equals(reason)
                || DETAIL_FAILED.equals(reason) || EPISODE_MISSING.equals(reason);
    }

    /** Worker UNAVAILABLE stays business-only; BLOCKED may resume globally only before setPlayer. */
    public static boolean shouldResumeGlobally(boolean resultDelivered, String status, String reason) {
        return !resultDelivered && (BLOCKED.equals(status) || UNAVAILABLE.equals(status) && shouldFallback(reason));
    }

    public static void validate(Bundle message) {
        if (message == null || message.getInt("version") != VERSION) throw new IllegalArgumentException(SNAPSHOT_INVALID);
        Parcel parcel = Parcel.obtain();
        try {
            parcel.writeBundle(message);
            if (parcel.dataSize() > MAX_CONTROL_BYTES) throw new IllegalArgumentException(SNAPSHOT_INVALID);
        } finally { parcel.recycle(); }
    }

    public interface OriginSessionPort extends IInterface {
        void open(Bundle request, ParcelFileDescriptor snapshotFd, IBinder owner, OriginReply reply) throws RemoteException;
        void play(Bundle request, OriginReply reply) throws RemoteException;
        void cancel(long generation) throws RemoteException;
        void close(String leaseId, long generation) throws RemoteException;

        abstract class Stub extends Binder implements OriginSessionPort {
            private static final String DESCRIPTOR = "com.fongmi.android.tv.origin.Session.v1";
            public Stub() { attachInterface(this, DESCRIPTOR); }
            public static OriginSessionPort asInterface(IBinder binder) {
                if (binder == null) return null;
                IInterface local = binder.queryLocalInterface(DESCRIPTOR);
                return local instanceof OriginSessionPort ? (OriginSessionPort) local : new Proxy(binder);
            }
            @Override public IBinder asBinder() { return this; }
            @Override protected boolean onTransact(int code, Parcel data, Parcel result, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { result.writeString(DESCRIPTOR); return true; }
                data.enforceInterface(DESCRIPTOR);
                switch (code) {
                    case 1: open(readBundle(data), readFd(data), data.readStrongBinder(), OriginReply.Stub.asInterface(data.readStrongBinder())); break;
                    case 2: play(readBundle(data), OriginReply.Stub.asInterface(data.readStrongBinder())); break;
                    case 3: cancel(data.readLong()); break;
                    case 4: close(data.readString(), data.readLong()); break;
                    default: return super.onTransact(code, data, result, flags);
                }
                return true;
            }
            private static final class Proxy implements OriginSessionPort {
                private final IBinder binder;
                Proxy(IBinder binder) { this.binder = binder; }
                @Override public IBinder asBinder() { return binder; }
                @Override public void open(Bundle request, ParcelFileDescriptor fd, IBinder owner, OriginReply reply) throws RemoteException {
                    validate(request);
                    Parcel data = Parcel.obtain();
                    try { data.writeInterfaceToken(DESCRIPTOR); writeBundle(data, request); writeFd(data, fd); data.writeStrongBinder(owner); data.writeStrongBinder(reply.asBinder()); transact(1, data); }
                    finally { data.recycle(); }
                }
                @Override public void play(Bundle request, OriginReply reply) throws RemoteException {
                    validate(request);
                    Parcel data = Parcel.obtain();
                    try { data.writeInterfaceToken(DESCRIPTOR); writeBundle(data, request); data.writeStrongBinder(reply.asBinder()); transact(2, data); }
                    finally { data.recycle(); }
                }
                @Override public void cancel(long generation) throws RemoteException {
                    Parcel data = Parcel.obtain();
                    try { data.writeInterfaceToken(DESCRIPTOR); data.writeLong(generation); transact(3, data); } finally { data.recycle(); }
                }
                @Override public void close(String leaseId, long generation) throws RemoteException {
                    Parcel data = Parcel.obtain();
                    try { data.writeInterfaceToken(DESCRIPTOR); data.writeString(leaseId); data.writeLong(generation); transact(4, data); } finally { data.recycle(); }
                }
                private void transact(int code, Parcel data) throws RemoteException {
                    if (!binder.transact(code, data, null, IBinder.FLAG_ONEWAY)) throw new RemoteException(WORKER_DIED);
                }
            }
        }
    }

    public interface OriginReply extends IInterface {
        void complete(Bundle envelope, ParcelFileDescriptor jsonFd) throws RemoteException;
        abstract class Stub extends Binder implements OriginReply {
            private static final String DESCRIPTOR = "com.fongmi.android.tv.origin.Reply.v1";
            public Stub() { attachInterface(this, DESCRIPTOR); }
            public static OriginReply asInterface(IBinder binder) {
                if (binder == null) return null;
                IInterface local = binder.queryLocalInterface(DESCRIPTOR);
                return local instanceof OriginReply ? (OriginReply) local : new Proxy(binder);
            }
            @Override public IBinder asBinder() { return this; }
            @Override protected boolean onTransact(int code, Parcel data, Parcel result, int flags) throws RemoteException {
                if (code == INTERFACE_TRANSACTION) { result.writeString(DESCRIPTOR); return true; }
                if (code != 1) return super.onTransact(code, data, result, flags);
                data.enforceInterface(DESCRIPTOR);
                complete(readBundle(data), readFd(data));
                return true;
            }
            private static final class Proxy implements OriginReply {
                private final IBinder binder;
                Proxy(IBinder binder) { this.binder = binder; }
                @Override public IBinder asBinder() { return binder; }
                @Override public void complete(Bundle envelope, ParcelFileDescriptor fd) throws RemoteException {
                    validate(envelope);
                    Parcel data = Parcel.obtain();
                    try {
                        data.writeInterfaceToken(DESCRIPTOR); writeBundle(data, envelope); writeFd(data, fd);
                        if (!binder.transact(1, data, null, IBinder.FLAG_ONEWAY)) throw new RemoteException(WORKER_DIED);
                    } finally { data.recycle(); }
                }
            }
        }
    }

    private static void writeBundle(Parcel parcel, Bundle value) { parcel.writeBundle(value); }
    private static Bundle readBundle(Parcel parcel) { return parcel.readBundle(OriginSessionContract.class.getClassLoader()); }
    private static void writeFd(Parcel parcel, ParcelFileDescriptor fd) { parcel.writeInt(fd == null ? 0 : 1); if (fd != null) fd.writeToParcel(parcel, 0); }
    private static ParcelFileDescriptor readFd(Parcel parcel) { return parcel.readInt() == 0 ? null : ParcelFileDescriptor.CREATOR.createFromParcel(parcel); }
    public static void closeFd(ParcelFileDescriptor fd) { if (fd != null) try { fd.close(); } catch (java.io.IOException ignored) { } }
}
