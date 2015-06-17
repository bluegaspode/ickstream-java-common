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

package com.ickstream.common.jsonrpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import org.apache.commons.lang.StringUtils;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;

/**
 * Implementation of a JSON-RPC service, the purpose is to abstract JSON-RPC transport protocol and parsing from the
 * service implementation.
 * <p>
 * The communication is handled over {@link InputStream} and {@link OutputStream}.
 * </p>
 * <p>
 * The implemetation will expose all public methods in the service interface specified
 * as a JSON-RPC service and use the {@link JsonRpcParam}, {@link JsonRpcParamStructure} annotations to detect which
 * method parameters that are required and fill them with data from the incomming JSON-RPC requests.
 * </p>
 * <p>
 * The implementation will also use {@link JsonRpcResult} and {@link JsonRpcErrors} annotations to detect how to create
 * and represent the response or errors from the called method.
 * </p>
 */
public class StreamJsonRpcService {
    private Object serviceImplementation;
    private Class serviceInterface;
    private JsonHelper jsonHelper = new JsonHelper();
    private static final Map<String, List<Method>> methodCache = new HashMap<String, List<Method>>();
    static final Map<String, Method> matchedMethodCache = new HashMap<String, Method>();
    private static final Map<Method, Annotation[][]> methodParameterAnnotationsCache = new HashMap<Method, Annotation[][]>();
    private Boolean returnOnVoid;
    private Boolean ignoreResponses;
    private MessageLogger messageLogger;

    /**
     * Creates a new instance which expose the specified service interface and implements it using the specified
     * service implementation.
     * <p>
     * The instance will not return any response for method with a void return value.
     * The instance will ignore JSON-RPC response messages, this is typically what a service should do since it answers
     * on JSON-RPC requests and doesn't send any JSON-RPC requests by itself.
     * </p>
     *
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param <T>                   The service interface to expose
     */
    public <T> StreamJsonRpcService(T serviceImplementation, Class<T> serviceInterface) {
        this(serviceImplementation, serviceInterface, false, true);
    }

    /**
     * Creates a new instance which expose the specified service interface and implements it using the specified
     * service implementation.
     * <p>
     * The instance will ignore JSON-RPC response messages, this is typically what a service should do since it answers
     * on JSON-RPC requests and doesn't send any JSON-RPC requests by itself.
     * </p>
     *
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param returnOnVoid          true if void methods should return a JSON-RPC response, else false
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     */
    public <I, T extends I> StreamJsonRpcService(T serviceImplementation, Class<I> serviceInterface, Boolean returnOnVoid) {
        this(serviceImplementation, serviceInterface, returnOnVoid, true);
    }

    /**
     * Creates a new instance which expose the specified service interface and implements it using the specified
     * service implementation
     *
     * @param serviceImplementation The service implementation that implements the service interface
     * @param serviceInterface      The service interface to expose
     * @param returnOnVoid          true if void methods should return a JSON-RPC response, else false
     * @param ignoreResponses       true if response message should be completely ignored
     * @param <T>                   The service implementation to use
     * @param <I>                   The service interface to expose
     */
    public <I, T extends I> StreamJsonRpcService(T serviceImplementation, Class<I> serviceInterface, Boolean returnOnVoid, Boolean ignoreResponses) {
        this.serviceImplementation = serviceImplementation;
        this.serviceInterface = serviceInterface;
        this.returnOnVoid = returnOnVoid;
        this.ignoreResponses = ignoreResponses;
    }

    /**
     * Set the message logger implementation to use to log incoming and outgoing messages handled by this service
     *
     * @param messageLogger The message logger implementation
     */
    public void setMessageLogger(MessageLogger messageLogger) {
        this.messageLogger = messageLogger;
    }

    /**
     * Process a JSON-RPC request received on an input stream and write the result (if any) to the specified output
     * stream
     *
     * @param input The input stream that contains the JSON-RPC request
     * @param ops   The output stream where the JSON-RPC response should be written
     */
    protected void handle(InputStream input, OutputStream ops) {
        ValueNode id = null;
        String version = null;

        JsonRpcRequest request = jsonHelper.streamToObject(input, JsonRpcRequest.class);
        if (request == null) {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null);
            response.setError(new JsonRpcResponse.Error(JsonRpcError.INVALID_JSON, "Invalid JSON"));
            if (messageLogger != null) {
                messageLogger.onOutgoingMessage(null, jsonHelper.objectToString(response));
            }
            try {
                jsonHelper.objectToStream(ops, response);
            } catch (IOException e2) {
                e2.printStackTrace();
                throw new RuntimeException(e2);
            }
            return;
        }
        JsonRpcResponse.Error error = null;
        JsonNode result = null;

        if (messageLogger != null) {
            messageLogger.onIncomingMessage(null, jsonHelper.objectToString(request));
        }
        if (StringUtils.isEmpty(request.getJsonrpc()) || StringUtils.isEmpty(request.getMethod())) {
            if (ignoreResponses && StringUtils.isEmpty(request.getMethod())) {
                // Just ignore responses which doesn't have a "method" attribute
                return;
            }
            JsonRpcResponse response = new JsonRpcResponse(
                    !StringUtils.isEmpty(request.getJsonrpc()) ? request.getJsonrpc() : "2.0",
                    (request.getId() != null && !StringUtils.isEmpty(request.getId().asText())) ? request.getId() : null
            );
            response.setError(new JsonRpcResponse.Error(JsonRpcError.INVALID_REQUEST, "Invalid Request"));
            if (messageLogger != null) {
                messageLogger.onOutgoingMessage(null, jsonHelper.objectToString(response));
            }
            try {
                jsonHelper.objectToStream(ops, response);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return;
        }

        version = request.getJsonrpc();
        String methodName = request.getMethod();
        id = request.getId();
        JsonNode paramsNode = request.getParams();

        List<Method> methods = getMethods(methodName, serviceInterface, true);

        if (methods.size() == 0) {
            if (id == null) {
                // This is a notification, let's ignore unknown notifications
                return;
            }
            JsonRpcResponse response = new JsonRpcResponse(version, id);
            if (getMethods(methodName, serviceImplementation.getClass(), false).size() == 0) {
                response.setError(new JsonRpcResponse.Error(JsonRpcError.METHOD_NOT_FOUND, "Method not found"));
            } else {
                response.setError(new JsonRpcResponse.Error(JsonRpcError.UNAUTHORIZED, "Unauthroized access"));
            }
            if (messageLogger != null) {
                messageLogger.onOutgoingMessage(null, jsonHelper.objectToString(response));
            }
            try {
                jsonHelper.objectToStream(ops, response);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
            return;
        }

        Method matchedMethod = null;

        StringBuilder sb = new StringBuilder(200);
        sb.append(serviceImplementation.getClass().getName()).append(".").append(methodName).append(":");
        if (paramsNode != null && !paramsNode.isNull()) {
            Iterator<String> iterator = paramsNode.fieldNames();
            while (iterator.hasNext()) {
                sb.append(iterator.next());
                sb.append(",");
            }
        }
        String matchedMethodKey = sb.toString();
        synchronized (matchedMethodCache) {
            matchedMethod = matchedMethodCache.get(matchedMethodKey);
        }
        if (matchedMethod == null) {
            Method matchingMethodWithOptionalParameters = null;
            for (Method method : methods) {
                boolean match = true;
                boolean exactMatch = true;
                int i = 0;
                for (Annotation[] annotations : method.getParameterAnnotations()) {
                    boolean foundParameter = false;
                    boolean optionalParameter = false;
                    for (Annotation annotation : annotations) {
                        if (annotation instanceof JsonRpcParam) {
                            String name = ((JsonRpcParam) annotation).name();
                            if (paramsNode != null && paramsNode.has(name) && !paramsNode.get(name).isNull()) {
                                Class cls = method.getParameterTypes()[i];
                                if (Number.class.isAssignableFrom(cls)) {
                                    if (paramsNode.get(name).isNumber()) {
                                        foundParameter = true;
                                    }
                                } else if (String.class.isAssignableFrom(cls)) {
                                    if (paramsNode.get(name).isTextual()) {
                                        foundParameter = true;
                                    }
                                } else if (Boolean.class.isAssignableFrom(cls)) {
                                    if (paramsNode.get(name).isBoolean()) {
                                        foundParameter = true;
                                    }
                                } else if (Collection.class.isAssignableFrom(cls)) {
                                    if (paramsNode.get(name).isArray()) {
                                        foundParameter = true;
                                    }
                                } else if (Date.class.isAssignableFrom(cls)) {
                                    if (paramsNode.get(name).isLong()) {
                                        foundParameter = true;
                                    }
                                } else {
                                    if (paramsNode.get(name).isObject()) {
                                        foundParameter = true;
                                    }
                                }

                            } else {
                                foundParameter = false;
                                if (((JsonRpcParam) annotation).optional()) {
                                    optionalParameter = true;
                                }
                            }
                            break;
                        } else if (annotation instanceof JsonRpcParamStructure) {
                            foundParameter = false;
                            optionalParameter = true;
                        } else if (annotation instanceof JsonRpcParamArray) {
                            if (paramsNode != null && !paramsNode.isNull()) {
                                Class cls = method.getParameterTypes()[i];
                                if (Collection.class.isAssignableFrom(cls)) {
                                    if (paramsNode.isArray()) {
                                        foundParameter = true;
                                    }
                                }
                            } else {
                                foundParameter = false;
                                if (((JsonRpcParamArray) annotation).optional()) {
                                    optionalParameter = true;
                                }
                            }
                        }
                    }
                    if (!foundParameter) {
                        if (!optionalParameter) {
                            match = false;
                            break;
                        }
                        exactMatch = false;
                    }
                    i++;
                }
                if (exactMatch && match) {
                    if ((matchedMethod == null || method.getParameterTypes().length > matchedMethod.getParameterTypes().length)) {
                        if ((paramsNode == null && method.getParameterTypes().length == 0) || (method.getParameterTypes().length == paramsNode.size())) {
                            matchedMethod = method;
                        } else {
                            exactMatch = false;
                        }
                    }
                }
                if (!exactMatch && match) {
                    if (matchingMethodWithOptionalParameters == null || method.getParameterTypes().length > matchingMethodWithOptionalParameters.getParameterTypes().length) {
                        matchingMethodWithOptionalParameters = method;
                    }
                }
            }
            if (matchedMethod == null && matchingMethodWithOptionalParameters != null) {
                matchedMethod = matchingMethodWithOptionalParameters;
            }

            if (matchedMethod != null) {
                synchronized (matchedMethodCache) {
                    matchedMethodCache.put(matchedMethodKey, matchedMethod);
                }
            }
        }
        Class returnType = null;
        if (matchedMethod != null) {
            returnType = matchedMethod.getReturnType();
            List<JsonNode> parameters = getInputAgumentsForMethod(matchedMethod, paramsNode);
            try {
                result = invokeMethod(matchedMethod, parameters);
                if (id == null) {
                    // This is a notification, let's ignore the result
                    return;
                }
                JsonRpcResult resultName = matchedMethod.getAnnotation(JsonRpcResult.class);
                if (resultName != null && resultName.value() != null && resultName.value().length() > 0) {
                    result = jsonHelper.createObject(resultName.value(), result);
                }

            } catch (Throwable e) {
                if (InvocationTargetException.class.isInstance(e)) {
                    e = InvocationTargetException.class.cast(e).getTargetException();
                }
                if (matchedMethod.isAnnotationPresent(JsonRpcErrors.class)) {
                    JsonRpcErrors errorMappings = matchedMethod.getAnnotation(JsonRpcErrors.class);
                    for (JsonRpcError jsonRpcError : errorMappings.value()) {
                        if (jsonRpcError.exception().isInstance(e)) {
                            if (!jsonRpcError.data().equals("")) {
                                error = new JsonRpcResponse.Error(jsonRpcError.code(), jsonRpcError.message(), jsonRpcError.data());
                            } else if (e.getMessage() != null && e.getMessage().length() > 0) {
                                error = new JsonRpcResponse.Error(jsonRpcError.code(), jsonRpcError.message(), e.getMessage());
                            } else {
                                error = new JsonRpcResponse.Error(jsonRpcError.code(), jsonRpcError.message());
                            }
                            break;
                        }
                    }
                }
                if (error == null) {
                    if (e.getMessage() == null) {
                        error = new JsonRpcResponse.Error(JsonRpcError.SERVICE_ERROR, e.getMessage(), e.getClass().getName());
                    } else {
                        StringWriter stringWriter = new StringWriter();
                        PrintWriter writer = new PrintWriter(stringWriter);
                        e.printStackTrace(writer);
                        error = new JsonRpcResponse.Error(JsonRpcError.SERVICE_ERROR, stringWriter.toString(), e.getClass().getName());
                    }
                }
            }
            if (id == null) {
                //This is a notification, let's ignore the result
                return;
            }
        } else {
            error = new JsonRpcResponse.Error(JsonRpcError.INVALID_PARAMS, "Invalid parameter list for method " + methodName, paramsNode != null ? paramsNode.toString() : null);
        }
        JsonRpcResponse response = new JsonRpcResponse(version, id);
        if (error == null) {
            if (result != null || !returnType.getName().equals("void") || returnOnVoid) {
                response.setResult(result);
            } else {
                response = null;
            }
        } else if (error != null) {
            response.setError(error);
        }

        if (response != null) {
            if (messageLogger != null) {
                messageLogger.onOutgoingMessage(null, jsonHelper.objectToString(response));
            }
            // write it
            try {
                jsonHelper.objectToStream(ops, response);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }
    }

    private List<JsonNode> getInputAgumentsForMethod(Method method, JsonNode paramsNode) {
        Annotation[][] parameterAnnotations;
        synchronized (methodParameterAnnotationsCache) {
            parameterAnnotations = methodParameterAnnotationsCache.get(method);
            if (parameterAnnotations == null) {
                parameterAnnotations = method.getParameterAnnotations();
                methodParameterAnnotationsCache.put(method, parameterAnnotations);
            }
        }
        List<JsonNode> params = new ArrayList<JsonNode>();
        int i = 0;
        for (Annotation[] annotations : parameterAnnotations) {
            String name = null;
            boolean structure = false;
            boolean array = false;
            for (Annotation annotation : annotations) {
                if (annotation instanceof JsonRpcParam) {
                    name = ((JsonRpcParam) annotation).name();
                } else if (annotation instanceof JsonRpcParamStructure) {
                    structure = true;
                } else if (annotation instanceof JsonRpcParamArray) {
                    array = true;
                }
            }
            if (name != null && paramsNode != null && paramsNode.has(name)) {
                params.add(paramsNode.get(name));
            } else if (structure) {
                if (paramsNode != null) {
                    params.add(paramsNode);
                } else {
                    params.add(jsonHelper.createObject());
                }
            } else if (array) {
                if (paramsNode != null) {
                    params.add(paramsNode);
                } else {
                    params.add(null);
                }
            } else {
                params.add(null);
            }
            i++;

        }
        return params;
    }

    private JsonNode invokeMethod(Method method, List<JsonNode> params) throws IOException, InvocationTargetException, IllegalAccessException {
        Object[] convertedParams = new Object[params.size()];
        Type[] parameterTypes = method.getGenericParameterTypes();

        for (int i = 0; i < parameterTypes.length; i++) {
            if (params.get(i) != null) {
                convertedParams[i] = jsonHelper.jsonToObject(params.get(i), parameterTypes[i]);
            } else {
                convertedParams[i] = null;
            }
        }

        Object result = method.invoke(serviceImplementation, convertedParams);
        return (method.getGenericReturnType() != null) ? jsonHelper.objectToJson(result) : null;
    }

    private List<Method> getMethods(String name, Class serviceInterface, Boolean cached) {
        if (cached) {
            synchronized (methodCache) {
                String cacheKey = serviceImplementation.getClass().getName() + "." + serviceInterface.getName() + "." + name;
                List<Method> methods = methodCache.get(cacheKey);
                if (methods == null) {
                    methods = new ArrayList<Method>();
                    for (Method method : serviceInterface.getMethods()) {
                        if (method.getName().equals(name)) {
                            methods.add(method);
                        }
                    }
                    methodCache.put(cacheKey, methods);
                }
                return methods;
            }
        } else {
            List<Method> methods = new ArrayList<Method>();
            for (Method method : serviceInterface.getMethods()) {
                if (method.getName().equals(name)) {
                    methods.add(method);
                }
            }
            return methods;
        }
    }
}
