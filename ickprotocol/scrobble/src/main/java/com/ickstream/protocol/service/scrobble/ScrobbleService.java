/*
 * Copyright (c) 2013-2014, ickStream GmbH
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *   * Neither the name of ickStream nor the names of its contributors
 *     may be used to endorse or promote products derived from this software
 *     without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.ickstream.protocol.service.scrobble;

import com.ickstream.common.jsonrpc.*;
import com.ickstream.protocol.common.exception.ServiceException;
import com.ickstream.protocol.common.exception.ServiceTimeoutException;
import com.ickstream.protocol.service.AbstractService;
import org.apache.http.client.HttpClient;

/**
 * Client class for accessing Scrobble service
 * <p/>
 * See the official API documentation for details regarding individual methods and parameters.
 */
public class ScrobbleService extends AbstractService {

    /**
     * Creates a new instance using the specified HttpClient and endpoint URL
     *
     * @param client   The HttpClient instance to use when communicating
     * @param endpoint The endpoint URL to use when communicating
     */
    public ScrobbleService(HttpClient client, String endpoint) {
        this(client, endpoint, (Integer) null);
    }

    /**
     * Creates a new instance using the specified HttpClient, endpoint URL and identity provider
     *
     * @param client     The HttpClient instance to use when communicating
     * @param endpoint   The endpoint URL to use when communicating
     * @param idProvider The identity provider to use when generating JSON-RPC request identities
     */
    public ScrobbleService(HttpClient client, String endpoint, IdProvider idProvider) {
        this(client, endpoint, idProvider, null);
    }

    /**
     * Creates a new instance using the specified HttpClient, endpoint URL and default timeout
     *
     * @param client         The HttpClient instance to use when communicating
     * @param endpoint       The endpoint URL to use when communicating
     * @param defaultTimeout The default timeout to use if no explicity timeout is specified in the method call
     */
    public ScrobbleService(HttpClient client, String endpoint, Integer defaultTimeout) {
        this(client, endpoint, null, defaultTimeout);
    }

    /**
     * Creates a new instance using the specified HttpClient, endpoint URL, identity provider and default timeout
     *
     * @param client         The HttpClient instance to use when communicating
     * @param endpoint       The endpoint URL to use when communicating
     * @param idProvider     The identity provider to use when generating JSON-RPC request identities
     * @param defaultTimeout The default timeout to use if no explicity timeout is specified in the method call
     */
    public ScrobbleService(HttpClient client, String endpoint, IdProvider idProvider, Integer defaultTimeout) {
        super(new HttpMessageSender(client, endpoint, true), idProvider, defaultTimeout);
        ((HttpMessageSender) getMessageSender()).setResponseHandler(this);
    }

    /**
     * Set message logging to use to log incoming and outgoing messages handled by this client
     *
     * @param messageLogger A message logger implementation
     */
    public void setMessageLogger(MessageLogger messageLogger) {
        ((HttpMessageSender) getMessageSender()).setMessageLogger(messageLogger);
    }

    /**
     * Set access token to use for authorization
     *
     * @param accessToken OAuth access token to use for authorization
     */
    public void setAccessToken(String accessToken) {
        ((HttpMessageSender) getMessageSender()).setAccessToken(accessToken);
    }

    public Boolean playedTrack(PlayedItem playedItem) throws ServiceException, ServiceTimeoutException {
        return playedTrack(playedItem, (Integer) null);
    }

    public Boolean playedTrack(PlayedItem playedItem, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("playedTrack", playedItem, Boolean.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void playedTrack(PlayedItem playedItem, MessageHandler<Boolean> messageHandler) {
        playedTrack(playedItem, messageHandler, (Integer) null);
    }

    public void playedTrack(PlayedItem playedItem, MessageHandler<Boolean> messageHandler, Integer timeout) {
        sendRequest("playedTrack", playedItem, Boolean.class, messageHandler, timeout);
    }
}
