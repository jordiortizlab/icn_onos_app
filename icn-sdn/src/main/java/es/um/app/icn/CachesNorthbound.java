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

@Path("caches")
public class CachesNorthbound extends AbstractWebResource {
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
        Collection<Cache> caches = icn.retrieveCaches();
        if (caches == null) {
            log.error("No caches in icn {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("No caches in icn " + icnName).build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        ArrayNode cachesarray = result.putArray("caches");
        CacheCodec cc = new CacheCodec();
        for (Cache cache : caches) {
            ObjectNode encodedcache = cc.encode(cache, this);
            cachesarray.add(encodedcache);
        }
        return ok(result.toString()).build(); // 200 OK otherwise
	}

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response create(@QueryParam("name")String icnName, InputStream stream) {
        IIcnService icnService = getService(IIcnService.class);
        Icn icn = icnService.retrieveIcn(icnName);
        if (icn == null) {
            // 404 Not Found if there's no icn with this name
            log.error("Unable to locate icn {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate icn " + icnName).build();
        }
        try {
            ObjectNode locationobject = (ObjectNode) mapper().readTree(stream);
            Cache newCache = new CacheCodec().decode(locationobject, this);

            //Finally create the cache
            icnService.createCache(icn, newCache);
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.set("cache", new CacheCodec().encode(newCache, this));
            return ok(result.toString()).build(); // 200 OK otherwise

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cache in param updatedcache when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cache in param updatedcache when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cache in param updatedcache when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cache in param updatedcache when calling update method").build();
        } catch (UnsupportedOperationException | ClassCastException | NullPointerException | IllegalArgumentException e)
        {
            e.printStackTrace();
            //The cache already exists, abort
            log.error("Cache probably already exists, cache storage problem {}", e.toString());
            return Response.status(Response.Status.CONFLICT).entity("Cache probably already exists, cache storage problem").build();
        }

	}
	
}
