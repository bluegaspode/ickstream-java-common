/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.controller.browse;

import com.ickstream.common.jsonrpc.MessageHandlerAdapter;
import com.ickstream.controller.service.ServiceController;
import com.ickstream.protocol.common.data.ContentItem;
import com.ickstream.protocol.service.content.ContentResponse;

import java.util.*;

public class FullBrowseMenu extends AbstractBrowseMenu {
    private Map<String, TypeMenuItem> typeMenus = new HashMap<String, TypeMenuItem>();
    private Map<String, Integer> typeMenuPriorities = new HashMap<String, Integer>();

    public FullBrowseMenu(ServiceController service, List<TypeMenuItem> typeMenusTemplates) {
        super(service);
        for (TypeMenuItem typeMenu : typeMenusTemplates) {
            this.typeMenus.put(typeMenu.getId(), typeMenu);
        }
        initTypeMenuPriorities(typeMenusTemplates);
    }

    private void initTypeMenuPriorities(List<TypeMenuItem> typeMenuTemplates) {
        int i = typeMenus.size();
        for (TypeMenuItem typeMenu : typeMenuTemplates) {
            this.typeMenuPriorities.put(typeMenu.getId(), i);
            i--;
        }
    }

    @Override
    public void findContexts(final ResponseListener<BrowseResponse> listener) {
        getProtocol(new ResponseListener<Boolean>() {
            @Override
            public void onResponse(Boolean contentResponse) {
                listener.onResponse(createContextsResponse());
            }
        });
    }

    @Override
    public void findItemsInContext(final String contextId, final MenuItem contentItem, final ResponseListener<BrowseResponse> listener) {
        if (contentItem == null || contentItem instanceof ContextMenuItem) {
            getProtocol(new ResponseListener<Boolean>() {
                @Override
                public void onResponse(Boolean contentResponse) {
                    BrowseResponse response = createTypeMenuResponse(contextId, contentItem);
                    listener.onResponse(response);
                }
            });
        } else {
            findItemsInContextByType(contextId, null, contentItem, listener);
        }
    }

    @Override
    public void findItemsInContextByType(final String contextId, final String type, final MenuItem contentItem, final ResponseListener<BrowseResponse> listener) {
        getProtocol(new ResponseListener<Boolean>() {
            @Override
            public void onResponse(Boolean contentResponse) {
                Map<String, String> parameters = createChildRequestParameters(contextId, type, contentItem);
                if (parameters != null) {
                    final String context = parameters.remove("contextId");
                    service.findItems(null, context, new HashMap<String, Object>(parameters), new MessageHandlerAdapter<ContentResponse>() {
                        @Override
                        public void onMessage(ContentResponse message) {
                            if (message != null) {
                                BrowseResponse browseResponse = new BrowseResponse();
                                browseResponse.setExpirationTimestamp(message.getExpirationTimestamp());
                                browseResponse.setCount(message.getCount());
                                browseResponse.setCountAll(message.getCountAll());
                                browseResponse.setOffset(message.getOffset());
                                browseResponse.setItems(new ArrayList<MenuItem>(message.getCount()));
                                for (ContentItem item : message.getItems()) {
                                    browseResponse.getItems().add(new ContentMenuItem(service, context, item, contentItem));
                                }
                                listener.onResponse(browseResponse);
                            }
                        }
                    }, 10000);
                } else {
                    listener.onResponse(null);
                }
            }
        });
    }

    private BrowseResponse createContextsResponse() {
        BrowseResponse response = new BrowseResponse();
        response.setItems(new ArrayList<MenuItem>());
        response.setItems(new ArrayList<MenuItem>(findPossibleTopLevelContexts()));

        if (response.getItems().size() > 1) {
            Iterator<MenuItem> it = response.getItems().iterator();
            while (it.hasNext()) {
                if (it.next().getContextId().equals("allMusic")) {
                    it.remove();
                }
            }
        }

        response.setOffset(0);
        response.setCount(response.getItems().size());
        response.setCountAll(response.getItems().size());
        return response;
    }

    private BrowseResponse createTypeMenuResponse(String contextId, MenuItem parent) {
        BrowseResponse response = new BrowseResponse();
        response.setItems(new ArrayList<MenuItem>());
        List<String> possibleTypeRequests = findPossibleTypeRequests(contextId, parent);
        for (String possibleTypeRequest : possibleTypeRequests) {
            if (typeMenus.containsKey(possibleTypeRequest)) {
                TypeMenuItem template = typeMenus.get(possibleTypeRequest);
                response.getItems().add(new TypeMenuItem(service, contextId, possibleTypeRequest, template.getText(), template.getImage(), parent));
            } else {
                response.getItems().add(new TypeMenuItem(service, contextId, possibleTypeRequest, possibleTypeRequest, null, parent));
            }
        }
        response.setOffset(0);
        response.setCount(response.getItems().size());
        response.setCountAll(response.getItems().size());
        return response;
    }

    private Map<String, String> createChildRequestParameters(String contextId, String type, MenuItem parentItem) {
        if (contextId != null) {
            // Find within the same context
            Map<String, String> result = createChildRequestParametersFromContext(contextId, type, parentItem);
            if (result != null) {
                return result;
            }
        }
        if (contextId == null || !contextId.equals("allMusic")) {
            // Find within the allMusic context
            Map<String, String> result = createChildRequestParametersFromContext("allMusic", type, parentItem);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    private Map<String, String> createChildRequestParametersFromContext(String contextId, String type, MenuItem parentItem) {
        return createChildRequestParametersFromContext(contextId, type, parentItem, new Comparator<Map<String, String>>() {
            @Override
            public int compare(Map<String, String> entry1, Map<String, String> entry2) {
                if (entry1.containsKey("type") && !entry2.containsKey("type")) {
                    return -1;
                } else if (!entry1.containsKey("type") && entry2.containsKey("type")) {
                    return 1;
                } else {
                    return getTypePriority(entry1.get("type")) - getTypePriority(entry2.get("type"));
                }
            }
        });
    }

    private Integer getTypePriority(String type) {
        if (typeMenuPriorities.containsKey(type)) {
            return typeMenuPriorities.get(type);
        } else {
            return 0;
        }
    }
}