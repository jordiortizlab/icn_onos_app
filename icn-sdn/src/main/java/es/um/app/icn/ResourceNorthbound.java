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
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

public class ResourceNorthbound extends ServerResource {

	@Get("json")
	public Resource retrieve() {
		ICdnService service = (ICdnService) getContext().getAttributes().
				get(ICdnService.class.getCanonicalName());
		String cdnName = (String) getRequestAttributes().get("name");
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		String id = (String) getRequestAttributes().get("id");
		Resource resource = service.retrieveResource(cdn, id);
		if (resource == null) {
			// 404 Not Found if there's no resource with this id
			setStatus(Status.CLIENT_ERROR_NOT_FOUND);
			return null;
		}
		return resource; // 200 OK otherwise
	}
	
}
