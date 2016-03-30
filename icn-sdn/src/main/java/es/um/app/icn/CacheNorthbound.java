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

import org.restlet.data.Status;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Put;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class CacheNorthbound extends ServerResource {

	@Get("json")
	public Cache retrieve() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String cdnName = (String) getRequestAttributes().get("name");
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		String cacheName = (String) getRequestAttributes().get("cname");
		Cache cache = service.retrieveCache(cdn, cacheName);
		if (cache == null) {
			// 404 Not Found if there's no cache with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		return cache; // 200 OK otherwise
	}
	
	@Put("json")
	public Cache update(String body) {
		Gson gson = new Gson();
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String cdnName = (String) getRequestAttributes().get("name");
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		String cacheName = (String) getRequestAttributes().get("cname");
		Cache cache = service.retrieveCache(cdn, cacheName);
		if (cache == null) {
			// 404 Not Found if there's no cache with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		try {
			Cache updatedCache = gson.fromJson(body, Cache.class);
			if (!cacheName.equals(updatedCache.name)) {
				// 400 Bad Request if names in uri and body don't match
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				return cache;
			}
			return service.updateCache(cdn, updatedCache); // 20O OK otherwise
		} catch (JsonSyntaxException e) {
			// 400 Bad Request if cannot parse Json body
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		} 
	}
	
	@Delete("json")
	public Cache remove() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String cdnName = (String) getRequestAttributes().get("name");
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		String cacheName = (String) getRequestAttributes().get("cname");
		Cache cache = service.removeCache(cdn, cacheName);
		if (cache == null) {
			// 404 Not Found if there's no cache with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		return cache; // 200 OK otherwise
	}
	
}
