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

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.AssetManager;
import com.adobe.marketing.mobile.services.AppContextService;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheEntry;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.services.ui.InAppMessage;
import com.adobe.marketing.mobile.services.ui.Presentable;
import com.adobe.marketing.mobile.services.ui.PresentationUtilityProvider;
import com.adobe.marketing.mobile.services.ui.UIService;
import com.adobe.marketing.mobile.services.uri.UriOpening;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.Silent.class)
public class FullScreenMessageTests {
    private HashMap<String, Object> happyMessageMap;
    private HashMap<String, Object> happyDetailMap;
    private HashMap<String, String> metadataMap;
    private ArrayList<ArrayList<String>> happyRemoteAssets;
    private ArrayList<String> remoteAssets;
    private String assetPath;
    private static final String messageId = "07a1c997-2450-46f0-a454-537906404124";
    private static final String MESSAGES_CACHE =
            CampaignConstants.CACHE_BASE_DIR
                    + File.separator
                    + CampaignConstants.MESSAGE_CACHE_DIR
                    + File.separator;

    @Mock UIService mockUIService;
    @Mock UriOpening mockUriService;

    @Mock AppContextService mockAppContextService;
    @Mock Context mockContext;
    @Mock AssetManager mockAssetManager;
    @Mock InputStream mockInputStream;
    @Mock CacheService mockCacheService;
    @Mock CacheResult mockCacheResult;
    @Mock ServiceProvider mockServiceProvider;
    @Mock CampaignExtension mockCampaignExtension;
    @Mock Presentable<InAppMessage> mockFullscreenMessage;
    @Mock FullScreenMessage mockCampaignFullScreenMessage;
    ArgumentCaptor<InAppMessage> inAppMessageArgumentCaptor;

    @Before
    public void setup() {
        remoteAssets = new ArrayList<>();
        remoteAssets.add("http://asset1-url00.jpeg");
        remoteAssets.add("fallback.jpeg");

        happyRemoteAssets = new ArrayList<>();
        happyRemoteAssets.add(remoteAssets);

        happyDetailMap = new HashMap<>();
        happyDetailMap.put("template", "fullscreen");
        happyDetailMap.put("html", "happy_test.html");
        happyDetailMap.put("remoteAssets", happyRemoteAssets);

        happyMessageMap = new HashMap<>();
        happyMessageMap.put("id", messageId);
        happyMessageMap.put("type", "iam");
        happyMessageMap.put("assetsPath", assetPath);
        happyMessageMap.put("detail", happyDetailMap);

        final String sha256HashForRemoteUrl =
                "fb0d3704b73d5fa012a521ea31013a61020e79610a3c27e8dd1007f3ec278195";
        final String cachedFileName =
                sha256HashForRemoteUrl + ".12345"; // 12345 is just a random extension.
        metadataMap = new HashMap<>();
        metadataMap.put(
                CampaignConstants.METADATA_PATH,
                MESSAGES_CACHE + messageId + File.separator + cachedFileName);
    }

    private void setupServiceProviderMockAndRunTest(Runnable testRunnable) {
        File assetFile = TestUtils.getResource("happy_test.html");
        if (assetFile != null) {
            assetPath = assetFile.getParent();
        }

        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic =
                Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic
                    .when(ServiceProvider::getInstance)
                    .thenReturn(mockServiceProvider);
            when(mockServiceProvider.getAppContextService()).thenReturn(mockAppContextService);
            when(mockAppContextService.getApplicationContext()).thenReturn(mockContext);
            when(mockContext.getAssets()).thenReturn(mockAssetManager);
            when(mockAssetManager.open(anyString())).thenReturn(mockInputStream);
            when(mockServiceProvider.getUIService()).thenReturn(mockUIService);
            when(mockServiceProvider.getUriService()).thenReturn(mockUriService);
            when(mockServiceProvider.getCacheService()).thenReturn(mockCacheService);
            when(mockCacheService.get(anyString(), eq("happy_test.html")))
                    .thenReturn(mockCacheResult);
            when(mockCacheService.get(anyString(), eq("http://asset1-url00.jpeg")))
                    .thenReturn(mockCacheResult);
            when(mockCacheResult.getData()).thenReturn(new FileInputStream(assetFile));
            when(mockCacheResult.getMetadata()).thenReturn(metadataMap);
            inAppMessageArgumentCaptor = ArgumentCaptor.forClass(InAppMessage.class);
            when(mockUIService.create(
                            inAppMessageArgumentCaptor.capture(),
                            any(PresentationUtilityProvider.class)))
                    .thenReturn(mockFullscreenMessage);
            testRunnable.run();
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @After
    public void cleanUp() {
        deleteCacheDir();
    }

    /** Delete the cache directory if present. */
    private void deleteCacheDir() {
        final File cacheDir = new File(MESSAGES_CACHE);

        if (cacheDir != null && cacheDir.exists()) {
            clearCacheFiles(cacheDir);
        }
    }

    /**
     * Deletes the directory and all files inside it.
     *
     * @param file instance of {@link File} points to the directory need to be deleted.
     */
    private static void clearCacheFiles(final File file) {
        // clear files from directory first
        if (file.isDirectory()) {
            final String[] children = file.list();

            if (children != null) {
                for (final String child : children) {
                    final File childFile = new File(file, child);
                    clearCacheFiles(childFile);
                }
            }
        }

        file.delete(); // delete file or empty directory
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void FullScreenMessage_ShouldThrowException_When_NullConsequence()
            throws CampaignMessageRequiredFieldMissingException {
        new FullScreenMessage(mockCampaignExtension, null);
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_ConsequenceMapIsEmpty() throws Exception {
        // test
        new FullScreenMessage(
                mockCampaignExtension,
                TestUtils.createRuleConsequence(new HashMap<String, Object>()));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageId() throws Exception {
        // setup
        happyMessageMap.remove("id");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageId() throws Exception {
        // setup
        happyMessageMap.put("id", "");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_NoMessageType() throws Exception {
        // setup
        happyMessageMap.remove("type");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_EmptyMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_InvalidMessageType() throws Exception {
        // setup
        happyMessageMap.put("type", "invalid");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsIncorrect() throws Exception {
        // Setup
        happyDetailMap.clear();
        happyDetailMap.put("blah", "skdjfh");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsMissing() throws Exception {
        // Setup
        happyMessageMap.remove("detail");

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void init_ExceptionThrown_When_DetailMapIsEmpty() throws Exception {
        // Setup
        happyMessageMap.put("detail", new HashMap<String, Object>());

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void FullScreenMessage_ShouldThrowException_When_MissingHTMLInJsonPayload()
            throws CampaignMessageRequiredFieldMissingException {
        // setup
        happyDetailMap.remove("html");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test(expected = CampaignMessageRequiredFieldMissingException.class)
    public void FullScreenMessage_ShouldThrowException_When_EmptyHTMLInJsonPayload()
            throws CampaignMessageRequiredFieldMissingException {
        // setup
        happyDetailMap.put("html", "");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    @Test
    public void FullScreenMessage_ShouldNotThrowException_When_MissingAssetsInJsonPayload()
            throws CampaignMessageRequiredFieldMissingException {
        // setup
        happyDetailMap.remove("remoteAssets");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        new FullScreenMessage(
                mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));
    }

    // extractAsset
    @Test
    public void FullScreenMessage_shouldNotCreateAssets_When_AssetJsonIsNull()
            throws CampaignMessageRequiredFieldMissingException {
        // setup
        happyDetailMap.remove("remoteAssets");
        happyMessageMap.put("detail", happyDetailMap);

        // test
        final FullScreenMessage fullScreenMessage =
                new FullScreenMessage(
                        mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertEquals(new ArrayList<>(), fullScreenMessage.getAssetsList());
    }

    @Test
    public void FullScreenMessage_shouldCreateAssets_When_AssetJsonIsValid()
            throws CampaignMessageRequiredFieldMissingException {
        // test
        final FullScreenMessage fullScreenMessage =
                new FullScreenMessage(
                        mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // verify
        assertNotNull(fullScreenMessage.getAssetsList());
        assertEquals(fullScreenMessage.getAssetsList().get(0).size(), 2);
    }

    // showMessage
    @Test
    public void showMessage_shouldCallUIServiceWithOriginalHTML_When_AssetsAreNull() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    happyDetailMap.remove("remoteAssets");
                    happyMessageMap.put("detail", happyDetailMap);
                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify
                    verify(mockUIService, times(1))
                            .create(
                                    any(InAppMessage.class),
                                    any(PresentationUtilityProvider.class));
                    final String htmlContent =
                            inAppMessageArgumentCaptor.getValue().getSettings().getContent();
                    assertEquals(
                            "<!DOCTYPE html>\n"
                                    + "<html>\n"
                                    + "<head><meta charset=\"UTF-8\"></head>\n"
                                    + "<body>\n"
                                    + "everything is awesome http://asset1-url00.jpeg\n"
                                    + "</body>\n"
                                    + "</html>",
                            htmlContent);
                });
    }

    @Test
    public void showMessage_shouldCallSetLocalAssetsMapWithEmptyMap_When_NoAssets() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    happyDetailMap.remove("remoteAssets");
                    happyMessageMap.put("detail", happyDetailMap);
                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(Collections.emptyMap(), actualMap);
                });
    }

    @Test
    public void showMessage_Should_Call_setLocalAssetsMap_with_a_Non_Empty_Map_WhenAssetsPresent() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    final Map<String, String> expectedMap = new HashMap<>();
                    expectedMap.put(
                            "http://asset1-url00.jpeg",
                            "campaign/messages/07a1c997-2450-46f0-a454-537906404124");
                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(expectedMap, actualMap);
                });
    }

    // bundled asset caching
    @Test
    public void
            showMessage_ShouldCacheBundledAssets_When_RemoteAssetFailedToBeCachedAndLocalAssetPresentInConsequenceDetails() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    // simulate remote asset failed to be cached
                    Mockito.when(mockCacheService.get(anyString(), eq("http://asset1-url00.jpeg")))
                            .thenReturn(null);
                    // expected uri will be a remote uri
                    final Map<String, String> expectedMap = new HashMap<>();
                    expectedMap.put(
                            "http://asset1-url00.jpeg",
                            "campaign/messages/07a1c997-2450-46f0-a454-537906404124");

                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify cache service set is called as bundled asset is cached
                    verify(mockCacheService, times(1))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(expectedMap, actualMap);
                });
    }

    @Test
    public void
            showMessage_ShouldNotCacheBundledAssets_When_RemoteAssetWasCachedAndLocalAssetPresentInConsequenceDetails() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    // expected uri will be a remote uri
                    final Map<String, String> expectedMap = new HashMap<>();
                    expectedMap.put(
                            "http://asset1-url00.jpeg",
                            "campaign/messages/07a1c997-2450-46f0-a454-537906404124");

                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify cache service set is not called as the remote asset was able to be
                    // cached previously
                    verify(mockCacheService, times(0))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(expectedMap, actualMap);
                });
    }

    @Test
    public void
            showMessage_ShouldCacheBundledAssets_When_OnlyLocalAssetPresentInConsequenceDetails() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    // simulate remote asset not cached
                    Mockito.when(mockCacheService.get(anyString(), eq("http://asset1-url00.jpeg")))
                            .thenReturn(null);
                    // create remote asset map containing bundled image only
                    remoteAssets = new ArrayList<>();
                    remoteAssets.add("fallback.jpeg");
                    happyRemoteAssets = new ArrayList<>();
                    happyRemoteAssets.add(remoteAssets);
                    happyDetailMap.put("remoteAssets", happyRemoteAssets);
                    happyMessageMap.put("detail", happyDetailMap);
                    // expected uri will be a file uri
                    final Map<String, String> expectedMap = new HashMap<>();
                    expectedMap.put(
                            "file:///android_asset/fallback.jpeg",
                            "campaign/messages/07a1c997-2450-46f0-a454-537906404124");

                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify cache service set is called as bundled asset is cached
                    verify(mockCacheService, times(1))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(expectedMap, actualMap);
                });
    }

    @Test
    public void
            showMessage_ShouldNotCacheBundledAssets_When_OnlyRemoteAssetPresentInConsequenceDetails() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    // create remote asset map containing remote asset only
                    remoteAssets = new ArrayList<>();
                    remoteAssets.add("http://asset1-url00.jpeg");
                    happyRemoteAssets = new ArrayList<>();
                    happyRemoteAssets.add(remoteAssets);
                    happyDetailMap.put("remoteAssets", happyRemoteAssets);
                    happyMessageMap.put("detail", happyDetailMap);
                    // expected uri will be a remote uri
                    final Map<String, String> expectedMap = new HashMap<>();
                    expectedMap.put(
                            "http://asset1-url00.jpeg",
                            "campaign/messages/07a1c997-2450-46f0-a454-537906404124");

                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify cache service set is not called as the remote asset was the only asset
                    // provided
                    verify(mockCacheService, times(0))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(expectedMap, actualMap);
                });
    }

    @Test
    public void
            showMessage_ShouldNotCacheBundledAssets_When_RemoteAssetFailedToBeCachedAndLocalAssetNotPresentOnDevice() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    // simulate remote asset failed to be cached
                    Mockito.when(mockCacheService.get(anyString(), eq("http://asset1-url00.jpeg")))
                            .thenReturn(null);
                    try {
                        when(mockAssetManager.open(anyString())).thenThrow(IOException.class);
                    } catch (IOException ignored) {
                    }

                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify cache service set is not called as bundled asset is not present on the
                    // device
                    verify(mockCacheService, times(0))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    final Map<String, String> actualMap =
                            inAppMessageArgumentCaptor.getValue().getSettings().getAssetMap();
                    Assert.assertEquals(Collections.emptyMap(), actualMap);
                });
    }

    @Test
    public void showMessage_ShouldNotCacheBundledAssets_When_CacheServiceIsNotAvailable() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    Mockito.when(ServiceProvider.getInstance().getCacheService()).thenReturn(null);
                    try {
                        final FullScreenMessage fullScreenMessage =
                                new FullScreenMessage(
                                        mockCampaignExtension,
                                        TestUtils.createRuleConsequence(happyMessageMap));
                        // test
                        fullScreenMessage.showMessage();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }

                    // verify
                    verify(mockCacheService, times(0))
                            .set(anyString(), anyString(), any(CacheEntry.class));
                    verifyNoInteractions(mockFullscreenMessage);
                });
    }

    // fullscreen listener
    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldNotCallRemoveViewedClickedWithData_When_URLInvalid() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();
                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage, "http://www.adobe.com");
                        // verify
                        verify(mockCampaignFullScreenMessage, times(0)).clickedWithData(anyMap());
                        verify(mockCampaignFullScreenMessage, times(0)).viewed();
                        verify(mockFullscreenMessage, times(0)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldNotCallRemoveViewedClickedWithData_When_URLDoesNotContainValidScheme() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();
                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage,
                                "adbapp://confirm/?id=h11901a,86f10d,3&url=https://www.adobe.com");

                        // verify
                        verify(mockCampaignFullScreenMessage, times(0)).clickedWithData(anyMap());
                        verify(mockCampaignFullScreenMessage, times(0)).viewed();
                        verify(mockFullscreenMessage, times(0)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldCallRemoveViewedClickedWithData_When_URLIsConfirmTagId3() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();
                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage,
                                "adbinapp://confirm/?id=h11901a,86f10d,3&url=https://www.adobe.com");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,3", clickedDataMap.get("id"));
                        assertEquals("https://www.adobe.com", clickedDataMap.get("url"));
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(1)).openUri("https://www.adobe.com");
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldCallRemoveViewedClickedWithData_When_URLIsConfirmTagId4() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage,
                                "adbinapp://confirm/?id=h11901a,86f10d,4&url=https://www.adobe.com");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,4", clickedDataMap.get("id"));
                        assertEquals("https://www.adobe.com", clickedDataMap.get("url"));
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(1)).openUri("https://www.adobe.com");
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldCallRemoveViewedClickedWithData_When_URLIsConfirmTagId3WithoutRedirectUrl() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage, "adbinapp://confirm/?id=h11901a,86f10d,3");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,3", clickedDataMap.get("id"));
                        assertNull(clickedDataMap.get("url"));
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(0)).openUri(anyString());
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldCallRemoveViewedClickedWithData_When_URLIsConfirmTag4WithoutRedirectUrl() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage, "adbinapp://confirm/?id=h11901a,86f10d,4");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,4", clickedDataMap.get("id"));
                        assertNull(clickedDataMap.get("url"));
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(0)).openUri(anyString());
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ShouldCallRemoveViewedClickedWithData_When_URLIsCancelTagId5() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage, "adbinapp://cancel?id=h11901a,86f10d,5");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("cancel", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,5", clickedDataMap.get("id"));
                        assertNull(clickedDataMap.get("url"));
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(0)).openUri(anyString());
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ClickedWithDataMapUrlContainsAllQueryParameterKeyValuePairs_When_ClickThroughUrlIsUrlEncoded() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test, url is https://www.adobe.com/?key1=value1&key2=value2&key3=value3
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage,
                                "adbinapp://confirm/?id=h11901a,86f10d,4&url=https%3A%2F%2Fwww.adobe.com%2F%3Fkey1%3Dvalue1%26key2%3Dvalue2%26key3%3Dvalue3");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,4", clickedDataMap.get("id"));
                        final String clickthroughUrl = (String) clickedDataMap.get("url");
                        assertEquals(
                                "https://www.adobe.com/?key1=value1&key2=value2&key3=value3",
                                clickthroughUrl);
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(1)).openUri(eq(clickthroughUrl));
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void
            fullscreenListenerOverrideUrlLoad_ClickedWithDataMapUrlContainsAllQueryParameterKeys_When_ClickThroughUrlIsUrlEncodedAndContainsQueryParametersWithEmptyValues() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test, url is https://www.adobe.com/?key1=&key2=value2&key3=&key4=value4
                        fullScreenMessageUiListener.onUrlLoading(
                                mockFullscreenMessage,
                                "adbinapp://confirm/?id=h11901a,86f10d,4&url=https%3A%2F%2Fwww.adobe.com%2F%3Fkey1%3D%26key2%3Dvalue2%26key3%3D%26key4%3Dvalue4");

                        // verify
                        verify(mockCampaignExtension, times(2))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(2, messageInteractions.size());
                        Map<String, Object> clickedDataMap = messageInteractions.get(0);
                        assertEquals("confirm", clickedDataMap.get("type"));
                        assertEquals("h11901a,86f10d,4", clickedDataMap.get("id"));
                        final String clickthroughUrl = (String) clickedDataMap.get("url");
                        assertEquals(
                                "https://www.adobe.com/?key1=&key2=value2&key3=&key4=value4",
                                clickthroughUrl);
                        assertEquals("1", clickedDataMap.get("a.message.clicked"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                clickedDataMap.get("a.message.id"));
                        Map<String, Object> viewedDataMap = messageInteractions.get(1);
                        assertEquals("1", viewedDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                viewedDataMap.get("a.message.id"));
                        verify(mockUriService, times(1)).openUri(eq(clickthroughUrl));
                        verify(mockFullscreenMessage, times(1)).dismiss();
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void fullscreenListenerOnDismiss_happy() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onDismiss(mockFullscreenMessage);

                        // verify
                        verify(mockCampaignExtension, times(1))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(1, messageInteractions.size());
                        Map<String, Object> dismissDataMap = messageInteractions.get(0);
                        assertEquals("1", dismissDataMap.get("a.message.viewed"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                dismissDataMap.get("a.message.id"));
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void fullscreenListenerOnShow_happy() {
        // setup
        setupServiceProviderMockAndRunTest(
                () -> {
                    try {
                        ArgumentCaptor<Map<String, Object>> mapArgumentCaptor =
                                ArgumentCaptor.forClass(Map.class);
                        final FullScreenMessage.FullScreenMessageUiListener
                                fullScreenMessageUiListener =
                                        new FullScreenMessage(
                                                mockCampaignExtension,
                                                TestUtils.createRuleConsequence(happyMessageMap))
                                        .new FullScreenMessageUiListener();

                        // test
                        fullScreenMessageUiListener.onShow(mockFullscreenMessage);

                        // verify
                        verify(mockCampaignExtension, times(1))
                                .dispatchMessageInteraction(mapArgumentCaptor.capture());
                        List<Map<String, Object>> messageInteractions =
                                mapArgumentCaptor.getAllValues();
                        assertEquals(1, messageInteractions.size());
                        Map<String, Object> dismissDataMap = messageInteractions.get(0);
                        assertEquals("1", dismissDataMap.get("a.message.triggered"));
                        assertEquals(
                                "07a1c997-2450-46f0-a454-537906404124",
                                dismissDataMap.get("a.message.id"));
                    } catch (CampaignMessageRequiredFieldMissingException exception) {
                        fail(exception.getMessage());
                    }
                });
    }

    @Test
    public void shouldDownloadAssets_ReturnsTrue_happy() throws Exception {
        // Setup
        final FullScreenMessage fullScreenMessage =
                new FullScreenMessage(
                        mockCampaignExtension, TestUtils.createRuleConsequence(happyMessageMap));

        // test
        boolean shouldDownloadAssets = fullScreenMessage.shouldDownloadAssets();

        // verify
        assertTrue(shouldDownloadAssets);
    }
}
