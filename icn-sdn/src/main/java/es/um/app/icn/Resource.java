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

import java.util.ArrayList;
import java.util.Collection;

public class Resource {

	private String id;
	private String hostname;
	private String name;
    private String fullurl;
	private long requests;
	private Collection<Cache> caches;
	
	public Resource() {
		caches = new ArrayList<Cache>();
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public long getRequests() {
		return requests;
	}

	public void setRequests(long requests) {
		this.requests = requests;
	}

	public Collection<Cache> getCaches() {
		return caches;
	}

	public void setCaches(Collection<Cache> caches) {
		this.caches = caches;
	}

    public String getFullurl() {
        return fullurl;
    }

    public void setFullurl(String fullurl) {
        this.fullurl = fullurl;
    }


    public Cache addCache(Cache cache) {
		if (caches.add(cache)) {
			return cache;
		}
		return null;
	}
	
	public Cache removeCache(Cache cache) {
		if (caches.remove(cache)) {
			return cache;
		}
		return null;
	}

    @Override
    public String toString() {
        return "Resource{" +
                "id='" + id + '\'' +
                ", hostname='" + hostname + '\'' +
                ", name='" + name + '\'' +
                ", fullurl='" + fullurl + '\'' +
                ", requests=" + requests +
                ", caches=" + caches +
                '}';
    }
}
