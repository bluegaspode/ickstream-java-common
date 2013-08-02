/*
 * Copyright (C) 2013 ickStream GmbH
 * All rights reserved
 */

package com.ickstream.common.jsonrpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;

/**
 * Utility class that contains methods to convert objects to JSON and vice versa.
 */
public class JsonHelper {
    private static final ObjectMapper mapper = createObjectMapper();

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * Convert the specified JSON structure to an instance of the specified Java class
     *
     * @param json        The JSON structure to convert
     * @param objectClass The Java class to convert the JSON structure to, the class must have a default constructor
     * @param <T>         The Java class to convert the JSON structure to, the class must have a default constructor
     * @return A new instance of the specified Java class or null if no instance could be created
     */
    public <T> T jsonToObject(JsonNode json, Class<T> objectClass) {
        if (json != null) {
            try {
                return mapper.treeToValue(json, objectClass);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Convert the specified JSON structure to an instance of the specified Java class
     *
     * @param json       The JSON structure to convert
     * @param objectType The Java type to convert the JSON structure to, the class must have a default constructor
     * @return A new instance of the specified Java type or null if no instance could be created
     */
    public Object jsonToObject(JsonNode json, Type objectType) {
        if (json != null) {
            try {
                return mapper.readValue(json.traverse(), mapper.getTypeFactory().constructType(objectType));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * Convert the specified JSON string to an instance of the specified Java class
     *
     * @param text        The JSON string to convert
     * @param objectClass The Java class to convert the JSON structure to, the class must have a default constructor
     * @param <T>         The Java class to convert the JSON structure to, the class must have a default constructor
     * @return A new instance of the specified Java class or null if no instance could be created
     */
    public <T> T stringToObject(String text, Class<T> objectClass) {
        try {
            return mapper.treeToValue(mapper.readTree(text), objectClass);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Convert the JSON data in an {@link InputStream} to an instance of the specified Java class
     *
     * @param stream      The stream containing the JSON daata
     * @param objectClass The Java class to convert the JSON structure to, the class must have a default constructor
     * @param <T>         The Java class to convert the JSON structure to, the class must have a default constructor
     * @return A new instance of the specified Java class or null if no instance could be created
     */
    public <T> T streamToObject(InputStream stream, Class<T> objectClass) {
        try {
            return mapper.treeToValue(mapper.readTree(stream), objectClass);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Converts the specified object to JSON structure
     *
     * @param object The object to convert to JSON
     * @return The converted JSON structure
     */
    public JsonNode objectToJson(Object object) {
        return mapper.valueToTree(object);
    }

    /**
     * Converts the specified object to JSON string
     *
     * @param object The object to convert to a JSON string
     * @return The converted JSON string
     */
    public String objectToString(Object object) {
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Converts the specified object to JSON and write it to an {@link OutputStream}
     *
     * @param output The stream to write the result to
     * @param object The object to convert to JSON
     * @throws IOException If the result couldn't be written to the stream
     */
    public void objectToStream(OutputStream output, Object object) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(output, object);
    }

    /**
     * Create a new empty JSON object structure
     *
     * @return A new empty JSON object structure
     */
    public JsonNode createObject() {
        return mapper.createObjectNode();
    }

    /**
     * Create a new JSON object structure which contains the specified attribute and value
     *
     * @param attribute The attribute name
     * @param value     The value of the attribute
     * @return A new JSON object structure containing the specified attribute
     */
    public JsonNode createObject(String attribute, JsonNode value) {
        if (attribute != null) {
            ObjectNode obj = mapper.createObjectNode();
            obj.put(attribute, value);
            return obj;
        } else if (value != null) {
            return value;
        } else {
            return mapper.createObjectNode();
        }
    }
}
