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
import java.io.InputStream;
import java.util.Collection;

@Path("providers")
public class ProvidersNorthbound extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @GET
    @Path("Retrieve")
    @Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("name")String cdnName) {
        ICdnService cdnService = getService(ICdnService.class);
        Cdn cdn = cdnService.retrieveCdn(cdnName);
        if (cdn == null) {
            // 404 Not Found if there's no cdn with this name
            log.error("Unable to locate cdn {}", cdnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
        }
        Collection<Provider> providers = cdn.retrieveProviders();
        if (providers == null) {
            log.error("No providers in cdn {}", cdnName);
            return Response.status(Response.Status.NOT_FOUND).entity("No providers in cdn " + cdnName).build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        ArrayNode providersarray = result.putArray("providers");
        ProviderCodec cc = new ProviderCodec();
        for (Provider provider : providers) {
            ObjectNode encodedprovider = cc.encode(provider, this);
            providersarray.add(encodedprovider);
        }
        return ok(result.toString()).build(); // 200 OK otherwise
	}

    @PUT
    @Path("create")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@QueryParam("name")String cdnName, InputStream stream) {
        ICdnService cdnService = getService(ICdnService.class);
        Cdn cdn = cdnService.retrieveCdn(cdnName);
        if (cdn == null) {
            // 404 Not Found if there's no cdn with this name
            log.error("Unable to locate cdn {}", cdnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
        }
        try {
            ObjectNode locationobject = (ObjectNode) mapper().readTree(stream);
            Provider newProvider = new ProviderCodec().decode(locationobject, this);

            //Finally create the provider
            cdnService.createProvider(cdn, newProvider);
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.set("provider", new ProviderCodec().encode(newProvider, this));
            return ok(result.toString()).build(); // 200 OK otherwise

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized provider in param updatedprovider when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized provider in param updatedprovider when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized provider in param updatedprovider when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized provider in param updatedprovider when calling update method").build();
        } catch (UnsupportedOperationException | ClassCastException | NullPointerException | IllegalArgumentException e)
        {
            e.printStackTrace();
            //The provider already exists, abort
            log.error("Provider probably already exists, provider storage problem {}", e.toString());
            return Response.status(Response.Status.CONFLICT).entity("Provider probably already exists, provider storage problem").build();
        }

    }
	
}
