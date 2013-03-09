/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.controller.device;

import com.ickstream.common.ickdiscovery.DeviceListener;
import com.ickstream.common.ickdiscovery.ServiceType;
import com.ickstream.common.jsonrpc.MessageHandlerAdapter;
import com.ickstream.common.jsonrpc.MessageLogger;
import com.ickstream.controller.ObjectChangeListener;
import com.ickstream.controller.ThreadFramework;
import com.ickstream.protocol.service.core.CoreService;
import com.ickstream.protocol.service.core.DeviceResponse;
import com.ickstream.protocol.service.core.FindDevicesResponse;

import java.util.*;

public class DeviceDiscoveryController implements DeviceListener {
    protected final Map<String, Device> devices = new HashMap<String, Device>();
    private final List<ObjectChangeListener<Device>> deviceListeners = new ArrayList<ObjectChangeListener<Device>>();
    private MessageLogger messageLogger;
    private CoreService coreService;
    private ThreadFramework threadFramework;

    private enum EventSource {
        CLOUD,
        NETWORK
    }

    public DeviceDiscoveryController(ThreadFramework threadFramework, CoreService coreService, MessageLogger messageLogger) {
        this.messageLogger = messageLogger;
        this.coreService = coreService;
        this.threadFramework = threadFramework;
    }

    public void setAccessToken(String accessToken) {
        coreService.setAccessToken(accessToken);
    }

    public void refreshDevices() {
        coreService.findDevices(null, new MessageHandlerAdapter<FindDevicesResponse>() {
            @Override
            public void onMessage(FindDevicesResponse message) {
                Set<String> previous;
                synchronized (devices) {
                    previous = new HashSet<String>(devices.keySet());
                    for (DeviceResponse response : message.getItems()) {
                        previous.remove(response.getId());
                    }
                }
                for (DeviceResponse response : message.getItems()) {
                    addUpdateDiscoveredDevices(response.getId(), response.getName(), EventSource.CLOUD, response.getModel(), response.getAddress(), response.getPublicAddress());
                }
                for (String serviceId : previous) {
                    removeDiscoveredDevice(serviceId, EventSource.CLOUD);
                }
            }
        }, 5000);
    }

    @Override
    public void onDeviceAdded(final String deviceId, final String deviceName, final ServiceType type) {
        threadFramework.invoke(new Runnable() {
            @Override
            public void run() {
                if (type.isType(ServiceType.PLAYER)) {
                    addUpdateDiscoveredDevices(deviceId, deviceName, EventSource.NETWORK);
                }
            }
        });
    }

    @Override
    public void onDeviceUpdated(final String deviceId, final String deviceName, final ServiceType type) {
        threadFramework.invoke(new Runnable() {
            @Override
            public void run() {
                if (type.isType(ServiceType.PLAYER)) {
                    addUpdateDiscoveredDevices(deviceId, deviceName, EventSource.NETWORK);
                } else {
                    removeDiscoveredDevice(deviceId, EventSource.NETWORK);
                }
            }
        });
    }

    @Override
    public void onDeviceRemoved(final String deviceId) {
        threadFramework.invoke(new Runnable() {
            @Override
            public void run() {
                removeDiscoveredDevice(deviceId, EventSource.NETWORK);
            }
        });
    }

    public void addDeviceListener(ObjectChangeListener<Device> deviceListener) {
        synchronized (deviceListeners) {
            deviceListeners.add(deviceListener);
        }
    }

    public void removeDeviceListener(ObjectChangeListener<Device> deviceListener) {
        synchronized (deviceListeners) {
            deviceListeners.remove(deviceListener);
        }
    }

    protected void removeDiscoveredDevice(final String deviceId, EventSource eventSource) {
        Device device;
        boolean removedDevice = false;
        synchronized (devices) {
            device = devices.get(deviceId);
            if (device != null) {
                if ((device.getCloudState() != Device.CloudState.REGISTERED && eventSource == EventSource.NETWORK) ||
                        (device.getConnectionState() != Device.ConnectionState.DISCONNECTED && eventSource == EventSource.CLOUD)) {
                    devices.remove(deviceId);
                    removedDevice = true;
                }
            }
        }
        if (device != null && removedDevice) {
            for (ObjectChangeListener<Device> deviceListener : deviceListeners) {
                deviceListener.onRemoved(device);
            }
        } else if (device != null && device.getCloudState() == Device.CloudState.REGISTERED && eventSource == EventSource.NETWORK) {
            device.setConnectionState(Device.ConnectionState.DISCONNECTED);
        } else if (device != null && device.getConnectionState() == Device.ConnectionState.DISCONNECTED && eventSource == EventSource.CLOUD) {
            device.setCloudState(Device.CloudState.UNREGISTERED);
        }
    }

    protected void addUpdateDiscoveredDevices(final String deviceId, final String deviceName, EventSource eventSource) {
        addUpdateDiscoveredDevices(deviceId, deviceName, eventSource, null, null, null);
    }

    protected void addUpdateDiscoveredDevices(final String deviceId, final String name, EventSource eventSource, final String model, final String address, final String publicAddress) {
        Device device;
        boolean newDevice = true;
        synchronized (devices) {
            if (devices.containsKey(deviceId)) {
                device = devices.get(deviceId);
                if (name != null && (eventSource == EventSource.CLOUD || device.getCloudState() != Device.CloudState.REGISTERED)) {
                    device.setName(name);
                }
                if (model != null) {
                    device.setModel(model);
                }
                if (address != null) {
                    device.setAddress(address);
                }
                newDevice = false;
            } else {
                device = new Device(deviceId);
                device.setName(name);
                device.setModel(model);
                device.setAddress(address);
                device.setPublicAddress(publicAddress);
                devices.put(deviceId, device);
            }
        }
        for (ObjectChangeListener<Device> deviceListener : deviceListeners) {
            if (newDevice) {
                deviceListener.onAdded(device);
            }
        }
        if (eventSource == EventSource.CLOUD) {
            device.setCloudState(Device.CloudState.REGISTERED);
        } else {
            device.setConnectionState(Device.ConnectionState.CONNECTED);
        }
    }

    public List<Device> getDevices() {
        synchronized (devices) {
            return new ArrayList<Device>(devices.values());
        }
    }
}