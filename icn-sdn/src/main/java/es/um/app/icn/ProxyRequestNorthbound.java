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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

@Path("proxyrequest")
public class ProxyRequestNorthbound extends AbstractWebResource {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@POST
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	public Response create(InputStream stream) {
		IIcnPrivateService cdnService = getService(IIcnPrivateService.class);
		try {
			ObjectNode locationobject = (ObjectNode) mapper().readTree(stream);
			ProxyRequest newproxyreq = new ProxyRequestCodec().decode(locationobject, this);
            if (!cdnService.processResourceRequest(newproxyreq)) {
                return Response.status(Response.Status.NOT_ACCEPTABLE).entity("Unable to process request").build();
			}
		} catch (JsonProcessingException e) {
			e.printStackTrace();
            log.error("Unable to dejsonize the ProxyRequest");
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to dejsonize the ProxyRequest").build();
		} catch (IOException e) {
			e.printStackTrace();
            log.error("Unable to dejsonize the ProxyRequest");
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to dejsonize the ProxyRequest").build();
		}
        return Response.status(Response.Status.OK).build();
    }
	
}
