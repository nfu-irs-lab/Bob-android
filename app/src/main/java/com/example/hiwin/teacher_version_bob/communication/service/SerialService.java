package com.example.hiwin.teacher_version_bob.communication.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import com.example.hiwin.teacher_version_bob.communication.bluetooth.concrete.SerialSocket;
import com.example.hiwin.teacher_version_bob.communication.bluetooth.framework.SerialListener;


import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * create notification and queue serial data while activity is not in the foreground
 * use listener chain: SerialSocket -> SerialService -> UI fragment
 */
public class SerialService extends Service implements SerialListener {


    public class SerialBinder extends Binder {
        public SerialService getService() {
            return SerialService.this;
        }
    }

    private enum QueueType {Connect, ConnectError, Read, IoError}

    private class QueueItem {
        QueueType type;
        byte[] data;
        Exception e;

        QueueItem(QueueType type, byte[] data, Exception e) {
            this.type = type;
            this.data = data;
            this.e = e;
        }
    }

    private final Handler mainLooper;
    private final IBinder binder;
    private final Queue<QueueItem> queue1, queue2;

    private SerialSocket socket;
    private SerialListener listener;
    private boolean connected;

    /**
     * Lifecylce
     */
    public SerialService() {
        mainLooper = new Handler(Looper.getMainLooper());
        binder = new SerialBinder();
        queue1 = new LinkedList<>();
        queue2 = new LinkedList<>();
    }

    @Override
    public void onDestroy() {
        disconnect();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * Api
     */
    public void connect(SerialSocket socket) throws IOException {
        socket.connect(this);
        this.socket = socket;
        connected = true;
    }

    public void disconnect() {
        connected = false; // ignore data,errors while disconnecting
        if (socket != null) {
            socket.disconnect();
            socket = null;
        }
    }

    public void write(byte[] data) throws IOException {
        if (!connected)
            throw new IOException("not connected");
        socket.write(data);
    }

    public void attach(SerialListener listener) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread())
            throw new IllegalArgumentException("not in main thread");
        // use synchronized() to prevent new items in queue2
        // new items will not be added to queue1 because mainLooper.post and attach() run in main thread
        synchronized (this) {
            this.listener = listener;
        }
        for (QueueItem item : queue1) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.data);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        for (QueueItem item : queue2) {
            switch (item.type) {
                case Connect:
                    listener.onSerialConnect();
                    break;
                case ConnectError:
                    listener.onSerialConnectError(item.e);
                    break;
                case Read:
                    listener.onSerialRead(item.data);
                    break;
                case IoError:
                    listener.onSerialIoError(item.e);
                    break;
            }
        }
        queue1.clear();
        queue2.clear();
    }

    public void detach() {

        // items already in event queue (posted before detach() to mainLooper) will end up in queue1
        // items occurring later, will be moved directly to queue2
        // detach() and mainLooper.post run in the main thread, so all items are caught
        listener = null;
    }


    /**
     * SerialListener
     */
    public void onSerialConnect() {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onSerialConnect();
                            } else {
                                queue1.add(new QueueItem(QueueType.Connect, null, null));
                            }
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Connect, null, null));
                }
            }
        }
    }

    public void onSerialConnectError(final Exception e) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onSerialConnectError(e);
                            } else {
                                queue1.add(new QueueItem(QueueType.ConnectError, null, e));
                                SerialService.this.disconnect();
                            }
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.ConnectError, null, e));
                    disconnect();
                }
            }
        }
    }

    public void onSerialRead(final byte[] data) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onSerialRead(data);
                            } else {
                                queue1.add(new QueueItem(QueueType.Read, data, null));
                            }
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.Read, data, null));
                }
            }
        }
    }

    public void onSerialIoError(final Exception e) {
        if (connected) {
            synchronized (this) {
                if (listener != null) {
                    mainLooper.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null) {
                                listener.onSerialIoError(e);
                            } else {
                                queue1.add(new QueueItem(QueueType.IoError, null, e));
                                SerialService.this.disconnect();
                            }
                        }
                    });
                } else {
                    queue2.add(new QueueItem(QueueType.IoError, null, e));
                    disconnect();
                }
            }
        }
    }

}
