/**
 *    Copyright 2014, University of Murcia (Spain)
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 *    
 *    Author:
 *      Francisco J. Ros
 *      <fjros@um.es>
 **/

package es.um.app.icn;

import java.util.Collection;
import java.util.HashMap;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

public class IcnClosestCache implements Icn {

    protected String name;
    protected String description;
    protected HashMap<String, Provider> providers;
    protected HashMap<String, Cache> caches;
    protected HashMap<String, Resource> resources;

    static public final String DESCRIPTION = "CLOSEST";

    public IcnClosestCache() {
        providers = new HashMap<String, Provider>();
        caches = new HashMap<String, Cache>();
        resources = new HashMap<String, Resource>();
    }

    /**
     * Find the appropriate cache for a resource. Currently we just return
     * the closest cache for the requester.
     * @param service
     * @param resourceName
     * @param sw Requester's switch.
     * @param inPort Requester's input port.
     * @return An appropriate cache to host the resource.
     */
    public Cache findCacheForNewResource(IcnService service,
                                            String resourceName, DeviceId sw, PortNumber inPort) {
        return (Cache) service.findClosestMiddlebox(caches.values(), sw, inPort);
    }

    /**
     * Find the appropriate cache hosting a resource. Currently we just return
     * the closest cache for the requester that hosts the resource.
     * @param service
     * @param resourceName
     * @param sw Requester's switch.
     * @param inPort Requester's input port.
     * @return A cache hosting the resource.
     */
    public Cache findCacheForExistingResource(IcnService service,
            String resourceName, DeviceId sw, PortNumber inPort) {
        Resource resource = resources.get(resourceName);
        if (resource == null)
            return null;
        return (Cache) service.findClosestMiddlebox(resource.getCaches(), sw, inPort);
    }

    @Override
    public String getType() {
        return DESCRIPTION;
    }

    public Collection<Provider> retrieveProviders() {
        return providers.values();
    }

    public Provider retrieveProvider(String name) {
        return providers.get(name);
    }

    public Provider createProvider(Provider provider) {
        providers.put(provider.name, provider);
        return provider;
    }

    public Provider updateProvider(Provider provider) {
        providers.put(provider.name, provider);
        return provider;
    }

    public Provider removeProvider(String name) {
        return providers.remove(name);
    }

    public Collection<Cache> retrieveCaches() {
        return caches.values();
    }

    public Cache retrieveCache(String name) {
        return caches.get(name);
    }

    public Cache createCache(Cache cache) {
        caches.put(cache.name, cache);
        return cache;
    }

    public Cache updateCache(Cache cache) {
        caches.put(cache.name, cache);
        return cache;
    }

    public Cache removeCache(String name) {
        return caches.remove(name);
    }

    public Collection<Resource> retrieveResources() {
        return resources.values();
    }

    public Resource retrieveResource(String id) {
        return resources.get(id);
    }

    public Resource createResource(Resource resource) {
        resources.put(resource.getName(), resource);
        return resource;
    }

    public Resource createResource(Resource resource, Proxy proxy) {
        resources.put(resource.getName(), resource);
        return resource;
    }

    public Resource updateResource(Resource resource) {
        resources.put(resource.getName(), resource);
        return resource;
    }

    public Resource removeResource(String name) {
        return resources.remove(name);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}
