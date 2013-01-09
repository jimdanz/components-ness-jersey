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
package com.nesscomputing.exception;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

/**
 * Register NessApiException subclasses so they may be correctly mapped to and from HTTP responses.
 */
public class NessApiExceptionBinder
{
    private final Binder binder;

    public NessApiExceptionBinder(Binder binder) {
        this.binder = binder;
    }

    public static void registerExceptionClass(Binder binder, Class<? extends NessApiException> klass)
    {
        new NessApiExceptionBinder(binder).registerExceptionClass(klass);
    }

    public void registerExceptionClass(Class<? extends NessApiException> klass)
    {
        final ExceptionReviver predicate = new ExceptionReviver(klass);
        MapBinder.newMapBinder(binder, String.class, ExceptionReviver.class).permitDuplicates()
            .addBinding(predicate.getMatchedType()).toInstance(predicate);
    }
}