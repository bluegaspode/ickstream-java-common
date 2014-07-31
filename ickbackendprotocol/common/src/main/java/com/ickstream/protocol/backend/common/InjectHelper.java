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

package com.ickstream.protocol.backend.common;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Injection helper which keeps track of the current injector and provides methods to get instances of
 * all objects for which injection modules exists
 */
public class InjectHelper {
    private static Injector injector = null;
    private static List<AbstractModule> modules = null;

    /**
     * Get a list the default injection modules used
     *
     * @return A list of injection modules
     */
    public static List<AbstractModule> getModules() {
        if (modules == null) {
            modules = new ArrayList<AbstractModule>();
            Iterator<AbstractModule> moduleIterator = ServiceLoader.load(AbstractModule.class).iterator();
            while (moduleIterator.hasNext()) {
                modules.add(moduleIterator.next());
            }
        }
        return modules;
    }

    /**
     * Set the current injector instance to use
     *
     * @param newInjector The injector instance to use
     */
    public static void setInjector(Injector newInjector) {
        injector = newInjector;
    }

    /**
     * Inject all member variables in the provided object through its @{@link com.google.inject.Inject} annotations
     *
     * @param obj The object to inject member variables in
     */
    public static void injectMembers(Object obj) {
        if (injector == null) {
            throw new RuntimeException("Injector framework not initialized yet");
        }
        injector.injectMembers(obj);
    }

    /**
     * Get an instance of the specified class and name
     *
     * @param T    The class to get an instance for
     * @param name The name of the instance to get
     * @return An instance of the requested class
     */
    public static <T> T instanceWithName(Class<T> T, String name) {
        if (injector == null) {
            throw new RuntimeException("Injector framework not initialized yet");
        }
        return (T) injector.getInstance(Key.get(T, Names.named(name)));
    }

    /**
     * Get an instance of the specified class
     *
     * @param T The class to get an instance for
     * @return An instance of the requested class
     */
    public static <T> T instance(Class<T> T) {
        if (injector == null) {
            throw new RuntimeException("Injector framework not initialized yet");
        }
        return (T) injector.getInstance(Key.get(T));
    }
}
