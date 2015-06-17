package com.ickstream.protocol.service.content;

import java.util.ArrayList;
import java.util.List;

public class GetCurrentRadioTrackRequest {
    private String itemId;
    private List<String> preferredFormats = new ArrayList<String>();

    public GetCurrentRadioTrackRequest() {
    }

    public GetCurrentRadioTrackRequest(String itemId) {
        this.itemId = itemId;
    }

    public GetCurrentRadioTrackRequest(String itemId, List<String> preferredFormats) {
        this.itemId = itemId;
        this.preferredFormats = preferredFormats;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public List<String> getPreferredFormats() {
        return preferredFormats;
    }

    public void setPreferredFormats(List<String> preferredFormats) {
        this.preferredFormats = preferredFormats;
    }
}
