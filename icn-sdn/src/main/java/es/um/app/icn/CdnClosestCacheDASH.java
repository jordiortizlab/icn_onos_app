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
 *      Jordi Ortiz
 *      <jordi.ortiz@um.es>
 **/

package es.um.app.icn;

import com.mashape.unirest.http.Headers;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.async.Callback;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.concurrent.Future;

public class CdnClosestCacheDASH extends CdnClosestCache {
    private final Logger log = LoggerFactory.getLogger(getClass());

    static public final String DESCRIPTION = "CLOSESTDASH";

    @Override
    public Resource createResource(Resource resource, Proxy proxy) {
        if (resource.getName().endsWith(".mpd") || resource.getName().endsWith("*.MPD")) {
            // It is an MPD. Let's download it and populate caches
            log.info("Downloading MPD file {}", resource.getName());
            Future<HttpResponse<JsonNode>> future = Unirest.get(resource.getName())
                    .header("accept", "application/xml")
                    .asJsonAsync(new Callback<JsonNode>() {

                        public void failed(UnirestException e) {
                            log.error("The request has failed. Unable to get {}", resource.getName());
                        }

                        public void completed(HttpResponse<JsonNode> response) {
                            int code = response.getStatus();
                            Headers headers = response.getHeaders();
                            JsonNode body = response.getBody();
                            InputStream rawBody = response.getRawBody();
                            log.info("Downloaded: ", response.getBody());

                        }

                        public void cancelled() {
                            log.error("The request has been cancelled {}", resource.getName());
                        }

                    });
        }
        return super.createResource(resource);
    }
}
