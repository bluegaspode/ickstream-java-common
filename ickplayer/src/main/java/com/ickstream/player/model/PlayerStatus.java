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

package com.ickstream.player.model;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ickstream.protocol.service.player.PlaybackMode;
import com.ickstream.protocol.service.player.PlaybackQueueItem;
import com.ickstream.protocol.service.player.PlaybackQueueMode;

public class PlayerStatus {
    private Long changedTimestamp = System.currentTimeMillis();
    private Boolean playing = Boolean.FALSE;
    private Double volumeLevel = 0.1;
    private Boolean muted = Boolean.FALSE;
    private Double seekPos;
    private long timeSeekPosWasSet;
    private Integer playbackQueuePos;
    private Integer radioPlaybackPos;
    private PlaybackQueueMode playbackQueueMode = PlaybackQueueMode.QUEUE;
    private PlaybackMode playbackMode = PlaybackMode.QUEUE_STATIC;
    @JsonIgnore
    private PlaybackQueue radioPlaybackQueue; // seperate queue to not loose static queue
    @JsonIgnore
    private PlaybackQueue playbackQueue;

    @JsonIgnore
    private PlayerStatusStorage storage;

    public PlayerStatus() {
        this(new PlaybackQueue(), new PlaybackQueue());
    }

    public PlayerStatus(PlaybackQueue playbackQueue) {
    	this(playbackQueue, new PlaybackQueue());
    }

    public PlayerStatus(PlaybackQueue playbackQueue, PlaybackQueue radioPlaybackQueue) {
        this.playbackQueue = playbackQueue;
        this.radioPlaybackQueue = radioPlaybackQueue;
    }

    public PlayerStatus(PlaybackQueue playbackQueue, PlaybackQueue radioPlaybackQueue, PlayerStatusStorage storage) {
        this(playbackQueue, radioPlaybackQueue);
        this.storage = storage;
    }

    public void updateTimestamp() {
        Long newTimestamp = System.currentTimeMillis();
        if (newTimestamp.equals(changedTimestamp)) {
            newTimestamp++;
        }
        changedTimestamp = newTimestamp;
        if (this.storage != null) {
            storage.store(this);
        }
    }

    public Boolean getPlaying() {
        return playing;
    }

    public void setPlaying(Boolean playing) {
        this.playing = playing;
        updateTimestamp();
    }

    public Double getVolumeLevel() {
        return volumeLevel;
    }

    public void setVolumeLevel(Double volumeLevel) {
        this.volumeLevel = volumeLevel;
        updateTimestamp();
    }

    public Boolean getMuted() {
        return muted;
    }

    public void setMuted(Boolean muted) {
        this.muted = muted;
        updateTimestamp();
    }

    public Double getSeekPos() {
    	if (seekPos!=null && getPlaying()) {
    		long nowInMilliseconds = new Date().getTime();
    		return Double.valueOf(seekPos.doubleValue() + ((nowInMilliseconds - timeSeekPosWasSet) / 1000.0));
    	} else {
    		return seekPos;
    	}
    }

    public void setSeekPos(Double seekPos) {
        this.seekPos = seekPos;
        this.timeSeekPosWasSet = new Date().getTime();
    }

    public Integer getPlaybackQueuePos() {
        return playbackQueuePos;
    }

    public void setPlaybackQueuePos(Integer playbackQueuePos) {
        if ((playbackQueuePos == null && this.playbackQueuePos != null) || (playbackQueuePos != null && !playbackQueuePos.equals(this.playbackQueuePos))) {
            this.playbackQueuePos = playbackQueuePos;
            updateTimestamp();
        }
    }

    public PlaybackQueue getPlaybackQueue() {
        return playbackQueue;
    }

    public void setPlaybackQueue(PlaybackQueue playbackQueue) {
        if (playbackQueue != null) {
            this.playbackQueue = playbackQueue;
        } else {
            this.playbackQueue = new PlaybackQueue();
        }
        updateTimestamp();
    }

    public PlaybackQueueMode getPlaybackQueueMode() {
        return playbackQueueMode;
    }

    public void setPlaybackQueueMode(PlaybackQueueMode playbackQueueMode) {
        if (!this.playbackQueueMode.equals(playbackQueueMode)) {
            this.playbackQueueMode = playbackQueueMode;
            updateTimestamp();
        }
    }

    @JsonIgnore
    public PlaybackQueueItem getCurrentPlaylistItem() {
    	if (playbackMode.equals(PlaybackMode.QUEUE_RADIO)) {
    		if (getRadioPlaybackPos() != null) {
    			if (getRadioPlaybackPos() < getRadioPlaybackQueue().getItems().size()) {    				
    				return getRadioPlaybackQueue().getItems().get(getRadioPlaybackPos());
    			}
    		}
    	} else if (playbackMode.equals(PlaybackMode.QUEUE_STATIC)) {
    		if (getPlaybackQueuePos() != null) {
	            if (getPlaybackQueuePos() < getPlaybackQueue().getItems().size()) {
	                return getPlaybackQueue().getItems().get(getPlaybackQueuePos());
	            }
    		}
    	}
        return null;
    }

    public Long getChangedTimestamp() {
        return changedTimestamp;
    }

    public PlayerStatusStorage getStorage() {
        return storage;
    }

    public void setStorage(PlayerStatusStorage storage) {
        this.storage = storage;
    }

	public PlaybackMode getPlaybackMode() {
		return playbackMode;
	}

	public void setPlaybackMode(PlaybackMode playbackMode) {
		if (!this.playbackMode.equals(playbackMode)) {
            this.playbackMode = playbackMode;
            updateTimestamp();
        }
	}

	public Integer getRadioPlaybackPos() {
		return radioPlaybackPos;
	}

	public void setRadioPlaybackPos(Integer radioPlaybackPos) {
        if ((radioPlaybackPos == null && this.radioPlaybackPos != null) || (radioPlaybackPos != null && !radioPlaybackPos.equals(this.radioPlaybackPos))) {
            this.radioPlaybackPos = radioPlaybackPos;
            updateTimestamp();
        }
	}

	public PlaybackQueue getRadioPlaybackQueue() {
		return radioPlaybackQueue;
	}

	public void setRadioPlaybackQueue(PlaybackQueue radioPlaybackQueue) {
		if (radioPlaybackQueue != null) {
			this.radioPlaybackQueue = radioPlaybackQueue;			
		} else {
			this.radioPlaybackQueue = new PlaybackQueue();
		}
        updateTimestamp();
	}
}
