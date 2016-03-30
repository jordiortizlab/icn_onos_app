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

import org.restlet.data.Status;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ProxiesNorthbound extends ServerResource {

	@Get("json")
	public Collection<Proxy> retrieve() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		return service.retrieveProxies(); // 200 OK
	}
	
	@Post("json")
	public Proxy create(String body) {
		Gson gson = new Gson();
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		try {
			Proxy proxy = gson.fromJson(body, Proxy.class);
			// 409 Conflict if duplicated name
			if (service.retrieveProxy(proxy.name) != null) {
				setStatus(Status.CLIENT_ERROR_CONFLICT);
				return proxy;
			}
			// 201 Created if everything ok
			setStatus(Status.SUCCESS_CREATED);
			return service.createProxy(proxy);
		} catch (JsonSyntaxException e) {
			// 400 Bad Request if cannot parse Json body
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		}
	}
	
}
