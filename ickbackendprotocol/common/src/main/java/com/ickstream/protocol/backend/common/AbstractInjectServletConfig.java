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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Abstract class that implements base functionality needed for a {@link GuiceServletContextListener} implementation.
 * <p/>
 * Typically the subclass overrides {@link #createBusinessLoggingModule()} to activate business logging and also
 * {@link #createCacheManagerModule()} to enable caching.
 */
public abstract class AbstractInjectServletConfig extends GuiceServletContextListener {
    private String serviceId;

    /**
     * Constructor which creates a new instance and configures it with the service identity
     *
     * @param serviceId The service identity
     */
    protected AbstractInjectServletConfig(String serviceId) {
        this.serviceId = serviceId;
    }

    /**
     * Creates and returns the {@link Injector} instance that should be used by Google Guice and
     * also configure {@link InjectHelper} with this instance.
     * <p/>
     * This module installs the {@link ServletModule} implementation that's returned from {@link #createInjectModule()}
     * and in addition to this it also loads some additional modules which are needed by all services.
     * <p/>
     * It's possible to disable caching by using the system property "ickstream-cache=false" or
     * "ickstream-<serviceid>-cache=false" if the {@link #AbstractInjectServletConfig(String)} constructor has been used.
     * <p/>
     * It's possible to disable business logging by using the system property "ickstream-cache=false" or
     * "ickstream-<serviceid>-cache=false" if the {@link #AbstractInjectServletConfig(String)} constructor has been used.
     * <p/>
     * The {@link #createAdditionalInjectModules()} method can be overridden to include additinoal injection modules
     * specific to the service.
     * <p/>
     * The {@link #createCacheManagerModule} method needs to be overriden to enable caching and the
     * {@link #createBusinessLoggingModule()} method needs to be overridden to enable business logging
     *
     * @return The injector that should be used
     */
    @Override
    protected Injector getInjector() {
        List<Module> modules = new ArrayList<Module>();
        modules.add(createInjectModule());
        if (System.getProperty("ickstream-logging", "true").equalsIgnoreCase("true") &&
                (serviceId == null || System.getProperty("ickstream-" + serviceId + "-logging", "true").equalsIgnoreCase("true"))) {
            Module module = createBusinessLoggingModule();
            if (module != null) {
                modules.add(module);
            }
        } else {
            modules.add(new NoBusinessLoggerModule());
        }

        if (System.getProperty("ickstream-cache", "true").equalsIgnoreCase("true") &&
                (serviceId == null || System.getProperty("ickstream-" + serviceId + "-cache", "true").equalsIgnoreCase("true"))) {
            Module module = createCacheManagerModule();
            if (module != null) {
                modules.add(module);
            }
        } else if (System.getProperty("ickstream-core-cache", "true").equalsIgnoreCase("true")) {
            modules.add(new CoreBackendCacheManagerModule());
        } else {
            modules.add(new NoCacheManagerModule());
        }
        modules.addAll(createAdditionalInjectModules());
        Injector injector = Guice.createInjector(modules);
        InjectHelper.setInjector(injector);
        return injector;
    }

    /**
     * Creates and configures the {@link ServletModule} to use to configure and expose services.
     * Typically this should be an implementation of the {@link AbstractInjectModule} class.
     *
     * @return The servlet module to use
     */
    protected abstract ServletModule createInjectModule();

    /**
     * To activate business logging this method has to be overridden and return a module which provides instances of
     * {@link BusinessLogger}
     *
     * @return A module that provides a {@link BusinessLogger} instance
     */
    protected Module createBusinessLoggingModule() {
        return new NoBusinessLoggerModule();
    }

    /**
     * To activate caching this method has to be overridden and return a module which provides instances of
     * {@link CacheManager}. Unless the service wants to have custom caching, it can just return an instance of
     * {@link CacheManagerModule}
     *
     * @return A module that provides a {@link CacheManager} instance
     */
    protected Module createCacheManagerModule() {
        return new CoreBackendCacheManagerModule();
    }

    /**
     * Create and return any additional Guice {@link Module} instances that should be included in the created injector
     * The default implementation provided by this class does not add any additional modules.
     *
     * @return A list of additional modules to include in the injector
     */
    protected List<Module> createAdditionalInjectModules() {
        return new ArrayList<Module>();
    }
}
