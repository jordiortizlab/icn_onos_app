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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

@Path("provider")
public class ProviderNorthbound extends AbstractWebResource {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("name")String cdnName, @QueryParam("pname")String provName) {
		ICdnService service = getService(ICdnService.class);
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			log.error("Unable to locate cdn {}", cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
		}
		Provider provider = service.retrieveProvider(cdn, provName);
		if (provider == null) {
			// 404 Not Found if there's no provider with this name
			log.error("Unable to locate provider {}", provName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate provider " + provName).build();
		}
		ObjectNode result = new ObjectMapper().createObjectNode();
		result.set("provider", new ProviderCodec().encode(provider, this));
		return ok(result.toString()).build(); // 200 OK otherwise
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response update(@QueryParam("name")String cdnName, @QueryParam("pname")String provName, InputStream stream) {
		ICdnService service = getService(ICdnService.class);
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			log.error("Unable to locate cdn {}", cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
		}
		Provider provider = service.retrieveProvider(cdn, provName);
		if (provider == null) {
			log.info("Unable to locate provider {}. New provider definition", provName);
		}

		try {
			ObjectNode providerobject = (ObjectNode) mapper().readTree(stream);
			Provider updatedprovider = new ProviderCodec().decode(providerobject, this);
			if (provider != null && !provider.getName().equals(updatedprovider.getName()))
			{
				log.error("JSonized provider name does not match pname");
				return Response.status(Response.Status.NOT_FOUND).entity("JSonized provider name does not match pname").build();
			}
			if (provider != null) {
				//Finally update the provider
				service.updateProvider(cdn, updatedprovider);
			}
			else
			{
				//Finally update the provider
				service.createProvider(cdn, updatedprovider);
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
			log.error("Unable to parse jsonized provider in param updatedprovider when calling update method {}", e.toString() );
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized provider in param updatedprovider when calling update method").build();
		} catch (IOException e) {
			e.printStackTrace();
			log.error("Unable to parse jsonized provider in param updatedprovider when calling update method {}", e.toString());
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized provider in param updatedprovider when calling update method").build();
		}
		return Response.status(Response.Status.OK).build();
	}

	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response remove(@QueryParam("name")String cdnName, @QueryParam("pname")String provName) {
		ICdnService service = getService(ICdnService.class);
		Cdn cdn = service.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			log.error("Unable to locate cdn {}", cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
		}
		Provider provider = service.retrieveProvider(cdn, provName);
		if (provider == null) {
			// 404 Not Found if there's no provider with this name
			log.error("Unable to locate provider {}", provName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate provider " + provName).build();
		}
		Provider removeProvider = service.removeProvider(cdn, provName);
		if (removeProvider == null) {
			// 404 Not Found if there's no provider with this name
			log.error("Unable to remove provider {}", provName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to remove provider " + provName).build();
		}
		ObjectNode result = new ObjectMapper().createObjectNode();
		result.set("provider", new ProviderCodec().encode(removeProvider, this));
		return ok(result.toString()).build(); // 200 OK otherwise
	}
	
}
