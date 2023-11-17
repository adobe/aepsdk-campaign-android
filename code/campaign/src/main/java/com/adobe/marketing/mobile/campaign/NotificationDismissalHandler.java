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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link BroadcastReceiver} that triggers when user dismisses local notification from
 * notification panel. It does the click tracking for local notification.
 */
final class NotificationDismissalHandler extends BroadcastReceiver {
    private static final String SELF_TAG = "NotificationDismissalHandler";
    private static final String KEY_BROADLOG_ID = "broadlogId";
    private static final String KEY_DELIVERY_ID = "deliveryId";
    private static final String KEY_ACTION = "action";
    private static final String NOTIFICATION_USER_INFO_KEY = "NOTIFICATION_USER_INFO";
    private static final int MESSAGE_INFO_MAP_SIZE = 3;

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (!intent.hasExtra(NOTIFICATION_USER_INFO_KEY)) {
            return;
        }
        Log.debug(CampaignConstants.LOG_TAG, SELF_TAG, "Notification dismissed");
        final Map<String, Object> notificationData =
                (Map<String, Object>) intent.getSerializableExtra(NOTIFICATION_USER_INFO_KEY);

        if (notificationData != null) {
            final Map<String, Object> contextData = new HashMap<>(MESSAGE_INFO_MAP_SIZE);
            contextData.put(KEY_BROADLOG_ID, notificationData.get(KEY_BROADLOG_ID));
            contextData.put(KEY_DELIVERY_ID, notificationData.get(KEY_DELIVERY_ID));
            contextData.put(KEY_ACTION, "2");
            MobileCore.collectMessageInfo(contextData);
        }
    }
}
