/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.protocol.service.core;

public class AccountChangeIdentityAttributes {
    private String type;
    private String identity;

    public AccountChangeIdentityAttributes() {
    }

    public AccountChangeIdentityAttributes(String type, String identity) {
        this.type = type;
        this.identity = identity;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }
}