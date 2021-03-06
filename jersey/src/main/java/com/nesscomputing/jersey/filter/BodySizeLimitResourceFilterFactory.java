/**
 * Copyright (C) 2012 Ness Computing, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nesscomputing.jersey.filter;

import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ObjectUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nesscomputing.config.Config;
import com.nesscomputing.jersey.util.MaxSizeInputStream;
import com.nesscomputing.logging.Log;
import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

/**
 * A {@link ResourceFilterFactory} which discovers all resource methods.  If they take
 * an input stream, they will wrap it in {@link MaxSizeInputStream} with the configured
 * size.  Configuration keys, with the highest priority last:
 * <table border=1>
 * <tr><th>Key</th><th>Default Value</th><th>Purpose</th></tr>
 * <tr><td>ness.filter.max-body-size</td><td>1MB</td><td>Global restriction / default</td></tr>
 * <tr><td>ness.filter.max-body-size.<i>full-class-name</i></td><td>-</td><td>Restricts all resource methods in the class</td></tr>
 * <tr><td>ness.filter.max-body-size.<i>full-class-name</i>.<i>method-name</i></td><td>Restricts a particular method</td></tr>
 * </table>
 */
@Singleton
public class BodySizeLimitResourceFilterFactory implements ResourceFilterFactory {

    private static final Log LOG = Log.findLog();

    private final Config config;
    private final long defaultSizeLimit;

    @Inject
    BodySizeLimitResourceFilterFactory(Config config) {
        this.config = config;
        NessJerseyFiltersConfig filterConfig = config.getBean(NessJerseyFiltersConfig.class);

        defaultSizeLimit = filterConfig.getMaxBodySize();

    }

    private final BodySizeLimit defaultLimit = new BodySizeLimit() {
        @Override
        public long value() {
            return defaultSizeLimit;
        }
        @Override
        public Class<? extends Annotation> annotationType() {
            return BodySizeLimit.class;
        }
    };

    @Override
    public List<ResourceFilter> create(AbstractMethod am) {
        BodySizeLimit annotation = ObjectUtils.firstNonNull(
                am.getAnnotation(BodySizeLimit.class),
                am.getResource().getAnnotation(BodySizeLimit.class),
                defaultLimit);

        final String classConfigKey = "ness.filter.max-body-size." + am.getResource().getResourceClass().getName();
        final Long classConfigOverride = config.getConfiguration().getLong(classConfigKey, null);

        final String methodConfigKey = classConfigKey + "." + am.getMethod().getName();
        final Long methodConfigOverride = config.getConfiguration().getLong(methodConfigKey, null);

        final long value = ObjectUtils.firstNonNull(methodConfigOverride, classConfigOverride, annotation.value());

        if (value < 0) {
            LOG.warn("Ignoring bad body limit %d", value);
            return null;
        }

        if (value != annotation.value()) {
            LOG.debug("Use configured value %d on %s", value, am);
        }
        else if (annotation != defaultLimit) {
            LOG.debug("Found annotation for size %d on %s", value, am);
        } else {
            LOG.trace("No annotation found, default size %d used on %s", defaultLimit.value(), am);
        }

        return Collections.<ResourceFilter>singletonList(new Filter(value));
    }

    private static class Filter implements ResourceFilter, ContainerRequestFilter {
        private final long maxSize;

        public Filter(long maxSize) {
            this.maxSize = maxSize;
        }

        @Override
        public ContainerRequestFilter getRequestFilter() {
            return this;
        }

        @Override
        public ContainerResponseFilter getResponseFilter() {
            return null;
        }

        @Override
        public ContainerRequest filter(ContainerRequest request) {
            InputStream entityInputStream = request.getEntityInputStream();
            if (entityInputStream != null) {
                request.setEntityInputStream(new MaxSizeInputStream(entityInputStream, maxSize));
            }
            return request;
        }
    }
}
