/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.common.jsonrpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import junit.framework.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.*;

public class SyncJsonRpcClientTest extends AbstractJsonRpcTest {
    ObjectMapper mapper = new ObjectMapper();

    private static class MessageErrorSenderImpl extends MessageSenderImpl {

        public MessageErrorSenderImpl(JsonRpcResponse responseData) {
            super(responseData);
        }

        @Override
        public void sendMessage(String message) {
            this.message = message;
            try {
                getResponseData().setId(getParamFromJson(message, "id"));
                getResponseData().setResult(null);
                getResponseData().setError(new JsonRpcResponse.Error(-32000, "Some message", "Some data"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            getResponseHandler().onResponse(getResponseData());
        }
    }

    private static class MessageSenderImpl implements MessageSender {
        String message;
        private JsonRpcResponse responseData;
        private JsonRpcResponseHandler responseHandler;

        public MessageSenderImpl(JsonRpcResponse responseData) {
            this.responseData = responseData;
        }

        public JsonRpcResponseHandler getResponseHandler() {
            return responseHandler;
        }

        public void setResponseHandler(JsonRpcResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        public JsonRpcResponse getResponseData() {
            return responseData;
        }

        @Override
        public void sendMessage(String message) {
            this.message = message;
            try {
                responseData.setId(getParamFromJson(message, "id"));
                responseData.setResult(new ObjectMapper().valueToTree(2 * Integer.valueOf(getParamFromJson(message, "params"))));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            responseHandler.onResponse(responseData);
        }
    }

    private static class DelayedMessageSenderImpl implements MessageSender {
        private String message;
        private JsonRpcResponse responseData;
        private JsonRpcResponseHandler responseHandler;
        private long delay;

        public DelayedMessageSenderImpl(JsonRpcResponse responseData, long delay) {
            this.responseData = responseData;
            this.delay = delay;
        }

        public JsonRpcResponseHandler getResponseHandler() {
            return responseHandler;
        }

        public void setResponseHandler(JsonRpcResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        @Override
        public void sendMessage(final String message) {
            this.message = message;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    try {
                        responseData.setId(getParamFromJson(message, "id"));
                    } catch (IOException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                    responseHandler.onResponse(responseData);
                }
            }).start();
        }
    }

    public static class MultipleDelayedMessageSenderImpl implements MessageSender {
        private JsonRpcResponse response;
        private int multiplier;
        private JsonRpcResponseHandler responseHandler;

        public MultipleDelayedMessageSenderImpl(JsonRpcResponse response, int multiplier) {
            this.response = response;
            this.multiplier = multiplier;
        }

        public JsonRpcResponseHandler getResponseHandler() {
            return responseHandler;
        }

        public void setResponseHandler(JsonRpcResponseHandler responseHandler) {
            this.responseHandler = responseHandler;
        }

        @Override
        public void sendMessage(String message) {
            try {
                int input = Integer.valueOf(getParamFromJson(message, "params"));
                JsonRpcResponse response = new JsonRpcResponse();
                response.setId(getParamFromJson(message, "id"));
                response.setResult(new ObjectMapper().valueToTree(input * multiplier));
                DelayedMessageSenderImpl sender = new DelayedMessageSenderImpl(response, input * 200);
                sender.setResponseHandler(responseHandler);
                sender.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void testRequestResponse() throws IOException, JsonRpcException, JsonRpcTimeoutException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);
        response.setResult(mapper.valueToTree(new Integer("2")));

        MessageSenderImpl sender = new MessageSenderImpl(response);
        SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        Integer result = client.sendRequest("someMethod", 1, Integer.class);

        Assert.assertEquals("1", getParamFromJson(sender.message, "params"));
        Assert.assertEquals(Integer.valueOf(2), result);
    }

    @Test
    public void testRequestError() throws IOException, JsonRpcException, JsonRpcTimeoutException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);
        response.setResult(mapper.valueToTree(new Integer("2")));

        MessageErrorSenderImpl sender = new MessageErrorSenderImpl(response);
        SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        try {
            client.sendRequest("someMethod", 1, Integer.class);
            Assert.assertFalse(true);
        } catch (JsonRpcException e) {
            Assert.assertEquals(-32000, e.getCode());
            Assert.assertEquals("-32000: Some message", e.getMessage());
            Assert.assertEquals("Some data", e.getData());
        }
    }

    @Test
    public void testRequestWithDelayedResponse() throws IOException, JsonRpcException, JsonRpcTimeoutException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);
        response.setResult(mapper.valueToTree(new Integer("2")));

        DelayedMessageSenderImpl sender = new DelayedMessageSenderImpl(response, 2000);
        SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        Integer result = client.sendRequest("someMethod", 1, Integer.class);

        Assert.assertEquals("1", getParamFromJson(sender.message, "params"));
        Assert.assertEquals(Integer.valueOf(2), result);
    }

    @Test
    public void testRequestWithDelayedResponseTimeout() throws IOException, JsonRpcException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);
        response.setResult(mapper.valueToTree(new Integer("2")));

        DelayedMessageSenderImpl sender = new DelayedMessageSenderImpl(response, 2000);
        SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        try {
            client.sendRequest("someMethod", 1, Integer.class, 500);
            Assert.assertTrue(false);
        } catch (JsonRpcTimeoutException e) {
            //Ok, this is the expected path
        }
        Assert.assertEquals("1", getParamFromJson(sender.message, "params"));
    }

    @Test
    public void testRequestWithDelayedResponseNoTimeout() throws IOException, JsonRpcException, JsonRpcTimeoutException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);
        response.setResult(mapper.valueToTree(new Integer("2")));

        DelayedMessageSenderImpl sender = new DelayedMessageSenderImpl(response, 2000);
        SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        Integer result = client.sendRequest("someMethod", 1, Integer.class, 3000);

        Assert.assertEquals("1", getParamFromJson(sender.message, "params"));
        Assert.assertEquals(Integer.valueOf(2), result);
    }

    @Test
    public void testRequestWithMultipleDelayedResponse() throws IOException, InterruptedException {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null);

        MultipleDelayedMessageSenderImpl sender = new MultipleDelayedMessageSenderImpl(response, 2);
        final SyncJsonRpcClient client = new SyncJsonRpcClient(sender);
        sender.setResponseHandler(client);

        final Map<Integer, Integer> results = Collections.synchronizedMap(new HashMap<Integer, Integer>());
        List<Thread> threads = new ArrayList<Thread>();
        for (int i = 10; i > 0; i--) {
            final int current = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Integer result = client.sendRequest("someMethod", current, Integer.class);
                        results.put(current, result);
                        Assert.assertEquals(Integer.valueOf(current * 2), result);
                    } catch (JsonRpcException e) {
                        throw new RuntimeException(e);
                    } catch (JsonRpcTimeoutException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            threads.add(t);
            t.start();
        }
        for (Thread thread : threads) {
            thread.join();
        }
        Assert.assertEquals(Integer.valueOf(2), results.get(1));
        Assert.assertEquals(Integer.valueOf(4), results.get(2));
        Assert.assertEquals(Integer.valueOf(6), results.get(3));
        Assert.assertEquals(Integer.valueOf(8), results.get(4));
        Assert.assertEquals(Integer.valueOf(10), results.get(5));
        Assert.assertEquals(Integer.valueOf(12), results.get(6));
        Assert.assertEquals(Integer.valueOf(14), results.get(7));
        Assert.assertEquals(Integer.valueOf(16), results.get(8));
        Assert.assertEquals(Integer.valueOf(18), results.get(9));
        Assert.assertEquals(Integer.valueOf(20), results.get(10));
    }
}
