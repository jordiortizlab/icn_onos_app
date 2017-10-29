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

@Path("proxies")
public class ProxiesNorthbound extends AbstractWebResource {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response retrieve(@QueryParam("name")String icnName) {
        IIcnService icnService = getService(IIcnService.class);
        Collection<Proxy> proxys = icnService.retrieveProxies();
        if (proxys == null) {
            log.error("No proxys in icn {}", icnName);
            return Response.status(Response.Status.NOT_FOUND).entity("No proxys in icn " + icnName).build();
        }
        ObjectNode result = new ObjectMapper().createObjectNode();
        ArrayNode proxysarray = result.putArray("proxys");
        ProxyCodec cc = new ProxyCodec();
        for (Proxy proxy : proxys) {
            ObjectNode encodedproxy = cc.encode(proxy, this);
            proxysarray.add(encodedproxy);
        }
        return ok(result.toString()).build(); // 200 OK otherwise
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(InputStream stream) {
        IIcnService icnService = getService(IIcnService.class);
        try {
            ObjectNode locationobject = (ObjectNode) mapper().readTree(stream);
            Proxy newProxy = new ProxyCodec().decode(locationobject, this);

            //Finally create the proxy
            icnService.createProxy(newProxy);
            ObjectNode result = new ObjectMapper().createObjectNode();
            result.set("proxy", new ProxyCodec().encode(newProxy, this));
            return ok(result.toString()).build(); // 200 OK otherwise

        } catch (JsonProcessingException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized proxy in param updatedproxy when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized proxy in param updatedproxy when calling update method").build();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("Unable to parse jsonized proxy in param updatedproxy when calling update method {}", e.toString());
            return Response.status(Response.Status.NOT_FOUND).entity("Unable to parse jsonized proxy in param updatedproxy when calling update method").build();
        } catch (UnsupportedOperationException | ClassCastException | NullPointerException | IllegalArgumentException e)
        {
            e.printStackTrace();
            //The proxy already exists, abort
            log.error("Proxy probably already exists, proxy storage problem {}", e.toString());
            return Response.status(Response.Status.CONFLICT).entity("Proxy probably already exists, proxy storage problem").build();
        }

    }

}
