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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

public class ResourceNorthbound extends AbstractWebResource {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@GET
	@Path("Retrieve")
	@Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("name")String cdnName, @QueryParam("id")String id) {
		ICdnService service = getService(ICdnService.class);
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			log.error("Unable to locate cdn {}", cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
		}

		Resource resource = service.retrieveResource(cdn, id);
		if (resource == null) {
			// 404 Not Found if there's no resource with this id
			log.error("Unable to locate resource with id {} in cdn {}", id, cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate resource").build();
		}
		ObjectNode result = new ObjectMapper().createObjectNode();
		result.set("resource", new ResourceCodec().encode(resource, this));
		return ok(result.toString()).build(); // 200 OK otherwise

	}
	
}
