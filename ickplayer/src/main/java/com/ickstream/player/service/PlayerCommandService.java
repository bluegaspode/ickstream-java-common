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
import com.ickstream.player.model.PlaybackQueue;
import com.ickstream.player.model.PlaybackQueueItemInstance;
import com.ickstream.player.model.PlayerStatus;
import com.ickstream.protocol.common.NetworkAddressHelper;
import com.ickstream.protocol.common.exception.ServiceException;
import com.ickstream.protocol.common.exception.ServiceTimeoutException;
import com.ickstream.protocol.service.core.AddDeviceRequest;
import com.ickstream.protocol.service.core.AddDeviceResponse;
import com.ickstream.protocol.service.core.CoreServiceFactory;
import com.ickstream.protocol.service.core.GetUserResponse;
import com.ickstream.protocol.service.player.*;

import java.util.*;

public class PlayerCommandService {
    private String apiKey;
    private PlayerStatus playerStatus;
    private PlayerManager player;
    private Timer volumeNotificationTimer;
    private final Object syncObject;

    /**
     * Should only be used for testing purposes, use {@link #PlayerCommandService(String, PlayerManager, com.ickstream.player.model.PlayerStatus, Object)} in other scenarios
     *
     * @param playerStatus
     */
    public PlayerCommandService(PlayerStatus playerStatus) {
        this.playerStatus = playerStatus;
        this.syncObject = new Object();
    }

    /**
     * @param syncObject all access to playerStatus will be synchronized against this object.
     *                   It is assumed that a PlayerManager will do likewise based on the actual playerstate.
     *                   By using the syncObject it can be guaranteed that remote calls and internal state changes
     *                   don't get into each other way.
     */
    public PlayerCommandService(String apiKey, PlayerManager player, PlayerStatus playerStatus, final Object syncObject) {
        this.apiKey = apiKey;
        this.playerStatus = playerStatus;
        this.player = player;
        this.syncObject = syncObject;
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
    public PlayerConfigurationResponse setPlayerConfiguration(@JsonRpcParamStructure PlayerConfigurationRequest configuration) throws ServiceException, ServiceTimeoutException {
        synchronized (syncObject) {
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
            if (configuration.getDeviceRegistrationToken() != null && configuration.getDeviceRegistrationToken().length() > 0) {
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
                        String userId = response.getUserId();
                        if (userId == null) {
                            // This is a special case which only happens when used towards an old server that doesn't return userId in addDevice response
                            try {
                                GetUserResponse user = CoreServiceFactory.getCoreService(player.getCloudCoreUrl(), response.getAccessToken()).getUser();
                                if (user != null) {
                                    userId = user.getId();
                                }
                            } catch (ServiceException e) {
                                e.printStackTrace();
                            } catch (ServiceTimeoutException e) {
                                e.printStackTrace();
                            }
                        }
                        player.setUserId(userId);
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
    }


    public ProtocolVersionsResponse getProtocolVersions() {
        return new ProtocolVersionsResponse("1.0", "1.0");
    }

    public PlayerConfigurationResponse getPlayerConfiguration() {
        synchronized (syncObject) {
            PlayerConfigurationResponse response = new PlayerConfigurationResponse();
            response.setId(player.getId());
            response.setPlayerName(player.getName());
            response.setPlayerModel(player.getModel());
            response.setCloudCoreUrl(player.getCloudCoreUrl());
            if (player != null && player.hasAccessToken()) {
                response.setCloudCoreStatus(CloudCoreStatus.REGISTERED);
                response.setUserId(player.getUserId());
            } else {
                response.setCloudCoreStatus(CloudCoreStatus.UNREGISTERED);
            }
            return response;
        }
    }

    public PlayerStatusResponse getPlayerStatus() {
        synchronized (syncObject) {
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
                response.setUserId(player.getUserId());
            } else {
                response.setCloudCoreStatus(CloudCoreStatus.UNREGISTERED);
            }
            return response;
        }
    }

    public SetPlaylistNameResponse setPlaylistName(@JsonRpcParamStructure SetPlaylistNameRequest request) {
        synchronized (syncObject) {
            playerStatus.getPlaybackQueue().setId(request.getPlaylistId());
            playerStatus.getPlaybackQueue().setName(request.getPlaylistName());
            if (player != null) {
                player.sendPlaylistChangedNotification();
            }
            return new SetPlaylistNameResponse(playerStatus.getPlaybackQueue().getId(), playerStatus.getPlaybackQueue().getName(), playerStatus.getPlaybackQueue().getItems().size());
        }
    }

    public PlaybackQueueResponse getPlaybackQueue(@JsonRpcParamStructure PlaybackQueueRequest request) {
        synchronized (syncObject) {
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
    }

    public PlaybackQueueModificationResponse addTracks(@JsonRpcParamStructure PlaybackQueueAddTracksRequest request) {
        synchronized (syncObject) {
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
    }

    public PlaybackQueueModificationResponse removeTracks(@JsonRpcParamStructure PlaybackQueueRemoveTracksRequest request) {
        synchronized (syncObject) {
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
    }

    public PlaybackQueueModificationResponse moveTracks(@JsonRpcParamStructure PlaybackQueueMoveTracksRequest request) {
        synchronized (syncObject) {
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

            if (player != null) {
                // playlist
                player.sendPlaylistChangedNotification();
                // playbackQueuePos
                player.sendPlayerStatusChangedNotification();
            }

            return new PlaybackQueueModificationResponse(true, modifiedPlaybackQueuePos);
        }
    }

    public PlaybackQueueModificationResponse setTracks(@JsonRpcParamStructure PlaybackQueueSetTracksRequest request) {
        synchronized (syncObject) {
			playerStatus.setPlaybackMode(PlaybackMode.QUEUE_STATIC);
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
    }


    @JsonRpcResult("playing")
    public Boolean play(@JsonRpcParam(name = "playing") Boolean play) {
        synchronized (syncObject) {
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
    }

    public SeekPosition getSeekPosition() {
        synchronized (syncObject) {
            SeekPosition response = new SeekPosition();
            response.setPlaybackQueuePos(playerStatus.getPlaybackQueuePos());
            if (player != null && playerStatus.getPlaybackQueuePos() != null && playerStatus.getPlaying()) {
                playerStatus.setSeekPos(player.getSeekPosition());
            }
            response.setSeekPos(playerStatus.getSeekPos());
            return response;
        }
    }

    public SeekPosition setSeekPosition(@JsonRpcParamStructure SeekPosition request) {
        synchronized (syncObject) {
            if (request.getPlaybackQueuePos() != null && playerStatus.getPlaybackQueue().getItems().size() > request.getPlaybackQueuePos()) {
                playerStatus.setPlaybackQueuePos(request.getPlaybackQueuePos());
                Double seekPosition = request.getSeekPos() != null ? request.getSeekPos() : 0;
                //TODO: Handle logic regarding seek position and length of track
                playerStatus.setSeekPos(seekPosition);
                if (player != null) {
                    player.setSeekPosition(seekPosition);
                }

                return getSeekPosition();
            } else {
                throw new IllegalArgumentException("Invalid playback queue position specified");
            }
        }
    }

    public TrackResponse getTrack(@JsonRpcParam(name = "playbackQueuePos", optional = true) Integer playbackQueuePos) {
        synchronized (syncObject) {
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
    }

    @JsonRpcResult("playbackQueuePos")
    public Integer setTrack(@JsonRpcParam(name = "playbackQueuePos") Integer playbackQueuePos) {
        synchronized (syncObject) {
			playerStatus.setPlaybackMode(PlaybackMode.QUEUE_STATIC);
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
    }

    @JsonRpcResult("track")
    public PlaybackQueueItem setTrackMetadata(@JsonRpcParamStructure TrackMetadataRequest request) {
        synchronized (syncObject) {
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
    }
    
	public void setRadioTrack(@JsonRpcParam(name = "radioId") final String radioId ) {
		synchronized (syncObject) {
			playerStatus.setPlaybackMode(PlaybackMode.QUEUE_RADIO);
			
			playerStatus.setRadioPlaybackQueue(new PlaybackQueue());
			
			// creare radio queue
			playerStatus.getRadioPlaybackQueue().setId(radioId);
	        playerStatus.getRadioPlaybackQueue().setName(radioId);
	        // initialize with item empty list
	        // items will be filled dynamically while playing
	        List<PlaybackQueueItemInstance> instances = new ArrayList<PlaybackQueueItemInstance>();
	        playerStatus.getRadioPlaybackQueue().setOriginallyOrderedItems(new ArrayList<PlaybackQueueItemInstance>(instances));
	        playerStatus.getRadioPlaybackQueue().setItems(instances);

	        playerStatus.setRadioPlaybackPos(0);
			
			if (playerStatus.getPlaying() && player != null) {
				player.play();
			} else {
				if (player != null) {
					player.sendPlayerStatusChangedNotification();
				}
			}
		}
	}
	
	public void skipTrack(@JsonRpcParam(name = "offset") final Integer offset ) {
		synchronized (syncObject) {
			if (playerStatus.getPlaybackMode().equals(PlaybackMode.QUEUE_RADIO)) {
				playerStatus.setRadioPlaybackPos(playerStatus.getRadioPlaybackPos() + offset + 1);
				if (playerStatus.getPlaying() && player != null) {
					player.play();
				} else {
					if (player != null) {
						player.sendPlayerStatusChangedNotification();
					}
				}
			}
		}
	}

    public VolumeResponse getVolume() {
        synchronized (syncObject) {
            VolumeResponse response = new VolumeResponse();
            if (player != null && !playerStatus.getMuted()) {
                response.setVolumeLevel(player.getVolume());
            } else {
                response.setVolumeLevel(playerStatus.getVolumeLevel());
            }
            response.setMuted(playerStatus.getMuted());
            return response;
        }
    }

    public VolumeResponse setVolume(@JsonRpcParamStructure VolumeRequest request) {
        synchronized (syncObject) {
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
    }

    public PlaybackQueueModeResponse setPlaybackQueueMode(@JsonRpcParamStructure PlaybackQueueModeRequest request) {
        synchronized (syncObject) {
            boolean shuffle = false;
            boolean shuffleWasTurnedOff = (playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE))
                    && !(request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) || request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE));
            boolean shuffleWasTurnedOn = (request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE) && !playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE))
                    || (request.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_REPEAT_SHUFFLE) && !playerStatus.getPlaybackQueueMode().equals(PlaybackQueueMode.QUEUE_SHUFFLE));

            if (shuffleWasTurnedOff) {
                Integer currentPos = playerStatus.getPlaybackQueuePos();
                PlaybackQueueItemInstance currentTrack = null;
                if (currentPos != null && playerStatus.getPlaybackQueue().getItems().size() > currentPos) {
                    currentTrack = playerStatus.getPlaybackQueue().getItems().get(currentPos);
                }
                playerStatus.getPlaybackQueue().setItems(new ArrayList<PlaybackQueueItemInstance>(playerStatus.getPlaybackQueue().getOriginallyOrderedItems()));
                if (player != null) {
                    player.sendPlaylistChangedNotification();
                }
                if (currentTrack != null) {
                    int newPos = playerStatus.getPlaybackQueue().getItems().indexOf(currentTrack);
                    if (newPos >= 0) {
                        playerStatus.setPlaybackQueuePos(newPos);
                        if (player != null) {
                            player.sendPlayerStatusChangedNotification();
                        }
                    }
                }
            } else if (shuffleWasTurnedOn) {
                shuffle = true;
            }

            playerStatus.setPlaybackQueueMode(request.getPlaybackQueueMode());
            if (shuffle) {
                boolean needsEvents = internalShuffleTracks();
                if (needsEvents && player != null) {
                    player.sendPlaylistChangedNotification();
                    // playerStatusChanged should be sent here, because playbackPosition has changed.
                    // we do it later anyways because of the playbackQueueModeChange
                }
            }
            // every change of playbackQueueMode needs a playerstatusChangedNotification
            if (player != null) {
                player.sendPlayerStatusChangedNotification();
            }
            return new PlaybackQueueModeResponse(playerStatus.getPlaybackQueueMode());
        }
    }

    public PlaybackQueueModificationResponse shuffleTracks() {
        synchronized (syncObject) {
            boolean needsEvents = internalShuffleTracks();
            if (needsEvents && player != null) {
                player.sendPlaylistChangedNotification();
                player.sendPlayerStatusChangedNotification();
            }
            return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
        }
    }

    /**
     * shuffles all tracks without sending any notification. Tracks are not shuffled when there are no or only one item in the playbackQueue
     *
     * @return whether tracks have been shuffled at all and a notification needs to be sent to the player
     * caller has to make sure, that following notifications are sent:
     * playlistChangeNotification: because all tracks have changed their order
     * playerStatusChanged: because the playbackQueuePos will have changed
     */

    private boolean internalShuffleTracks() {
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
            return true;
        }
        return false;
    }

    public synchronized PlaybackQueueModificationResponse setDynamicPlaybackQueueParameters(@JsonRpcParamStructure DynamicPlaybackQueueParametersRequest request) {
        synchronized (syncObject) {
            //TODO: Implement
            return new PlaybackQueueModificationResponse(true, playerStatus.getPlaybackQueuePos());
        }
    }
}
