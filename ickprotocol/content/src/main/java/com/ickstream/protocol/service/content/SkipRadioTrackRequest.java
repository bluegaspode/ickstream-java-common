package com.ickstream.protocol.service.content;

import java.util.ArrayList;
import java.util.List;

public class SkipRadioTrackRequest {
    private String itemId;
    private List<String> preferredFormats = new ArrayList<String>();
    private Integer offset;

    public SkipRadioTrackRequest() {
    }

    public SkipRadioTrackRequest(String itemId) {
        this.itemId = itemId;
        this.setOffset(0);
    }

    public SkipRadioTrackRequest(String itemId, List<String> preferredFormats) {
    	this(itemId);
        this.preferredFormats = preferredFormats;
    }
    
    public SkipRadioTrackRequest(String itemId, Integer offset) {
        this.itemId = itemId;
        this.setOffset(offset);
    }
    
    public SkipRadioTrackRequest(String itemId, List<String> preferredFormats, Integer offset) {
    	this(itemId,offset);
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

	public Integer getOffset() {
		return offset;
	}

	public void setOffset(Integer offset) {
		this.offset = offset;
	}
}
