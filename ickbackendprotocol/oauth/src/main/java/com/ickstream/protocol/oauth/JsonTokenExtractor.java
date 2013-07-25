/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.protocol.oauth;

import org.scribe.exceptions.OAuthException;
import org.scribe.extractors.AccessTokenExtractor;
import org.scribe.model.Token;
import org.scribe.utils.Preconditions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonTokenExtractor implements AccessTokenExtractor {
    private Pattern accessTokenPattern = Pattern.compile("\"access_token\"\\s*:\\s*\"(\\S*?)\"");

    @Override
    public Token extract(String response) {
        Preconditions.checkEmptyString(response, "Cannot extract a token from a null or empty String");
        Matcher matcher = accessTokenPattern.matcher(response);
        if (matcher.find()) {
            return new Token(matcher.group(1), "", response);
        } else {
            throw new OAuthException("Cannot extract an acces token. Response was: " + response);
        }
    }

}