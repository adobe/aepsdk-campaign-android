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

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.platform.app.InstrumentationRegistry;

import com.adobe.marketing.mobile.LoggingMode;
import com.adobe.marketing.mobile.MobileCore;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.services.caching.CacheEntry;
import com.adobe.marketing.mobile.services.caching.CacheExpiry;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Test helper for functional testing to read, write, reset and assert against eventhub events, shared states and persistence data.
 */
public class TestHelper {
    private static final String LOG_TAG = "TestHelper";
    // List of threads to wait for after test execution
    private static final List<String> knownThreads = new ArrayList<String>();
    static Application defaultApplication;

    {
        knownThreads.add("pool"); // used for threads that execute the listeners code
        knownThreads.add("ADB"); // module internal threads
    }

    /**
     * Waits for all the {@code #knownThreads} to finish or fails the test after timeoutMillis if some of them are still running
     * when the timer expires. If timeoutMillis is 0, a default timeout will be set = 1000ms
     *
     * @param timeoutMillis max waiting time
     */
    public static void waitForThreads(final int timeoutMillis) {
        int TEST_DEFAULT_TIMEOUT_MS = 1000;
        int TEST_DEFAULT_SLEEP_MS = 50;
        int TEST_INITIAL_SLEEP_MS = 100;

        long startTime = System.currentTimeMillis();
        int timeoutTestMillis = timeoutMillis > 0 ? timeoutMillis : TEST_DEFAULT_TIMEOUT_MS;
        int sleepTime = Math.min(timeoutTestMillis, TEST_DEFAULT_SLEEP_MS);

        sleep(TEST_INITIAL_SLEEP_MS);
        Set<Thread> threadSet = getEligibleThreads();

        while (threadSet.size() > 0 && ((System.currentTimeMillis() - startTime) < timeoutTestMillis)) {
            MobileCore.log(LoggingMode.DEBUG, LOG_TAG, "waitForThreads - Still waiting for " + threadSet.size() + " thread(s)");

            for (Thread t : threadSet) {

                MobileCore.log(LoggingMode.DEBUG, LOG_TAG, "waitForThreads - Waiting for thread " + t.getName() + " (" + t.getId() + ")");
                boolean done = false;
                boolean timedOut = false;

                while (!done && !timedOut) {
                    if (t.getState().equals(Thread.State.TERMINATED)
                            || t.getState().equals(Thread.State.TIMED_WAITING)
                            || t.getState().equals(Thread.State.WAITING)) {
                        //Cannot use the join() API since we use a cached thread pool, which
                        //means that we keep idle threads around for 60secs (default timeout).
                        done = true;
                    } else {
                        //blocking
                        sleep(sleepTime);
                        timedOut = (System.currentTimeMillis() - startTime) > timeoutTestMillis;
                    }
                }

                if (timedOut) {
                    MobileCore.log(LoggingMode.DEBUG, LOG_TAG,
                            "waitForThreads - Timeout out waiting for thread " + t.getName() + " (" + t.getId() + ")");
                } else {
                    MobileCore.log(LoggingMode.DEBUG, LOG_TAG,
                            "waitForThreads - Done waiting for thread " + t.getName() + " (" + t.getId() + ")");
                }
            }

            threadSet = getEligibleThreads();
        }

        MobileCore.log(LoggingMode.DEBUG, LOG_TAG, "waitForThreads - All known threads are terminated.");
    }

    /**
     * Retrieves all the known threads that are still running
     *
     * @return set of running tests
     */
    private static Set<Thread> getEligibleThreads() {
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        Set<Thread> eligibleThreads = new HashSet<Thread>();

        for (Thread t : threadSet) {
            if (isAppThread(t) && !t.getState().equals(Thread.State.WAITING) && !t.getState().equals(Thread.State.TERMINATED)
                    && !t.getState().equals(Thread.State.TIMED_WAITING)) {
                eligibleThreads.add(t);
            }
        }

        return eligibleThreads;
    }

    /**
     * Checks if current thread is not a daemon and its name starts with one of the known thread names specified here
     * {@link #knownThreads}
     *
     * @param t current thread to verify
     * @return true if it is a known thread, false otherwise
     */
    private static boolean isAppThread(final Thread t) {
        if (t.isDaemon()) {
            return false;
        }

        for (String prefix : knownThreads) {
            if (t.getName().startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Event Test Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * Pause test execution for the given {@code milliseconds}
     *
     * @param milliseconds the time to sleep the current thread.
     */
    public static void sleep(int milliseconds) {
        try {
            Thread.sleep(milliseconds);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * {@code TestRule} which sets up the MobileCore for testing before each test execution, and
     * tearsdown the MobileCore after test execution.
     * <p>
     * To use, add the following to your test class:
     * <pre>
     *    @Rule
     *    public TestHelper.SetupCoreRule coreRule = new TestHelper.SetupCoreRule();
     * </pre>
     */
    public static class SetupCoreRule implements TestRule {

        @Override
        public Statement apply(final Statement base, final Description description) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    if (defaultApplication == null) {
                        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
                        defaultApplication = Instrumentation.newApplication(CustomApplication.class, context);
                    }

                    MobileCore.setLogLevel(LoggingMode.VERBOSE);
                    MobileCore.setApplication(defaultApplication);

                    try {
                        base.evaluate();
                    } catch (Throwable e) {
                        MobileCore.log(LoggingMode.DEBUG, "SetupCoreRule", "Wait after test failure.");
                        throw e; // rethrow test failure
                    } finally {
                        // After test execution
                        MobileCore.log(LoggingMode.DEBUG, "SetupCoreRule", "Finished '" + description.getMethodName() + "'");
                        waitForThreads(5000); // wait to allow thread to run after test execution
                    }
                }
            };
        }
    }

    /**
     * Dummy Application for the test instrumentation
     */
    public static class CustomApplication extends Application {
        public CustomApplication() {
        }
    }

    public static SimpleDateFormat createRFC2822Formatter() {
        final String pattern = "EEE, dd MMM yyyy HH:mm:ss z";
        final SimpleDateFormat rfc2822formatter = new SimpleDateFormat(pattern, Locale.US);
        rfc2822formatter.setTimeZone(TimeZone.getTimeZone("GMT"));
        return rfc2822formatter;
    }

    /**
     * Adds a zip file to the Campaign extension rules cache.
     */
    static void addRulesZipToCache(final Map<String, String> metaData) {
        final File assetDir = new File(CampaignConstants.CACHE_BASE_DIR + File.separator + CampaignConstants.RULES_CACHE_FOLDER);
        final InputStream zipFile = convertResourceFileToInputStream("rules-broadcast.zip");
        final CacheEntry mockCachedZip = new CacheEntry(zipFile, CacheExpiry.never(), metaData);
        ServiceProvider.getInstance().getCacheService().set(assetDir.getAbsolutePath(), CampaignConstants.ZIP_HANDLE, mockCachedZip);
    }

    /**
     * Converts a file in the resources directory into an {@link InputStream}.
     *
     * @param filename the {@code String} filename of a file located in the resource directory
     * @return a {@code InputStream} of the specified file
     */
    static InputStream convertResourceFileToInputStream(final String filename) {
        return TestHelper.class.getClassLoader().getResourceAsStream(filename);
    }

    /**
     * Set the persistence data for Identity extension.
     */
    static void setIdentityPersistence(final Map<String, Object> persistedData, final Application application) {
        if (persistedData != null) {
            final JSONObject persistedJSON = new JSONObject(persistedData);
            updatePersistence("com.adobe.identity",
                    "identity.properties", persistedJSON.toString(), application);
        }
    }

    /**
     * Helper method to update the {@link SharedPreferences} data.
     *
     * @param datastore   the name of the datastore to be updated
     * @param key         the persisted data key that has to be updated
     * @param value       the new value
     * @param application the current test application
     */
    public static void updatePersistence(final String datastore, final String key, final String value, final Application application) {
        if (application == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. Application is null, fast failing the test case.");
        }

        final Context context = application.getApplicationContext();

        if (context == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. Context is null, fast failing the test case.");
        }

        SharedPreferences sharedPreferences = context.getSharedPreferences(datastore, Context.MODE_PRIVATE);

        if (sharedPreferences == null) {
            Assert.fail("Unable to updatePersistence by TestPersistenceHelper. sharedPreferences is null, fast failing the test case.");
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    static Map<String, Object> createIdentityMap(final String namespace, final String id) {
        Map<String, Object> namespaceObj = new HashMap<>();
        namespaceObj.put("authenticationState", "ambiguous");
        namespaceObj.put("id", id);
        namespaceObj.put("primary", false);

        List<Map<String, Object>> namespaceIds = new ArrayList<>();
        namespaceIds.add(namespaceObj);

        Map<String, List<Map<String, Object>>> identityMap = new HashMap<>();
        identityMap.put(namespace, namespaceIds);

        Map<String, Object> xdmMap = new HashMap<>();
        xdmMap.put("identityMap", identityMap);

        return xdmMap;
    }
}