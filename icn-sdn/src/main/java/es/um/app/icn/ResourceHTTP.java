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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;

public class ResourceHTTP implements Resource {
    private final Logger log = LoggerFactory.getLogger(getClass());
    private static String DESCRIPTION = "HTTP";

    private String id;
    private String hostname;
    private String name;
    private URL url;
    private long requests;
    private Collection<Cache> caches;

    public ResourceHTTP() {
        caches = new ArrayList<Cache>();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
    }

    @Override
    public long getRequests() {
        return requests;
    }

    @Override
    public void setRequests(long requests) {
        this.requests = requests;
    }

    @Override
    public Collection<Cache> getCaches() {
        return caches;
    }

    @Override
    public void setCaches(Collection<Cache> caches) {
        this.caches = caches;
    }

    @Override
    public String getFullurl() {
        return url.toString();
    }

    @Override
    public void setFullurl(String fullurl) {
        try {
            url = new URL(fullurl);
        } catch (MalformedURLException e) {
            log.error("Unable to parse the url {}", fullurl);
        }
    }

    @Override
    public String getBasePathUrl() {
        return url.getPath();
    }

    @Override
    public String getFilename() {
        return url.getFile();
    }

    @Override
    public Cache addCache(Cache cache) {
        if (caches.add(cache)) {
            return cache;
        }
        return null;
    }

    @Override
    public Cache removeCache(Cache cache) {
        if (caches.remove(cache)) {
            return cache;
        }
        return null;
    }

    @Override
    public String getType() {
        return DESCRIPTION;
    }

    @Override
    public String toString() {
        return "ResourceHTTP{" +
                "id='" + id + '\'' +
                ", hostname='" + hostname + '\'' +
                ", name='" + name + '\'' +
                ", fullurl='" + url.toString() + '\'' +
                ", requests=" + requests +
                ", caches=" + caches +
                '}';
    }
}
