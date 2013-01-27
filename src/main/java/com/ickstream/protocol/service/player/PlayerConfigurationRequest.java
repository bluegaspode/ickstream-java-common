/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.protocol.service.player;

public class PlayerConfigurationRequest {
    String playerName;
    String accessToken;

    public PlayerConfigurationRequest() {
    }

    public PlayerConfigurationRequest(String playerName, String accessToken) {
        this.playerName = playerName;
        this.accessToken = accessToken;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}
