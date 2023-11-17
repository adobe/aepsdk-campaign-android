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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.ui.Alert;
import com.adobe.marketing.mobile.services.ui.Presentable;
import com.adobe.marketing.mobile.services.ui.PresentationUtilityProvider;
import com.adobe.marketing.mobile.services.ui.UIService;
import com.adobe.marketing.mobile.services.ui.alert.AlertSettings;
import com.adobe.marketing.mobile.services.uri.UriOpening;

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
public class AlertMessageTests {
    private HashMap<String, Object> happyMessageMap;
    private HashMap<String, Object> happyDetailMap;

    @Mock
    UIService mockUIService;
    @Mock
    UriOpening mockUriService;
    @Mock
    ServiceProvider mockServiceProvider;
    @Mock
    CampaignExtension mockCampaignExtension;
    @Mock
    Presentable<Alert> mockAlertMessage;
    ArgumentCaptor<Alert> alertArgumentCaptor;

    @Before
    public void setup() {
        happyDetailMap = new HashMap<>();
        happyDetailMap.put("template", "alert");
        happyDetailMap.put("title", "Title");
        happyDetailMap.put("content", "content");
        happyDetailMap.put("confirm", "Y");
        happyDetailMap.put("cancel", "N");
        happyDetailMap.put("url", "http://www.adobe.com");

        happyMessageMap = new HashMap<>();
        happyMessageMap.put("id", "123");
        happyMessageMap.put("type", "iam");
        happyMessageMap.put("detail", happyDetailMap);
    }

    private void setupServiceProviderMockAndRunTest(Runnable testRunnable) {
        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic = Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
            when(mockServiceProvider.getUIService()).thenReturn(mockUIService);
            when(mockServiceProvider.getUriService()).thenReturn(mockUriService);
            alertArgumentCaptor = ArgumentCaptor.forClass(Alert.class);
            when(mockUIService.create(alertArgumentCaptor.capture(), any(PresentationUtilityProvider.class))).thenReturn(mockAlertMessage);
            testRunnable.run();
        }
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_ConsequenceIsNull() throws Exception {
        //test
        new AlertMessage(mockCampaignExtension, null);
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_ConsequenceMapIsEmpty() throws Exception {
        //test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(new HashMap<>()));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageId() throws Exception {
        // setup
        happyMessageMap.remove("id");

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageId() throws Exception {
        // setup
        happyMessageMap.put("id", "");

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageType() throws Exception {
        // setup
        happyMessageMap.remove("type");

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "");

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_InvalidMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "invalid");

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsIncorrect() throws Exception {
        //Setup
        happyDetailMap.clear();
        happyDetailMap.put("blah", "skdjfh");
        happyMessageMap.put("detail", happyDetailMap);

        //test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsMissing() throws Exception {
        //Setup
        happyMessageMap.remove("detail");

        //test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsEmpty() throws Exception {
        //Setup
        happyMessageMap.put("detail", new HashMap<String, Object>());

        //test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapDoesNotContainTitle() throws Exception {
        // setup
        happyDetailMap.remove("title");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapContainsEmptyTitle() throws Exception {
        // setup
        happyDetailMap.put("title", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapDoesNotContainContent() throws Exception {
        // setup
        happyDetailMap.remove("content");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapContainsEmptyContent() throws Exception {
        // setup
        happyDetailMap.put("content", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapDoesNotContainCancel() throws Exception {
        // setup
        happyDetailMap.remove("cancel");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapContainsEmptyCancel() throws Exception {
        // setup
        happyDetailMap.put("cancel", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainConfirm() throws Exception {
        // setup
        happyDetailMap.remove("confirm");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final AlertMessage message = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptyConfirm() throws Exception {
        // setup
        happyDetailMap.put("confirm", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final AlertMessage message = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapDoesNotContainUrl() throws Exception {
        // setup
        happyDetailMap.remove("url");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final AlertMessage message = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_DetailMapContainsEmptyUrl() throws Exception {
        // setup
        happyDetailMap.put("url", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final AlertMessage message = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(message);
    }

    @Test
    public void init_Success_When_MessagePayloadIsValid() throws Exception {
        // test
        AlertMessage alertMessage = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // Verify
        assertNotNull(alertMessage);
        assertEquals("123", alertMessage.messageId);
        assertEquals("Title", alertMessage.title);
        assertEquals("content", alertMessage.content);
        assertEquals("Y", alertMessage.confirmButtonText);
        assertEquals("N", alertMessage.cancelButtonText);
    }


    @Test
    public void showMessage_ShowsAlert_Happy() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            // test
            try {
                AlertMessage alertMessage = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
                alertMessage.showMessage();
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            final AlertSettings alertSetting = alertArgumentCaptor.getValue().getSettings();
            assertEquals("content", alertSetting.getMessage());
            assertEquals("Title", alertSetting.getTitle());
            assertEquals("N", alertSetting.getNegativeButtonText());
            assertEquals("Y", alertSetting.getPositiveButtonText());
            // verify that a valid listener was attached to the call.
            assertNotNull(alertArgumentCaptor.getValue().getEventListener());
        });
    }

    @Test
    public void showUrl_UIServiceShowUrlIsCalled_When_MessageContainsValidUrl() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
            // test
            try {
                AlertMessage alertMessage = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
                alertMessage.showUrl();
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockUriService, times(1)).openUri(stringArgumentCaptor.capture());
            assertEquals("http://www.adobe.com", stringArgumentCaptor.getValue());
        });
    }

    @Test
    public void showUrl_UIServiceShowUrlNotCalled_When_MessageContainsNullUrl() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            happyDetailMap.remove("url");
            happyMessageMap.put("detail", happyDetailMap);

            // test
            try {
                AlertMessage alertMessage = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
                alertMessage.showUrl();
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockUriService, times(0)).openUri(anyString());
        });
    }

    @Test
    public void alertListener_TriggeredHitDispatched_When_onShowCalled() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onShow(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(1)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            Map<String, Object> messageInteractionMap = mapArgumentCaptor.getValue();
            assertEquals(2, messageInteractionMap.size());
            assertEquals("1", messageInteractionMap.get("a.message.triggered"));
            assertEquals("123", messageInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_ViewedHitDispatched_When_onDismissCalled() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onDismiss(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(1)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            Map<String, Object> messageInteractionMap = mapArgumentCaptor.getValue();
            assertEquals(2, messageInteractionMap.size());
            assertEquals("1", messageInteractionMap.get("a.message.viewed"));
            assertEquals("123", messageInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_ViewedHitDispatched_When_onNegativeResponseCalled() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onNegativeResponse(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(1)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            Map<String, Object> messageInteractionMap = mapArgumentCaptor.getValue();
            assertEquals(2, messageInteractionMap.size());
            assertEquals("1", messageInteractionMap.get("a.message.viewed"));
            assertEquals("123", messageInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_ClickedHitDispatched_When_onPositiveResponseCalled() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onPositiveResponse(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(2)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            assertEquals(2, mapArgumentCaptor.getAllValues().size());
            List<Map<String, Object>> messageInteractions = mapArgumentCaptor.getAllValues();
            Map<String, Object> viewedInteractionMap = messageInteractions.get(0);
            assertEquals(2, viewedInteractionMap.size());
            assertEquals("1", viewedInteractionMap.get("a.message.viewed"));
            assertEquals("123", viewedInteractionMap.get("a.message.id"));
            Map<String, Object> clickedInteractionMap = messageInteractions.get(1);
            assertEquals(3, clickedInteractionMap.size());
            assertEquals("http://www.adobe.com", clickedInteractionMap.get("url"));
            assertEquals("1", clickedInteractionMap.get("a.message.clicked"));
            assertEquals("123", clickedInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_ClickedHitDispatched_When_onPositiveResponseCalled_whenUrlNull() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            happyDetailMap.remove("url");
            happyMessageMap.put("detail", happyDetailMap);
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onPositiveResponse(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(2)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            assertEquals(2, mapArgumentCaptor.getAllValues().size());
            List<Map<String, Object>> messageInteractions = mapArgumentCaptor.getAllValues();
            Map<String, Object> viewedInteractionMap = messageInteractions.get(0);
            assertEquals(2, viewedInteractionMap.size());
            assertNull(viewedInteractionMap.get("url"));
            assertEquals("1", viewedInteractionMap.get("a.message.viewed"));
            assertEquals("123", viewedInteractionMap.get("a.message.id"));
            Map<String, Object> clickedInteractionMap = messageInteractions.get(1);
            assertEquals(2, clickedInteractionMap.size());
            assertNull(clickedInteractionMap.get("url"));
            assertEquals("1", clickedInteractionMap.get("a.message.clicked"));
            assertEquals("123", clickedInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_ClickedHitDispatched_When_onPositiveResponseCalled_whenUrlEmpty() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            happyDetailMap.put("url", "");
            happyMessageMap.put("detail", happyDetailMap);
            ArgumentCaptor<Map<String, Object>> mapArgumentCaptor = ArgumentCaptor.forClass(Map.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onPositiveResponse(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockCampaignExtension, times(2)).dispatchMessageInteraction(mapArgumentCaptor.capture());
            assertEquals(2, mapArgumentCaptor.getAllValues().size());
            List<Map<String, Object>> messageInteractions = mapArgumentCaptor.getAllValues();
            Map<String, Object> viewedInteractionMap = messageInteractions.get(0);
            assertEquals(2, viewedInteractionMap.size());
            assertNull(viewedInteractionMap.get("url"));
            assertEquals("1", viewedInteractionMap.get("a.message.viewed"));
            assertEquals("123", viewedInteractionMap.get("a.message.id"));
            Map<String, Object> clickedInteractionMap = messageInteractions.get(1);
            assertEquals(2, clickedInteractionMap.size());
            assertNull(clickedInteractionMap.get("url"));
            assertEquals("1", clickedInteractionMap.get("a.message.clicked"));
            assertEquals("123", clickedInteractionMap.get("a.message.id"));
        });
    }

    @Test
    public void alertListener_showUrlCalled_When_onPositiveResponseCalled() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
            // test
            try {
                final Presentable<Alert> mockAlertPresentable = Mockito.mock(Presentable.class);
                AlertMessage.UIAlertMessageUIListener uiAlertMessageUIListener = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap)).new UIAlertMessageUIListener();
                uiAlertMessageUIListener.onPositiveResponse(mockAlertPresentable);
            } catch (CampaignMessageRequiredFieldMissingException exception) {
                fail(exception.getMessage());
            }

            // verify
            verify(mockUriService, times(1)).openUri(stringArgumentCaptor.capture());
            assertEquals("http://www.adobe.com", stringArgumentCaptor.getValue());
        });
    }

    @Test
    public void shouldDownloadAssets_ReturnsFalse_happy() throws Exception {
        // setup
        AlertMessage alertMessage = new AlertMessage(mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // test
        boolean shouldDownloadAssets = alertMessage.shouldDownloadAssets();

        // verify
        assertFalse(shouldDownloadAssets);
    }
}
