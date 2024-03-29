/*
 * Copyright 2016-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package es.um;

import es.um.app.icn.*;
import org.onlab.rest.AbstractWebApplication;

import java.util.Set;

/**
 * ICN over SDN REST API web application.
 */
public class AppWebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(
                CacheNorthbound.class,
                CachesNorthbound.class,
                IcnNorthbound.class,
                IcnServiceNorthbound.class,
                ProviderNorthbound.class,
                ProvidersNorthbound.class,
                ProxiesNorthbound.class,
                ProxyNorthbound.class,
                ProxyRequestNorthbound.class,
                ResourceNorthbound.class,
                ResourcesNorthbound.class
                );
    }
}
