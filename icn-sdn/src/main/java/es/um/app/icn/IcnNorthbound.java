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

@Path("cdn")
public class IcnNorthbound extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("name")String icnName) {
        IIcnService icnService = getService(IIcnService.class);
        Icn icn = icnService.retrieveIcn(icnName);
        if (icn == null) {
            // 404 Not Found if there's no icn with this name
            log.error("Unable to locate icn {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate icn " + icnName).build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.set("icn", new IcnCodec().encode(icn, this));
        return ok(result.toString()).build(); // 200 OK otherwise
	}

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response update(@QueryParam("name")String icnName, InputStream stream) {
        IIcnService icnService = getService(IIcnService.class);
        Icn icn = icnService.retrieveIcn(icnName);
        if (icn == null) {
            log.info("Unable to locate icn {}. New icn definition", icnName);
        }
        try {
            ObjectNode icnobject = (ObjectNode) mapper().readTree(stream);
            Icn updatedIcn = new IcnCodec().decode(icnobject, this);
            if (!icnName.equals(updatedIcn.getName())) {
                log.error("jsonized icn name and icnName argument differ");
                return Response.status(Response.Status.NOT_FOUND).entity("jsonized icn name and icnName argument differ").build();
            }
            if (icn != null) {
                //Finally update the icn
                icnService.updateIcn(updatedIcn);
            }
            else
            {
                icnService.createIcn(updatedIcn);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized icn in param updatedicn when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized icn in param updatedicn when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized icn in param updatedicn when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized icn in param updatedicn when calling update method").build();
        }
        return Response.status(Response.Status.OK).build();
	}

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response remove(@QueryParam("name")String icnName) {
        IIcnService icnService = getService(IIcnService.class);
        Icn icn = icnService.retrieveIcn(icnName);
        if (icn == null) {
            // 404 Not Found if there's no icn with this name
            log.error("Unable to locate icn {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate icn " + icnName).build();
        }
        if (!icnName.equals(icn.getName())) {
            log.error("Icn not found, unable to remove {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Icn not found, unable to remove").build();
        }
        Icn deletedicn = icnService.removeIcn(icnName);
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.set("Icn", new IcnCodec().encode(deletedicn, this));
        return ok(result.toString()).build(); // 200 OK otherwise
	}
	
}
