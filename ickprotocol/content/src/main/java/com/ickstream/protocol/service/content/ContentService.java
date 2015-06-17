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

package com.ickstream.protocol.service.content;

import com.ickstream.common.jsonrpc.*;
import com.ickstream.player.model.PlaybackQueueItemInstance;
import com.ickstream.protocol.common.ChunkedRequest;
import com.ickstream.protocol.common.data.ContentItem;
import com.ickstream.protocol.common.data.StreamingReference;
import com.ickstream.protocol.common.exception.ServiceException;
import com.ickstream.protocol.common.exception.ServiceTimeoutException;
import com.ickstream.protocol.service.AbstractService;
import com.ickstream.protocol.service.AccountInformation;
import com.ickstream.protocol.service.PersonalizedService;

import java.util.HashMap;
import java.util.Map;

/**
 * Abstract class that implements client access to content services implementing the Content Access protocol
 * <p>
 * See the official API documentation for details regarding individual methods and parameters.
 * </p>
 */
public abstract class ContentService extends AbstractService implements PersonalizedService {
    private String id;
    protected final static String PROTOCOL_VERSION = "2.0";

    /**
     * Creates a new instance for the specified service identity using the specified message sender
     *
     * @param id            The identity of the service this client should communicate with
     * @param messageSender The message sender implementation which should be used when sending message to the service
     */
    public ContentService(String id, MessageSender messageSender) {
        this(id, messageSender, (Integer) null);
    }

    /**
     * Creates a new instance for the specified service identity using the specified message sender
     *
     * @param id            The identity of the service this client should communicate with
     * @param messageSender The message sender implementation which should be used when sending message to the service
     * @param idProvider    The identity provider to use to generate JSON-RPC request identities
     */
    public ContentService(String id, MessageSender messageSender, IdProvider idProvider) {
        this(id, messageSender, idProvider, null);
    }

    /**
     * Creates a new instance for the specified service identity using the specified message sender
     *
     * @param id             The identity of the service this client should communicate with
     * @param messageSender  The message sender implementation which should be used when sending message to the service
     * @param defaultTimeout The default timeout when not explicitly specified in a method call
     */
    public ContentService(String id, MessageSender messageSender, Integer defaultTimeout) {
        this(id, messageSender, null, defaultTimeout);
    }

    /**
     * Creates a new instance for the specified service identity using the specified message sender
     *
     * @param id             The identity of the service this client should communicate with
     * @param messageSender  The message sender implementation which should be used when sending message to the service
     * @param idProvider     The identity provider to use to generate JSON-RPC request identities
     * @param defaultTimeout The default timeout when not explicitly specified in a method call
     */
    public ContentService(String id, MessageSender messageSender, IdProvider idProvider, Integer defaultTimeout) {
        super(messageSender, idProvider, defaultTimeout);
        this.id = id;
    }

    /**
     * Get the identity of the service this client communicates with
     *
     * @return The identity of the service
     */
    public String getId() {
        return id;
    }

    /**
     * Set the message logger to use to log messages produced by this client
     *
     * @param messageLogger A message logger implementation
     */
    public abstract void setMessageLogger(MessageLogger messageLogger);

    @Override
    public AccountInformation getAccountInformation() throws ServiceException, ServiceTimeoutException {
        return getAccountInformation((Integer) null);
    }

    @Override
    public AccountInformation getAccountInformation(Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getAccountInformation", null, AccountInformation.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getAccountInformation(MessageHandler<AccountInformation> messageHandler) {
        getAccountInformation(messageHandler, (Integer) null);
    }

    public void getAccountInformation(MessageHandler<AccountInformation> messageHandler, Integer timeout) {
        sendRequest("getAccountInformation", null, AccountInformation.class, messageHandler, timeout);
    }

    @Deprecated
    public GetProtocolDescriptionResponse getProtocolDescription(GetProtocolDescriptionRequest request) throws ServiceException, ServiceTimeoutException {
        return getProtocolDescription(request, (Integer) null);
    }

    @Deprecated
    public GetProtocolDescriptionResponse getProtocolDescription(GetProtocolDescriptionRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getProtocolDescription", request, GetProtocolDescriptionResponse.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    @Deprecated
    public void getProtocolDescription(GetProtocolDescriptionRequest request, MessageHandler<GetProtocolDescriptionResponse> messageHandler) {
        getProtocolDescription(request, messageHandler, (Integer) null);
    }

    @Deprecated
    public void getProtocolDescription(GetProtocolDescriptionRequest request, MessageHandler<GetProtocolDescriptionResponse> messageHandler, Integer timeout) {
        sendRequest("getProtocolDescription", request, GetProtocolDescriptionResponse.class, messageHandler, timeout);
    }

    public GetProtocolDescription2Response getProtocolDescription2(GetProtocolDescriptionRequest request) throws ServiceException, ServiceTimeoutException {
        return getProtocolDescription2(request, (Integer) null);
    }

    public GetProtocolDescription2Response getProtocolDescription2(GetProtocolDescriptionRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getProtocolDescription2", request, GetProtocolDescription2Response.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getProtocolDescription2(GetProtocolDescriptionRequest request, MessageHandler<GetProtocolDescription2Response> messageHandler) {
        getProtocolDescription2(request, messageHandler, (Integer) null);
    }

    public void getProtocolDescription2(GetProtocolDescriptionRequest request, MessageHandler<GetProtocolDescription2Response> messageHandler, Integer timeout) {
        sendRequest("getProtocolDescription2", request, GetProtocolDescription2Response.class, messageHandler, timeout);
    }

    public GetManagementProtocolDescriptionResponse getManagementProtocolDescription(ChunkedRequest request) throws ServiceException, ServiceTimeoutException {
        return getManagementProtocolDescription(request, (Integer) null);
    }

    public GetManagementProtocolDescriptionResponse getManagementProtocolDescription(ChunkedRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getManagementProtocolDescription", request, GetManagementProtocolDescriptionResponse.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getManagementProtocolDescription(ChunkedRequest request, MessageHandler<GetManagementProtocolDescriptionResponse> messageHandler) {
        getManagementProtocolDescription(request, messageHandler, (Integer) null);
    }

    public void getManagementProtocolDescription(ChunkedRequest request, MessageHandler<GetManagementProtocolDescriptionResponse> messageHandler, Integer timeout) {
        sendRequest("getManagementProtocolDescription", request, GetManagementProtocolDescriptionResponse.class, messageHandler, timeout);
    }

    public GetPreferredMenusResponse getPreferredMenus(GetPreferredMenusRequest request) throws ServiceException, ServiceTimeoutException {
        return getPreferredMenus(request, (Integer) null);
    }

    public GetPreferredMenusResponse getPreferredMenus(GetPreferredMenusRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getPreferredMenus", request, GetPreferredMenusResponse.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getPreferredMenus(GetPreferredMenusRequest request, MessageHandler<GetPreferredMenusResponse> messageHandler) {
        getPreferredMenus(request, messageHandler, (Integer) null);
    }

    public void getPreferredMenus(GetPreferredMenusRequest request, MessageHandler<GetPreferredMenusResponse> messageHandler, Integer timeout) {
        sendRequest("getPreferredMenus", request, GetPreferredMenusResponse.class, messageHandler, timeout);
    }

    public ContentResponse findItems(ChunkedRequest request, String contextId, String language, Map<String, Object> params) throws ServiceException, ServiceTimeoutException {
        return findItems(request, contextId, language, params, (Integer) null);
    }

    public ContentResponse findItems(ChunkedRequest request, String contextId, String language, Map<String, Object> params, Integer timeout) throws ServiceException, ServiceTimeoutException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (request != null) {
            if (request.getCount() != null) {
                parameters.put("count", request.getCount());
            }
            if (request.getOffset() != null) {
                parameters.put("offset", request.getOffset());
            }
        }
        if (contextId != null) {
            parameters.put("contextId", contextId);
        }
        if (language != null) {
            parameters.put("language", language);
        }
        parameters.putAll(params);
        try {
            return sendRequest("findItems", parameters, ContentResponse.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void findItems(ChunkedRequest request, String contextId, String language, Map<String, Object> params, MessageHandler<ContentResponse> messageHandler) {
        findItems(request, contextId, language, params, messageHandler, (Integer) null);
    }

    public void findItems(ChunkedRequest request, String contextId, String language, Map<String, Object> params, MessageHandler<ContentResponse> messageHandler, Integer timeout) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (request != null) {
            if (request.getCount() != null) {
                parameters.put("count", request.getCount());
            }
            if (request.getOffset() != null) {
                parameters.put("offset", request.getOffset());
            }
        }
        if (contextId != null) {
            parameters.put("contextId", contextId);
        }
        if (language != null) {
            parameters.put("language", language);
        }
        parameters.putAll(params);
        sendRequest("findItems", parameters, ContentResponse.class, messageHandler, timeout);
    }

    public ContentItem getItem(String contextId, String language, String itemId) throws ServiceException, ServiceTimeoutException {
        return getItem(contextId, language, itemId, (Integer) null);
    }

    public ContentItem getItem(String contextId, String language, String itemId, Integer timeout) throws ServiceException, ServiceTimeoutException {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (itemId != null) {
            parameters.put("itemId", contextId);
        }
        if (contextId != null) {
            parameters.put("contextId", contextId);
        }
        if (language != null) {
            parameters.put("language", language);
        }
        try {
            return sendRequest("getItem", parameters, ContentItem.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getItem(String contextId, String language, String itemId, MessageHandler<ContentItem> messageHandler) {
        getItem(contextId, language, itemId, messageHandler, (Integer) null);
    }

    public void getItem(String contextId, String language, String itemId, MessageHandler<ContentItem> messageHandler, Integer timeout) {
        Map<String, Object> parameters = new HashMap<String, Object>();
        if (itemId != null) {
            parameters.put("itemId", itemId);
        }
        if (contextId != null) {
            parameters.put("contextId", contextId);
        }
        if (language != null) {
            parameters.put("language", language);
        }
        sendRequest("getItem", parameters, ContentItem.class, messageHandler, timeout);
    }

    public StreamingReference getItemStreamingRef(GetItemStreamingRefRequest request) throws ServiceException, ServiceTimeoutException {
        return getItemStreamingRef(request, (Integer) null);
    }

    public StreamingReference getItemStreamingRef(GetItemStreamingRefRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getItemStreamingRef", request, StreamingReference.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getItemStreamingRef(GetItemStreamingRefRequest request, MessageHandler<StreamingReference> messageHandler) {
        getItemStreamingRef(request, messageHandler, (Integer) null);
    }

    public void getItemStreamingRef(GetItemStreamingRefRequest request, MessageHandler<StreamingReference> messageHandler, Integer timeout) {
        sendRequest("getItemStreamingRef", request, StreamingReference.class, messageHandler, timeout);
    }

    public PlaybackQueueItemInstance getCurrentRadioTrack(GetCurrentRadioTrackRequest request) throws ServiceException, ServiceTimeoutException {
        return getCurrentRadioTrack(request, (Integer) null);
    }

    public PlaybackQueueItemInstance getCurrentRadioTrack(GetCurrentRadioTrackRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getRadioTrack", request, PlaybackQueueItemInstance.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getCurrentRadioTrack(GetCurrentRadioTrackRequest request, MessageHandler<PlaybackQueueItemInstance> messageHandler) {
    	getCurrentRadioTrack(request, messageHandler, (Integer) null);
    }

    public void getCurrentRadioTrack(GetCurrentRadioTrackRequest request, MessageHandler<PlaybackQueueItemInstance> messageHandler, Integer timeout) {
        sendRequest("getRadioTrack", request, PlaybackQueueItemInstance.class, messageHandler, timeout);
    }

    public PlaybackQueueItemInstance getSkippedRadioTrack(SkipRadioTrackRequest request) throws ServiceException, ServiceTimeoutException {
        return getSkippedRadioTrack(request, (Integer) null);
    }

    public PlaybackQueueItemInstance getSkippedRadioTrack(SkipRadioTrackRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("skipRadioTrack", request, PlaybackQueueItemInstance.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getSkippedRadioTrack(SkipRadioTrackRequest request, MessageHandler<PlaybackQueueItemInstance> messageHandler) {
    	getSkippedRadioTrack(request, messageHandler, (Integer) null);
    }

    public void getSkippedRadioTrack(SkipRadioTrackRequest request, MessageHandler<PlaybackQueueItemInstance> messageHandler, Integer timeout) {
        sendRequest("skipRadioTrack", request, PlaybackQueueItemInstance.class, messageHandler, timeout);
    }

    public ContentResponse getNextDynamicPlaylistTracksRequest(GetNextDynamicPlaylistTracksRequest request) throws ServiceException, ServiceTimeoutException {
        return getNextDynamicPlaylistTracksRequest(request, (Integer) null);
    }

    public ContentResponse getNextDynamicPlaylistTracksRequest(GetNextDynamicPlaylistTracksRequest request, Integer timeout) throws ServiceException, ServiceTimeoutException {
        try {
            return sendRequest("getNextDynamicPlaylistTracksRequest", request, ContentResponse.class, timeout);
        } catch (JsonRpcException e) {
            throw getServiceException(e);
        } catch (JsonRpcTimeoutException e) {
            throw new ServiceTimeoutException(e);
        }
    }

    public void getNextDynamicPlaylistTracksRequest(GetNextDynamicPlaylistTracksRequest request, MessageHandler<ContentResponse> messageHandler) {
        getNextDynamicPlaylistTracksRequest(request, messageHandler, (Integer) null);
    }

    public void getNextDynamicPlaylistTracksRequest(GetNextDynamicPlaylistTracksRequest request, MessageHandler<ContentResponse> messageHandler, Integer timeout) {
        sendRequest("getNextDynamicPlaylistTracksRequest", request, ContentResponse.class, messageHandler, timeout);
    }
}
