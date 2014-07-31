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

package com.ickstream.player.service;

import com.ickstream.common.jsonrpc.*;
import com.ickstream.player.model.PlaybackQueueItemInstance;
import com.ickstream.player.model.PlayerStatus;
import com.ickstream.protocol.common.NetworkAddressHelper;
import com.ickstream.protocol.common.exception.ServiceException;
import com.ickstream.protocol.common.exception.ServiceTimeoutException;
import com.ickstream.protocol.service.core.AddDeviceRequest;
import com.ickstream.protocol.service.core.AddDeviceResponse;
import com.ickstream.protocol.service.core.CoreServiceFactory;
import com.ickstream.protocol.service.player.*;

import java.util.*;

public class PlayerCommandService {
    private String apiKey;
    private PlayerStatus playerStatus;
    private PlayerManager player;
    private Timer volumeNotificationTimer;

    public PlayerCommandService(PlayerStatus playerStatus) {
        this.playerStatus = playerStatus;
    }

    public PlayerCommandService(String apiKey, PlayerManager player, PlayerStatus playerStatus) {
        this.apiKey = apiKey;
        this.playerStatus = playerStatus;
        this.player = player;
    }

    public static List<PlaybackQueueItemInstance> createInstanceList(List<PlaybackQueueItem> items) {
        List<PlaybackQueueItemInstance> instances = new ArrayList<PlaybackQueueItemInstance>(items.size());
        for (PlaybackQueueItem item : items) {
            PlaybackQueueItemInstance instance = new PlaybackQueueItemInstance();
            instance.setId(item.getId());
            instance.setText(item.getText());
            instance.setType(item.getType());
            instance.setItemAttributes(item.getItemAttributes());
            instance.setStreamingRefs(item.getStreamingRefs());
            instance.setImage(item.getImage());
            instances.add(instance);
        }
        return instances;
    }

    public static PlaybackQueueItem createPlaybackQueueItem(PlaybackQueueItemInstance instance) {
        PlaybackQueueItem item = new PlaybackQueueItem();
        item.setId(instance.getId());
        item.setText(instance.getText());
        item.setType(instance.getType());
        item.setItemAttributes(instance.getItemAttributes());
        item.setStreamingRefs(instance.getStreamingRefs());
        item.setImage(instance.getImage());
        return item;
    }

    public static List<PlaybackQueueItem> createPlaybackQueueItemList(List<PlaybackQueueItemInstance> instances) {
        List<PlaybackQueueItem> items = new ArrayList<PlaybackQueueItem>(instances.size());
        for (PlaybackQueueItemInstance instance : instances) {
            items.add(createPlaybackQueueItem(instance));
        }
        return items;
    }

    @JsonRpcErrors({
            @JsonRpcError(exception = ServiceException.class, code = -32001, message = "Error when registering device"),
            @JsonRpcError(exception = ServiceTimeoutException.class, code = -32001, message = "Timeout when registering device")
    })
    public synchronized PlayerConfigurationResponse setPlayerConfiguration(@JsonRpcParamStructure PlayerConfigurationRequest configuration) throws ServiceException, ServiceTimeoutException {
        boolean sendPlayerStatusChanged = false;
        if (configuration.getCloudCoreUrl() != null) {
            if (!player.getCloudCoreUrl().equals(configuration.getCloudCoreUrl())) {
                if (player.hasAccessToken()) {
                    sendPlayerStatusChanged = true;
                }
                player.setCloudCoreUrl(configuration.getCloudCoreUrl());
                if (player.hasAccessToken()) {
                    player.setAccessToken(null);
                }
            }
        }
        if (configuration.getAccessToken() != null) {
            if (configuration.getAccessToken().length() == 0) {
                if (player.hasAccessToken()) {
                    player.setAccessToken(null);
                    sendPlayerStatusChanged = true;
                }
            } else {
                player.setAccessToken(configuration.getAccessToken());
                sendPlayerStatusChanged = true;
            }
        } else if (configuration.getDeviceRegistrationToken() != null && configuration.getDeviceRegistrationToken().length() > 0) {
            AddDeviceRequest request = new AddDeviceRequest();
            request.setAddress(NetworkAddressHelper.getNetworkAddress());
            request.setApplicationId(apiKey);
            request.setHardwareId(player.getHardwareId());
            if (player.hasAccessToken()) {
                player.setAccessToken(null);
            }
            // We will send playerStatusChanged when the registration has finished/failed instead of immediately
            sendPlayerStatusChanged = false;
            CoreServiceFactory.getCoreService(player.getCloudCoreUrl(), configuration.getDeviceRegistrationToken()).addDevice(request, new MessageHandlerAdapter<AddDeviceResponse>() {
                @Override
                public void onMessage(AddDeviceResponse response) {
                    player.setAccessToken(response.getAccessToken());
                }

                @Override
                public void onError(int code, String message, String data) {
                    System.err.println("Error when registering player: " + code + " " + message + " " + data);
                }

                @Override
                public void onFinished() {
                    player.sendPlayerStatusChangedNotification();
                }
            }, 30000);
        } else if (configuration.getDeviceRegistrationToken() != null) {
            if (player.hasAccessToken()) {
                player.setAccessToken(null);
                sendPlayerStatusChanged = true;
            }
        }
        if (configuration.getPlayerName() != null) {
            player.setName(configuration.getPlayerName());
        }
        if (sendPlayerStatusChanged) {
            player.sendPlayerStatusChangedNotification();
        }
        return getPlayerConfiguration();
    }


    public synchronized ProtocolVersionsResponse getProtocolVersions() {
        return new ProtocolVersionsResponse("1.0", "1.0");
    }

    public synchronized PlayerConfigurationResponse getPlayerConfiguration() {
        PlayerConfigurationResponse response = new PlayerConfigurationResponse();
        response.setPlayerName(player.getName());
        response.setHardwareId(player.getHardwareId());
        response.setPlayerModel(player.getModel());
        response.setCloudCoreUrl(player.getCloudCoreUrl());
        return response;
    }

    public synchronized PlayerStatusResponse getPlayerStatus() {
        PlayerStatusResponse response = new PlayerStatusResponse();
        response.setPlaying(playerStatus.getPlaying());
        response.setPlaybackQueuePos(playerStatus.getPlaybackQueuePos());
        if (player != null && playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaying()) {
            playerStatus.setSeekPos(player.getSeekPosition());
        }
        response.setSeekPos(playerStatus.getSeekPos());
        if (playerStatus.getPlaybackQueue().getItems().size() > 0) {
            response.setTrack(createPlaybackQueueItem(playerStatus.getPlaybackQueue().getItems().get(playerStatus.getPlaybackQueuePos())));
        }
        if (player != null && !playerStatus.getMuted()) {
            response.setVolumeLevel(player.getVolume());
        } else {
            response.setVolumeLevel(playerStatus.getVolumeLevel());
        }
        response.setLastChanged(playerStatus.getChangedTimestamp());
        response.setMuted(playerStatus.getMuted());
        response.setPlaybackQueueMode(playerStatus.getPlaybackQueueMode());
        if (player != null && player.hasAccessToken()) {
            response.setCloudCoreStatus(CloudCoreStatus.REGISTERED);
        } else {
            response.setCloudCoreStatus(CloudCoreStatus.UNREGISTERED);
        }
        return response;
    }

    public synchronized SetPlaylistNameResponse setPlaylistName(@JsonRpcParamStructure SetPlaylistNameRequest request) {
        playerStatus.getPlaybackQueue().setId(request.getPlaylistId());
        playerStatus.getPlaybackQueue().setName(request.getPlaylistName());
        if (player != null) {
            player.sendPlaylistChangedNotification();
        }
        return new SetPlaylistNameResponse(playerStatus.getPlaybackQueue().getId(), playerStatus.getPlaybackQueue().getName(), playerStatus.getPlaybackQueue().getItems().size());
    }

    public synchronized PlaybackQueueResponse getPlaybackQueue(@JsonRpcParamStructure PlaybackQueueRequest request) {
        PlaybackQueueResponse response = new PlaybackQueueResponse();
        response.setPlaylistId(playerStatus.getPlaybackQueue().getId());
        response.setPlaylistName(playerStatus.getPlaybackQueue().getName());
        if (request.getOrder() != null) {
            response.setOrder(request.getOrder());
        } else {
            response.setOrder(PlaybackQueueOrder.CURRENT);
        }
        List<PlaybackQueueItem> items = createPlaybackQueueItemList(playerStatus.getPlaybackQueue().getItems());
        if (response.getOrder().equals(PlaybackQueueOrder.ORIGINAL)) {
            items = createPlaybackQueueItemList(playerStatus.getPlaybackQueue().getOriginallyOrderedItems());
        }
        Integer offset = request.getOffset() != null ? request.getOffset() : 0;
        Integer count = request.getCount() != null ? request.getCount() : items.size();
        response.setOffset(offset);
        response.setCountAll(playerStatus.getPlaybackQueue().getItems().size());
        if (offset < items.size()) {
            if (offset + count > items.size()) {
                response.setItems(items.subList(offset, items.size()));
            } else {
                response.setItems(items.subList(offset, offset + count));
            }
        } else {
            response.setItems(items.subList(offset, items.size()));
        }
        response.setCount(response.getItems().size());
        response.setLastChanged(playerStatus.getPlaybackQueue().getChangedTimestamp());
        return response;
    }

    public synchronized PlaybackQueueModificationResponse addTracks(@JsonRpcParamStructure PlaybackQueueAddTracksRequest request) {
        List<PlaybackQueueItemInstance> instances = createInstanceList(request.getItems());
        if (request.getPlaybackQueuePos() != null) {
            // Insert tracks in middle
            playerStatus.getPlaybackQueue().getItems().addAll(request.getPlaybackQueuePos(), instances);
            playerStatus.getPlaybackQueue().getOriginallyOrderedItems().addAll(request.getPlaybackQueuePos(), instances);

            playerStatus.getPlaybackQueue().updateTimestamp();
            if (playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueuePos() >= request.getPlaybackQueuePos()) {
                playerStatus.setPlaybackQueuePos(playerStatus.getPlaybackQueuePos() + request.getItems().size());
            }
        } else {
            if (playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE)) {
                // Add tracks at random position after currently playing track
                int currentPlaybackQueuePos = 0;
                if (playerStatus.getPlaybackQueuePos() != null) {
                    currentPlaybackQueuePos = playerStatus.getPlaybackQueuePos();
                }
                int rangeLength = playerStatus.getPlaybackQueue().getItems().size() - currentPlaybackQueuePos - 1;
                if (rangeLength > 0) {
                    int randomPosition = currentPlaybackQueuePos + (int) (Math.random() * rangeLength) + 1;
                    if (randomPosition < playerStatus.getPlaybackQueue().getItems().size() - 1) {
                        playerStatus.getPlaybackQueue().getItems().addAll(randomPosition, instances);
                    } else {
                        playerStatus.getPlaybackQueue().getItems().addAll(instances);
                    }
                } else {
                    playerStatus.getPlaybackQueue().getItems().addAll(instances);
                }
                playerStatus.getPlaybackQueue().getOriginallyOrderedItems().addAll(instances);
            } else {
                // Add tracks at end
                playerStatus.getPlaybackQueue().getItems().addAll(instances);
                playerStatus.getPlaybackQueue().getOriginallyOrderedItems().addAll(instances);
            }
            playerStatus.getPlaybackQueue().updateTimestamp();
        }
        // Set playback queue position to first track if there weren't any tracks in the playback queue before
        if (playerStatus.getPlaybackQueuePos() == null) {
            playerStatus.setPlaybackQueuePos(0);
        }
        if (player != null) {
            player.sendPlaylistChangedNotification();
        }
        return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
    }

    public synchronized PlaybackQueueModificationResponse removeTracks(@JsonRpcParamStructure PlaybackQueueRemoveTracksRequest request) {

        List<PlaybackQueueItemInstance> modifiedPlaybackQueue = new ArrayList<PlaybackQueueItemInstance>(playerStatus.getPlaybackQueue().getItems());
        List<PlaybackQueueItemInstance> modifiedOriginallyOrderedPlaybackQueue = new ArrayList<PlaybackQueueItemInstance>(playerStatus.getPlaybackQueue().getOriginallyOrderedItems());
        int modifiedPlaybackQueuePos = playerStatus.getPlaybackQueuePos();
        boolean affectsPlayback = false;
        for (PlaybackQueueItemReference itemReference : request.getItems()) {
            if (itemReference.getPlaybackQueuePos() != null) {
                PlaybackQueueItem item = playerStatus.getPlaybackQueue().getItems().get(itemReference.getPlaybackQueuePos());
                if (item.getId().equals(itemReference.getId())) {
                    if (itemReference.getPlaybackQueuePos() < playerStatus.getPlaybackQueuePos()) {
                        modifiedPlaybackQueuePos--;
                    } else if (itemReference.getPlaybackQueuePos().equals(playerStatus.getPlaybackQueuePos())) {
                        affectsPlayback = true;
                    }
                    modifiedPlaybackQueue.remove(item);
                    for (int i = 0; i < modifiedOriginallyOrderedPlaybackQueue.size(); i++) {
                        // Intentionally using == instead of equals as we want the exact instance
                        if (modifiedOriginallyOrderedPlaybackQueue.get(i) == item) {
                            modifiedOriginallyOrderedPlaybackQueue.remove(i);
                        }
                    }

                } else {
                    throw new IllegalArgumentException("Track identity and playback queue position doesn't match (trackId=" + itemReference.getId() + ", playbackQueuePos=" + itemReference.getPlaybackQueuePos() + ")");
                }
            } else {
                int i = 0;
                for (Iterator<PlaybackQueueItemInstance> it = modifiedPlaybackQueue.iterator(); it.hasNext(); i++) {
                    PlaybackQueueItem item = it.next();
                    if (item.getId().equals(itemReference.getId())) {
                        if (i < modifiedPlaybackQueuePos) {
                            modifiedPlaybackQueuePos--;
                        } else if (i == modifiedPlaybackQueuePos) {
                            affectsPlayback = true;
                        }
                        it.remove();
                        for (int j = 0; j < modifiedOriginallyOrderedPlaybackQueue.size(); j++) {
                            // Intentionally using == instead of equals as we want the exact instance
                            if (modifiedOriginallyOrderedPlaybackQueue.get(j) == item) {
                                modifiedOriginallyOrderedPlaybackQueue.remove(j);
                            }
                        }
                    }
                }
            }
        }
        playerStatus.getPlaybackQueue().setOriginallyOrderedItems(modifiedOriginallyOrderedPlaybackQueue);
        playerStatus.getPlaybackQueue().setItems(modifiedPlaybackQueue);

        if (modifiedPlaybackQueuePos >= modifiedPlaybackQueue.size()) {
            if (modifiedPlaybackQueuePos > 0) {
                modifiedPlaybackQueuePos--;
            }
        }
        if (!playerStatus.getPlaybackQueuePos().equals(modifiedPlaybackQueuePos)) {
            playerStatus.setPlaybackQueuePos(modifiedPlaybackQueuePos);
            if (!playerStatus.getPlaying()) {
                if (player != null) {
                    player.sendPlayerStatusChangedNotification();
                }
            }
        }
        // Make sure we make the player aware that it should change track
        if (playerStatus.getPlaying() && affectsPlayback && player != null) {
            if (modifiedPlaybackQueue.size() > 0) {
                player.play();
            } else {
                playerStatus.setPlaybackQueuePos(null);
                playerStatus.setSeekPos(null);
                player.pause();
            }
        }
        if (player != null) {
            player.sendPlaylistChangedNotification();
        }
        return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
    }

    public synchronized PlaybackQueueModificationResponse moveTracks(@JsonRpcParamStructure PlaybackQueueMoveTracksRequest request) {
        Integer modifiedPlaybackQueuePos = playerStatus.getPlaybackQueuePos();
        List<PlaybackQueueItemInstance> modifiedPlaylist = new ArrayList<PlaybackQueueItemInstance>(playerStatus.getPlaybackQueue().getItems());
        Integer wantedPlaybackQueuePos = request.getPlaybackQueuePos() != null ? request.getPlaybackQueuePos() : playerStatus.getPlaybackQueue().getItems().size();
        for (PlaybackQueueItemReference playbackQueueItemReference : request.getItems()) {
            if (playbackQueueItemReference.getPlaybackQueuePos() == null) {
                throw new IllegalArgumentException("moveTracks with items without playbackQueuePos not supported");
            }
            if (playbackQueueItemReference.getId() == null) {
                throw new IllegalArgumentException("moveTracks with items without id not supported");
            }
            // Move that doesn't affect playback queue position
            if (wantedPlaybackQueuePos <= modifiedPlaybackQueuePos && playbackQueueItemReference.getPlaybackQueuePos() < modifiedPlaybackQueuePos ||
                    wantedPlaybackQueuePos > modifiedPlaybackQueuePos && playbackQueueItemReference.getPlaybackQueuePos() > modifiedPlaybackQueuePos) {

                PlaybackQueueItemInstance item = modifiedPlaylist.remove(playbackQueueItemReference.getPlaybackQueuePos().intValue());
                if (!item.getId().equals(playbackQueueItemReference.getId())) {
                    throw new IllegalArgumentException("Playback queue position " + playbackQueueItemReference.getPlaybackQueuePos() + " does not match " + playbackQueueItemReference.getId());
                }
                int offset = 0;
                if (wantedPlaybackQueuePos >= playbackQueueItemReference.getPlaybackQueuePos()) {
                    offset = -1;
                }
                if (wantedPlaybackQueuePos + offset < modifiedPlaylist.size()) {
                    modifiedPlaylist.add(wantedPlaybackQueuePos + offset, item);
                } else {
                    modifiedPlaylist.add(item);
                }
                if (wantedPlaybackQueuePos < playbackQueueItemReference.getPlaybackQueuePos()) {
                    wantedPlaybackQueuePos++;
                }

                // Move that increase playback queue position
            } else if (wantedPlaybackQueuePos <= modifiedPlaybackQueuePos && playbackQueueItemReference.getPlaybackQueuePos() > modifiedPlaybackQueuePos) {
                PlaybackQueueItemInstance item = modifiedPlaylist.remove(playbackQueueItemReference.getPlaybackQueuePos().intValue());
                if (!item.getId().equals(playbackQueueItemReference.getId())) {
                    throw new IllegalArgumentException("Playback queue position " + playbackQueueItemReference.getPlaybackQueuePos() + " does not match " + playbackQueueItemReference.getId());
                }
                modifiedPlaylist.add(wantedPlaybackQueuePos, item);
                modifiedPlaybackQueuePos++;
                wantedPlaybackQueuePos++;

                // Move that decrease playback queue position
            } else if (wantedPlaybackQueuePos > modifiedPlaybackQueuePos && playbackQueueItemReference.getPlaybackQueuePos() < modifiedPlaybackQueuePos) {
                PlaybackQueueItemInstance item = modifiedPlaylist.remove(playbackQueueItemReference.getPlaybackQueuePos().intValue());
                if (!item.getId().equals(playbackQueueItemReference.getId())) {
                    throw new IllegalArgumentException("Playback queue position " + playbackQueueItemReference.getPlaybackQueuePos() + " does not match " + playbackQueueItemReference.getId());
                }
                int offset = 0;
                if (wantedPlaybackQueuePos >= playbackQueueItemReference.getPlaybackQueuePos()) {
                    offset = -1;
                }
                if (wantedPlaybackQueuePos + offset < modifiedPlaylist.size()) {
                    modifiedPlaylist.add(wantedPlaybackQueuePos + offset, item);
                } else {
                    modifiedPlaylist.add(item);
                }
                modifiedPlaybackQueuePos--;

                // Move of currently playing track
            } else if (playbackQueueItemReference.getPlaybackQueuePos().equals(modifiedPlaybackQueuePos)) {
                PlaybackQueueItemInstance item = modifiedPlaylist.remove(playbackQueueItemReference.getPlaybackQueuePos().intValue());
                if (!item.getId().equals(playbackQueueItemReference.getId())) {
                    throw new IllegalArgumentException("Playback queue position " + playbackQueueItemReference.getPlaybackQueuePos() + " does not match " + playbackQueueItemReference.getId());
                }
                if (wantedPlaybackQueuePos < modifiedPlaylist.size() + 1) {
                    if (wantedPlaybackQueuePos > playbackQueueItemReference.getPlaybackQueuePos()) {
                        modifiedPlaylist.add(wantedPlaybackQueuePos - 1, item);
                        modifiedPlaybackQueuePos = wantedPlaybackQueuePos - 1;
                    } else {
                        modifiedPlaylist.add(wantedPlaybackQueuePos, item);
                        modifiedPlaybackQueuePos = wantedPlaybackQueuePos;
                    }
                } else {
                    modifiedPlaylist.add(item);
                    modifiedPlaybackQueuePos = wantedPlaybackQueuePos - 1;
                }
                if (wantedPlaybackQueuePos < playbackQueueItemReference.getPlaybackQueuePos()) {
                    wantedPlaybackQueuePos++;
                }
            }
        }
        if (!(playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE))) {
            playerStatus.getPlaybackQueue().setOriginallyOrderedItems(new ArrayList<PlaybackQueueItemInstance>(modifiedPlaylist));
        }
        playerStatus.getPlaybackQueue().setItems(modifiedPlaylist);

        playerStatus.getPlaybackQueue().updateTimestamp();
        playerStatus.setPlaybackQueuePos(modifiedPlaybackQueuePos);
        return new PlaybackQueueModificationResponse(true, modifiedPlaybackQueuePos);
    }

    public synchronized PlaybackQueueModificationResponse setTracks(@JsonRpcParamStructure PlaybackQueueSetTracksRequest request) {
        playerStatus.getPlaybackQueue().setId(request.getPlaylistId());
        playerStatus.getPlaybackQueue().setName(request.getPlaylistName());
        List<PlaybackQueueItemInstance> instances = createInstanceList(request.getItems());
        playerStatus.getPlaybackQueue().setOriginallyOrderedItems(new ArrayList<PlaybackQueueItemInstance>(instances));
        playerStatus.getPlaybackQueue().setItems(instances);

        Integer playbackQueuePos = request.getPlaybackQueuePos() != null ? request.getPlaybackQueuePos() : 0;
        if (request.getItems().size() > 0) {
            setTrack(playbackQueuePos);
        } else {
            playerStatus.setSeekPos(null);
            playerStatus.setPlaybackQueuePos(null);
            if (player != null && playerStatus.getPlaying()) {
                player.pause();
            }
        }
        if (player != null) {
            player.sendPlaylistChangedNotification();
        }
        return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
    }


    @JsonRpcResult("playing")
    public synchronized Boolean play(@JsonRpcParam(name = "playing") Boolean play) {
        if (playerStatus.getPlaybackQueuePos() != null && play != null) {
            if (!playerStatus.getPlaying() && play) {
                if (player == null || player.play()) {
                    playerStatus.setPlaying(true);
                }
            } else if (playerStatus.getPlaying() && !play) {
                if (player == null || player.pause()) {
                    playerStatus.setPlaying(false);
                }
            }
        }
        return playerStatus.getPlaying();
    }

    public synchronized SeekPosition getSeekPosition() {
        SeekPosition response = new SeekPosition();
        response.setPlaybackQueuePos(playerStatus.getPlaybackQueuePos());
        if (player != null && playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaying()) {
            playerStatus.setSeekPos(player.getSeekPosition());
        }
        response.setSeekPos(playerStatus.getSeekPos());
        return response;
    }

    public synchronized SeekPosition setSeekPosition(@JsonRpcParamStructure SeekPosition request) {
        if (request.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueue().getItems().size() > request.getPlaybackQueuePos()) {
            playerStatus.setPlaybackQueuePos(request.getPlaybackQueuePos());
            Double seekPosition = request.getSeekPos() != null ? request.getSeekPos() : 0;
            //TODO: Handle logic regarding seek position and length of track
            playerStatus.setSeekPos(seekPosition);
            return getSeekPosition();
        } else {
            throw new IllegalArgumentException("Invalid playback queue position specified");
        }
    }

    public synchronized TrackResponse getTrack(@JsonRpcParam(name = "playbackQueuePos", optional = true) Integer playbackQueuePos) {
        TrackResponse response = new TrackResponse();
        response.setPlaylistId(playerStatus.getPlaybackQueue().getId());
        response.setPlaylistName(playerStatus.getPlaybackQueue().getName());
        if (playbackQueuePos != null && playbackQueuePos < playerStatus.getPlaybackQueue().getItems().size()) {
            response.setPlaybackQueuePos(playbackQueuePos);
            response.setTrack(createPlaybackQueueItem(playerStatus.getPlaybackQueue().getItems().get(playbackQueuePos)));
        } else if (playbackQueuePos == null && playerStatus.getPlaybackQueuePos() != null) {
            response.setPlaybackQueuePos(playerStatus.getPlaybackQueuePos());
            response.setTrack(createPlaybackQueueItem(playerStatus.getPlaybackQueue().getItems().get(playerStatus.getPlaybackQueuePos())));
        }
        return response;
    }

    @JsonRpcResult("playbackQueuePos")
    public synchronized Integer setTrack(@JsonRpcParam(name = "playbackQueuePos") Integer playbackQueuePos) {
        if (playbackQueuePos != null && playbackQueuePos < playerStatus.getPlaybackQueue().getItems().size()) {
            playerStatus.setPlaybackQueuePos(playbackQueuePos);
            playerStatus.setSeekPos(0d);
            // Make sure we make the player aware that it should change track
            if (playerStatus.getPlaying() && player != null) {
                player.play();
            } else {
                if (player != null) {
                    player.sendPlayerStatusChangedNotification();
                }
            }
            return playbackQueuePos;
        } else {
            throw new IllegalArgumentException("Invalid playback queue position specified");
        }
    }

    @JsonRpcResult("track")
    public synchronized PlaybackQueueItem setTrackMetadata(@JsonRpcParamStructure TrackMetadataRequest request) {
        if (request.getPlaybackQueuePos() != null) {
            if (request.getPlaybackQueuePos() < playerStatus.getPlaybackQueue().getItems().size()) {
                PlaybackQueueItemInstance item = playerStatus.getPlaybackQueue().getItems().get(request.getPlaybackQueuePos());
                if (request.getTrack().getId().equals(item.getId())) {
                    if (request.getReplace()) {
                        item.setType(request.getTrack().getType());
                        item.setImage(request.getTrack().getImage());
                        item.setText(request.getTrack().getText());
                        item.setItemAttributes(request.getTrack().getItemAttributes());
                        item.setStreamingRefs(request.getTrack().getStreamingRefs());
                        playerStatus.getPlaybackQueue().updateTimestamp();
                    } else {
                        if (request.getTrack().getImage() != null) {
                            item.setImage(request.getTrack().getImage());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getText() != null) {
                            item.setText(request.getTrack().getText());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getType() != null) {
                            item.setType(request.getTrack().getType());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getStreamingRefs() != null) {
                            item.setStreamingRefs(request.getTrack().getStreamingRefs());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        //TODO: Implement copying of item attributes
                    }
                    if (playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueuePos().equals(request.getPlaybackQueuePos())) {
                        playerStatus.updateTimestamp();
                        if (player != null) {
                            player.sendPlayerStatusChangedNotification();
                        }
                    }
                    if (player != null) {
                        player.sendPlaylistChangedNotification();
                    }
                    return playerStatus.getPlaybackQueue().getItems().get(request.getPlaybackQueuePos());
                } else {
                    throw new RuntimeException("Specified track doesn't exist at the specified playback queue position");
                }
            } else {
                throw new RuntimeException("Invalid playback queue position");
            }
        } else {
            PlaybackQueueItem response = null;
            for (int playbackQueuePos = 0; playbackQueuePos < playerStatus.getPlaybackQueue().getItems().size(); playbackQueuePos++) {
                PlaybackQueueItemInstance item = playerStatus.getPlaybackQueue().getItems().get(playbackQueuePos);
                if (request.getTrack().getId().equals(item.getId())) {
                    if (request.getReplace()) {
                        item.setType(request.getTrack().getType());
                        item.setImage(request.getTrack().getImage());
                        item.setText(request.getTrack().getText());
                        item.setItemAttributes(request.getTrack().getItemAttributes());
                        item.setStreamingRefs(request.getTrack().getStreamingRefs());
                        playerStatus.getPlaybackQueue().updateTimestamp();
                        response = request.getTrack();
                    } else {
                        if (request.getTrack().getImage() != null) {
                            item.setImage(request.getTrack().getImage());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getText() != null) {
                            item.setText(request.getTrack().getText());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getType() != null) {
                            item.setType(request.getTrack().getType());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        if (request.getTrack().getStreamingRefs() != null) {
                            item.setStreamingRefs(request.getTrack().getStreamingRefs());
                            playerStatus.getPlaybackQueue().updateTimestamp();
                        }
                        //TODO: Implement copying of item attributes
                        response = item;
                    }
                    if (playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueuePos().equals(playbackQueuePos)) {
                        playerStatus.updateTimestamp();
                        if (player != null) {
                            player.sendPlayerStatusChangedNotification();
                        }
                    }
                }
            }
            if (player != null) {
                player.sendPlaylistChangedNotification();
            }
            return response;
        }
    }

    public synchronized VolumeResponse getVolume() {
        VolumeResponse response = new VolumeResponse();
        if (player != null && !playerStatus.getMuted()) {
            response.setVolumeLevel(player.getVolume());
        } else {
            response.setVolumeLevel(playerStatus.getVolumeLevel());
        }
        response.setMuted(playerStatus.getMuted());
        return response;
    }

    public synchronized VolumeResponse setVolume(@JsonRpcParamStructure VolumeRequest request) {
        Double volume = playerStatus.getVolumeLevel();
        if (request.getVolumeLevel() != null) {
            volume = request.getVolumeLevel();
        } else if (request.getRelativeVolumeLevel() != null) {
            volume += request.getRelativeVolumeLevel();
        }
        if (volume < 0) {
            volume = 0d;
        }
        if (volume > 1) {
            volume = 1d;
        }
        playerStatus.setVolumeLevel(volume);
        if (player != null) {
            if ((request.getMuted() == null && !playerStatus.getMuted()) ||
                    (request.getMuted() != null && !request.getMuted())) {

                player.setVolume(volume);
            }
        }
        if (request.getMuted() != null) {
            playerStatus.setMuted(request.getMuted());
            if (player != null && request.getMuted()) {
                player.setVolume(0.0);
            }
        }
        if (volumeNotificationTimer == null) {
            volumeNotificationTimer = new Timer();
            volumeNotificationTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    if (player != null) {
                        player.sendPlayerStatusChangedNotification();
                    }
                    volumeNotificationTimer = null;
                }
            }, 2000);
        }
        return getVolume();
    }

    public synchronized PlaybackQueueModeResponse setPlaybackQueueMode(@JsonRpcParamStructure PlaybackQueueModeRequest request) {
        boolean shuffle = false;
        if ((playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE)) &&
                !(request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE))) {
            Integer currentPos = playerStatus.getPlaybackQueuePos();
            PlaybackQueueItemInstance currentTrack = null;
            if (currentPos != null) {
                currentTrack = playerStatus.getPlaybackQueue().getItems().get(currentPos);
            }
            playerStatus.getPlaybackQueue().setItems(new ArrayList<PlaybackQueueItemInstance>(playerStatus.getPlaybackQueue().getOriginallyOrderedItems()));
            if (currentTrack != null) {
                int newPos = playerStatus.getPlaybackQueue().getItems().indexOf(currentTrack);
                if (newPos >= 0) {
                    playerStatus.setPlaybackQueuePos(newPos);
                }
            }
        } else if ((request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) && !playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE)) ||
                (request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE) && !playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE))) {
            shuffle = true;
        }
        playerStatus.setPlaybackQueueMode(request.getPlaybackQueueMode());
        if (shuffle) {
            shuffleTracks();
        } else if (player != null) {
            player.sendPlayerStatusChangedNotification();
        }
        return new PlaybackQueueModeResponse(playerStatus.getPlaybackQueueMode());
    }

    public synchronized PlaybackQueueModificationResponse shuffleTracks() {
        List<PlaybackQueueItemInstance> playbackQueueItems = playerStatus.getPlaybackQueue().getItems();
        if (playbackQueueItems.size() > 1) {
            PlaybackQueueItemInstance currentItem = null;
            if (playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueuePos() < playbackQueueItems.size()) {
                currentItem = playbackQueueItems.remove(playerStatus.getPlaybackQueuePos().intValue());
            }
            Collections.shuffle(playbackQueueItems);
            if (currentItem != null) {
                playbackQueueItems.add(0, currentItem);
            }
            if (!playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) && !playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE)) {
                playerStatus.getPlaybackQueue().setOriginallyOrderedItems(new ArrayList<PlaybackQueueItemInstance>(playbackQueueItems));
            }
            playerStatus.getPlaybackQueue().setItems(playbackQueueItems);
            playerStatus.setPlaybackQueuePos(0);
            if (player != null) {
                player.sendPlaylistChangedNotification();
                player.sendPlayerStatusChangedNotification();
            }
        }
        return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
    }

    public synchronized PlaybackQueueModificationResponse setDynamicPlaybackQueueParameters(@JsonRpcParamStructure DynamicPlaybackQueueParametersRequest request) {
        //TODO: Implement
        return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
    }
}
