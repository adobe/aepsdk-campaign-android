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

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.adobe.marketing.mobile.ExtensionApi;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRule;
import com.adobe.marketing.mobile.launch.rulesengine.LaunchRulesEngine;
import com.adobe.marketing.mobile.launch.rulesengine.RuleConsequence;
import com.adobe.marketing.mobile.services.DeviceInforming;
import com.adobe.marketing.mobile.services.HttpConnecting;
import com.adobe.marketing.mobile.services.NetworkCallback;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheEntry;
import com.adobe.marketing.mobile.services.caching.CacheResult;
import com.adobe.marketing.mobile.services.caching.CacheService;
import com.adobe.marketing.mobile.services.ui.UIService;

@RunWith(MockitoJUnitRunner.Silent.class)
public class CampaignRulesDownloaderTests {

    private CampaignRulesDownloader campaignRulesDownloader;

    private File cacheDir;
    private HashMap<String, Object> detailMap;
    private ArrayList<ArrayList<String>> remoteAssetsList;
    private ArrayList<String> remoteAssetOne;
    private ArrayList<String> remoteAssetTwo;
    private final File zipFile = TestUtils.getResource("campaign_rules.zip");
    private final File ruleJsonFile = TestUtils.getResource("rules.json");
    private HashMap<String, String> metadataMap;
    private static final String ETAG = "\"ABCDE-12345\"";
    private static final String WEAK_ETAG = "W/\"ABCDE-12345\"";
    private static final String TIME_SINCE_EPOCH_MILLISECONDS = "1669896000000";
    private static final String TIME_SINCE_EPOCH_RFC2882 = "Thu, 01 Dec 2022 12:00:00 GMT";
    private static final String messageId = "07a1c997-2450-46f0-a454-537906404124";
    private static final String MESSAGES_CACHE = CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.MESSAGE_CACHE_DIR + File.separator;
    private FakeNamedCollection fakeNamedCollection = new FakeNamedCollection();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    ExtensionApi mockExtensionApi;
    @Mock
    ServiceProvider mockServiceProvider;
    @Mock
    LaunchRulesEngine mockRulesEngine;
    @Mock
    UIService mockUIService;
    @Mock
    CacheService mockCacheService;
    @Mock
    CacheResult mockCacheResult;
    @Mock
    DeviceInforming mockDeviceInfoService;
    @Mock
    Networking mockNetworkService;
    @Mock
    HttpConnecting mockHttpConnection;
    @Mock
    LaunchRule mockLaunchRule;
    @Mock
    RuleConsequence mockRuleConsequence;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        String sha256HashForRemoteUrl = "fb0d3704b73d5fa012a521ea31013a61020e79610a3c27e8dd1007f3ec278195";
        String cachedFileName = sha256HashForRemoteUrl + ".12345"; //12345 is just a random extension.

        metadataMap = new HashMap<>();
        metadataMap.put(CampaignConstants.METADATA_PATH, MESSAGES_CACHE + messageId + File.separator + cachedFileName);
        metadataMap.put(CampaignConstants.HTTP_HEADER_ETAG, ETAG);
        metadataMap.put(CampaignConstants.HTTP_HEADER_LAST_MODIFIED, TIME_SINCE_EPOCH_MILLISECONDS);

        remoteAssetOne = new ArrayList<>();
        remoteAssetOne.add("http://asset1-url00.jpeg");
        remoteAssetOne.add("http://asset1-url01.jpeg");
        remoteAssetOne.add("01.jpeg");

        remoteAssetTwo = new ArrayList<>();
        remoteAssetTwo.add("http://asset2-url10.jpeg");
        remoteAssetTwo.add("http://asset2-url11.jpeg");

        remoteAssetsList = new ArrayList<>();
        remoteAssetsList.add(remoteAssetOne);
        remoteAssetsList.add(remoteAssetTwo);

        detailMap = new HashMap<>();
        detailMap.put("template", "fullscreen");
        detailMap.put("html", "happy_test.html");
        detailMap.put("remoteAssets", remoteAssetsList);

        when(mockRuleConsequence.getDetail()).thenReturn(detailMap);
        when(mockRuleConsequence.getId()).thenReturn(messageId);
        when(mockRuleConsequence.getType()).thenReturn(CampaignConstants.MESSAGE_CONSEQUENCE_MESSAGE_TYPE);
    }

    @After
    public void tearDown() {
        clearCacheFiles(cacheDir);
        fakeNamedCollection = new FakeNamedCollection();
    }

    /**
     * Deletes the directory and all files inside it.
     *
     * @param file instance of {@link File} points to the directory need to be deleted.
     */
    private static void clearCacheFiles(final File file) {
        // clear files from directory first
        if (file.isDirectory()) {
            String[] children = file.list();

            if (children != null) {
                for (final String child : children) {
                    final File childFile = new File(file, child);
                    clearCacheFiles(childFile);
                }
            }
        }

        file.delete(); // delete file or empty directory
    }

    private void setupServiceProviderMockAndRunTest(boolean networkServiceNull, Runnable testRunnable) {
        cacheDir = new File("cache");
        cacheDir.mkdirs();
        cacheDir.setWritable(true);
        try (MockedStatic<ServiceProvider> serviceProviderMockedStatic = Mockito.mockStatic(ServiceProvider.class)) {
            serviceProviderMockedStatic.when(ServiceProvider::getInstance).thenReturn(mockServiceProvider);
            when(mockCacheResult.getData()).thenReturn(new FileInputStream(ruleJsonFile));
            when(mockCacheResult.getMetadata()).thenReturn(metadataMap);
            when(mockCacheService.set(anyString(), anyString(), any(CacheEntry.class))).thenReturn(true);
            when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME))).thenReturn(mockCacheResult);
            when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR), eq(CampaignConstants.RULES_JSON_FILE_NAME))).thenReturn(mockCacheResult);
            when(mockServiceProvider.getCacheService()).thenReturn(mockCacheService);
            when(mockServiceProvider.getDeviceInfoService()).thenReturn(mockDeviceInfoService);
            when(mockServiceProvider.getUIService()).thenReturn(mockUIService);
            if (!networkServiceNull) {
                when(mockServiceProvider.getNetworkService()).thenReturn(mockNetworkService);
            } else {
                when(mockServiceProvider.getNetworkService()).thenReturn(null);
            }
            when(mockDeviceInfoService.getApplicationCacheDir()).thenReturn(cacheDir);
            // create CampaignRulesDownloader instance
            campaignRulesDownloader = new CampaignRulesDownloader(mockExtensionApi, mockRulesEngine, fakeNamedCollection, mockCacheService);
            testRunnable.run();
        } catch (FileNotFoundException e) {
            fail(e.getMessage());
        }
    }

    // =================================================================================================================
    // void loadRulesFromUrl(final String url, final String linkageFields)
    // =================================================================================================================
    @Test
    public void test_loadRulesFromUrl_When_urlIsNull() {
        // setup
        setupServiceProviderMockAndRunTest(false, () -> {
            // test
            campaignRulesDownloader.loadRulesFromUrl(null, null);

            // verify no network request
            verify(mockNetworkService, times(0)).connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_networkServiceIsNull() {
        // setup
        setupServiceProviderMockAndRunTest(true, () -> {
            // test
            campaignRulesDownloader.loadRulesFromUrl(null, null);

            // verify no network request
            verify(mockNetworkService, times(0)).connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_etagPresentInCachedFileMetadata_Then_RuleDownloadRequestContainsEtagInHeaders() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
        // setup cached rules zip
        when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.ZIP_HANDLE))).thenReturn(mockCacheResult);

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            try {
                when(mockHttpConnection.getInputStream()).thenReturn(new FileInputStream(zipFile));
            } catch (FileNotFoundException e) {
                fail(e.getMessage());
            }
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request headers contain etag
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(ETAG, headers.get(CampaignConstants.HTTP_HEADER_IF_NONE_MATCH));
            assertEquals(TIME_SINCE_EPOCH_RFC2882, headers.get(CampaignConstants.HTTP_HEADER_IF_MODIFIED_SINCE));
            // verify extracted rules json is cached
            verify(mockCacheService, times(1)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules json is retrieved to be loaded into the rules engine
            verify(mockCacheService, times(1)).get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME));
            // verify rules remote url added to named collection
            assertEquals(rulesUrl, fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules loaded into the rules engine
            verify(mockRulesEngine, times(1)).replaceRules(any());
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_WeakEtagPresentInCachedFileMetadata_Then_RuleDownloadRequestContainsWeakEtagInHeaders() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
        // setup cached rules zip
        when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.ZIP_HANDLE))).thenReturn(mockCacheResult);
        // setup metadata map with weak etag
        metadataMap.put(CampaignConstants.HTTP_HEADER_ETAG, WEAK_ETAG);

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            try {
                when(mockHttpConnection.getInputStream()).thenReturn(new FileInputStream(zipFile));
            } catch (FileNotFoundException e) {
                fail(e.getMessage());
            }
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request headers contain weak etag
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(WEAK_ETAG, headers.get(CampaignConstants.HTTP_HEADER_IF_NONE_MATCH));
            assertEquals(TIME_SINCE_EPOCH_RFC2882, headers.get(CampaignConstants.HTTP_HEADER_IF_MODIFIED_SINCE));
            // verify extracted rules json is cached
            verify(mockCacheService, times(1)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules json is retrieved to be loaded into the rules engine
            verify(mockCacheService, times(1)).get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME));
            // verify rules remote url added to named collection
            assertEquals(rulesUrl, fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules loaded into the rules engine
            verify(mockRulesEngine, times(1)).replaceRules(any());
        });
    }


    @Test
    public void test_loadRulesFromUrl_When_NoCachedMessagesPresent_Then_RuleDownloadRequestContainsNoEtagInHeaders() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
        // setup no cached rules zip present
        when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR), eq(CampaignConstants.ZIP_HANDLE))).thenReturn(null);

        metadataMap.put(CampaignConstants.HTTP_HEADER_ETAG, WEAK_ETAG);
        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            try {
                when(mockHttpConnection.getInputStream()).thenReturn(new FileInputStream(zipFile));
            } catch (FileNotFoundException e) {
                fail(e.getMessage());
            }
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request has empty headers
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(0, headers.size());
            // verify extracted rules json is cached
            verify(mockCacheService, times(1)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules json is retrieved to be loaded into the rules engine
            verify(mockCacheService, times(1)).get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME));
            // verify rules remote url added to named collection
            assertEquals(rulesUrl, fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules loaded into the rules engine
            verify(mockRulesEngine, times(1)).replaceRules(any());
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_LinkageFieldsSet_Then_RuleDownloadRequestContainsEncodedLinkageFieldsInHeaders() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
        // setup cached rules zip
        when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.ZIP_HANDLE))).thenReturn(mockCacheResult);
        // setup encoded linkage fields string
        String linkageFields = "dXNlck5hbWU6dGVzdFVzZXI="; // userName:testUser

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            try {
                when(mockHttpConnection.getInputStream()).thenReturn(new FileInputStream(zipFile));
            } catch (FileNotFoundException e) {
                fail(e.getMessage());
            }
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, linkageFields);

            // verify network request headers contain linkage fields
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(ETAG, headers.get(CampaignConstants.HTTP_HEADER_IF_NONE_MATCH));
            assertEquals(TIME_SINCE_EPOCH_RFC2882, headers.get(CampaignConstants.HTTP_HEADER_IF_MODIFIED_SINCE));
            assertEquals(linkageFields, headers.get(CampaignConstants.LINKAGE_FIELD_NETWORK_HEADER));
            // verify extracted rules json is cached
            verify(mockCacheService, times(1)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules json is retrieved to be loaded into the rules engine
            verify(mockCacheService, times(1)).get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME));
            // verify rules remote url added to named collection
            assertEquals(rulesUrl, fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules loaded into the rules engine
            verify(mockRulesEngine, times(1)).replaceRules(any());
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_RuleDownloadRequestResponseContainsNotModified_Then_RulesLoadedFromCache() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);
        // setup cached rules zip
        when(mockCacheService.get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.ZIP_HANDLE))).thenReturn(mockCacheResult);

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_MODIFIED);
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request headers contain etag
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(ETAG, headers.get(CampaignConstants.HTTP_HEADER_IF_NONE_MATCH));
            assertEquals(TIME_SINCE_EPOCH_RFC2882, headers.get(CampaignConstants.HTTP_HEADER_IF_MODIFIED_SINCE));
            // verify no extracted rules json is cached
            verify(mockCacheService, times(0)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules remote url not added to named collection
            assertEquals("", fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules not loaded
            verify(mockRulesEngine, times(0)).replaceRules(any());
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_RuleDownloadRequestResponseContainsNotFound_Then_NoRulesLoadedOrCached() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_NOT_FOUND);
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(0, headers.size());
            // verify no extracted rules json is cached
            verify(mockCacheService, times(0)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify rules remote url not added to named collection
            assertEquals("", fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify rules not loaded into the rules engine
            verify(mockRulesEngine, times(0)).replaceRules(any());
        });
    }

    @Test
    public void test_loadRulesFromUrl_When_InvalidZipContentDownloaded_Then_NoRulesLoadedOrCached() {
        // setup
        ArgumentCaptor<NetworkRequest> networkRequestArgumentCaptor = ArgumentCaptor.forClass(NetworkRequest.class);

        setupServiceProviderMockAndRunTest(false, () -> {
            when(mockHttpConnection.getResponseCode()).thenReturn(HttpURLConnection.HTTP_OK);
            when(mockHttpConnection.getInputStream()).thenReturn(null);
            doAnswer((Answer<Void>) invocation -> {
                NetworkCallback callback = invocation.getArgument(1);
                callback.call(mockHttpConnection);
                return null;
            }).when(mockNetworkService)
                    .connectAsync(any(NetworkRequest.class), any(NetworkCallback.class));
            String rulesUrl =
                    "https://mcias-va7.cloud.adobe.io/mcias/mcias.campaign-demo.adobe.com/PR146b40abd1be4a0ab224c16cbdc04bff/37922783516695133647566171476397216484/rules.zip";

            // test
            campaignRulesDownloader.loadRulesFromUrl(rulesUrl, null);

            // verify network request
            verify(mockNetworkService, times(1)).connectAsync(networkRequestArgumentCaptor.capture(), any(NetworkCallback.class));
            NetworkRequest capturedNetworkRequest = networkRequestArgumentCaptor.getValue();
            Map<String, String> headers = capturedNetworkRequest.getHeaders();
            assertEquals(0, headers.size());
            // verify no extracted rules json is cached
            verify(mockCacheService, times(0)).set(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq("rules.json"), any(CacheEntry.class));
            // verify no rules json is retrieved to be loaded into the rules engine
            verify(mockCacheService, times(0)).get(eq(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER), eq(CampaignConstants.RULES_JSON_FILE_NAME));
            // verify no rules remote url added to named collection
            assertEquals("", fakeNamedCollection.getString(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REMOTES_URL_KEY, ""));
            // verify no rules loaded into the rules engine
            verify(mockRulesEngine, times(0)).replaceRules(any());
        });
    }

    // =================================================================================================================
    //  void cacheRemoteAssets(final List<LaunchRule> campaignRules)
    // =================================================================================================================
    @Test
    public void test_cacheRemoteAssets_When_campaignRulesAreEmpty_Then_AssetsNotDownloaded() {
        // setup
        try (MockedConstruction mockConstruction = mockConstruction(CampaignMessageAssetsDownloader.class)) {
            setupServiceProviderMockAndRunTest(false, () -> {
                List<LaunchRule> campaignRules = new ArrayList<>();

                // test
                campaignRulesDownloader.cacheRemoteAssets(campaignRules);

                // verify no CampaignMessageAssetsDownloader instance instantiated
                assertEquals(0, mockConstruction.constructed().size());
            });
        }
    }

    @Test
    public void test_cacheRemoteAssets_When_campaignRulesValid_Then_AssetsDownloaded() {
        // setup
        try (MockedConstruction mockConstruction = mockConstruction(CampaignMessageAssetsDownloader.class)) {
            List<RuleConsequence> ruleConsequenceList = new ArrayList<>();
            ruleConsequenceList.add(mockRuleConsequence);
            when(mockLaunchRule.getConsequenceList()).thenReturn(ruleConsequenceList);

            setupServiceProviderMockAndRunTest(false, () -> {
                List<LaunchRule> campaignRules = new ArrayList<>();
                campaignRules.add(mockLaunchRule);

                // test
                campaignRulesDownloader.cacheRemoteAssets(campaignRules);

                // verify
                CampaignMessageAssetsDownloader mockCampaignMessageAssetsDownloader = (CampaignMessageAssetsDownloader) mockConstruction.constructed().get(0);
                verify(mockCampaignMessageAssetsDownloader, times(1)).downloadAssetCollection();
            });
        }
    }

    @Test
    public void test_cacheRemoteAssets_When_campaignRuleAssetsEmpty_Then_AssetsNotDownloaded() {
        // setup
        detailMap.put("remoteAssets", new ArrayList<>());
        when(mockRuleConsequence.getDetail()).thenReturn(detailMap);
        try (MockedConstruction mockConstruction = mockConstruction(CampaignMessageAssetsDownloader.class)) {
            List<RuleConsequence> ruleConsequenceList = new ArrayList<>();
            ruleConsequenceList.add(mockRuleConsequence);
            when(mockLaunchRule.getConsequenceList()).thenReturn(ruleConsequenceList);

            setupServiceProviderMockAndRunTest(false, () -> {
                List<LaunchRule> campaignRules = new ArrayList<>();
                campaignRules.add(mockLaunchRule);

                // test
                campaignRulesDownloader.cacheRemoteAssets(campaignRules);

                // verify no CampaignMessageAssetsDownloader instance instantiated
                assertEquals(0, mockConstruction.constructed().size());
            });
        }
    }
}