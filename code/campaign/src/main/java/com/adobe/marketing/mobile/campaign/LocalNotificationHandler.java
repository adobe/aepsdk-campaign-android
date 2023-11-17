/*
  Copyright 2023 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
 */
package com.adobe.marketing.mobile.campaign;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;
import java.security.SecureRandom;
import java.util.HashMap;

@SuppressWarnings("unchecked")
class LocalNotificationHandler extends BroadcastReceiver {

    private static final String LOG_TAG = "Campaign";
    private static final String SELF_TAG = "LocalNotificationHandler";
    private static final String NOTIFICATION_CHANNEL_NAME = "ADOBE_EXPERIENCE_PLATFORM_SDK";
    private static final String NOTIFICATION_CHANNEL_ID = "ADOBE_EXPERIENCE_PLATFORM_SDK";
    private static final String NOTIFICATION_CHANNEL_DESCRIPTION =
            "Adobe Experience Platform SDK Notifications";
    private static final String NOTIFICATION_CONTENT_KEY = "NOTIFICATION_CONTENT";
    private static final String NOTIFICATION_USER_INFO_KEY = "NOTIFICATION_USER_INFO";
    private static final String NOTIFICATION_IDENTIFIER_KEY = "NOTIFICATION_IDENTIFIER";
    private static final String NOTIFICATION_DEEPLINK_KEY = "NOTIFICATION_DEEPLINK";
    private static final String NOTIFICATION_SOUND_KEY = "NOTIFICATION_SOUND";
    private static final String NOTIFICATION_SENDER_CODE_KEY = "NOTIFICATION_SENDER_CODE";
    private static final int NOTIFICATION_SENDER_CODE = 750183;
    private static final String NOTIFICATION_REQUEST_CODE_KEY = "NOTIFICATION_REQUEST_CODE";
    private static final String NOTIFICATION_TITLE = "NOTIFICATION_TITLE";
    private static final int DEFAULT_ICON_RESOURCE_ID = -1;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        // get message and request code from previous context
        final Bundle bundle = intent.getExtras();

        if (bundle == null) {
            Log.debug(LOG_TAG,
                    SELF_TAG,
                    "Failed to load extras from local notification intent");
            return;
        }

        final Context appContext = context.getApplicationContext();
        final String message = bundle.getString(NOTIFICATION_CONTENT_KEY);
        final int requestCode = bundle.getInt(NOTIFICATION_REQUEST_CODE_KEY);
        final int senderCode = bundle.getInt(NOTIFICATION_SENDER_CODE_KEY);
        final String messageID = bundle.getString(NOTIFICATION_IDENTIFIER_KEY);
        final String deeplink = bundle.getString(NOTIFICATION_DEEPLINK_KEY);
        final String sound = bundle.getString(NOTIFICATION_SOUND_KEY);
        final HashMap<String, Object> userInfo = (HashMap<String, Object>) bundle.getSerializable(NOTIFICATION_USER_INFO_KEY);
        final String title = bundle.getString(NOTIFICATION_TITLE);

        // if our request codes are not matching, we don't care about this intent
        if (senderCode != NOTIFICATION_SENDER_CODE) {
            Log.trace(LOG_TAG, SELF_TAG, "Request code does not match");
            return;
        }

        // if our message is null, we still don't care
        if (message == null) {
            Log.debug(LOG_TAG, SELF_TAG, "%s (local notification message)", Log.UNEXPECTED_NULL_VALUE);
            return;
        }

        final Activity currentActivity =
                ServiceProvider.getInstance().getAppContextService().getCurrentActivity();
        Intent resumeIntent;

        // if we have a deep link, we need to create a new Intent because the old intents are using
        // setClass (overrides opening a deeplink)
        if (deeplink != null && !deeplink.isEmpty()) {
            resumeIntent = new Intent(Intent.ACTION_VIEW);
            resumeIntent.setData(Uri.parse(deeplink));
        } else if (currentActivity != null) {
            resumeIntent = currentActivity.getIntent();
        } else {
            resumeIntent = intent;
        }

        resumeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        resumeIntent.putExtra(NOTIFICATION_IDENTIFIER_KEY, messageID);
        resumeIntent.putExtra(NOTIFICATION_USER_INFO_KEY, userInfo);

        final int buildVersion = Build.VERSION.SDK_INT;
        final NotificationManager notificationManager =
                (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);

        try {
            // if we have an activity for this notification, use it
            final int flags = (buildVersion >= Build.VERSION_CODES.M)
                    ? (PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)
                    : PendingIntent.FLAG_UPDATE_CURRENT;

            final PendingIntent sender = PendingIntent.getActivity(
                                appContext,
                                senderCode,
                                resumeIntent,
                                flags);
            if (sender == null) {
                Log.debug(LOG_TAG,
                        SELF_TAG,
                        "Failed to retrieve sender from broadcast, unable to post notification");
                return;
            }

            // Todo: This seems redundant as the App is first launched before handling Broadcast
            // intent
            // App.INSTANCE.setAppContext(context.getApplicationContext());
            final DeviceInforming systemInfoService =
                    ServiceProvider.getInstance().getDeviceInfoService();
            final String appName = systemInfoService.getApplicationName();

            // notification channels are required if api level is 26 or higher
            if (buildVersion >= Build.VERSION_CODES.O) {
                if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    final NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                            NOTIFICATION_CHANNEL_NAME,
                            NotificationManager.IMPORTANCE_HIGH);
                    notificationChannel.setDescription(NOTIFICATION_CHANNEL_DESCRIPTION);
                    notificationManager.createNotificationChannel(notificationChannel);
                }
                // TODO: handle setting of sound...the previous method got deprecated in API 26
            }

            // set all the notification properties (small icon, content title, and content text are
            // all required)
            // small icon shows up in the status bar
            final NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setStyle(new NotificationCompat.BigTextStyle())
                    .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setStyle(new NotificationCompat.BigTextStyle())
                    .setSmallIcon(getSmallIcon());
            final Bitmap largeIcon = getLargeIcon(context);
            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon);
            }
            if (!StringUtils.isNullOrEmpty(title)) {
                notificationBuilder.setContentTitle(title);
            } else {
                notificationBuilder.setContentTitle(appName);
            }
            notificationBuilder.setContentText(message);
            notificationBuilder.setContentIntent(sender);

            // Setting the delete intent for tracking click on deletion.
            final Intent deleteIntent = new Intent(appContext, NotificationDismissalHandler.class);
            deleteIntent.putExtra(NOTIFICATION_USER_INFO_KEY, userInfo);
            final PendingIntent pendingIntent =  PendingIntent.getBroadcast(appContext,
                        senderCode,
                        deleteIntent,
                        flags);
            notificationBuilder.setDeleteIntent(pendingIntent);

            // this causes the notification to automatically go away when it is touched
            notificationBuilder.setAutoCancel(true);
            notificationManager.notify(new SecureRandom().nextInt(), (Notification) notificationBuilder.build());
        } catch (final Exception e) {
            Log.warning(LOG_TAG,
                    SELF_TAG,
                    "unexpected error posting notification (%s)",
                    e);
        }
    }

    private int getSmallIcon() {
        return MobileCore.getSmallIconResourceID() != DEFAULT_ICON_RESOURCE_ID
                ? MobileCore.getSmallIconResourceID()
                : android.R.drawable.sym_def_app_icon;
    }

    private Bitmap getLargeIcon(final Context appContext) {
        if (appContext == null) {
            return null;
        }

        Drawable iconDrawable = null;
        // first see if we have a user defined one
        final int largeIconResourceId = MobileCore.getLargeIconResourceID();

        if (largeIconResourceId != DEFAULT_ICON_RESOURCE_ID) {
            iconDrawable = ContextCompat.getDrawable(appContext, largeIconResourceId);
        }
        // no user defined icon, try to get one from package manager
        else {
            final ApplicationInfo applicationInfo = appContext.getApplicationInfo();

            if (applicationInfo != null && appContext.getPackageManager() != null) {
                final PackageManager packageManager = appContext.getPackageManager();
                iconDrawable = packageManager.getApplicationIcon(applicationInfo);
            }
        }

        if (iconDrawable == null) {
            return null;
        }

        final Bitmap icon;
        if (iconDrawable instanceof BitmapDrawable) {
            icon = ((BitmapDrawable) iconDrawable).getBitmap();
        } else {
            icon = getBitmapFromDrawable(iconDrawable);
        }

        return icon;
    }

    /**
     * Draws the drawable provided into a new Bitmap
     *
     * @param drawable The {@link Drawable} that needs to be extracted into a Bitmap
     * @return The {@link Bitmap} drawn from the drawable.
     */
    private Bitmap getBitmapFromDrawable(final Drawable drawable) {
        final Bitmap bmp =
                Bitmap.createBitmap(
                        drawable.getIntrinsicWidth(),
                        drawable.getIntrinsicHeight(),
                        Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(bmp);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bmp;
    }
}
