/*
 * Copyright (C) 2012 Erland Isaksson (erland@isaksson.info)
 * All rights reserved.
 */

package com.ickstream.protocol.device.player;

public class VolumeRequest {
    private Double volumeLevel;
    private Double relativeVolumeLevel;
    private Boolean muted;

    public Double getVolumeLevel() {
        return volumeLevel;
    }

    public void setVolumeLevel(Double volumeLevel) {
        this.volumeLevel = volumeLevel;
    }

    public Double getRelativeVolumeLevel() {
        return relativeVolumeLevel;
    }

    public void setRelativeVolumeLevel(Double relativeVolumeLevel) {
        this.relativeVolumeLevel = relativeVolumeLevel;
    }

    public Boolean getMuted() {
        return muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
    }
}
