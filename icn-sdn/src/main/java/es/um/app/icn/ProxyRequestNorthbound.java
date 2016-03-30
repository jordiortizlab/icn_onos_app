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
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class ProxyRequestNorthbound extends ServerResource {

	@Post("json")
	public ProxyRequest create(String body) {
		Gson gson = new Gson();
		ICdnPrivateService service = (ICdnPrivateService) getContext().getAttributes().
				get(ICdnPrivateService.class.getCanonicalName());
		try {
			ProxyRequest req = gson.fromJson(body, ProxyRequest.class);
			service.processResourceRequest(req);
			// 204 No Content if success
			setStatus(Status.SUCCESS_NO_CONTENT);
		} catch (JsonSyntaxException e) {
			// 400 Bad Request if cannot parse Json body
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
		}
		// Always return empty body
		return null;
	}
	
}
