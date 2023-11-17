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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;

import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LocalNotificationServiceTests {

    @Mock
    private Activity mockActivity;
    @Mock
    private Context mockContext;
    @Mock
    private AlarmManager mockAlarmManager;
    @Mock
    private Intent mockIntent;
    @Mock
    private NotificationSetting mockNotificationSetting;

    @Before
    public void setup() {
        when(mockContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(mockAlarmManager);
    }
    @Test
    public void localNotificationIsShown_When_NoOtherMessagesAreDisplayed() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link", null,
                "sound.wav", null));

        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        final long expectedTriggerTime = getTriggerTimeForFireDate(123456);
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationWithTitleIsShown_When_NoOtherMessagesAreDisplayed() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link",  null,
                "sound.wav", "title"));
        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        final long expectedTriggerTime = getTriggerTimeForFireDate(123456);
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationIsShown_When_OtherMessagesAreDisplayed() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link",  null,
                "sound.wav", null));

        final long expectedTriggerTime = getTriggerTimeForFireDate(123456);
        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationWithTitleIsShown_When_OtherMessagesAreDisplayed() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link",  null,
                "sound.wav", "title"));

        final long expectedTriggerTime = getTriggerTimeForFireDate(123456);
        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationIsShown_When_OtherMessagesAreDisplayed1() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 0, 123, "myscheme://link",  null,
                "sound.wav", null));

        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        final long expectedTriggerTime = getTriggerTimeForDelaySecs(123);
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationWithTitleIsShown_When_OtherMessagesAreDisplayed1() {
        //test
        LocalNotificationService.showLocalNotification(mockContext, NotificationSetting.build("id", "content", 0, 123, "myscheme://link",  null,
                "sound.wav", "title"));

        //verify that the Alarm was set
        ArgumentCaptor<Long> triggerTimeCaptor = ArgumentCaptor.forClass(long.class);
        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager).set(eq(AlarmManager.RTC_WAKEUP), triggerTimeCaptor.capture(), isNull());
        //verify that the alarm time is within the delta of 50ms :)
        final long expectedTriggerTime = getTriggerTimeForDelaySecs(123);
        assertTrue(triggerTimeCaptor.getValue() - expectedTriggerTime < 50);
    }

    @Test
    public void localNotificationIsNotShown_When_ContextIsNull() {
        //test
        LocalNotificationService.showLocalNotification(null, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link",  null,
                "sound.wav", "title"));

        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager, times(0)).set(eq(AlarmManager.RTC_WAKEUP), anyLong(), isNull());
    }

    @Test
    public void localNotificationWithTitleIsNotShown_When_ContextIsNull() {
        //test
        LocalNotificationService.showLocalNotification(null, NotificationSetting.build("id", "content", 123456, 123, "myscheme://link",  null,
                "sound.wav", null));

        //The Pending Intent is null matched only because in this test we are not able to mock a static call
        //to PendingIntent.getBroadcast() without using additional libraries - which is a no-no
        verify(mockAlarmManager, times(0)).set(eq(AlarmManager.RTC_WAKEUP), anyLong(), isNull());
    }

    private long getTriggerTimeForFireDate(long fireDate) {
        final Calendar calendar = Calendar.getInstance();

        // do math to calculate number of seconds to add, because android api for calendar.builder is API 26...
        final int secondsUntilFireDate = (int)(fireDate - (calendar.getTimeInMillis() / 1000));

        if (secondsUntilFireDate > 0) {
            calendar.add(Calendar.SECOND, secondsUntilFireDate);
        }

        return calendar.getTimeInMillis();
    }

    private long getTriggerTimeForDelaySecs(int delaySecs) {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.SECOND, delaySecs);

        return calendar.getTimeInMillis();
    }
}
