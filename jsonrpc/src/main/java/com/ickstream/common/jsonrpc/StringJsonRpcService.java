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

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.WriterOutputStream;

import java.io.IOException;
import java.io.StringWriter;

/**
 * Implementation of a JSON-RPC service, the purpose is to abstract JSON-RPC transport protocol and parsing from the
 * service implementation.
 * <p>
 * The communication is handled as {@link String} objects. If the communication channel is already using
 * {@link java.io.InputStream} and {@link java.io.OutputStream} it's preferred to use the {@link StreamJsonRpcService} instead of this
 * class
 * </p>
 * <p>
 * See {@link StreamJsonRpcService} for more details
 * </p>
 */
public class StringJsonRpcService extends StreamJsonRpcService {
    /**
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     *
     * See {@link StreamJsonRpcService#StreamJsonRpcService(Object, Class)} for more information
     */
    public <I, T extends I> StringJsonRpcService(T serviceImplementation, Class<I> serviceInterface) {
        super(serviceImplementation, serviceInterface);
    }

    /**
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param returnOnVoid          true if void methods should return a JSON-RPC response, else false
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     *
     * See {@link StreamJsonRpcService#StreamJsonRpcService(Object, Class, Boolean)} for more information
     */
    public <I, T extends I> StringJsonRpcService(T serviceImplementation, Class<I> serviceInterface, Boolean returnOnVoid) {
        super(serviceImplementation, serviceInterface, returnOnVoid);
    }

    /**
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param returnOnVoid          true if void methods should return a JSON-RPC response, else false
     * @param ignoreResponses       true if response message should be completely ignored
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     *
     * See {@link StreamJsonRpcService#StreamJsonRpcService(Object, Class, Boolean, Boolean)} for more information
     */
    public <I, T extends I> StringJsonRpcService(T serviceImplementation, Class<I> serviceInterface, Boolean returnOnVoid, Boolean ignoreResponses) {
        super(serviceImplementation, serviceInterface, returnOnVoid, ignoreResponses);
    }

    /**
     * Process a JSON-RPC request received as a {@link String} and produce the response as a {@link String}, it's the
     * caller responsibility to read/write the strings to the appropriate communication channel
     *
     * @param request The JSON-RPC request/notification as a string
     * @return The JSON-RPC response as a String or null if no response is generated
     */
    public String handle(String request) {
        StringWriter writer = new StringWriter();
        try {
            super.handle(IOUtils.toInputStream(request, "UTF-8"), new WriterOutputStream(writer, "UTF-8"));
        } catch (IOException e) {
            e.printStackTrace();
            JsonRpcResponse response = new JsonRpcResponse("2.0", null);
            response.setError(new JsonRpcResponse.Error(JsonRpcError.INVALID_JSON, "Invalid JSON"));
            return new JsonHelper().objectToString(response);
        }
        String result = writer.toString();
        if (result != null && result.length() > 0) {
            return result;
        } else {
            return null;
        }
    }
}
