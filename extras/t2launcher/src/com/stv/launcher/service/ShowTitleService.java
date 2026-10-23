package com.stv.launcher.service;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.util.Log;

/**
 * Compatibility endpoint for older LeTV input activities that bind this exact component.
 * They use the standard Messenger protocol for optional title-bar show/hide notifications and
 * unconditionally unbind on destruction. The custom launcher has no vendor title-bar overlay,
 * so notifications are acknowledged by accepting the messages without drawing over live TV.
 * This service does not change TV inputs or emulate any TV hardware API.
 */
public final class ShowTitleService extends Service {
  private final Messenger messenger = new Messenger(new Handler(Looper.getMainLooper()) {
    @Override
    public void handleMessage(Message message) {
      Log.d("AerialTitleBridge", "title visibility notification: " + message.what);
    }
  });
  @Override
  public IBinder onBind(Intent intent) {
    return messenger.getBinder();
  }
}
