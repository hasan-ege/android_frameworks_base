/*
 * Copyright (C) 2026 The crDroid metroid port
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.display;

import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Slog;

import com.android.internal.os.BackgroundThread;

/**
 * Nothing Phone (3): tells the sensor hub, through Nothing's sensor extension HAL, when a call
 * holds the proximity screen-off wake lock. The under-display proximity sensor only keeps
 * measuring with the panel off while it knows this; otherwise the screen turns off at the ear and
 * never comes back on when the phone is taken away.
 *
 * Interface (from the stock system's use of the HAL): execSyncIntInt(4, 7, 0, data[15]) with
 * data[0] = 1 when the wake lock is taken, 2 when it is released. Does nothing on devices
 * without the HAL.
 */
final class NtProximityNotifier {
    private static final String TAG = "NtProximityNotifier";
    private static final String SERVICE =
            "vendor.noth.hardware.sensor.sensor_extension.ISensorExtension/default";
    private static final String DESCRIPTOR =
            "vendor.noth.hardware.sensor.sensor_extension.ISensorExtension";
    private static final int TRANSACTION_EXEC_SYNC_INT_INT = IBinder.FIRST_CALL_TRANSACTION;
    private static final int GROUP_PROXIMITY = 4, CMD_SCREEN_OFF_WAKELOCK = 7;
    private static final int ACQUIRED = 1, RELEASED = 2;

    private static volatile Boolean sPresent;

    private NtProximityNotifier() {}

    static void notify(boolean wakeLockHeld) {
        if (Boolean.FALSE.equals(sPresent)) return;
        BackgroundThread.getHandler().post(() -> send(wakeLockHeld ? ACQUIRED : RELEASED));
    }

    private static void send(int state) {
        IBinder b;
        try {
            b = ServiceManager.isDeclared(SERVICE) ? ServiceManager.waitForService(SERVICE) : null;
        } catch (RuntimeException e) {
            b = null;
        }
        if (b == null) {
            sPresent = false;
            return;
        }
        sPresent = true;
        Parcel data = Parcel.obtain(b);
        Parcel reply = Parcel.obtain();
        try {
            int[] in = new int[15];
            in[0] = state;
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeInt(GROUP_PROXIMITY);
            data.writeInt(CMD_SCREEN_OFF_WAKELOCK);
            data.writeInt(0);
            data.writeIntArray(in);
            b.transact(TRANSACTION_EXEC_SYNC_INT_INT, data, reply, 0);
            reply.readException();
            Slog.i(TAG, "proximity screen-off wake lock " + (state == ACQUIRED ? "held" : "released"));
        } catch (RemoteException | RuntimeException e) {
            Slog.w(TAG, "sensor extension call failed", e);
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
