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

/**
 * Some documentation about javax.ws.rs https://docs.oracle.com/javaee/6/api/javax/ws/rs/package-summary.html
 */

@Path("Cache")
public class CacheNorthbound extends AbstractWebResource {
	private final Logger log = LoggerFactory.getLogger(getClass());

	@GET
	@Path("Retrieve")
    @Produces(MediaType.APPLICATION_JSON)
	public Response retrieve(@QueryParam("name")String cdnName, @QueryParam("cname")String cacheName) {
		ICdnService cdnService = getService(ICdnService.class);
		Cdn cdn = cdnService.retrieveCdn(cdnName);
		if (cdn == null) {
			// 404 Not Found if there's no cdn with this name
			log.error("Unable to locate cdn {}", cdnName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
		}
		Cache cache = cdnService.retrieveCache(cdn, cacheName);
		if (cache == null) {
			// 404 Not Found if there's no cache with this name
			log.error("Unable to locate cache {}", cacheName);
			return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cache " + cacheName).build();
		}
		ObjectNode result = new ObjectMapper().createObjectNode();
		result.set("cache", new CacheCodec().encode(cache, this));
		return ok(result.toString()).build(); // 200 OK otherwise
	}

    @PUT
    @Path("update")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
	public Response update(@QueryParam("name")String cdnName, @QueryParam("cname")String cacheName, @QueryParam("updatedcache") String jsonupdatedcache) {
        ICdnService cdnService = getService(ICdnService.class);
        Cdn cdn = cdnService.retrieveCdn(cdnName);
        if (cdn == null) {
            // 404 Not Found if there's no cdn with this name
            log.error("Unable to locate cdn {}", cdnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
        }
        Cache cache = cdnService.retrieveCache(cdn, cacheName);
        if (cache == null) {
            // 404 Not Found if there's no cache with this name
            log.error("Unable to locate cache {}", cacheName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cache " + cacheName).build();
        }

        try {
            ObjectNode locationobject = (ObjectNode) new ObjectMapper().readTree(jsonupdatedcache);
            Cache updatedCache = new CacheCodec().decode(locationobject, this);
            if (!cacheName.equals(updatedCache.name)) {
                log.error("jsonized cache name and cname argument differ");
                return Response.status(Response.Status.NOT_FOUND).entity("jsonized cache name and cname argument differ").build();
            }
            //Finally update the cache
            cdnService.updateCache(cdn, updatedCache);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cache in param updatedcache when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cache in param updatedcache when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized cache in param updatedcache when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized cache in param updatedcache when calling update method").build();
        }
        return Response.status(Response.Status.OK).build();
	}

    @DELETE
    @Path("remove")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(@QueryParam("name")String cdnName, @QueryParam("cname")String cacheName) {
        ICdnService cdnService = getService(ICdnService.class);
        Cdn cdn = cdnService.retrieveCdn(cdnName);
        if (cdn == null) {
            // 404 Not Found if there's no cdn with this name
            log.error("Unable to locate cdn {}", cdnName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cdn " + cdnName).build();
        }
        Cache cache = cdnService.retrieveCache(cdn, cacheName);
        if (cache == null) {
            // 404 Not Found if there's no cache with this name
            log.error("Unable to locate cache {}", cacheName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to locate cache " + cacheName).build();
        }
		Cache deletedcache = cdnService.removeCache(cdn, cacheName);
		if (deletedcache == null) {
			// 404 Not Found if there's no cache with this name
            log.error("Unable to remove cache {}", cacheName);
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to remove cache " + cacheName).build();
		}
        ObjectNode result = new ObjectMapper().createObjectNode();
        result.set("cache", new CacheCodec().encode(deletedcache, this));
        return ok(result.toString()).build(); // 200 OK otherwise
	}
	
}
