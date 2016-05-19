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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.Collection;

@Path("cdns")
public class CdnsNorthbound extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
	public Response retrieve() {
        ICdnService cdnService = getService(ICdnService.class);
        Collection<Cdn> cdns = cdnService.retrieveCdns();
        if (cdns == null)
        {
            log.error("No Cdn available");
            return Response.status(Response.Status.NOT_FOUND).entity("No Cdn available").build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        ArrayNode cdnsarray = result.putArray("cdns");
        CdnCodec cc = new CdnCodec();
        for (Cdn cdn : cdns) {
            ObjectNode encodedcdn = cc.encode(cdn, this);
            cdnsarray.add(encodedcdn);
        }
        return ok(result.toString()).build(); // 200 OK otherwise
    }

    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response create(@QueryParam("newcdn") String jsonnewcdn) {
        ICdnService cdnService = getService(ICdnService.class);
        try {
            ObjectNode cdnobject = (ObjectNode) new ObjectMapper().readTree(jsonnewcdn);
            Cdn cdn = new CdnCodec().decode(cdnobject, this);
            cdnService.createCdn(cdn);
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.set("cdn", new CdnCodec().encode(cdn, this));
            return ok(result.toString()).build(); // 200 OK otherwise
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cdn in param updatedcdn when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cdn in param updatedcdn when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cdn in param updatedcdn when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cdn in param updatedcdn when calling update method").build();
        } catch (UnsupportedOperationException | ClassCastException | NullPointerException | IllegalArgumentException e)
        {
            e.printStackTrace();
            //The cdn already exists, abort
            log.error("cdn probably already exists, cdn storage problem {}", e.toString());
            return Response.status(Response.Status.CONFLICT).entity("cdn probably already exists, cdn storage problem").build();
        }
    }
	
}
