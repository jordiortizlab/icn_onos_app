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

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

@Path("proxy")
public class ProxyNorthbound extends AbstractWebResource {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("pname")String name) {
		IIcnService service = getService(IIcnService.class);
		Proxy proxy = service.retrieveProxy(name);
		if (proxy == null) {
			// 404 Not Found if there's no proxy with this name
			log.error("Unable to locate proxy {}", name);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate proxy " + name).build();
		}
		ObjectNode result = new ObjectMapper().createObjectNode();
		result.set("proxy", new ProxyCodec().encode(proxy, this));
		return ok(result.toString()).build(); // 200 OK otherwise
	}

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response update(@QueryParam("name")String name, InputStream stream) {
		IIcnService icnService = getService(IIcnService.class);
		Proxy proxy = icnService.retrieveProxy(name);
		if (proxy == null) {
			log.info("Unable to locate proxy {}. New proxy definition", name);
		}
        try {
            ObjectNode proxyobject = (ObjectNode) mapper().readTree(stream);
            Proxy updatedProxy = new ProxyCodec().decode(proxyobject, this);
            if (!updatedProxy.getName().equals(name)){
                log.error("JSonized proxy name differs from name argument when updating");
                return Response.status(Response.Status.NOT_FOUND).entity("JSonized proxy name differs from name argument when updating").build();
            }
            if (proxy != null) {
                // update proxy
                icnService.updateProxy(updatedProxy);
            }
            else {
                // We need to create a new proxy
                icnService.createProxy(updatedProxy);
            }

        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to dejsonize proxy {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to dejsonize proxy").build();
        }
        return Response.status(Response.Status.OK).build();
	}

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response remove(@QueryParam("name")String name) {
        IIcnService icnService = getService(IIcnService.class);
        Proxy proxy = icnService.retrieveProxy(name);
        if (proxy == null) {
            // 404 Not Found if there's no proxy with this name
            log.error("Unable to locate proxy {}", name);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate proxy " + name).build();
        }
        Proxy removeProxy = icnService.removeProxy(name);
        if (removeProxy == null)
        {
            log.error("Unable to remove proxy {}", name);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to remove proxy " + name).build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.set("proxy", new ProxyCodec().encode(removeProxy, this));
        return ok(result.toString()).build(); // 200 OK otherwise
    }
	
}
