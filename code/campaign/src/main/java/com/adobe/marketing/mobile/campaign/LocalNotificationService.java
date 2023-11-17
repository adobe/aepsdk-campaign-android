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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.util.MapUtils;

import java.security.SecureRandom;
import java.util.Calendar;
import java.util.HashMap;

class LocalNotificationService {
    private static final String LOG_TAG = "Campaign";
    private static final String SELF_TAG = "LocalNotificationService";
    private static final String NOTIFICATION_CONTENT_KEY = "NOTIFICATION_CONTENT";
    private static final String NOTIFICATION_USER_INFO_KEY = "NOTIFICATION_USER_INFO";
    private static final String NOTIFICATION_IDENTIFIER_KEY = "NOTIFICATION_IDENTIFIER";
    private static final String NOTIFICATION_DEEPLINK_KEY = "NOTIFICATION_DEEPLINK";
    private static final String NOTIFICATION_SOUND_KEY = "NOTIFICATION_SOUND";
    private static final String NOTIFICATION_SENDER_CODE_KEY = "NOTIFICATION_SENDER_CODE";
    private static final int NOTIFICATION_SENDER_CODE = 750183;
    private static final String NOTIFICATION_REQUEST_CODE_KEY = "NOTIFICATION_REQUEST_CODE";
    private static final String NOTIFICATION_TITLE = "NOTIFICATION_TITLE";

    static void showLocalNotification(final Context appContext, final NotificationSetting notificationSetting) {
        if (appContext == null) {
            Log.warning(LOG_TAG, SELF_TAG, "Application context is null, unable to show local notification");
            return;
        }

        final int requestCode = new SecureRandom().nextInt();

        // prefer a specified fireDate, otherwise use delaySeconds
        final Calendar calendar = Calendar.getInstance();

        if (notificationSetting.getFireDate() > 0) {
            // do math to calculate number of seconds to add, because android api for
            // calendar.builder is API 26...
            final int secondsUntilFireDate =
                    (int) (notificationSetting.getFireDate() - (calendar.getTimeInMillis() / 1000));

            if (secondsUntilFireDate > 0) {
                calendar.add(Calendar.SECOND, secondsUntilFireDate);
            }
        } else {
            calendar.add(Calendar.SECOND, notificationSetting.getDelaySeconds());
        }

        final Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(appContext, LocalNotificationHandler.class);
        intent.putExtra(NOTIFICATION_SENDER_CODE_KEY, NOTIFICATION_SENDER_CODE);
        intent.putExtra(NOTIFICATION_IDENTIFIER_KEY, notificationSetting.getIdentifier());
        intent.putExtra(NOTIFICATION_REQUEST_CODE_KEY, requestCode);
        intent.putExtra(NOTIFICATION_DEEPLINK_KEY, notificationSetting.getDeeplink());
        intent.putExtra(NOTIFICATION_CONTENT_KEY, notificationSetting.getContent());
        final HashMap<String, Object> userInfo =
                !MapUtils.isNullOrEmpty(notificationSetting.getUserInfo())
                        ? new HashMap<>(notificationSetting.getUserInfo())
                        : null;
        if (!MapUtils.isNullOrEmpty(userInfo)) {
            intent.putExtra(NOTIFICATION_USER_INFO_KEY, userInfo);
        }
        intent.putExtra(NOTIFICATION_SOUND_KEY, notificationSetting.getSound());
        intent.putExtra(NOTIFICATION_TITLE, notificationSetting.getTitle());

        try {
            final int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                    ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    : PendingIntent.FLAG_UPDATE_CURRENT;
            final PendingIntent sender =
                        PendingIntent.getBroadcast(
                                appContext,
                                requestCode,
                                intent,
                                flags);

            final AlarmManager alarmManager =
                    (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);

            if (alarmManager != null) {
                alarmManager.set(AlarmManager.RTC_WAKEUP, calendar.getTimeInMillis(), sender);
            }
        } catch (final Exception e) {
            Log.warning(LOG_TAG,
                    LOG_TAG,
                    String.format(
                            "Unable to create PendingIntent object, error: %s",
                            e.getLocalizedMessage()));
        }
    }
}
