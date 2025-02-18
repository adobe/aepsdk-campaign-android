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

import androidx.annotation.NonNull;
import com.adobe.marketing.mobile.services.DataEntity;
import com.adobe.marketing.mobile.services.HitProcessing;
import com.adobe.marketing.mobile.services.HitProcessingResult;
import com.adobe.marketing.mobile.services.Log;
import com.adobe.marketing.mobile.services.NamedCollection;
import com.adobe.marketing.mobile.services.NetworkRequest;
import com.adobe.marketing.mobile.services.Networking;
import com.adobe.marketing.mobile.services.ServiceProvider;
import com.adobe.marketing.mobile.util.StringUtils;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Implements {@code HitProcessing} and aids in the necessary tasks to send network requests for the
 * Campaign Extension.
 */
class CampaignHitProcessor implements HitProcessing {
    private static final int RETRY_INTERVAL = 30;
    private final String SELF_TAG = "CampaignHitProcessor";

    @Override
    public int retryInterval(@NonNull final DataEntity dataEntity) {
        return RETRY_INTERVAL;
    }

    /**
     * Processes and attempts to send the provided {@code DataEntity}.
     *
     * <p>
     *
     * <p>This method will call {@code NetworkService#connectAsync(NetworkRequest, NetworkCallback)}
     * to send the {@link CampaignExtension} network request represented by the {@link CampaignHit}.
     * A {@code NetworkService.HttpConnection} instance is returned from the {@code
     * NetworkService#connectUrl()} method call which is used to determine if the {@code
     * CampaignExtension} network request should be retried.
     *
     * <p>If the {@code NetworkService.HttpConnecting} connection is null (which occurs when the
     * android device has no network connection), this method will return {@code boolean} true and
     * the {@code hit} will be retried later.
     *
     * <p>If the {@code NetworkService.HttpConnecting} connection contains a response code that is
     * recoverable ({@link HttpURLConnection#HTTP_CLIENT_TIMEOUT}, {@link
     * HttpURLConnection#HTTP_UNAVAILABLE}, or {@link HttpURLConnection#HTTP_GATEWAY_TIMEOUT}) this
     * method will return {@code boolean} true and the {@code hit} will be retried later.
     *
     * <p>If the {@code NetworkService.HttpConnecting} contains a {@link HttpURLConnection#HTTP_OK}
     * response code, or any additional response code not considered to be a recoverable network
     * error, then this method will return {@code boolean} false. The {@code hit} will be considered
     * processed and will not be retried.
     *
     * @param dataEntity {@link DataEntity} instance to be processed
     * @param hitProcessingResult {@link HitProcessingResult} containing the status of the hit
     *     processing
     */
    @Override
    public void processHit(
            @NonNull final DataEntity dataEntity,
            @NonNull final HitProcessingResult hitProcessingResult) {
        if (dataEntity == null || StringUtils.isNullOrEmpty(dataEntity.getData())) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processHit - Data entity contained an empty payload. Hit will not be"
                            + " processed.");
            hitProcessingResult.complete(true);
            return;
        }

        // convert the data entity to a campaign hit
        final CampaignHit campaignHit = Utils.campaignHitFromDataEntity(dataEntity);
        if (campaignHit == null) {
            Log.trace(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processHit - error occurred when creating a Campaign Hit from the given data"
                            + " entity");
            hitProcessingResult.complete(true);
            return;
        }

        final Map<String, String> headers =
                new HashMap<String, String>() {
                    {
                        put(CampaignConstants.HTTP_HEADER_KEY_CONNECTION, "close");
                        put(
                                CampaignConstants.HTTP_HEADER_KEY_CONTENT_TYPE,
                                CampaignConstants.HTTP_HEADER_CONTENT_TYPE_JSON_APPLICATION);
                        put(CampaignConstants.HTTP_HEADER_KEY_ACCEPT, "*/*");
                    }
                };
        final Networking networkService = ServiceProvider.getInstance().getNetworkService();
        if (networkService == null) {
            Log.warning(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processHit -The network service is unavailable, the hit will be retried"
                            + " later.");
            hitProcessingResult.complete(false);
            return;
        }
        final NetworkRequest networkRequest =
                new NetworkRequest(
                        campaignHit.url,
                        campaignHit.getHttpCommand(),
                        campaignHit.payload.getBytes(StandardCharsets.UTF_8),
                        headers,
                        campaignHit.timeout,
                        campaignHit.timeout);

        final CountDownLatch latch = new CountDownLatch(1);
        networkService.connectAsync(
                networkRequest,
                connection -> {
                    if (connection == null
                            || (connection.getResponseCode()
                                    == CampaignConstants.INVALID_CONNECTION_RESPONSE_CODE)) {
                        Log.debug(
                                CampaignConstants.LOG_TAG,
                                SELF_TAG,
                                "processHit - Could not process a Campaign network request because"
                                        + " the connection was null or response code was invalid."
                                        + " Retrying the request.");
                        hitProcessingResult.complete(false);
                    } else if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                        Log.debug(
                                CampaignConstants.LOG_TAG,
                                SELF_TAG,
                                "processHit - Request was sent to (%s)",
                                campaignHit.url);
                        updateTimestampInNamedCollection(System.currentTimeMillis());
                        hitProcessingResult.complete(true);
                        connection.close();
                    } else if (!CampaignConstants.recoverableNetworkErrorCodes.contains(
                            connection.getResponseCode())) {
                        Log.debug(
                                CampaignConstants.LOG_TAG,
                                SELF_TAG,
                                "processHit - Unrecoverable network error while processing"
                                        + " requests. Discarding request.");
                        hitProcessingResult.complete(true);
                        connection.close();
                    } else {
                        Log.debug(
                                CampaignConstants.LOG_TAG,
                                SELF_TAG,
                                "processHit - Recoverable network error while processing requests,"
                                        + " will retry.");
                        hitProcessingResult.complete(false);
                    }
                    latch.countDown();
                });
        try {
            latch.await((CampaignConstants.CAMPAIGN_TIMEOUT_DEFAULT + 1), TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Log.warning(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "processHit - exception occurred while waiting for connectAsync latch: %s",
                    e.getMessage());
        }
    }

    /**
     * Updates {@value CampaignConstants#CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY} in
     * {@code CampaignExtension}'s {@code NamedCollection}.
     *
     * <p>
     *
     * @param timestamp {@code long} containing the time of the last successful registration.
     */
    protected void updateTimestampInNamedCollection(final long timestamp) {
        final NamedCollection campaignNamedCollection =
                ServiceProvider.getInstance()
                        .getDataStoreService()
                        .getNamedCollection(CampaignConstants.CAMPAIGN_NAMED_COLLECTION_NAME);
        if (campaignNamedCollection == null) {
            Log.debug(
                    CampaignConstants.LOG_TAG,
                    SELF_TAG,
                    "updateTimestampInNamedCollection -  Campaign Data store is not available to"
                            + " update.");
            return;
        }

        Log.trace(
                CampaignConstants.LOG_TAG,
                SELF_TAG,
                "updateTimestampInNamedCollection -  Persisting timestamp (%d) in Campaign Data"
                        + " Store.",
                timestamp);
        campaignNamedCollection.setLong(
                CampaignConstants.CAMPAIGN_NAMED_COLLECTION_REGISTRATION_TIMESTAMP_KEY, timestamp);
    }
}
