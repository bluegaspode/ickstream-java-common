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

package com.ickstream.protocol.service.library;

import com.ickstream.common.jsonrpc.MessageLogger;
import com.ickstream.protocol.common.exception.ServiceException;
import com.ickstream.protocol.common.exception.ServiceTimeoutException;
import com.ickstream.protocol.service.core.CoreServiceFactory;
import com.ickstream.protocol.service.core.FindServicesRequest;
import com.ickstream.protocol.service.core.FindServicesResponse;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Factory which should be used to get client classes for {@link LibraryService}
 */
public class LibraryServiceFactory extends CoreServiceFactory {
    /**
     * Get a Library service client and configure it to use the specified url, access token and message logger.
     * The service URL will be detected by calling Cloud Core service.
     *
     * @param accessToken   The OAuth access token to use for authorization
     * @param messageLogger The message logger implementation to use for logging messages
     * @return A client for Scrobble service
     */
    public static LibraryService getLibraryService(String accessToken, MessageLogger messageLogger) {
        return getLibraryService(null, accessToken, messageLogger);
    }

    /**
     * Get a Library service client and configure it to use the specified url, access token and message logger.
     * The service URL will be detected by calling Cloud Core service.
     *
     * @param cloudCoreUrl  The endpoint URL of the Cloud Core service to use
     * @param accessToken   The OAuth access token to use for authorization
     * @param messageLogger The message logger implementation to use for logging messages
     * @return A client for Library service
     */
    public static LibraryService getLibraryService(String cloudCoreUrl, String accessToken, MessageLogger messageLogger) {
        try {
            FindServicesResponse response = getCoreService(cloudCoreUrl, accessToken, messageLogger).findServices(new FindServicesRequest("librarymanagement"));
            if (response != null && response.getItems().size() > 0) {
                LibraryService libraryService = new LibraryService(new DefaultHttpClient(), response.getItems().get(0).getUrl());
                libraryService.setAccessToken(accessToken);
                libraryService.setMessageLogger(messageLogger);
                return libraryService;
            }
        } catch (ServiceException e) {
            e.printStackTrace();
        } catch (ServiceTimeoutException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static LibraryService getLibraryService(String accessToken) {
        return getLibraryService(null, accessToken);
    }

    public static LibraryService getLibraryService(String cloudCoreUrl, String accessToken) {
        return getLibraryService(cloudCoreUrl, accessToken, null);
    }
}
