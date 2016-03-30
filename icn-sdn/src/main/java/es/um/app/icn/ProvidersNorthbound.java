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

public class ProvidersNorthbound extends ServerResource {

	@Get("json")
	public Collection<Provider> retrieve() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String cdnName = (String) getRequestAttributes().get("name");
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		return service.retrieveProviders(cdn); // 200 OK
	}
	
	@Post("json")
	public Provider create(String body) {
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
		try {
			Provider provider = gson.fromJson(body, Provider.class);
			// 409 Conflict if duplicated name
			if (service.retrieveProvider(cdn, provider.name) != null) {
				setStatus(Status.CLIENT_ERROR_CONFLICT);
				return provider;
			}
			// 201 Created if everything ok
			setStatus(Status.SUCCESS_CREATED);
			return service.createProvider(cdn, provider);
		} catch (JsonSyntaxException e) {
			// 400 Bad Request if cannot parse Json body
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return null;
		}
	}
	
}
