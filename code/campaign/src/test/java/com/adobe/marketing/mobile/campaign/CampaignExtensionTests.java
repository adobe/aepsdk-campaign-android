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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import com.adobe.marketing.mobile.Event;
import com.adobe.marketing.mobile.EventSource;
import com.adobe.marketing.mobile.EventType;
import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.ExtensionEventListener;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.MobilePrivacyStatus;
import com.adobe.marketing.mobile.SharedStateResolution;
import com.adobe.marketing.mobile.SharedStateResult;
import com.adobe.marketing.mobile.SharedStateStatus;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRule;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRulesEngine;
import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.rulesengine.Evaluable;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.DataQueue;
import com.adobe.marketing.mobile.services.DataQueuing;
import com.adobe.marketing.mobile.services.DataStoring;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.services.PersistentHitQueue;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.services.ui.AlertListener;
import com.adobe.marketing.mobile.services.ui.AlertSetting;
import com.adobe.marketing.mobile.services.ui.FullscreenMessageDelegate;
import com.adobe.marketing.mobile.services.ui.MessageSettings;
import com.adobe.marketing.mobile.services.ui.NotificationSetting;
import com.adobe.marketing.mobile.services.ui.UIService;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CampaignExtensionTests {

    private CampaignExtension campaignExtension;
    private File cacheDir;

    private static final String messageId = "07a1c997-2450-46f0-a454-537906404124";
    private static final String MESSAGES_CACHE = CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.MESSAGE_CACHE_DIR + File.separator;
    private static final String expectedRulesDownloadUrl = "https://testMcias/testServer/testPropertyId/testExperienceCloudId/rules.zip";

    private HashMap<String, Object> expectedClickedEventMessageData;
    private HashMap<String, Object> expectedViewedEventMessageData;
    private HashMap<String, String> metadataMap;

    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    ServiceProvider mockServiceProvider;
    @Mock
    UIService mockUIService;
    @Mock
    CacheService mockCacheService;
    @Mock
    CacheResult mockCacheResult;
    @Mock
    DataQueuing mockDataQueueService;
    @Mock
    DataQueue mockDataQueue;
    @Mock
    DeviceInforming mockDeviceInfoService;
    @Mock
    Networking mockNetworkService;
    @Mock
    DataStoring mockDataStoreService;
    @Mock
    NamedCollection mockNamedCollection;
    @Mock
    PersistentHitQueue mockPersistentHitQueue;
    @Mock
    LaunchRulesEngine mockRulesEngine;
    @Mock
    CampaignRulesDownloader mockCampaignRulesDownloader;
    @Mock
    Evaluable mockEvaluable;
    @Mock
    CampaignState mockCampaignState;
    @Mock
    Application mockApplication;
    @Mock
    Context mockContext;
    @Mock
    SharedPreferences mockSharedPreferences;

    @Before()
    public void setup() {
        cacheDir = new File("cache");
        cacheDir.mkdirs();
        cacheDir.setWritable(true);

        String sha256HashForRemoteUrl = "fb0d3704b73d5fa012a521ea31013a61020e79610a3c27e8dd1007f3ec278195";
        String cachedFileName = sha256HashForRemoteUrl + ".12345"; //12345 is just a random extension.
        metadataMap = new HashMap<>();
        metadataMap.put(CampaignConstants.METADATA_PATH, MESSAGES_CACHE + messageId + File.separator + cachedFileName);

        expectedClickedEventMessageData = new HashMap<>();
        expectedClickedEventMessageData.put("a.message.clicked", "1");
        expectedClickedEventMessageData.put("a.message.id", "47973");

        expectedViewedEventMessageData = new HashMap<>();
        expectedViewedEventMessageData.put("a.message.viewed", "1");
        expectedViewedEventMessageData.put("a.message.id", "47973");

        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, mockCampaignState, mockCacheService, mockCampaignRulesDownloader);
    }

    private void setupServiceProviderMockAndRunTest(Runnable testRunnable) {
        File assetFile = TestUtils.getResource("happy_test.html");
        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic = Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
            when(mockCacheService.get(anyString(), eq("happy_test.html"))).thenReturn(mockCacheResult);
            when(mockCacheService.get(anyString(), eq("http://asset1-url00.jpeg"))).thenReturn(mockCacheResult);
            when(mockCacheResult.getData()).thenReturn(new FileInputStream(assetFile));
            when(mockCacheResult.getMetadata()).thenReturn(metadataMap);
            when(mockServiceProvider.getDeviceInfoService()).thenReturn(mockDeviceInfoService);
            when(mockServiceProvider.getUIService()).thenReturn(mockUIService);
            when(mockServiceProvider.getNetworkService()).thenReturn(mockNetworkService);
            when(mockServiceProvider.getDataStoreService()).thenReturn(mockDataStoreService);
            when(mockServiceProvider.getCacheService()).thenReturn(mockCacheService);
            when(mockServiceProvider.getDataQueueService()).thenReturn(mockDataQueueService);
            when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(mockNamedCollection);
            when(mockDeviceInfoService.getApplicationCacheDir()).thenReturn(cacheDir);
            when(mockDeviceInfoService.getApplicationBaseDir()).thenReturn(cacheDir);
            when(mockDataQueueService.getDataQueue(anyString())).thenReturn(mockDataQueue);
            testRunnable.run();
        } catch (FileNotFoundException exception) {
            fail(exception.getMessage());
        }
    }

    @After
    public void teardown() {
        clearCacheFiles(cacheDir);
    }

    private SharedStateResult getConfigurationEventData(Map<String, Object> customConfig) {
        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.STATE_OWNER, CampaignConstants.EventDataKeys.Configuration.EXTENSION_NAME);
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_SERVER_KEY, "testServer");
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_PKEY_KEY, "testPkey");
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_MCIAS_KEY, "testMcias");
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_TIMEOUT, 10);
        configData.put(CampaignConstants.EventDataKeys.Configuration.PROPERTY_ID, "testPropertyId");
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_DELAY_KEY, 30);
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_PAUSED_KEY, false);
        configData.putAll(customConfig);
        SharedStateResult sharedStateResult = new SharedStateResult(SharedStateStatus.SET, configData);

        return sharedStateResult;
    }

    private SharedStateResult getIdentityEventData() {
        Map<String, Object> identityData = new HashMap<>();
        identityData.put(CampaignConstants.EventDataKeys.STATE_OWNER, CampaignConstants.EventDataKeys.Identity.EXTENSION_NAME);
        identityData.put(CampaignConstants.EventDataKeys.Identity.VISITOR_ID_MID, "testExperienceCloudId");
        SharedStateResult sharedStateResult = new SharedStateResult(SharedStateStatus.SET, identityData);

        return sharedStateResult;
    }

    private Map<String, Object> getLifecycleEventData() {
        Map<String, Object> lifecycleData = new HashMap<>();
        lifecycleData.put(CampaignConstants.EventDataKeys.Lifecycle.LAUNCH_EVENT, "LaunchEvent");

        Map<String, Object> lifecycleEventData = new HashMap<>();
        lifecycleEventData.put(CampaignConstants.EventDataKeys.Lifecycle.LIFECYCLE_CONTEXT_DATA, lifecycleData);
        return lifecycleEventData;
    }

    private Map<String, Object> getMessageConsequenceEventData(RuleConsequence consequence) {
        Map<String, Object> triggeredConsequenceData = new HashMap<>();

        triggeredConsequenceData.put(CampaignConstants.EventDataKeys.RuleEngine.CONSEQUENCE_TRIGGERED,
                consequence);
        return triggeredConsequenceData;
    }

    private Map<String, Object> getMessageTrackEventData(String broadlogId, String deliveryId, String action) {
        Map<String, Object> trackData = new HashMap<>();
        trackData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID, broadlogId);
        trackData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID, deliveryId);
        trackData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_ACTION, action);

        return trackData;
    }

    private void clearCacheFiles(File file) {
        // clear files from directory first
        if (file.isDirectory()) {
            String[] children = file.list();

            if (children != null) {
                for (String child : children) {
                    File childFile = new File(file, child);
                    clearCacheFiles(childFile);
                }
            }
        }

        file.delete(); // delete file or empty directory
    }

    @Test
    public void testConstructor() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            // test
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, mockCampaignState, mockCacheService, mockCampaignRulesDownloader);

            // verify
            assertNotNull(campaignExtension);
        });
    }

    @Test
    public void testGetName() {
        // test
        String extensionName = campaignExtension.getName();

        // verify
        assertEquals("com.adobe.module.campaign", extensionName);
    }

    @Test
    public void testGetFriendlyName() {
        // test
        String friendlyName = campaignExtension.getFriendlyName();

        // verify
        assertEquals("Campaign", friendlyName);
    }

    @Test
    public void testGetVersion() {
        // test
        String version = campaignExtension.getVersion();

        // verify
        assertEquals("2.0.2", version);
    }

    @Test
    public void test_onRegistered() {
        // test
        campaignExtension.onRegistered();
        // verify
        verify(mockExtensionApi, times(6)).registerEventListener(anyString(), anyString(), any(ExtensionEventListener.class));
    }

    // =================================================================================================================
    // public boolean readyForEvent(final Event event)
    // =================================================================================================================
    @Test
    public void test_readyForEvent_when_eventReceived_and_configurationAndIdentitySharedStateDataPresent_then_readyForEventTrue() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.configuration"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getConfigurationEventData(new HashMap<>()));
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.identity"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, new CampaignState(), mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                    .setEventData(getConfigurationEventData(new HashMap<>()).getValue())
                    .build();

            // verify
            assertTrue(campaignExtension.readyForEvent(testEvent));
        });
    }

    @Test
    public void test_readyForEvent_when_eventReceived_and_configurationSharedStateNotReady_then_readyForEventIsFalse() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.configuration"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.identity"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, new CampaignState(), mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                    .setEventData(getConfigurationEventData(new HashMap<>()).getValue())
                    .build();

            // verify
            assertFalse(campaignExtension.readyForEvent(testEvent));
        });
    }

    @Test
    public void test_readyForEvent_when_eventReceived_and_identitySharedStateNotReady_then_readyForEventIsFalse() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.configuration"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getConfigurationEventData(new HashMap<>()));
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.identity"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(new SharedStateResult(SharedStateStatus.PENDING, new HashMap<>()));
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, new CampaignState(), mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                    .setEventData(getConfigurationEventData(new HashMap<>()).getValue())
                    .build();

            // verify
            assertFalse(campaignExtension.readyForEvent(testEvent));
        });
    }

    @Test
    public void test_readyForEvent_when_identityEventReceived_and_configurationAndIdentitySharedStateDataPresent_then_rulesDownloadTriggered_and_readyForEventIsTrue() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.configuration"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getConfigurationEventData(new HashMap<>()));
            when(mockExtensionApi.getSharedState(eq("com.adobe.module.identity"), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, new CampaignState(), mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.IDENTITY, EventSource.RESPONSE_CONTENT)
                    .setEventData(getIdentityEventData().getValue())
                    .build();

            // verify
            assertTrue(campaignExtension.readyForEvent(testEvent));
            verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(null));
        });
    }

    // =================================================================================================================
    // void handleWildcardEvents(Event event)
    // =================================================================================================================
    @Test
    public void test_handleWildcardEvents_when_validEventForLocalNotification_happy() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<NotificationSetting> notificationSettingArgumentCaptor = ArgumentCaptor.forClass(NotificationSetting.class);
            Map<String, Object> detail =
                    new HashMap<String, Object>() {
                        {
                            put("template", "local");
                            put("content", "messageContent");
                        }
                    };

            List<RuleConsequence> ruleConsequenceList = new ArrayList<RuleConsequence>() {
                {
                    add(new RuleConsequence("id", "iam", detail));
                }
            };
            List<LaunchRule> triggeredRulesList = new ArrayList<LaunchRule>() {
                {
                    add(new LaunchRule(mockEvaluable, ruleConsequenceList));
                }
            };

            Event testEvent = new Event.Builder("Test event", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT)
                    .setEventData(null)
                    .build();

            when(mockRulesEngine.process(testEvent)).thenReturn(triggeredRulesList);

            // test
            campaignExtension.handleWildcardEvents(testEvent);

            // verify
            verify(mockUIService, times(1)).showLocalNotification(notificationSettingArgumentCaptor.capture());
            NotificationSetting notificationSetting = notificationSettingArgumentCaptor.getValue();
            assertEquals("messageContent", notificationSetting.getContent());
            assertEquals("id", notificationSetting.getIdentifier());
            assertEquals("", notificationSetting.getDeeplink());
            assertEquals(-1, notificationSetting.getFireDate());
            Map<String, Object> userInfo = notificationSetting.getUserInfo();
            assertEquals(null, userInfo);
        });
    }

    @Test
    public void test_handleWildcardEvents_when_validEventForAlert_happy() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<AlertSetting> alertSettingArgumentCaptor = ArgumentCaptor.forClass(AlertSetting.class);
            Map<String, Object> detail =
                    new HashMap<String, Object>() {
                        {
                            put("template", "alert");
                            put("title", "messageTitle");
                            put("content", "messageContent");
                            put("cancel", "No");
                        }
                    };

            List<RuleConsequence> ruleConsequenceList = new ArrayList<RuleConsequence>() {
                {
                    add(new RuleConsequence("id", "iam", detail));
                }
            };
            List<LaunchRule> triggeredRulesList = new ArrayList<LaunchRule>() {
                {
                    add(new LaunchRule(mockEvaluable, ruleConsequenceList));
                }
            };

            Event testEvent = new Event.Builder("Test event", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT)
                    .setEventData(null)
                    .build();

            when(mockRulesEngine.process(testEvent)).thenReturn(triggeredRulesList);

            // test
            campaignExtension.handleWildcardEvents(testEvent);

            // verify
            verify(mockUIService, times(1)).showAlert(alertSettingArgumentCaptor.capture(), any(AlertListener.class));
            AlertSetting alertSetting = alertSettingArgumentCaptor.getValue();
            assertEquals("messageContent", alertSetting.getMessage());
            assertEquals("messageTitle", alertSetting.getTitle());
            assertEquals("", alertSetting.getPositiveButtonText());
            assertEquals("No", alertSetting.getNegativeButtonText());
        });
    }

    @Test
    public void test_handleWildcardEvents_when_validEventForFullscreen_happy() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<String> stringArgumentCaptor = ArgumentCaptor.forClass(String.class);
            Map<String, Object> detail =
                    new HashMap<String, Object>() {
                        {
                            put("template", "fullscreen");
                            put("html", "happy_test.html");
                        }
                    };

            List<RuleConsequence> ruleConsequenceList = new ArrayList<RuleConsequence>() {
                {
                    add(new RuleConsequence("id", "iam", detail));
                }
            };
            List<LaunchRule> triggeredRulesList = new ArrayList<LaunchRule>() {
                {
                    add(new LaunchRule(mockEvaluable, ruleConsequenceList));
                }
            };

            Event testEvent = new Event.Builder("Test event", EventType.GENERIC_TRACK, EventSource.REQUEST_CONTENT)
                    .setEventData(null)
                    .build();

            when(mockRulesEngine.process(testEvent)).thenReturn(triggeredRulesList);

            // test
            campaignExtension.handleWildcardEvents(testEvent);

            // verify
            verify(mockUIService, times(1)).createFullscreenMessage(stringArgumentCaptor.capture(), any(FullscreenMessageDelegate.class), anyBoolean(), any(MessageSettings.class));
            String htmlContent = stringArgumentCaptor.getValue();
            assertEquals("<!DOCTYPE html>\n<html>\n<head><meta charset=\"UTF-8\"></head>\n<body>\neverything is awesome http://asset1-url00.jpeg\n</body>\n</html>", htmlContent);
        });
    }

    @Test
    public void test_handleWildcardEvents_when_nullDetailsPresentInConsequence_then_shouldNotShowMessage() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            List<RuleConsequence> ruleConsequenceList = new ArrayList<RuleConsequence>() {
                {
                    add(new RuleConsequence("id", "iam", null));
                }
            };
            List<LaunchRule> triggeredRulesList = new ArrayList<LaunchRule>() {
                {
                    add(new LaunchRule(mockEvaluable, ruleConsequenceList));
                }
            };

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
                    .setEventData(null)
                    .build();

            when(mockRulesEngine.process(testEvent)).thenReturn(triggeredRulesList);

            // test
            campaignExtension.handleWildcardEvents(testEvent);

            // verify
            verify(mockUIService, times(0)).createFullscreenMessage(anyString(), any(FullscreenMessageDelegate.class), anyBoolean(), any(MessageSettings.class));
            verify(mockUIService, times(0)).showAlert(any(AlertSetting.class), any(AlertListener.class));
            verify(mockUIService, times(0)).showLocalNotification(any(NotificationSetting.class));
        });
    }

    @Test
    public void test_handleWildcardEvents_when_emptyConsequenceListReturnedInTriggeredRules_then_shouldNotShowMessage() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            List<RuleConsequence> ruleConsequenceList = new ArrayList<>();
            List<LaunchRule> triggeredRulesList = new ArrayList<LaunchRule>() {
                {
                    add(new LaunchRule(mockEvaluable, ruleConsequenceList));
                }
            };

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_CONTENT)
                    .setEventData(getMessageConsequenceEventData(null))
                    .build();

            when(mockRulesEngine.process(testEvent)).thenReturn(triggeredRulesList);

            // test
            campaignExtension.handleWildcardEvents(testEvent);

            // verify
            verify(mockUIService, times(0)).createFullscreenMessage(anyString(), any(FullscreenMessageDelegate.class), anyBoolean(), any(MessageSettings.class));
            verify(mockUIService, times(0)).showAlert(any(AlertSetting.class), any(AlertListener.class));
            verify(mockUIService, times(0)).showLocalNotification(any(NotificationSetting.class));
        });
    }


    // =================================================================================================================
    // void setCampaignState(Event event)
    // =================================================================================================================

    @Test
    public void test_setCampaignState_when_nullStateOwner_then_shouldDoNothing() {
        // test
        campaignExtension.setCampaignState(null);

        // verify
        verify(mockCampaignState, times(0)).setState(any(SharedStateResult.class), any(SharedStateResult.class));
    }

    @Test
    public void test_setCampaignState_when_configurationStateOwner_then_shouldSetCampaignState() {
        // setup
        Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                .setEventData(getConfigurationEventData(new HashMap<>()).getValue())
                .build();
        when(mockExtensionApi.getSharedState(eq(CampaignConstants.EventDataKeys.Configuration.EXTENSION_NAME), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getConfigurationEventData(new HashMap<>()));
        // test
        campaignExtension.setCampaignState(testEvent);

        // verify setState called with the first argument being present. first argument is a config SharedStateResult.
        verify(mockCampaignState, times(1)).setState(any(SharedStateResult.class), eq(null));
    }

    @Test
    public void test_setCampaignState_when_identityStateOwner_then_shouldSetCampaignState() {
        // setup
        Event testEvent = new Event.Builder("Test event", EventType.IDENTITY, EventSource.RESPONSE_CONTENT)
                .setEventData(getConfigurationEventData(new HashMap<>()).getValue())
                .build();
        when(mockExtensionApi.getSharedState(eq(CampaignConstants.EventDataKeys.Identity.EXTENSION_NAME), any(Event.class), anyBoolean(), any(SharedStateResolution.class))).thenReturn(getIdentityEventData());
        // test
        campaignExtension.setCampaignState(testEvent);

        // verify setState called with the second argument being present. second argument is a identity SharedStateResult.
        verify(mockCampaignState, times(1)).setState(eq(null), any(SharedStateResult.class));
    }


    // =================================================================================================================
    // void handleLinkageFieldsEvent(Event event)
    // =================================================================================================================
    @Test
    public void test_handleLinkageFieldsEvent_when_eventIsNull() {
        try (MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // test
            campaignExtension.handleLinkageFieldsEvent(null);

            // verify
            logMockedStatic.verify(() -> Log.debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()));
            verify(mockCacheService, times(0)).remove(eq(CampaignConstants.RULES_CACHE_FOLDER), eq(""));
            verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
        }
    }

    @Test
    public void test_handleLinkageFieldsEvent_when_eventDataIsEmpty() {
        try (MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // setup
            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify
            logMockedStatic.verify(() -> Log.debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()));
            verify(mockCacheService, times(0)).remove(eq(CampaignConstants.RULES_CACHE_FOLDER), eq(""));
            verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
        }
    }

    @Test
    public void test_handleLinkageFieldsEvent_when_linkageFieldsAreEmpty() {
        try (MockedStatic<Log> logMockedStatic = Mockito.mockStatic(Log.class)) {
            // setup
            HashMap<String, Object> eventData = new HashMap<>();
            Map<String, String> linkageFields = new HashMap<>();
            eventData.put(CampaignConstants.EventDataKeys.Campaign.LINKAGE_FIELDS, linkageFields);

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY)
                    .setEventData(eventData)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify
            logMockedStatic.verify(() -> Log.debug(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.any()));
            verify(mockCacheService, times(0)).remove(eq(CampaignConstants.RULES_CACHE_FOLDER), eq(""));
            verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
        }
    }

    @Test
    public void test_handleLinkageFieldsEvent_setValidLinkageFields() {
        // setup
        try (MockedStatic<Base64> ignored = Mockito.mockStatic(Base64.class); MockedStatic<Utils> utilsMockedStatic = Mockito.mockStatic(Utils.class)) {
            Answer<String> stringAnswer = invocation -> "eyJrZXkxIjoidmFsdWUxIn0=";
            when(Base64.encodeToString(any(byte[].class), anyInt())).thenAnswer(stringAnswer);
            CampaignState campaignState = new CampaignState();
            campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);
            String expectedBase64EncodedLinkageFields = "eyJrZXkxIjoidmFsdWUxIn0=";
            HashMap<String, Object> eventData = new HashMap<>();
            Map<String, String> linkageFields = new HashMap<>();
            linkageFields.put("key1", "value1");
            eventData.put(CampaignConstants.EventDataKeys.Campaign.LINKAGE_FIELDS, linkageFields);

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY)
                    .setEventData(eventData)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify
            String encodedLinkageFields = campaignExtension.getLinkageFields();
            assertEquals(expectedBase64EncodedLinkageFields, encodedLinkageFields);
            utilsMockedStatic.verify(() -> Utils.cleanDirectory(any(File.class)), times(1));
            verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(encodedLinkageFields));
        }
    }

    @Test
    public void test_handleLinkageFieldsEvent_setValidLinkageFields_when_campaignStateNotReady() {
        // setup
        try (MockedStatic<Base64> ignored = Mockito.mockStatic(Base64.class)) {
            Answer<String> stringAnswer = invocation -> "eyJrZXkxIjoidmFsdWUxIn0=";
            when(Base64.encodeToString(any(byte[].class), anyInt())).thenAnswer(stringAnswer);
            String expectedBase64EncodedLinkageFields = "eyJrZXkxIjoidmFsdWUxIn0=";
            HashMap<String, Object> eventData = new HashMap<>();
            Map<String, String> linkageFields = new HashMap<>();
            linkageFields.put("key1", "value1");
            eventData.put(CampaignConstants.EventDataKeys.Campaign.LINKAGE_FIELDS, linkageFields);

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY)
                    .setEventData(eventData)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify
            String encodedLinkageFields = campaignExtension.getLinkageFields();
            assertEquals(expectedBase64EncodedLinkageFields, encodedLinkageFields);
            verify(mockCacheService, times(0)).remove(eq(CampaignConstants.CACHE_BASE_DIR), eq(CampaignConstants.RULES_CACHE_FOLDER));
            verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
        }
    }


    @Test
    public void test_handleResetLinkageFields_happy() {
        // setup
        try (MockedStatic<Utils> utilsMockedStatic = Mockito.mockStatic(Utils.class)) {
            CampaignState campaignState = new CampaignState();
            campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_RESET)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify
            String linkageFields = campaignExtension.getLinkageFields();
            assertEquals("", linkageFields);
            verify(mockRulesEngine, times(1)).replaceRules(any(List.class));
            utilsMockedStatic.verify(() -> Utils.cleanDirectory(any(File.class)), times(1));
            verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(""));
        }
    }

    @Test
    public void test_handleResetLinkageFields_when_linkageFieldsSetPreviously() {
        // setup
        try (MockedStatic<Base64> ignored = Mockito.mockStatic(Base64.class); MockedStatic<Utils> utilsMockedStatic = Mockito.mockStatic(Utils.class)) {
            String expectedBase64EncodedLinkageFields = "eyJrZXkxIjoidmFsdWUxIn0=";
            Answer<String> stringAnswer = invocation -> expectedBase64EncodedLinkageFields;
            when(Base64.encodeToString(any(byte[].class), anyInt())).thenAnswer(stringAnswer);
            CampaignState campaignState = new CampaignState();
            campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

            HashMap<String, Object> eventData = new HashMap<>();
            Map<String, String> linkageFields = new HashMap<>();
            linkageFields.put("key1", "value1");
            eventData.put(CampaignConstants.EventDataKeys.Campaign.LINKAGE_FIELDS, linkageFields);

            Event testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_IDENTITY)
                    .setEventData(eventData)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify linkage fields are set
            String encodedLinkageFields = campaignExtension.getLinkageFields();
            assertEquals(expectedBase64EncodedLinkageFields, encodedLinkageFields);
            verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(encodedLinkageFields));

            // setup reset event
            testEvent = new Event.Builder("Test event", EventType.CAMPAIGN, EventSource.REQUEST_RESET)
                    .build();

            // test
            campaignExtension.handleLinkageFieldsEvent(testEvent);

            // verify linkage fields reset
            String linkageFieldsString = campaignExtension.getLinkageFields();
            assertEquals("", linkageFieldsString);
            verify(mockRulesEngine, times(1)).replaceRules(any(List.class));
            utilsMockedStatic.verify(() -> Utils.cleanDirectory(any(File.class)), times(2));
            verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(""));
        }
    }

    // =================================================================================================================
    // void processConfigurationResponse(Event event)
    // =================================================================================================================
    @Test
    public void test_processConfiguration_When_NullEvent() {
        // test
        campaignExtension.processConfigurationResponse(null);

        // verify
        verify(mockPersistentHitQueue, times(0)).handlePrivacyChange(any(MobilePrivacyStatus.class));
        verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
    }

    @Test
    public void test_processConfiguration_When_NullEventData() {
        // setup
        Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                .build();

        // test
        campaignExtension.processConfigurationResponse(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).handlePrivacyChange(any(MobilePrivacyStatus.class));
        verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
    }

    @Test
    public void test_processConfiguration_When_PrivacyOptedIn() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        HashMap<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "optedin");

        Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                .setEventData(configData)
                .build();

        // test
        campaignExtension.processConfigurationResponse(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).handlePrivacyChange(eq(MobilePrivacyStatus.OPT_IN));
        verify(mockCampaignRulesDownloader, times(1)).loadRulesFromUrl(eq(expectedRulesDownloadUrl), eq(null));
    }

    @Test
    public void test_processConfiguration_When_PrivacyOptOut() {
        // setup
        try (MockedStatic<Utils> utilsMockedStatic = Mockito.mockStatic(Utils.class)) {
            when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(mockNamedCollection);
            CampaignState campaignState = new CampaignState();
            HashMap<String, Object> configData = new HashMap<>();
            configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "optedout");
            campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                    .setEventData(configData)
                    .build();

            // test
            campaignExtension.processConfigurationResponse(testEvent);

            // verify
            verify(mockPersistentHitQueue, times(1)).handlePrivacyChange(eq(MobilePrivacyStatus.OPT_OUT));
            String linkageFields = campaignExtension.getLinkageFields();
            assertEquals("", linkageFields);
            verify(mockRulesEngine, times(1)).replaceRules(eq(null));
            utilsMockedStatic.verify(() -> Utils.cleanDirectory(any(File.class)), times(1));
            verify(mockNamedCollection, times(1)).removeAll();
        }
    }

    @Test
    public void test_processConfiguration_When_PrivacyUnknown() {
        // setup
        CampaignState campaignState = new CampaignState();
        HashMap<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "unknown");
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.CONFIGURATION, EventSource.RESPONSE_CONTENT)
                .setEventData(configData)
                .build();

        // test
        campaignExtension.processConfigurationResponse(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).handlePrivacyChange(eq(MobilePrivacyStatus.UNKNOWN));
        verify(mockCampaignRulesDownloader, times(0)).loadRulesFromUrl(anyString(), anyString());
    }

    // =================================================================================================================
    // void processMessageInformation(Event event)
    // =================================================================================================================
    @Test
    public void test_processMessageInformation_when_campaignConfigured_then_shouldProcessRequest() {
        // setup
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "2")).build();

        String url = String.format(CampaignConstants.CAMPAIGN_TRACKING_URL, campaignState.getCampaignServer(),
                "h2347", "bb65", "2", campaignState.getExperienceCloudId());

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(1)).dispatch(eventArgumentCaptor.capture());
        Event capturedEvent = eventArgumentCaptor.getValue();
        assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
        assertEquals(EventSource.RESPONSE_CONTENT, capturedEvent.getSource());
        assertEquals("DataForMessageRequest", capturedEvent.getName());
        assertEquals(expectedClickedEventMessageData, capturedEvent.getEventData());
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
        CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
    }

    @Test
    public void test_processMessageInformation_when_noEventData_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_emptyEventData_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(new HashMap<>()).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_campaignNotConfigured_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_NoIdentity_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), null);
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_emptyBroadlogId_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("", "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_nullBroadlogId_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData(null, "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_emptyDeliveryId_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_nullDeliveryId_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", null, "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_emptyAction_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_nullAction_then_shouldNotProcessRequest() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", null)).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_PrivacyOptOut_then_shouldNotProcessRequest() {
        // setup
        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "optedout");

        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processMessageInformation_when_PrivacyUnknown_then_shouldNotProcessRequest() {
        // setup
        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "unknown");

        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.GENERIC_DATA,
                EventSource.OS).setEventData(getMessageTrackEventData("h2347", "bb65", "2")).build();

        // test
        campaignExtension.processMessageInformation(testEvent);

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    // =================================================================================================================
    // void processLifecycleUpdate(Event event)
    // =================================================================================================================
    @Test
    public void test_processLifecycleUpdate_when_EventIsNull() {
        // test
        campaignExtension.processLifecycleUpdate(null);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_EventIsEmpty() {
        // setup
        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_campaignConfigured_then_shouldQueueHit() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
            CampaignState campaignState = new CampaignState();
            campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
            FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
            when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);
            campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

            Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                    EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

            String payload = "{\"pushPlatform\":\"gcm\"" +
                    ",\"marketingCloudId\":\"" + campaignState.getExperienceCloudId() + "\"}";
            String url = String.format(CampaignConstants.CAMPAIGN_REGISTRATION_URL, campaignState.getCampaignServer(),
                    campaignState.getCampaignPkey(), campaignState.getExperienceCloudId());

            // test
            campaignExtension.processLifecycleUpdate(testEvent);

            // verify
            verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
            DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
            CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
            assertEquals(url, campaignHit.url);
            assertEquals(payload, campaignHit.payload);
        });
    }

    @Test
    public void test_processLifecycleUpdate_when_campaignNotConfigured_then_shouldNotQueueHit() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(null, getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_NoMid_then_shouldNotQueueHit() {
        // setup
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), null);
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_PrivacyOptOut_then_shouldNotQueueHit() {
        // setup
        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "optedout");

        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_PrivacyUnknown_then_shouldNotQueueHit() {
        // setup
        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.GLOBAL_CONFIG_PRIVACY, "unknown");

        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void
    test_processLifecycleUpdate_when_ecidIsChanged_and_registrationIsPaused_then_shouldNotQueueHit() {
        // setup
        ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        String payload = "{\"pushPlatform\":\"gcm\"" +
                ",\"marketingCloudId\":\"" + campaignState.getExperienceCloudId() + "\"}";
        String url = String.format(CampaignConstants.CAMPAIGN_REGISTRATION_URL, campaignState.getCampaignServer(),
                campaignState.getCampaignPkey(), campaignState.getExperienceCloudId());

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
        CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
        assertEquals(payload, campaignHit.payload);

        // setup for second part of test
        // add values to datastore to simulate a previous successful campaign registration request
        // and set registration paused status to true
        reset(mockPersistentHitQueue);
        Map<String, Object> pausedConfig = new HashMap<>();
        pausedConfig.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_PAUSED_KEY, true);
        campaignState.setState((getConfigurationEventData(pausedConfig)), getIdentityEventData());
        fakeNamedCollection.setLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, System.currentTimeMillis());
        fakeNamedCollection.setString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "newExperienceCloudId");

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_ecidIsChanged_then_shouldQueueHit() {
        // setup
        ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        String payload = "{\"pushPlatform\":\"gcm\"" +
                ",\"marketingCloudId\":\"" + campaignState.getExperienceCloudId() + "\"}";
        String url = String.format(CampaignConstants.CAMPAIGN_REGISTRATION_URL, campaignState.getCampaignServer(),
                campaignState.getCampaignPkey(), campaignState.getExperienceCloudId());

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
        CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
        assertEquals(payload, campaignHit.payload);

        // setup for second part of test
        // add values to datastore to simulate a previous successful campaign registration request
        reset(mockPersistentHitQueue);
        fakeNamedCollection.setLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, System.currentTimeMillis());
        fakeNamedCollection.setString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "newExperienceCloudId");

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        capturedDataEntity = dataEntityArgumentCaptor.getValue();
        campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
        assertEquals(payload, campaignHit.payload);
    }

    @Test
    public void
    test_processLifecycleUpdate_when_customRegistrationDelayOfZeroProvided_then_shouldQueueHit() {
        // setup
        // set registration delay to 0 days (send hit immediately)
        ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);

        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_DELAY_KEY, 0);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        String payload = "{\"pushPlatform\":\"gcm\"" +
                ",\"marketingCloudId\":\"" + campaignState.getExperienceCloudId() + "\"}";
        String url = String.format(CampaignConstants.CAMPAIGN_REGISTRATION_URL, campaignState.getCampaignServer(),
                campaignState.getCampaignPkey(), campaignState.getExperienceCloudId());


        // add values to datastore to simulate a previous successful campaign registration request
        fakeNamedCollection.setLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, System.currentTimeMillis());
        fakeNamedCollection.setString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "newExperienceCloudId");

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
        CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
        assertEquals(payload, campaignHit.payload);
    }

    @Test
    public void
    test_processLifecycleUpdate_when_customRegistrationDelayProvided_then_shouldNotQueueHit_ifDelayNotElapsed() {
        // setup
        // set registration delay to 30 days
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);

        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_DELAY_KEY, 30);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // add values to datastore to simulate a previous successful campaign registration request
        fakeNamedCollection.setLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, System.currentTimeMillis());
        fakeNamedCollection.setString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "testExperienceCloudId");

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    @Test
    public void test_processLifecycleUpdate_when_registrationPausedIsEqualToFalse_then_shouldQueueHit() {
        // setup
        // set registration delay to 30 days
        ArgumentCaptor<DataEntity> dataEntityArgumentCaptor = ArgumentCaptor.forClass(DataEntity.class);
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);

        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_PAUSED_KEY, false);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(new HashMap<>()), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        String payload = "{\"pushPlatform\":\"gcm\"" +
                ",\"marketingCloudId\":\"" + campaignState.getExperienceCloudId() + "\"}";
        String url = String.format(CampaignConstants.CAMPAIGN_REGISTRATION_URL, campaignState.getCampaignServer(),
                campaignState.getCampaignPkey(), campaignState.getExperienceCloudId());


        // add values to datastore to simulate a previous successful campaign registration request
        fakeNamedCollection.setLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, System.currentTimeMillis());
        fakeNamedCollection.setString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, "newExperienceCloudId");

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(1)).queue(dataEntityArgumentCaptor.capture());
        DataEntity capturedDataEntity = dataEntityArgumentCaptor.getValue();
        CampaignHit campaignHit = Utils.campaignHitFromDataEntity(capturedDataEntity);
        assertEquals(url, campaignHit.url);
        assertEquals(payload, campaignHit.payload);
    }

    @Test
    public void test_processLifecycleUpdate_when_registrationPausedIsEqualToTrue_then_shouldNotQueueHit() {
        // setup
        // set registration delay to 30 days
        FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(fakeNamedCollection);

        Map<String, Object> configData = new HashMap<>();
        configData.put(CampaignConstants.EventDataKeys.Configuration.CAMPAIGN_REGISTRATION_PAUSED_KEY, true);
        CampaignState campaignState = new CampaignState();
        campaignState.setState(getConfigurationEventData(configData), getIdentityEventData());
        campaignExtension = new CampaignExtension(mockExtensionApi, mockPersistentHitQueue, mockDataStoreService, mockRulesEngine, campaignState, mockCacheService, mockCampaignRulesDownloader);

        Event testEvent = new Event.Builder("Test event", EventType.LIFECYCLE,
                EventSource.RESPONSE_CONTENT).setEventData(getLifecycleEventData()).build();

        // test
        campaignExtension.processLifecycleUpdate(testEvent);

        // verify
        verify(mockPersistentHitQueue, times(0)).queue(any(DataEntity.class));
    }

    // =================================================================================================================
    // void dispatchMessageInteraction(Map<String, String> messageData)
    // =================================================================================================================
    @Test
    public void test_dispatchMessageInteraction_happy() {
        // setup
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        Map<String, Object> testMessageData = new HashMap<>();
        testMessageData.put(CampaignConstants.ContextDataKeys.MESSAGE_ID, "47973");
        testMessageData.put(CampaignConstants.ContextDataKeys.MESSAGE_CLICKED, "1");

        // test
        campaignExtension.dispatchMessageInteraction(testMessageData);

        // verify
        verify(mockExtensionApi, times(1)).dispatch(eventArgumentCaptor.capture());
        Event capturedEvent = eventArgumentCaptor.getValue();
        assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
        assertEquals(EventSource.RESPONSE_CONTENT, capturedEvent.getSource());
        assertEquals("DataForMessageRequest", capturedEvent.getName());
        assertEquals(expectedClickedEventMessageData, capturedEvent.getEventData());
    }

    @Test
    public void test_dispatchMessageInteraction_whenEmptyMessageData_then_shouldNotDispatch() {
        // test
        campaignExtension.dispatchMessageInteraction(new HashMap<>());

        // verify
        verify(mockExtensionApi, times(0)).dispatch(any(Event.class));
    }

    // =================================================================================================================
    // void dispatchMessageInfo(String broadlogId, String deliveryId, String action)
    // =================================================================================================================
    @Test
    public void test_dispatchMessageInfo_happy() {
        // setup
        ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
        Map<String, Object> expectedGenericData = new HashMap<>();
        String broadlogId = "h87a1";
        String deliveryId = "22dc";
        String action = "7";
        expectedGenericData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_BROADLOG_ID, broadlogId);
        expectedGenericData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_DELIVERY_ID, deliveryId);
        expectedGenericData.put(CampaignConstants.EventDataKeys.Campaign.TRACK_INFO_KEY_ACTION, action);

        // test
        campaignExtension.dispatchMessageInfo(broadlogId, deliveryId, action);

        // verify
        verify(mockExtensionApi, times(1)).dispatch(eventArgumentCaptor.capture());
        Event capturedEvent = eventArgumentCaptor.getValue();
        assertEquals(EventType.GENERIC_DATA, capturedEvent.getType());
        assertEquals(EventSource.OS, capturedEvent.getSource());
        assertEquals("InternalGenericDataEvent", capturedEvent.getName());
        assertEquals(expectedGenericData, capturedEvent.getEventData());
    }


    // =================================================================================================================
    // void clearRulesCacheDirectory()
    // =================================================================================================================
    @Test
    public void test_clearRulesCacheDirectory_happy() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            try(MockedStatic<Utils> utilsMockedStatic = Mockito.mockStatic(Utils.class)) {
                // test
                campaignExtension.clearRulesCacheDirectory();

                // verify
                utilsMockedStatic.verify(() -> Utils.cleanDirectory(any(File.class)), times(1));
            }
        });
    }

    // =================================================================================================================
    // Test function dispatchMessageEvent, which gets called on Generic data OS events.
    // =================================================================================================================
    @Test
    public void testDispatchMessageEventWithActionViewed() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
            String hexValueOfMessageId = "bb65";

            // test
            campaignExtension.dispatchMessageEvent("1", hexValueOfMessageId);

            // verify
            verify(mockExtensionApi, times(1)).dispatch(eventArgumentCaptor.capture());
            Event capturedEvent = eventArgumentCaptor.getValue();
            assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
            assertEquals(EventSource.RESPONSE_CONTENT, capturedEvent.getSource());
            assertEquals("DataForMessageRequest", capturedEvent.getName());
            assertEquals(expectedViewedEventMessageData, capturedEvent.getEventData());
        });
    }

    @Test
    public void testDispatchMessageEventWithActionClicked() {
        // setup
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
            String hexValueOfMessageId = "bb65";

            // test
            campaignExtension.dispatchMessageEvent("2", hexValueOfMessageId);

            // verify
            verify(mockExtensionApi, times(1)).dispatch(eventArgumentCaptor.capture());
            Event capturedEvent = eventArgumentCaptor.getValue();
            assertEquals(EventType.CAMPAIGN, capturedEvent.getType());
            assertEquals(EventSource.RESPONSE_CONTENT, capturedEvent.getSource());
            assertEquals("DataForMessageRequest", capturedEvent.getName());
            assertEquals(expectedClickedEventMessageData, capturedEvent.getEventData());
        });
    }

    @Test
    public void testDispatchMessageEventWithActionImpression() {
        setupServiceProviderMockAndRunTest(() -> {
            ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
            String hexValueOfMessageId = "bb65";

            // test
            campaignExtension.dispatchMessageEvent(null, hexValueOfMessageId);

            // verify
            verify(mockExtensionApi, times(0)).dispatch(eventArgumentCaptor.capture());
        });
    }

    // =================================================================================================================
    // void migrateFromACPCampaign(final NamedCollection namedCollection)
    // =================================================================================================================
    @Ignore // TODO: investigate why test passes locally but fails when running on ci
    @Test
    public void testACPDatastoreMigratedToAEPNamedCollection() throws Exception {
        final FakeNamedCollection testNamedCollection = new FakeNamedCollection();
        when(mockDataStoreService.getNamedCollection(eq(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_NAME))).thenReturn(testNamedCollection);
        final File testDatastore = TestUtils.getResource("CampaignDatastore.xml");
        final File testSharedPrefsFile = new File(cacheDir + File.separator + "shared_prefs" + File.separator + "CampaignDatastore.xml");
        TestFileUtils.copyFile(testDatastore, testSharedPrefsFile);

        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic = Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
            when(mockServiceProvider.getDeviceInfoService()).thenReturn(mockDeviceInfoService);
            when(mockServiceProvider.getDataStoreService()).thenReturn(mockDataStoreService);
            when(mockDataStoreService.getNamedCollection(anyString())).thenReturn(testNamedCollection);
            when(mockDeviceInfoService.getApplicationCacheDir()).thenReturn(cacheDir);
            when(mockDeviceInfoService.getApplicationBaseDir()).thenReturn(cacheDir);
            when(mockServiceProvider.getDataQueueService()).thenReturn(mockDataQueueService);
            when(mockDataQueueService.getDataQueue(anyString())).thenReturn(mockDataQueue);

            try (MockedStatic<MobileCore> mobileCoreMockedStatic = Mockito.mockStatic(MobileCore.class)) {
                mobileCoreMockedStatic.when(MobileCore::getApplication).thenReturn(mockApplication);
                when(mockApplication.getApplicationContext()).thenReturn(mockContext);
                when(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPreferences);
                when(mockSharedPreferences.getString(eq(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY), anyString())).thenReturn(expectedRulesDownloadUrl);
                when(mockSharedPreferences.getString(eq(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY), anyString())).thenReturn("testEcid");
                when(mockSharedPreferences.getLong(eq(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY), anyLong())).thenReturn(1671470288599L);

                // test
                campaignExtension = new CampaignExtension(mockExtensionApi);

                // verify
                assertEquals(expectedRulesDownloadUrl, testNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
                assertEquals("testEcid", testNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_EXPERIENCE_CLOUD_ID_KEY, ""));
                assertEquals(1671470288599L, testNamedCollection.getLong(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, -1L));
                // verify copied acp datastore is cleaned after it is migrated
                assertFalse(testSharedPrefsFile.exists());
            }
        };
    }
}
