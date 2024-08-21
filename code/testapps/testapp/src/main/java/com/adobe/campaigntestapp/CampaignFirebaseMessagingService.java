/*
  Copyright 2022 Adobe. All rights reserved.
  This file is licensed to you under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License. You may obtain a copy
  of the License at http://www.apache.org/licenses/LICENSE-2.0
  Unless required by applicable law or agreed to in writing, software distributed under
  the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS
  OF ANY KIND, either express or implied. See the License for the specific language
  governing permissions and limitations under the License.
*/

package com.adobe.campaigntestapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import android.os.Bundle;
import android.util.Log;

import com.adobe.marketing.mobile.MobileCore;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CampaignFirebaseMessagingService extends FirebaseMessagingService {
	public CampaignFirebaseMessagingService() {
	}

	private NotificationChannel mChannel;
	private NotificationManager notifManager;

	@Override
	public void onMessageReceived(RemoteMessage remoteMessage) {
		Log.d("Remote Message", "RemoteMessage: " + remoteMessage.toString());

		// Check if message contains a data payload.
		if (remoteMessage.getData().size() > 0) {
			Log.d("Remote Message", "RemoteMessage: " + remoteMessage.getData());
			displayNotification(remoteMessage);
		}

		// Check if message contains a notification payload.
		if (remoteMessage.getNotification() != null) {
			Log.d("Message Notification", "RemoteMessage: " + remoteMessage.getNotification().getBody());
		}
	}

	//Create and show push notification containgin the received FCM message
	private void displayNotification(RemoteMessage remoteMessage) {
		final int NOTIFY_ID = 1002;
		String name = "my_package_channel";
		String id = "my_package_channel_1";
		String description = "my_package_first_channel";
		String title = remoteMessage.getData().get("title");
		String body = remoteMessage.getData().get("body");

		Intent openIntent = new Intent(this, MainActivity.class);
		Intent dismissIntent = new Intent(this, NotificationDismissedReceiver.class);
		openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

		//put the data map into the intent to track clickthroughs
		Bundle pushData = new Bundle();
		Set<String> keySet = remoteMessage.getData().keySet();
		for (String key : keySet) {
			pushData.putString(key, remoteMessage.getData().get(key));
		}
		openIntent.putExtras(pushData);
		dismissIntent.putExtras(pushData);

		Notification.Builder builder;
		NotificationCompat.Builder compatBuilder;
		PendingIntent pendingIntent;
		PendingIntent onDismissPendingIntent;

		if (notifManager == null) {
			notifManager = (NotificationManager) getSystemService
						   (Context.NOTIFICATION_SERVICE);
		}

		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			System.out.println("Android OS version is 8 or higher!");

			builder = new Notification.Builder(this, id);
			int importance = NotificationManager.IMPORTANCE_HIGH;
			NotificationChannel mChannel = notifManager.getNotificationChannel(id);

			if (mChannel == null) {
				mChannel = new NotificationChannel(id, name, importance);
				mChannel.setDescription(description);
				mChannel.enableVibration(true);
				mChannel.setVibrationPattern(new long[] {100, 200, 300, 400, 500, 400, 300, 200, 400});
				notifManager.createNotificationChannel(mChannel);
			}

			pendingIntent = PendingIntent.getActivity(this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
			onDismissPendingIntent = PendingIntent.getBroadcast(this, 1, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
			builder.setContentTitle("Push Message Received")
			.setContentTitle(title)
			.setContentText(body)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setAutoCancel(true)
			.setContentIntent(pendingIntent)
			.setDeleteIntent(onDismissPendingIntent)
			.setTicker("Push Message Received");

			Notification notification = builder.build();
			notifManager.notify(NOTIFY_ID, notification);
			final Map<String, Object> messageInfo = new HashMap<>();
			for (Map.Entry<String, String> entry : remoteMessage.getData().entrySet()) {
				if (entry.getKey().equals("_mid")) {
					messageInfo.put("messageId", entry.getValue());
				} else if (entry.getKey().equals("_dId")) {
					messageInfo.put("deliveryId", entry.getValue());
				}
			}
			messageInfo.put("action", "7");
			MobileCore.collectMessageInfo(messageInfo);
		} else {
			System.out.println("Android OS version is 7 or lower !");
			pendingIntent = PendingIntent.getActivity(this, 0, openIntent, 0);
			NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this)
			.setContentTitle(title)
			.setContentText(body)
			.setSmallIcon(R.mipmap.ic_launcher)
			.setAutoCancel(true);

			NotificationManager notificationManager =
				(NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
			notificationManager.notify(0 /* ID of notification */, notificationBuilder.build());
		}
	}
}














