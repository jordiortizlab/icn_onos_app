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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class IcnClosestCacheDASH extends IcnClosestCache {
    private final Logger log = LoggerFactory.getLogger(getClass());

    static public final String DESCRIPTION = "CLOSESTDASH";
    private final ExecutorService pool;

    public IcnClosestCacheDASH() {
        super();
        pool = Executors.newFixedThreadPool(1);
    }

    @Override
    public Resource createResource(Resource resource, Proxy proxy) {
        if (resource.getFullurl().endsWith(".mpd") || resource.getFullurl().endsWith("*.MPD")) {
            // It is an MPD. Let's download it and populate caches
            log.info("Downloading MPD file {}", resource.getFullurl());
            String mpd = "";
            try {
                URL url = new URL(resource.getFullurl());
                URLConnection connection = url.openConnection();
                connection.connect();

                String inputLine;
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((inputLine = in.readLine()) != null) {
                    log.debug("Appending {}", inputLine);
                    mpd = mpd.concat(inputLine);
                }
                in.close();
                // Here we can make actions for the mpd in xml form
                pool.execute(new MPDParser(mpd));
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

    class MPDParser implements Runnable {
        private String xml;

        public MPDParser(String xml) {
            this.xml = xml;
        }

        @Override
        public void run() {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = null;
            log.info("Parsing: {}", xml);
            try {
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new StringReader(xml)));
                NodeList representation = doc.getElementsByTagName("Representation");
                for (int i = 0; i < representation.getLength(); i++) {
                    Node item = representation.item(i);
                    log.info("Representation: {}", item);
                }
            } catch (ParserConfigurationException e) {
                log.error("MPD:: Impossible to configure XML Parser {}", e);
                e.printStackTrace();
            } catch (SAXException e) {
                log.error("MPD:: Parse error {}", e);
            } catch (IOException e) {
                log.error("MPD:: Problem with MPD in XML form {}", e);
            }
        }
    }
}
