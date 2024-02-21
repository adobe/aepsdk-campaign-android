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

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.adobe.marketing.mobile.AdobeCallback;
import com.adobe.marketing.mobile.Campaign;
import com.adobe.marketing.mobile.Identity;
import com.adobe.marketing.mobile.Lifecycle;
import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.MobilePrivacyStatus;
import com.adobe.marketing.mobile.SDKHelper;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.ServiceProvider;

class Retry implements TestRule {
	private int numberOfTestAttempts;
	private int currentTestRun;

	public Retry(final int numberOfTestAttempts) {
		this.numberOfTestAttempts = numberOfTestAttempts;
	}

	public Statement apply(final Statement base, final Description description) {
		return statement(base, description);
	}

	private Statement statement(final Statement base, final Description description) {
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				Throwable caughtThrowable = null;

				for (int i = 0; i < numberOfTestAttempts; i++) {
					currentTestRun = i + 1;

					try {
						base.evaluate();
						return;
					} catch (Throwable t) {
						caughtThrowable = t;
						System.err.println(description.getDisplayName() + ": run " + currentTestRun + " failed, " +
										   (numberOfTestAttempts - currentTestRun) + " retries remain.");
						System.err.println("test failure caused by: " + caughtThrowable.getLocalizedMessage());
					}
				}

				System.err.println(description.getDisplayName() + ": giving up after " + numberOfTestAttempts + " failures");
				throw caughtThrowable;
			}
		};
	}
}

@RunWith(AndroidJUnit4.class)
public class CampaignFunctionalTests {

	@Rule
	public RuleChain rule = RuleChain.outerRule(new com.adobe.marketing.mobile.campaign.TestHelper.SetupCoreRule());

	// A test will be run at most 3 times
	@Rule
	public Retry totalTestCount = new Retry(3);

	static final String FIRST_NAME = "John";
	static final String LAST_NAME = "Doe";
	static final String EMAIL = "john.doe@email.com";
	static final String PUSH_PLATFORM = "gcm";
	static final String PAYLOAD_STRING = "{\"marketingCloudId\":\"%s\",\"pushPlatform\":\"%s\"}";
	static final String FIRST_NAME_FIELD = "\"cusFirstName\":\"%s\"";
	static final String LAST_NAME_FIELD = "\"cusLastName\":\"%s\"";
	static final String EMAIL_FIELD = "\"cusEmail\":\"%s\"";
	static boolean deleteAllCache = false;
	static final String IF_NONE_MATCH_HEADER_KEY = "If-None-Match";
	static final String IF_MODIFIED_HEADER_KEY = "If-Modified-Since";
	static final String CAMPAIGN_DATASTORE = "CampaignDataStore";
	static String lastModified = null;
	TestableNetworkService testableNetworkService;

	@Before
	public void setup() {
		MobileCore.setApplication(TestHelper.defaultApplication);
		MobileCore.clearUpdatedConfiguration();
		testableNetworkService = new TestableNetworkService();
		ServiceProvider.getInstance().getDataQueueService().getDataQueue(CampaignConstants.EXTENSION_NAME).clear();

		// set fake network service for testing
		ServiceProvider.getInstance().setNetworkService(testableNetworkService);

		TestHelper.setIdentityPersistence(TestHelper.createIdentityMap("ECID", "mockECID"), com.adobe.marketing.mobile.campaign.TestHelper.defaultApplication);
		Campaign.registerExtension();
		Identity.registerExtension();
		Lifecycle.registerExtension();

		MobileCore.start((AdobeCallback) o -> {});

		setupProgrammaticConfig();
	}

	@After
	public void tearDown() {
		lastModified = null;
		ServiceProvider.getInstance().getDataStoreService().getNamedCollection(CAMPAIGN_DATASTORE).removeAll();
		SDKHelper.resetSDK();
		cleanCache(TestHelper.defaultApplication.getApplicationContext());
	}

	private void setupProgrammaticConfig() {
		MobileCore.setLogLevel(LoggingMode.DEBUG);
		HashMap<String, Object> data = new HashMap<String, Object>();
		// ============================================================
		// global
		// ============================================================
		data.put(TestConstants.GLOBAL_PRIVACY, "optedin");
		data.put(TestConstants.GLOBAL_SSL, true);
		data.put(TestConstants.BUILD_ENVIRONMENT, "prod");
		data.put(TestConstants.PROPERTY_ID, "my_property_id");
		// ============================================================
		// campaign
		// ============================================================
		data.put(TestConstants.CAMPAIGN_TIMEOUT, 5);
		data.put(TestConstants.CAMPAIGN_SERVER, TestConstants.MOCK_CAMPAIGN_SERVER);
		data.put(TestConstants.CAMPAIGN_MCIAS, TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias");
		data.put(TestConstants.CAMPAIGN_PKEY, "pkey");
		// ============================================================
		// lifecycle
		// ============================================================
		data.put(TestConstants.LIFECYCLE_SESSION_TIMEOUT, 1);
		// ============================================================
		// identity
		// ============================================================
		data.put(TestConstants.IDENTITY_ORG_ID, "B1F855165B4C9EA50A495E06@AdobeOrg");
		data.put(TestConstants.IDENTITY_SERVER, TestConstants.MOCK_IDENTITY_SERVER);
		// ============================================================
		// rules
		// ============================================================
		data.put(TestConstants.RULES_SERVER, "https://" + TestConstants.MOCK_RULES_SERVER + "/dummy-rules.zip");
		MobileCore.updateConfiguration(data);
		testableNetworkService.waitAndGetCount(3, 5000);
		testableNetworkService.reset();
	}

	private String getExperienceCloudId() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] retrievedId = new String[1];
		AdobeCallback callback = (AdobeCallback<String>) data -> {
			retrievedId[0] = data;
			latch.countDown();
		};
		Identity.getExperienceCloudId(callback);
		latch.await(3, TimeUnit.SECONDS);

		return retrievedId[0];
	}

	private void setBuildEnvironment(final String buildEnvironment) {
		HashMap<String, Object> data = new HashMap<>();
		data.put(TestConstants.BUILD_ENVIRONMENT, buildEnvironment);
		data.put(TestConstants.CAMPAIGN_PKEY, "pkey_" + buildEnvironment);
		data.put(TestConstants.CAMPAIGN_SERVER, TestConstants.MOCK_CAMPAIGN_SERVER + "/" + buildEnvironment);
		MobileCore.updateConfiguration(data);
		TestHelper.sleep(2000);
		testableNetworkService.clearCapturedRequests();
	}

	private String decodeBase64(final String encodedString) {
		byte[] decodedBytes = new byte[0];

		try {
			decodedBytes = Base64.decode(encodedString.getBytes("UTF-8"), Base64.DEFAULT);
		} catch (UnsupportedEncodingException e) {
		}

		return new String(decodedBytes);
	}

	private static void cleanCache(Context defaultContext) {
		boolean success;

		if (defaultContext.equals(null)) {
			System.out.println("Default context is null, cannot clean cache\n");
			return;
		}

		File cache = defaultContext.getCacheDir();
		System.out.println("Cleaning cache directory: " + cache.getAbsolutePath());


		if (deleteAllCache) {
			deleteDir(cache);
			System.out.println("Inside cleanCache: Cleaned entire cache directory\n");
			return;
		}

		if (cache.exists() && cache.list().length != 0) {
			String[] children = cache.list();

			for (String s : children) {
				if (s != null) {
					success = deleteDir(new File(cache, s));

					if (success) {
						System.out.println("Inside cleanCache: Child folder " + s + " DELETED\n");
					}
				}
			}
		}
	}

	private static boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();

			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));

				if (!success) {
					return false;
				}
			}
		}

		return dir.delete();
	}

	private void updateTimestampInDatastore(final long timestamp) {
		ServiceProvider.getInstance().getDataStoreService().getNamedCollection(CAMPAIGN_DATASTORE).setLong("CampaignRegistrationTimestamp", timestamp);
	}

	private void updateEcidInDatastore(final String ecid) {
		ServiceProvider.getInstance().getDataStoreService().getNamedCollection(CAMPAIGN_DATASTORE).setString("ExperienceCloudId", ecid);
	}

	private void setRegistrationDelayOrRegistrationPaused(final int registrationDelay, final boolean registrationPaused) {
		HashMap<String, Object> data = new HashMap<>();
		data.put(TestConstants.CAMPAIGN_REGISTRATION_DELAY, registrationDelay);
		data.put(TestConstants.CAMPAIGN_REGISTRATION_PAUSED, registrationPaused);
		MobileCore.updateConfiguration(data);
		TestHelper.sleep(3000);
		testableNetworkService.clearCapturedRequests();
	}

	// profile update tests
	// Test Case No : 1
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnLifecycleLaunch() throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		testableNetworkService.setNetworkResponse(expectedUrl, "OK", 200);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 2
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnLifecycleLaunchWhenPrivacyIsOptedOut() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_IDENTITY_SERVER + "/demoptout.jpg?d_mid=" + experienceCloudId + "&d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg";
		testableNetworkService.setNetworkResponse(expectedUrl, "OK", 200);
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest optOutRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(optOutRequest);
		assertEquals(expectedUrl, optOutRequest.getUrl());
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.lifecycleStart(null);
		// verify
		optOutRequest = testableNetworkService.getRequest(expectedUrl);
		assertNull(optOutRequest);
	}

	// Test Case No : 3
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyProfileUpdateRetriedWhenARecoverableNetworkErrorIsEncountered()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		testableNetworkService.setNetworkResponse(expectedUrl, "GATEWAY_TIMEOUT", 504);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		assertEquals(expectedUrl, profileUpdateRequest.getUrl());
		String payload = new String(profileUpdateRequest.getBody());
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));
		testableNetworkService.clearCapturedRequests();
		testableNetworkService.setNetworkResponse(expectedUrl, "OK", 200);
		TestHelper.sleep(31000);
		// verify request retried
		testableNetworkService.waitForRequest(expectedUrl);
		profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		assertEquals(expectedUrl, profileUpdateRequest.getUrl());
		payload = new String(profileUpdateRequest.getBody());
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));
		testableNetworkService.clearCapturedRequests();
		// verify no more retried requests due to 200 response
		TestHelper.sleep(31000);
		assertEquals(0, testableNetworkService.waitAndGetCount(0));
	}

	// linkage Fields tests
	// Test Case No : 4
	@Test
	public void test_Functional_Campaign_setLinkageFields_VerifyRulesDownloadRequestWithCustomHttpHeader() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-personalized.zip", expectedUrl);
		testableNetworkService.clearCapturedRequests();
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(rulesDownloadRequest);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map<String, String> headers = rulesDownloadRequest.getHeaders();
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
	}

	// Test Case No : 5
	@Test
	public void test_Functional_Campaign_setLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyOptOut() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_IDENTITY_SERVER + "/demoptout.jpg?d_mid=" + experienceCloudId + "&d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg";
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest optOutRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(optOutRequest);
		assertEquals(expectedUrl, optOutRequest.getUrl());
		testableNetworkService.clearCapturedRequests();
		// setup
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		// test
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 6
	@Test
	public void test_Functional_Campaign_setLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyUnknown() {
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.UNKNOWN);
		TestHelper.sleep(1000);
		testableNetworkService.clearCapturedRequests();
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 7
	@Test
	public void
	test_Functional_Campaign_setLinkageFields_VerifyRulesDownloadRequestWithCustomHttpHeader_WhenInvokedAfterResetLinkageFields()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-broadcast.zip", expectedUrl);
		// test
		Campaign.resetLinkageFields();
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		// setup for testing with linkage fields
		testableNetworkService.reset();
		testableNetworkService.setResponseFromFile("zip/rules-personalized.zip", expectedUrl);
		// test after setting up linkage fields
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map<String, String> headers = rulesDownloadRequest.getHeaders();
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
	}

	// Test Case No : 8
	@Test
	public void test_Functional_Campaign_resetLinkageFields_VerifyRulesDownloadRequestWithoutCustomHttpHeader() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-broadcast.zip", expectedUrl);
		// test
		Campaign.resetLinkageFields();
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		assertEquals(0, rulesDownloadRequest.getHeaders().size());
	}

	// Test Case No : 9
	@Test
	public void test_Functional_Campaign_resetLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyOptOut() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_IDENTITY_SERVER + "/demoptout.jpg?d_mid=" + experienceCloudId + "&d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg";
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest optOutRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(optOutRequest);
		assertEquals(expectedUrl, optOutRequest.getUrl());
		testableNetworkService.clearCapturedRequests();
		Campaign.resetLinkageFields();
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 10
	@Test
	public void test_Functional_Campaign_resetLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyOptUnknown() throws
		InterruptedException {
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.UNKNOWN);
		TestHelper.sleep(1000);
		testableNetworkService.clearCapturedRequests();
		Campaign.resetLinkageFields();
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 11
	@Test
	public void
	test_Functional_Campaign_resetLinkageFields_VerifyRulesDownloadRequestWithoutCustomHttpHeader_WhenInvokedAfterSetLinkageFields()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-personalized.zip", expectedUrl);
		testableNetworkService.clearCapturedRequests();
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map<String, String> headers = rulesDownloadRequest.getHeaders();
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
		// setup for second part
		testableNetworkService.clearCapturedRequests();
		expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-broadcast.zip", expectedUrl);
		// test
		Campaign.resetLinkageFields();
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		assertNull(rulesDownloadRequest.getHeaders().get("X-InApp-Auth"));
	}

	// Test Case No : 12
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesDownloadRequestWithoutCustomHttpHeader_AfterPrivacyOptOutThenOptIn()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		testableNetworkService.setResponseFromFile("zip/rules-personalized.zip", expectedUrl);
		testableNetworkService.clearCapturedRequests();
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map<String, String> headers = rulesDownloadRequest.getHeaders();
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
		testableNetworkService.clearCapturedRequests();
		// opt out then opt in again for testing
		expectedUrl = "https://" + TestConstants.MOCK_IDENTITY_SERVER + "/demoptout.jpg?d_mid=" + experienceCloudId + "&d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg";
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest optOutRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(optOutRequest);
		assertEquals(expectedUrl, optOutRequest.getUrl());
		testableNetworkService.clearCapturedRequests();
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		TestHelper.sleep(3000);
		testableNetworkService.setResponseFromFile("zip/rules-broadcast.zip", expectedUrl);
		// get new MID
		String newExperienceCloudId = getExperienceCloudId();
		assertFalse(newExperienceCloudId.isEmpty());
		// verify
		expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + newExperienceCloudId +
				"/rules.zip";
		testableNetworkService.waitForRequest(expectedUrl);
		rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		assertNull(rulesDownloadRequest.getHeaders().get("X-InApp-Auth"));
	}

	// environment aware config tests
	// Test Case No : 13
	@Test
	public void
	test_Functional_Campaign_profileUpdate_WithDevEnvironment_VerifyProfileUpdateOnLifecycleLaunchUsesDevEnvironment()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/dev/rest/head/mobileAppV5/pkey_dev/subscriptions/" + experienceCloudId;
		setBuildEnvironment("dev");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 14
	@Test
	public void
	test_Functional_Campaign_profileUpdate_WithDevEnvironment_VerifyProfileUpdateOnLifecycleLaunchUsesStageEnvironment()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/stage/rest/head/mobileAppV5/pkey_stage/subscriptions/" + experienceCloudId;
		setBuildEnvironment("stage");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);

	}

	// Test Case No : 15
	@Test
	public void
	test_Functional_Campaign_profileUpdate_WithSomeOtherEnv_VerifyProfileUpdateOnLifecycleLaunchUsesSomeOtherEnv() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/someOtherEnv/rest/head/mobileAppV5/pkey_someOtherEnv/subscriptions/" + experienceCloudId;
		setBuildEnvironment("someOtherEnv");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 16
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationImpressionTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		String expectedTrackRequestUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/r/?id=h153d80,b670ea,7&mcId=" + experienceCloudId;
		testableNetworkService.setResponseFromFile("zip/rules-localNotification.zip", expectedUrl);
		// To trigger campaign rules download, use linkagefield
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// this sleep is required to allow some time for the downloaded rules to be processed
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		testableNetworkService.clearCapturedRequests();
		// To trigger impression tracking, use trackAction API
		MobileCore.trackAction("localImpression", null);
		testableNetworkService.waitForRequest(expectedTrackRequestUrl);
		NetworkRequest messageTrackRequest = testableNetworkService.getRequest(expectedTrackRequestUrl);
		// verify
		assertEquals(expectedTrackRequestUrl, messageTrackRequest.getUrl());
	}

	// Test Case No : 17
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationOpenTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedTrackRequestUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/r/?id=h153d80,b670ea,1&mcId=" + experienceCloudId;

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "1");
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		testableNetworkService.waitForRequest(expectedTrackRequestUrl);
		NetworkRequest messageTrackRequest = testableNetworkService.getRequest(expectedTrackRequestUrl);
		assertEquals(expectedTrackRequestUrl, messageTrackRequest.getUrl());
	}

	// Test Case No : 18
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationClickTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedTrackRequestUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/r/?id=h153d80,b670ea,2&mcId=" + experienceCloudId;

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "2");
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		testableNetworkService.waitForRequest(expectedTrackRequestUrl);
		NetworkRequest messageTrackRequest = testableNetworkService.getRequest(expectedTrackRequestUrl);
		assertEquals(expectedTrackRequestUrl, messageTrackRequest.getUrl());
	}

	// Test Case No : 19
	@Test
	public void test_Functional_Campaign_PushNotification_CheckPushNotificationImpressionTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedTrackRequestUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/r/?id=h153d80,b670ea,7&mcId=" + experienceCloudId;

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "7");
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		testableNetworkService.waitForRequest(expectedTrackRequestUrl);
		NetworkRequest messageTrackRequest = testableNetworkService.getRequest(expectedTrackRequestUrl);
		assertEquals(expectedTrackRequestUrl, messageTrackRequest.getUrl());
	}

	// Test Case No : 20
	// test_Functional_Campaign_PushNotification_CheckPushNotificationOpenTracking()
	// Same as Test Case No : 17  test_Functional_Campaign_InAppNotification_CheckLocalNotificationOpenTracking


	// Test Case No : 21
	// test_Functional_Campaign_PushNotification_CheckPushNotificationClickTracking()
	// Same as Test Case No : 18  test_Functional_Campaign_InAppNotification_CheckLocalNotificationClickTracking


	// Test Case No : 22
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckEmptyBroadlogId() throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "1");
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 23
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckEmptyDeliveryId() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "");
		contextData.put("action", "2");
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 24
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckEmptyAction() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "");
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 25
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckNullBroadlogId() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", null);
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "1");
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 26
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckNullDeliveryId() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", null);
		contextData.put("action", "2");
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 27
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckNullAction() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", null);
		testableNetworkService.clearCapturedRequests();
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// ETag tests
	// Test Case No : 28
	@Ignore // TODO: flaky test to be investigated
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesRequestContainsIfNoneMatchHeaderIfCachedRulesMetadataHasETag()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		Map<String, String> metadata = new HashMap<String, String>() {{
			put("Etag", "\"SOME-ETAG-12345\"");
			put("Last-Modified", "Tue, 31 Jan 2023 09:31:46 GMT");
		}};
		TestHelper.addRulesZipToCache(metadata);
		TestHelper.sleep(2000);
		// test
		// use configuration event to trigger rules download
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map headers = rulesDownloadRequest.getHeaders();
		assertNotNull(headers);
		assertEquals("SOME-ETAG-12345", headers.get(IF_NONE_MATCH_HEADER_KEY));
		assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", headers.get(IF_MODIFIED_HEADER_KEY));
	}

	// Test Case No : 29
	@Ignore // TODO: flaky test to be investigated
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesRequestDoesNotHaveIfNoneMatchHeaderIfCachedRulesMetadataDoesNotHaveETag()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
				"/rules.zip";
		Map<String, String> metadata = new HashMap<>();
		TestHelper.addRulesZipToCache(metadata);
		TestHelper.sleep(1000);
		// test
		// use configuration event to trigger rules download
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
		Map<String, String> headers = rulesDownloadRequest.getHeaders();
		assertEquals(0, headers.size());
	}

	// TODO: see if weak etag is supported in core 2.0
	// Test Case No : 29
//	@Test
//	public void
//	test_Functional_Campaign_rulesDownload_VerifyRulesRequestContainsIfNoneMatchHeader_AfterPreviousRulesDownloadContainedWeakETag()
//	throws InterruptedException {
//		// setup
//		String experienceCloudId = getExperienceCloudId();
//		assertFalse(experienceCloudId.isEmpty());
//		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/mcias/" + TestConstants.MOCK_CAMPAIGN_SERVER + "/my_property_id/" + experienceCloudId +
//				"/rules.zip";
//		Map<String, String> metadata = new HashMap<String, String>() {{
//			put("Etag", "W/\"SOME-WEAK-ETAG-12345\"");
//			put("Last-Modified", "Tue, 31 Jan 2023 09:31:46 GMT");
//		}};
//		TestHelper.addRulesZipToCache(metadata);
//		TestHelper.sleep(1000);
//		// test
//		// use configuration event to trigger rules download
//		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
//		// verify
//		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
//		// verify
//		testableNetworkService.waitForRequest(expectedUrl);
//		NetworkRequest rulesDownloadRequest = testableNetworkService.getRequest(expectedUrl);
//		assertEquals(expectedUrl, rulesDownloadRequest.getUrl());
//		Map headers = rulesDownloadRequest.getHeaders();
//		assertNotNull(headers);
//		assertEquals("SOME-ETAG-12345", headers.get(IF_NONE_MATCH_HEADER_KEY));
//		assertEquals("Thu, 01 Jan 1970 00:00:00 GMT", headers.get(IF_MODIFIED_HEADER_KEY));
//	}

	// registration reduction enhancement tests
	// Test Case No : 30
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnSecondLifecycleLaunch_WithDefaultRegistrationDelay()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		testableNetworkService.setNetworkResponse(expectedUrl, "OK", 200);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
		// test
		// test second lifecycle start
		testableNetworkService.clearCapturedRequests();
		MobileCore.lifecyclePause();
		TestHelper.sleep(2000);
		MobileCore.lifecycleStart(null);
		// verify no registration request due to 7 day default delay not being elapsed
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 31
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyProfileUpdateLifecycleLaunch_WithDefaultRegistrationDelayElapsed() throws
		InterruptedException {
		// setup, set a timestamp 8 days in the past
		String experienceCloudId = getExperienceCloudId();
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		testableNetworkService.setNetworkResponse(expectedUrl, "OK", 200);
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		assertFalse(experienceCloudId.isEmpty());
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 32
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnLifecycleLaunch_WithCustomRegistrationDelayElapsed() throws
		InterruptedException {
		// setup, set a timestamp 31 days in the past
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		// set a registration delay of 30 days
		setRegistrationDelayOrRegistrationPaused(30, false);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 33
	@Ignore // TODO: flaky test to be investigated
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnLifecycleLaunch_WithCustomRegistrationDelayNotElapsed()
	throws InterruptedException {
		// setup, set a timestamp 31 days in the past
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		// set a registration delay of 100 days
		setRegistrationDelayOrRegistrationPaused(100, false);
		// test
		MobileCore.lifecycleStart(null);
		// verify no registration request due to the 100 day custom registration delay not being elapsed
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 34
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnLifecycleLaunch_WhenRegistrationIsPaused()
	throws InterruptedException {
		// setup, set a timestamp 31 days in the past
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		// set a registration delay of 30 days and registration paused to true
		setRegistrationDelayOrRegistrationPaused(30, true);
		// test
		MobileCore.lifecycleStart(null);
		// verify no registration request due to the registration requests being paused
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 35
	@Ignore // TODO: second profile request queued but not sent when under test. to be investigated.
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnSecondLifecycleLaunch_WhenRegistrationDelaySetToZero()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		String expectedUrl = "https://" + TestConstants.MOCK_CAMPAIGN_SERVER + "/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId;
		// set registration delay to 0 which will send registration requests with every lifecycle launch
		setRegistrationDelayOrRegistrationPaused(0, false);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		testableNetworkService.waitForRequest(expectedUrl);
		NetworkRequest profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		String payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
		testableNetworkService.clearCapturedRequests();
		// test second lifecycle start
		MobileCore.lifecyclePause();
		TestHelper.sleep(2000);
		MobileCore.lifecycleStart(null);
		// verify registration request due to registration delay being set to 0
		profileUpdateRequest = testableNetworkService.getRequest(expectedUrl);
		assertNotNull(profileUpdateRequest);
		payload = new String(profileUpdateRequest.getBody());
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}
}