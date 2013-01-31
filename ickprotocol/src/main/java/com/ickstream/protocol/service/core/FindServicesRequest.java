/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.protocol.service.core;

import com.ickstream.protocol.common.ChunkedRequest;

public class FindServicesRequest extends ChunkedRequest {
    private String type;

    public FindServicesRequest() {
    }

    public FindServicesRequest(String type) {
        this.type = type;
    }

    public FindServicesRequest(Integer offset, Integer count) {
        super(offset, count);
    }

    public FindServicesRequest(Integer offset, Integer count, String type) {
        super(offset, count);
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
