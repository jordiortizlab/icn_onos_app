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

public class ProxyNorthbound extends ServerResource {

	@Get("json")
	public Proxy retrieve() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String name = (String) getRequestAttributes().get("name");
		Proxy proxy = service.retrieveProxy(name);
		if (proxy == null) {
			// 404 Not Found if there's no proxy with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
		}
		return proxy; // 200 OK otherwise
	}
	
	@Put("json")
	public Proxy update(String body) {
		Gson gson = new Gson();
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String name = (String) getRequestAttributes().get("name");
		try {
			Proxy proxy = gson.fromJson(body, Proxy.class);
			if (!name.equals(proxy.name)) {
				// 400 Bad Request if names in uri and body don't match
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				return proxy;
			}
			if (service.retrieveProxy(name) == null) {
				// 404 Not Found if there's no proxy with this name
				setStatus(Status.CLIENT_ERROR_NOT_FOUND);
				return proxy;
			}
			return service.updateProxy(proxy); // 20O OK otherwise
		} catch (JsonSyntaxException e) {
			// 400 Bad Request if cannot parse Json body
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		} 
	}
	
	@Delete("json")
	public Proxy remove() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String name = (String) getRequestAttributes().get("name");
		Proxy proxy = service.removeProxy(name);
		if (proxy == null) {
			// 404 Not Found if there's no proxy with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		return proxy; // 200 OK otherwise
	}
	
}
