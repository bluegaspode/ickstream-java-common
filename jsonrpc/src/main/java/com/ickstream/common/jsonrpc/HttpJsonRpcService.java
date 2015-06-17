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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of a JSON-RPC service, the purpose is to abstract JSON-RPC transport protocol and parsing from the
 * service implementation.
 * <p>
 * The communication is handled as {@link HttpServletRequest} and {@link HttpServletResponse} objects.
 * </p>
 * <p>
 * See {@link StreamJsonRpcService} for more details
 * </p>
 */
public class HttpJsonRpcService extends StreamJsonRpcService {

    /**
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     *
     * See {@link StreamJsonRpcService#StreamJsonRpcService(Object, Class)} for more information
     */
    public <I, T extends I> HttpJsonRpcService(T serviceImplementation, Class<I> serviceInterface) {
        super(serviceImplementation, serviceInterface, true);
    }

    /**
     * Process a JSON-RPC request received as a {@link HttpServletRequest} representing a HTTP POST request and writes
     * the result (if any) to the specified {@link HttpServletResponse}.
     *
     * @param request  The {@link HttpServletRequest} that contains the JSON-RPC request/notification
     * @param response The {@link HttpServletResponse} which the result should be written to
     * @throws java.io.IOException When an error occurrs
     */
    public void handle(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("utf-8");

        InputStream input = null;
        OutputStream output = response.getOutputStream();

        if (request.getMethod().equals("POST")) {
            input = request.getInputStream();
        } else {
            throw new IOException(
                    "Invalid request method, only POST is supported");
        }
        super.handle(input, output);
    }

}