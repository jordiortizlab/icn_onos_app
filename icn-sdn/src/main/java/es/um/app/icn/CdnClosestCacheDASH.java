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


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;


public class CdnClosestCacheDASH extends CdnClosestCache {
    private final Logger log = LoggerFactory.getLogger(getClass());

    static public final String DESCRIPTION = "CLOSESTDASH";

    @Override
    public Resource createResource(Resource resource, Proxy proxy) {
        if (resource.getFullurl().endsWith(".mpd") || resource.getFullurl().endsWith("*.MPD")) {
            // It is an MPD. Let's download it and populate caches
            log.info("Downloading MPD file {}", resource.getFullurl());

            try {
                URL url = new URL(resource.getFullurl());
                URLConnection connection = url.openConnection();
                log.info(connection.getContent().toString());
            } catch (MalformedURLException e) {
                log.error("Malformed URL: {}", resource.getFullurl());
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Impossible to connect {}", resource.getFullurl());
                e.printStackTrace();
            }

        }
        return super.createResource(resource);
    }
}
