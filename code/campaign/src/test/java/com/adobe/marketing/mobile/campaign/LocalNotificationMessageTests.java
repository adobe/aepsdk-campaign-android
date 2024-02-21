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

package com.adobe.marketing.mobile.campaign;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.ui.NotificationSetting;
import com.adobe.marketing.mobile.services.ui.UIService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LocalNotificationMessageTests {

    private HashMap<String, Object> happyMessageMap;
    private HashMap<String, Object> happyDetailMap;
    private HashMap<String, Object> happyUserDataMap;

    @Mock
    UIService mockUIService;
    @Mock
    ServiceProvider mockServiceProvider;
    @Mock
    CampaignExtension mockCampaignExtension;

    @Before
    public void setup() {
        happyUserDataMap = new HashMap<>();
        happyUserDataMap.put("a", "b");
        happyUserDataMap.put("broadlogId", "h3325");
        happyUserDataMap.put("deliveryId", "a2a1");

        happyDetailMap = new HashMap<>();
        happyDetailMap.put("template", "local");
        happyDetailMap.put("content", "content");
        happyDetailMap.put("wait", 3);
        happyDetailMap.put("date", 123456);
        happyDetailMap.put("adb_deeplink", "http://www.adobe.com");
        happyDetailMap.put("userData", happyUserDataMap);
        happyDetailMap.put("sound", "sound");
        happyDetailMap.put("title", "title");

        happyMessageMap = new HashMap<>();
        happyMessageMap.put("id", "123");
        happyMessageMap.put("type", "iam");
        happyMessageMap.put("detail", happyDetailMap);
    }

    private void setupServiceProviderMockAndRunTest(Runnable testRunnable) {
        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic = Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
            when(mockServiceProvider.getUIService()).thenReturn(mockUIService);
            testRunnable.run();
        }
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_ConsequenceIsNull() throws Exception {
        // test
        new LocalNotificationMessage(mockCampaignExtension, null);
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_ConsequenceMapIsEmpty() throws Exception {
        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(new HashMap<>()));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageId() throws Exception {
        // setup
        happyMessageMap.remove("id");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageId() throws Exception {
        // setup
        happyMessageMap.put("id", "");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageType() throws Exception {
        // setup
        happyMessageMap.remove("type");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_InvalidMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "invalid");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoDetailMap() throws Exception {
        // setup
        happyMessageMap.remove("detail");

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyDetailMap() throws Exception {
        // setup
        happyMessageMap.put("detail", new HashMap<String, Object>());

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsIncorrect() throws Exception {
        // setup
        happyDetailMap.clear();
        happyDetailMap.put("blah", "skdjfh");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapDoesNotContainContent() throws Exception {
        // setup
        happyDetailMap.remove("content");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapContainsEmptyContent() throws Exception {
        // setup
        happyDetailMap.remove("content");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new LocalNotificationMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test
    public void init_ExceptionNotThrown_When_DetailMapDoesNotContainFireDate() throws Exception {
        // setup
        happyDetailMap.remove("date");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_ExceptionNotThrown_When_DetailMapDoesNotContainWait() throws Exception {
        // setup
        happyDetailMap.remove("wait");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_ExceptionNotThrown_When_DetailMapDoesNotContainFireDateOrWait() throws Exception {
        // setup
        happyDetailMap.remove("date");
        happyDetailMap.remove("wait");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainCategory() throws Exception {
        // setup
        happyDetailMap.remove("category");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainSound() throws Exception {
        // setup
        happyDetailMap.remove("sound");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptySound() throws Exception {
        // setup
        happyDetailMap.put("sound", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainUserData() throws Exception {
        // setup
        happyDetailMap.remove("userData");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptyUserData() throws Exception {
        // setup
        happyDetailMap.put("userData", new HashMap<String, Object>());
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainDeeplink() throws Exception {
        // setup
        happyDetailMap.remove("adb_deeplink");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptyDeeplink() throws Exception {
        // setup
        happyDetailMap.put("adb_deeplink", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainTitle() throws Exception {
        // setup
        happyDetailMap.remove("title");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptyTitle() throws Exception {
        // setup
        happyDetailMap.put("title", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage message = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }


    @Test
    public void init_Success_When_ConsequenceIsValid() throws Exception {
        // test
        final LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertEquals("123", localNotificationMessage.messageId);
        assertEquals("content", localNotificationMessage.content);
        assertEquals(123456, localNotificationMessage.fireDate);
        assertEquals("b", localNotificationMessage.userdata.get("a"));
        assertEquals("h3325", localNotificationMessage.userdata.get("broadlogId"));
        assertEquals("a2a1", localNotificationMessage.userdata.get("deliveryId"));
        assertEquals("http://www.adobe.com", localNotificationMessage.deeplink);
        assertEquals("sound", localNotificationMessage.sound);
        assertEquals("title", localNotificationMessage.title);
    }

    @Test
    public void init_Success_When_DetailMapHasNoFireDate() throws Exception {
        // setup
        happyDetailMap.remove("date");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertEquals("123", localNotificationMessage.messageId);
        assertEquals("content", localNotificationMessage.content);
        assertEquals(3, localNotificationMessage.localNotificationDelay);
        assertEquals("b", localNotificationMessage.userdata.get("a"));
        assertEquals("h3325", localNotificationMessage.userdata.get("broadlogId"));
        assertEquals("a2a1", localNotificationMessage.userdata.get("deliveryId"));
        assertEquals("http://www.adobe.com", localNotificationMessage.deeplink);
        assertEquals("sound", localNotificationMessage.sound);
        assertEquals("title", localNotificationMessage.title);
    }

    @Test
    public void init_Success_When_DetailMapHasInvalidFireDate() throws Exception {
        // setup
        happyDetailMap.put("date", 0L);
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertEquals("123", localNotificationMessage.messageId);
        assertEquals("content", localNotificationMessage.content);
        assertEquals(3, localNotificationMessage.localNotificationDelay);
        assertEquals("b", localNotificationMessage.userdata.get("a"));
        assertEquals("h3325", localNotificationMessage.userdata.get("broadlogId"));
        assertEquals("a2a1", localNotificationMessage.userdata.get("deliveryId"));
        assertEquals("http://www.adobe.com", localNotificationMessage.deeplink);
        assertEquals("sound", localNotificationMessage.sound);
        assertEquals("title", localNotificationMessage.title);
    }

    @Test
    public void init_Success_When_DetailMapHasInvalidFireDateAndNoWait() throws Exception {
        // setup
        happyDetailMap.put("date", -1L);
        happyDetailMap.remove("wait");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertEquals("123", localNotificationMessage.messageId);
        assertEquals("content", localNotificationMessage.content);
        assertEquals(0, localNotificationMessage.localNotificationDelay);
        assertEquals("b", localNotificationMessage.userdata.get("a"));
        assertEquals("h3325", localNotificationMessage.userdata.get("broadlogId"));
        assertEquals("a2a1", localNotificationMessage.userdata.get("deliveryId"));
        assertEquals("http://www.adobe.com", localNotificationMessage.deeplink);
        assertEquals("sound", localNotificationMessage.sound);
        assertEquals("title", localNotificationMessage.title);
    }

    @Test
    public void showMessage_DispatchesTriggeredHitAndMessageInfoAndShowsNotification_happy() throws Exception {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<NotificationSetting> notificationSettingArgumentCaptor = ArgumentCaptor.forClass(NotificationSetting.class);
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
            // test
            try {
                LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                        TestUtils.createRuleConsequence(happyMessageMap));
                localNotificationMessage.showMessage();
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }
            // verify showLocalNotification called
            verify(mockUIService, times(1)).showLocalNotification(notificationSettingArgumentCaptor.capture());
            NotificationSetting notificationSetting = notificationSettingArgumentCaptor.getValue();
            assertEquals("content", notificationSetting.getContent());
            assertEquals("123", notificationSetting.getIdentifier());
            assertEquals("http://www.adobe.com", notificationSetting.getDeeplink());
            assertEquals(123456, notificationSetting.getFireDate());
            Map<String, Object> userInfo = notificationSetting.getUserInfo();
            assertEquals("b", userInfo.get("a"));
            assertEquals("h3325", userInfo.get("broadlogId"));
            assertEquals("a2a1", userInfo.get("deliveryId"));

            // verify tracking events
            verify(mockCampaignExtension, times(1)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            verify(mockCampaignExtension, times(1)).dispatchMessageInfo(stringArgumentCaptor.capture(), stringArgumentCaptor.capture(), stringArgumentCaptor.capture());
            Map<String, Object> triggeredDataMap = mapArgumentCaptor.getValue();
            assertEquals("1", triggeredDataMap.get("a.message.triggered"));
            assertEquals("123", triggeredDataMap.get("a.message.id"));
            List<String> viewedDataList = stringArgumentCaptor.getAllValues();
            assertEquals("h3325", viewedDataList.get(0));
            assertEquals("a2a1", viewedDataList.get(1));
            assertEquals("7", viewedDataList.get(2));
        });
    }

    @Test
    public void shouldDownloadAssets_ReturnsFalse_happy() throws Exception {
        // setup
        LocalNotificationMessage localNotificationMessage = new LocalNotificationMessage(mockCampaignExtension,
                TestUtils.createRuleConsequence(happyMessageMap));

        // test
        boolean shouldDownloadAssets = localNotificationMessage.shouldDownloadAssets();

        // verify
        assertFalse(shouldDownloadAssets);
    }
}
