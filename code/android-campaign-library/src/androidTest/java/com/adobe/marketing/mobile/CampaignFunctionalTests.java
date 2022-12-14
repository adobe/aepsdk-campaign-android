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

package com.adobe.marketing.mobile;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;

import android.content.Context;
import android.support.test.runner.AndroidJUnit4;
import android.util.Base64;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.adobe.marketing.mobile.E2ETestableNetworkService.NetworkRequest;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
public class CampaignFunctionalTests extends AbstractE2ETest {

	// A test will be run at most 3 times
	@Rule
	public Retry totalTestCount = new Retry(3);

	TestHelper testHelper = new TestHelper();
	static final String FIRST_NAME = "John";
	static final String LAST_NAME = "Doe";
	static final String EMAIL = "john.doe@email.com";
	static final String PUSH_PLATFORM = "gcm";
	static final String PAYLOAD_STRING = "{\"marketingCloudId\":\"%s\",\"pushPlatform\":\"%s\"}";
	static final String FIRST_NAME_FIELD = "\"cusFirstName\":\"%s\"";
	static final String LAST_NAME_FIELD = "\"cusLastName\":\"%s\"";
	static final String EMAIL_FIELD = "\"cusEmail\":\"%s\"";
	static boolean deleteAllCache = false;
	static final String ETAG_HEADER = "ETag";
	static final String ETAG = "\"SOME-ETAG-12345\"";
	static final String WEAK_ETAG = "W/\"SOME-WEAK-ETAG-12345\"";
	static final String LAST_MODIFIED_HEADER_KEY = "Last-Modified";
	static final String IF_NONE_MATCH_HEADER_KEY = "If-None-Match";
	static final String IF_RANGE_HEADER_KEY = "If-Range";
	static final String IF_MODIFIED_HEADER_KEY = "If-Modified-Since";
	static final String RANGE_HEADER_KEY = "Range";
	static final String EXPECTED_RANGE_VALUE = "bytes=2723-";
	static final String CAMPAIGN_DATASTORE = "CampaignDataStore";
	static String lastModified = null;
	CampaignTestingPlatform campaignTestingPlatform = new CampaignTestingPlatform();
	AndroidLocalStorageService localStorageService;

	@Before
	@Override
	public void setUp() {
		// need to do the testableNetworkService setup here as we are overriding it with our own test platform
		this.testableNetworkService = campaignTestingPlatform.e2EAndroidNetworkService;
		MobileCore.setCore((Core)null);
		MobileCore.setPlatformServices(campaignTestingPlatform);
		MobileCore.setApplication(this.defaultApplication);
		testHelper.cleanCache(this.defaultApplication.getApplicationContext());
		TestHelper.cleanLocalStorage();

		// begin AEP SDK setup for ACS testing
		cleanCache(defaultApplication.getApplicationContext());
		MobileCore.setApplication(defaultApplication);

		try {
			Campaign.registerExtension();
			Identity.registerExtension();
			Lifecycle.registerExtension();
			MobileCore.start(null);
		} catch (InvalidInitException e) {
			e.printStackTrace();
		}

		setupProgrammaticConfig();
		localStorageService = new AndroidLocalStorageService();
	}

	@After
	public void tearDown() {
		super.tearDown();
		lastModified = null;
		localStorageService.getDataStore(CAMPAIGN_DATASTORE).removeAll();
		testableNetworkService.resetTestableNetworkService();
	}

	@AfterClass
	public static void emptyCache() {
		deleteAllCache = true;
		cleanCache(MobileCore.getApplication().getApplicationContext());
	}

	private void setupProgrammaticConfig() {
		MobileCore.setLogLevel(LoggingMode.DEBUG);
		E2ETestableNetworkService.NetworkRequestMatcher matcher = new E2ERequestMatcher("sj1010006201050.corp.adobe.com/id?");
		testableNetworkService.setResponse(matcher,
										   "{\"d_mid\":\"65200765694214613857127198643018103011\",\"id_sync_ttl\":604800,\"d_blob\":\"hmk_Lq6TPIBMW925SPhw3Q\",\"dcs_region\":9,\"d_ottl\":7200,\"ibs\":[],\"subdomain\":\"obumobile5\",\"tid\":\"d47JfAKTTsU=\"}");
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
		data.put(TestConstants.CAMPAIGN_SERVER, TestConstants.HERMETIC_SERVER);
		data.put(TestConstants.CAMPAIGN_MCIAS, TestConstants.HERMETIC_SERVER + "/mcias");
		data.put(TestConstants.CAMPAIGN_PKEY, "pkey");
		// ============================================================
		// lifecycle
		// ============================================================
		data.put(TestConstants.LIFECYCLE_SESSION_TIMEOUT, 1);
		// ============================================================
		// identity
		// ============================================================
		data.put(TestConstants.IDENTITY_ORG_ID, "B1F855165B4C9EA50A495E06@AdobeOrg");
		data.put(TestConstants.IDENTITY_SERVER, TestConstants.HERMETIC_SERVER);
		// ============================================================
		// rules
		// ============================================================
		data.put(TestConstants.RULES_SERVER, "https://" + TestConstants.HERMETIC_SERVER + "/dummy-rules.zip");
		MobileCore.updateConfiguration(data);
		// account for initial identity network, launch rules download, and campaign rules download requests
		assertEquals(3, testableNetworkService.waitAndGetCount(3, 6000));
		testHelper.sleep(2000);

		testableNetworkService.resetTestableNetworkService();
	}

	private String getExperienceCloudId() throws InterruptedException {
		final CountDownLatch latch = new CountDownLatch(1);
		final String[] retrievedId = new String[1];
		AdobeCallback callback = new AdobeCallback<String>() {
			@Override
			public void call(String data) {
				retrievedId[0] = data;
				latch.countDown();
			}
		};
		Identity.getExperienceCloudId(callback);
		latch.await(10, TimeUnit.SECONDS);

		return retrievedId[0];
	}

	private void setBuildEnvironment(final String buildEnvironment) {
		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put(TestConstants.BUILD_ENVIRONMENT, buildEnvironment);
		data.put(TestConstants.CAMPAIGN_PKEY, "pkey_" + buildEnvironment);
		data.put(TestConstants.CAMPAIGN_SERVER, TestConstants.HERMETIC_SERVER + "/" + buildEnvironment);
		MobileCore.updateConfiguration(data);
		testHelper.sleep(2000);
		testableNetworkService.resetNetworkRequestList();
	}

	private void setResponseFromFile(final E2ETestableNetworkService testableNetworkService, final String path,
									 final String urlToMatch) {
		setResponseFromFileWithETag(testableNetworkService, path, urlToMatch, null);
	}

	private void setResponseFromFileWithETag(final E2ETestableNetworkService testableNetworkService, final String path,
			final String urlToMatch, final String etag) {
		E2ERequestMatcher networkMatcher = new E2ERequestMatcher(urlToMatch);
		InputStream zipFile = null;

		try {
			zipFile = App.getAppContext().getAssets().open(path);
		} catch (IOException e) {
			e.printStackTrace();
		}

		// we want to use the same last modified date for requests which share an ETag
		if (lastModified == null || etag == null) {
			SimpleDateFormat simpleDateFormat = TestHelper.createRFC2822Formatter();
			lastModified = simpleDateFormat.format(new Date());
		}

		Map<String, String> headers = new HashMap<>();
		headers.put(LAST_MODIFIED_HEADER_KEY, lastModified);

		if (etag != null) {
			headers.put(ETAG_HEADER, etag);
		}

		E2ETestableNetworkService.NetworkResponse networkResponse = new E2ETestableNetworkService.NetworkResponse(
			zipFile,
			200, headers);
		testableNetworkService.setResponse(networkMatcher, networkResponse);
	}

	private String decodeBase64(final String encodedString) {
		byte[] decodedBytes = new byte[0];

		try {
			decodedBytes = Base64.decode(encodedString.getBytes("UTF-8"), Base64.DEFAULT);
		} catch (UnsupportedEncodingException e) {
		}

		return new String(decodedBytes);
	}

	// todo: combine this with the cleanCache method within android-core
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
		localStorageService.getDataStore(CAMPAIGN_DATASTORE).setLong("CampaignRegistrationTimestamp", timestamp);
	}

	private void updateEcidInDatastore(final String ecid) {
		localStorageService.getDataStore(CAMPAIGN_DATASTORE).setString("ExperienceCloudId", ecid);
	}

	private void setRegistrationDelayOrRegistrationPaused(final int registrationDelay, final boolean registrationPaused) {
		HashMap<String, Object> data = new HashMap<String, Object>();
		data.put(TestConstants.CAMPAIGN_REGISTRATION_DELAY, registrationDelay);
		data.put(TestConstants.CAMPAIGN_REGISTRATION_PAUSED, registrationPaused);
		MobileCore.updateConfiguration(data);
		testHelper.sleep(2000);
		testableNetworkService.resetNetworkRequestList();
	}

	// profile update tests
	// Test Case No : 1
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnLifecycleLaunch() throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 2
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnLifecycleLaunchWhenPrivacyIsOptedOut() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest optOutRequest = testableNetworkService.getItem(0);
		List<String> urlParameters = Arrays.asList(optOutRequest.url.split("\\?|&"));
		assertTrue(urlParameters.contains("https://sj1010006201050.corp.adobe.com/demoptout.jpg"));
		assertTrue(urlParameters.contains("d_mid=" + experienceCloudId));
		assertTrue(urlParameters.contains("d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg"));
		testableNetworkService.resetNetworkRequestList();
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 3
	@Test
	public void test_Functional_Campaign_profileUpdate_VerifyProfileUpdateRetriedWhenARecoverableNetworkErrorIsEncountered()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		testableNetworkService.setDefaultResponse(new E2ETestableNetworkService.NetworkResponse("GATEWAY TIMEOUT", 504, null,
				0));
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));
		testableNetworkService.resetNetworkRequestList();
		testableNetworkService.setDefaultResponse(new E2ETestableNetworkService.NetworkResponse("OK", 200, null,
				0));
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 31000));
		profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
		testableNetworkService.resetNetworkRequestList();
		// verify no more retried requests due to 200 response
		testHelper.sleep(31000);
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
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
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
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest optOutRequest = testableNetworkService.getItem(0);
		List<String> urlParameters = Arrays.asList(optOutRequest.url.split("\\?|&"));
		assertTrue(urlParameters.contains("https://sj1010006201050.corp.adobe.com/demoptout.jpg"));
		assertTrue(urlParameters.contains("d_mid=" + experienceCloudId));
		assertTrue(urlParameters.contains("d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg"));
		testableNetworkService.resetNetworkRequestList();
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 6
	@Test
	public void test_Functional_Campaign_setLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyUnknown() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.UNKNOWN);
		testHelper.sleep(1000);
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
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		Campaign.resetLinkageFields();
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		assertNull(rulesDownloadRequest.requestProperty);
		// setup for testing with linkage fields
		testableNetworkService.resetNetworkRequestList();
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test after setting up linkage fields
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
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
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		Campaign.resetLinkageFields();
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		assertNull(rulesDownloadRequest.requestProperty);
	}

	// Test Case No : 9
	@Test
	public void test_Functional_Campaign_resetLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyOptOut() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest optOutRequest = testableNetworkService.getItem(0);
		List<String> urlParameters = Arrays.asList(optOutRequest.url.split("\\?|&"));
		assertTrue(urlParameters.contains("https://sj1010006201050.corp.adobe.com/demoptout.jpg"));
		assertTrue(urlParameters.contains("d_mid=" + experienceCloudId));
		assertTrue(urlParameters.contains("d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg"));
		testableNetworkService.resetNetworkRequestList();
		Campaign.resetLinkageFields();
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(0, 500));
	}

	// Test Case No : 10
	@Test
	public void test_Functional_Campaign_resetLinkageFields_VerifyNoRulesDownloadRequest_WhenPrivacyOptUnknown() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.UNKNOWN);
		testHelper.sleep(1000);
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
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
		// setup for second part
		testableNetworkService.resetNetworkRequestList();
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		Campaign.resetLinkageFields();
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		assertNull(rulesDownloadRequest.requestProperty);
	}

	// Test Case No : 12
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesDownloadRequestWithoutCustomHttpHeader_AfterPrivacyOptOutThenOptIn()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFile(testableNetworkService, "zip/rules-personalized.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// test
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
		assertNotNull(headers);
		final String decodedBytes = decodeBase64(headers.get("X-InApp-Auth"));
		assertTrue(decodedBytes.contains(String.format(FIRST_NAME_FIELD, FIRST_NAME)));
		assertTrue(decodedBytes.contains(String.format(LAST_NAME_FIELD, LAST_NAME)));
		assertTrue(decodedBytes.contains(String.format(EMAIL_FIELD, EMAIL)));
		testableNetworkService.resetNetworkRequestList();
		// opt out then opt in again for testing
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_OUT);
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest optOutRequest = testableNetworkService.getItem(0);
		List<String> urlParameters = Arrays.asList(optOutRequest.url.split("\\?|&"));
		assertTrue(urlParameters.contains("https://sj1010006201050.corp.adobe.com/demoptout.jpg"));
		assertTrue(urlParameters.contains("d_mid=" + experienceCloudId));
		assertTrue(urlParameters.contains("d_orgid=B1F855165B4C9EA50A495E06%40AdobeOrg"));
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		testHelper.sleep(3000);
		testableNetworkService.resetNetworkRequestList();
		setResponseFromFile(testableNetworkService, "zip/rules-broadcast.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// use configuration event to trigger rules download
		// todo: update this step once https://jira.corp.adobe.com/browse/AMSDK-7761 is fixed. manually triggering the rules download is a workaround for now.
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// get new MID
		String newExperienceCloudId = getExperienceCloudId();
		assertFalse(newExperienceCloudId.isEmpty());
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 newExperienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		assertNull(rulesDownloadRequest.requestProperty);
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
		setBuildEnvironment("dev");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/dev/rest/head/mobileAppV5/pkey_dev/subscriptions/" +
					 experienceCloudId, profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));
	}

	// Test Case No : 14
	@Test
	public void
	test_Functional_Campaign_profileUpdate_WithDevEnvironment_VerifyProfileUpdateOnLifecycleLaunchUsesStageEnvironment()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setBuildEnvironment("stage");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/stage/rest/head/mobileAppV5/pkey_stage/subscriptions/" +
					 experienceCloudId, profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));

	}

	// Test Case No : 15
	@Test
	public void
	test_Functional_Campaign_profileUpdate_WithSomeOtherEnv_VerifyProfileUpdateOnLifecycleLaunchUsesSomeOtherEnv() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setBuildEnvironment("someOtherEnv");
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/someOtherEnv/rest/head/mobileAppV5/pkey_someOtherEnv/subscriptions/"
					 + experienceCloudId, profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertTrue(payload.contains(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM)));
	}

	// Test Case No : 16
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationImpressionTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFile(testableNetworkService, "zip/rules-localNotification.zip",
							"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
							"/rules.zip");
		// To trigger campaign rules download, use linkagefield
		HashMap<String, String> linkageFields = new HashMap<>();
		linkageFields.put("cusFirstName", FIRST_NAME);
		linkageFields.put("cusLastName", LAST_NAME);
		linkageFields.put("cusEmail", EMAIL);
		Campaign.setLinkageFields(linkageFields);
		// this sleep is required to allow some time for the downloaded rules to be processed
		testHelper.sleep(1000);
		testableNetworkService.waitAndGetCount(1);
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		// Verify rules download request
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId +
					 "/rules.zip", rulesDownloadRequest.url);
		testableNetworkService.resetNetworkRequestList();
		// To trigger impression tracking, use trackAction API
		MobileCore.trackAction("localImpression", null);
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 5000));
		NetworkRequest messageTrackRequest = testableNetworkService.getItem(0);
		// Verify
		assertEquals("https://sj1010006201050.corp.adobe.com/r/?id=h153d80,b670ea,7&mcId=" +
					 experienceCloudId, messageTrackRequest.url);
	}

	// Test Case No : 17
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationOpenTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "1");
		// test
		MobileCore.collectMessageInfo(contextData);
		//Verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest messageTrackRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/r/?id=h153d80,b670ea,1&mcId=" +
					 experienceCloudId, messageTrackRequest.url);
	}

	// Test Case No : 18
	@Test
	public void test_Functional_Campaign_InAppNotification_CheckLocalNotificationClickTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "2");
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest messageTrackRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/r/?id=h153d80,b670ea,2&mcId=" +
					 experienceCloudId, messageTrackRequest.url);
	}

	// Test Case No : 19
	@Test
	public void test_Functional_Campaign_PushNotification_CheckPushNotificationImpressionTracking() throws
		InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());

		HashMap<String, Object> contextData = new HashMap<>();
		contextData.put("broadlogId", "h153d80");
		contextData.put("deliveryId", "b670ea");
		contextData.put("action", "7");
		// test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest messageTrackRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/r/?id=h153d80,b670ea,7&mcId=" +
					 experienceCloudId, messageTrackRequest.url);
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
		//test
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
		//test
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
		//test
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
		//test
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
		//test
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
		//test
		MobileCore.collectMessageInfo(contextData);
		// verify
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// ETag tests
	// Test Case No : 28
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesRequestContainsIfNoneMatchHeader_AfterPreviousRulesDownloadContainedETag()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", ETAG);
		// test
		// use configuration event to trigger rules download
		// todo: update this step once https://jira.corp.adobe.com/browse/AMSDK-7761 is fixed. manually triggering the rules download is a workaround for now.
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		testableNetworkService.resetTestableNetworkService();
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", ETAG);
		// test
		// use configuration event to trigger another rules download
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
		assertNotNull(headers);
		assertEquals(ETAG, headers.get(IF_NONE_MATCH_HEADER_KEY));
		assertEquals(ETAG, headers.get(IF_RANGE_HEADER_KEY));
		assertEquals(lastModified, headers.get(IF_MODIFIED_HEADER_KEY));
		assertEquals(EXPECTED_RANGE_VALUE, headers.get(RANGE_HEADER_KEY));
	}

	// Test Case No : 29
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesRequestDoesNotHaveIfNoneMatchHeader_AfterPreviousRulesDownloadDidNotHaveTag()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", null);
		// test
		// use configuration event to trigger rules download
		// todo: update this step once https://jira.corp.adobe.com/browse/AMSDK-7761 is fixed. manually triggering the rules download is a workaround for now.
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		testableNetworkService.resetTestableNetworkService();
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", null);
		// test
		// use configuration event to trigger another rules download
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
		assertNotNull(headers);
		assertNull(headers.get(IF_NONE_MATCH_HEADER_KEY));
		assertEquals(lastModified, headers.get(IF_RANGE_HEADER_KEY));
		assertEquals(lastModified, headers.get(IF_MODIFIED_HEADER_KEY));
		assertEquals(EXPECTED_RANGE_VALUE, headers.get(RANGE_HEADER_KEY));
		assertNull(headers.get(IF_NONE_MATCH_HEADER_KEY));
	}

	// Test Case No : 29
	@Test
	public void
	test_Functional_Campaign_rulesDownload_VerifyRulesRequestContainsIfNoneMatchHeader_AfterPreviousRulesDownloadContainedWeakETag()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", WEAK_ETAG);
		// test
		// use configuration event to trigger rules download
		// todo: update this step once https://jira.corp.adobe.com/browse/AMSDK-7761 is fixed. manually triggering the rules download is a workaround for now.
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		NetworkRequest rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		testableNetworkService.resetTestableNetworkService();
		setResponseFromFileWithETag(testableNetworkService, "zip/rules-broadcast.zip",
									"https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" + experienceCloudId +
									"/rules.zip", ETAG);
		// test
		// use configuration event to trigger another rules download
		MobileCore.setPrivacyStatus(MobilePrivacyStatus.OPT_IN);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1, 3000));
		rulesDownloadRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/mcias/sj1010006201050.corp.adobe.com/my_property_id/" +
					 experienceCloudId + "/rules.zip", rulesDownloadRequest.url);
		Map<String, String> headers = rulesDownloadRequest.requestProperty;
		assertNotNull(headers);
		assertEquals(WEAK_ETAG, headers.get(IF_NONE_MATCH_HEADER_KEY));
		assertEquals(WEAK_ETAG, headers.get(IF_RANGE_HEADER_KEY));
		assertEquals(lastModified, headers.get(IF_MODIFIED_HEADER_KEY));
		assertEquals(EXPECTED_RANGE_VALUE, headers.get(RANGE_HEADER_KEY));
	}

	// registration reduction enhancement tests
	// Test Case No : 30
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnSecondLifecycleLaunch_WithDefaultRegistrationDelay()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
		// test second lifecycle start
		testableNetworkService.resetNetworkRequestList();
		MobileCore.lifecyclePause();
		testHelper.sleep(2000);
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
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		assertFalse(experienceCloudId.isEmpty());
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 32
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnLifecycleLaunch_WithCustomRegistrationDelayElapsed() throws
		InterruptedException {
		// setup, set a timestamp 31 days in the past
		String experienceCloudId = getExperienceCloudId();
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		assertFalse(experienceCloudId.isEmpty());
		// set a registration delay of 30 days
		setRegistrationDelayOrRegistrationPaused(30, false);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}

	// Test Case No : 33
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyNoProfileUpdateOnLifecycleLaunch_WithCustomRegistrationDelayNotElapsed()
	throws InterruptedException {
		// setup, set a timestamp 31 days in the past
		String experienceCloudId = getExperienceCloudId();
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		assertFalse(experienceCloudId.isEmpty());
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
		long timestamp = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(31);
		updateTimestampInDatastore(timestamp);
		updateEcidInDatastore(experienceCloudId);
		assertFalse(experienceCloudId.isEmpty());
		// set a registration delay of 30 days and registration paused to true
		setRegistrationDelayOrRegistrationPaused(30, true);
		// test
		MobileCore.lifecycleStart(null);
		// verify no registration request due to the registration requests being paused
		assertEquals(0, testableNetworkService.waitAndGetCount(1));
	}

	// Test Case No : 35
	@Test
	public void
	test_Functional_Campaign_profileUpdate_VerifyProfileUpdateOnSecondLifecycleLaunch_WhenRegistrationDelaySetToZero()
	throws InterruptedException {
		// setup
		String experienceCloudId = getExperienceCloudId();
		assertFalse(experienceCloudId.isEmpty());
		// set registration delay to 0 which will send registration requests with every lifecycle launch
		setRegistrationDelayOrRegistrationPaused(0, false);
		// test
		MobileCore.lifecycleStart(null);
		// verify
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		NetworkRequest profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		String payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
		// test second lifecycle start
		testableNetworkService.resetNetworkRequestList();
		MobileCore.lifecyclePause();
		testHelper.sleep(2000);
		MobileCore.lifecycleStart(null);
		// verify registration request due to registration delay being set to 0
		assertEquals(1, testableNetworkService.waitAndGetCount(1));
		profileUpdateRequest = testableNetworkService.getItem(0);
		assertEquals("https://sj1010006201050.corp.adobe.com/rest/head/mobileAppV5/pkey/subscriptions/" + experienceCloudId,
					 profileUpdateRequest.url);
		payload = profileUpdateRequest.getPostString();
		assertEquals(String.format(PAYLOAD_STRING, experienceCloudId, PUSH_PLATFORM), payload);
	}
}