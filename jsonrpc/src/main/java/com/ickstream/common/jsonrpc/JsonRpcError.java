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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation that is used to specify which error code a specific exception should result in.
 * This annotation is used by {@link StreamJsonRpcService} or one of its implementations to create the appropriate
 * JSON-RPC response message after an exception has been thrown
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonRpcError {
    /**
     * The caller was not authorized to make the specified JSON-RPC request
     */
    public final static int UNAUTHORIZED = -32000;
    /**
     * An internal error has occurred inside the service implementing the processing of the JSON-RPC request
     */
    public final static int SERVICE_ERROR = -32001;
    /**
     * The request contained invalid JSON data
     */
    public final static int INVALID_JSON = -32700;
    /**
     * The request was not valid JSON-RPC request
     */
    public final static int INVALID_REQUEST = -32600;
    /**
     * The request method does not exist
     */
    public final static int METHOD_NOT_FOUND = -32601;
    /**
     * The specified list of parameters to the request is invalid
     */
    public final static int INVALID_PARAMS = -32602;

    /**
     * @return The exception thrown
     */
    Class<? extends Throwable> exception();

    /**
     * The error code to use in JSON-RPC response message
     * @return Error code
     */
    int code();

    /**
     * The error description to use in JSON-RPC response message
     * @return Error message
     */
    String message();

    /**
     * The additional data about the error to use in JSON-RPC response message
     * @return Additional information about the error
     */
    String data() default "";
}
