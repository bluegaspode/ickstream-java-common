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

package com.ickstream.common.jsonrpc;

import com.fasterxml.jackson.databind.node.ValueNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Synchronous client class for JSON-RPC requests. This class can be used independent of communication protocol
 * as the actual sending is done with a separate {@link MessageSender} implementation. If an asynchronous behavior
 * is wanted, use the {@link AsyncJsonRpcClient} class instead.
 */
public class SyncJsonRpcClient extends AsyncJsonRpcClient {
    private Integer defaultTimeout = null;

    /**
     * Creates a new instance which uses the specified message sender class to send messages.
     * The created instance will use the identity provider provided by {@link AsyncJsonRpcClient} to generate unique
     * identities for the JSON-RPC messages
     *
     * @param messageSender The message sender implementation which should be used to send messages
     */
    public SyncJsonRpcClient(MessageSender messageSender) {
        super(messageSender);
    }

    /**
     * Creates a new instance which uses th specified message sender and identity provider implementations
     *
     * @param messageSender The message sender implementation which should be used to send messages
     * @param idProvider    The identity provider implementation which should be use to generate JSON-RPC identities
     */
    public SyncJsonRpcClient(MessageSender messageSender, IdProvider idProvider) {
        super(messageSender, idProvider);
    }

    /**
     * Creates a new instance which uses the specified message sender implementation and provides a default timeout
     * for all calls.
     * The created instance will use the identity provider provided by {@link AsyncJsonRpcClient} to generate unique
     * identities for the JSON-RPC messages
     *
     * @param messageSender  The message sender implementation which should be used to send messages
     * @param defaultTimeout The default timeout which should be used in calls unless a specific timeout has been specified
     */
    public SyncJsonRpcClient(MessageSender messageSender, Integer defaultTimeout) {
        this(messageSender, null, defaultTimeout);
    }

    /**
     * Creates a new instance which uses th specified message sender and identity provider implementations, it also
     * allows you to specify a default timeout for all calls
     *
     * @param messageSender  The message sender implementation which should be used to send messages
     * @param idProvider     The identity provider implementation which should be use to generate JSON-RPC identities
     * @param defaultTimeout The default timeout which should be used in calls unless a specific timeout has been specified
     */
    public SyncJsonRpcClient(MessageSender messageSender, IdProvider idProvider, Integer defaultTimeout) {
        super(messageSender, idProvider, defaultTimeout);
        this.defaultTimeout = null;
    }

    private static class SynchronizedMessageHandler<T> implements MessageHandler<T> {
        private Semaphore semaphore;
        private List<T> results;
        private List<JsonRpcResponse.Error> errors;

        public SynchronizedMessageHandler(Semaphore semaphore, List<T> results, List<JsonRpcResponse.Error> errors) {
            this.semaphore = semaphore;
            this.results = results;
            this.errors = errors;
        }

        @Override
        public void onMessage(T message) {
            results.add(message);
            semaphore.release();
        }

        @Override
        public void onError(int code, String message, String data) {
            errors.add(new JsonRpcResponse.Error(code, message, data));
            semaphore.release();
        }

        @Override
        public void onTimeout() {
        }

        @Override
        public void onFinished() {
            // Do nothing
        }
    }

    /**
     * Send a JSON-RPC request using the specified method and parameters.
     * A timeout can be specified and this will be triggered if no response have been recevied within this time.
     *
     * @param method               The method to call
     * @param params               The parameters to the method, this must be possible to serialize to JSON with the {@link JsonHelper} class
     * @param messageResponseClass The type of the response, must have a default constructor and must be possible to instantiate from JSON using {@link JsonHelper}
     * @param timeout              The timeout in millisecond, if not specified the default timeout will be used
     * @param <T>                  The message response class to use
     * @return The JSON-RPC response
     * @throws JsonRpcTimeoutException if the operation timeout
     * @throws JsonRpcException        if the operation fails due to a JSON-RPC error received form the remote side
     */
    public <T> T sendRequest(String method, Object params, Class<T> messageResponseClass, Integer timeout) throws JsonRpcException, JsonRpcTimeoutException {
        List<T> results = new ArrayList<T>();
        List<JsonRpcResponse.Error> errors = new ArrayList<JsonRpcResponse.Error>();
        Semaphore semaphore = new Semaphore(0);
        MessageHandler<T> messageHandler = new SynchronizedMessageHandler<T>(semaphore, results, errors);

        ValueNode id = super.sendRequest(method, params, messageResponseClass, messageHandler);
        try {
            boolean acquired;
            if ((timeout == null && defaultTimeout == null) || (timeout != null && timeout < 0)) {
                semaphore.acquire();
                acquired = true;
            } else {
                acquired = semaphore.tryAcquire(timeout != null ? timeout : defaultTimeout, TimeUnit.MILLISECONDS);
            }
            if (acquired) {
                if (results.size() > 0) {
                    return results.get(0);
                } else {
                    if (errors.size() > 0 && errors.get(0) != null) {
                        throw new JsonRpcException(errors.get(0).getCode(), errors.get(0).getMessage(), errors.get(0).getData());
                    } else {
                        throw new JsonRpcException();
                    }
                }
            } else {
                removeMessageHandler(id);
                throw new JsonRpcTimeoutException();
            }
        } catch (InterruptedException e) {
            removeMessageHandler(id);
            throw new JsonRpcTimeoutException();
        }
    }

    /**
     * Send a JSON-RPC request using the specified method and parameters.
     *
     * @param method               The method to call
     * @param params               The parameters to the method, this must be possible to serialize to JSON with the {@link JsonHelper} class
     * @param messageResponseClass The type of the response, must have a default constructor and must be possible to instantiate from JSON using {@link JsonHelper}
     * @param <T>                  The message response class to use
     * @return The JSON-RPC response
     * @throws JsonRpcTimeoutException if the operation timeout
     * @throws JsonRpcException        if the operation fails due to a JSON-RPC error received form the remote side
     */
    public <T> T sendRequest(String method, Object params, Class<T> messageResponseClass) throws JsonRpcException, JsonRpcTimeoutException {
        return sendRequest(method, params, messageResponseClass, (Integer) null);
    }
}
