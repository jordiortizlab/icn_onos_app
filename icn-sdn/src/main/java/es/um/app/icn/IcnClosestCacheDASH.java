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


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
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
    public ResourceHTTP createResource(ResourceHTTP resourceHTTP, Proxy proxy) {
        ResourceHTTPDASH resourceDASH = null;
        if (resourceHTTP.getFilename().endsWith(".mpd") || resourceHTTP.getFilename().endsWith("*.MPD")) {
            // It is an MPD. Let's download it and populate caches
            log.info("Downloading MPD file {}", resourceHTTP.getFullurl());
            String mpd = "";
            try {
                URL url = new URL(resourceHTTP.getFullurl());
                URLConnection connection = url.openConnection();
                connection.connect();

                String inputLine;
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                while ((inputLine = in.readLine()) != null) {
                    log.debug("Appending {}", inputLine);
                    mpd = mpd.concat(inputLine);
                }
                in.close();
                resourceDASH = new ResourceHTTPDASH(resourceHTTP);
                // Here we can make actions for the mpd in xml form
                pool.execute(new MPDParser(mpd, resourceDASH));
            } catch (MalformedURLException e) {
                log.error("Malformed URL: {}", resourceHTTP.getFullurl());
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Impossible to connect {}", resourceHTTP.getFullurl());
                e.printStackTrace();
            }
        }
        resources.values().stream().filter(x -> {
            if (!x.getType().equals(ResourceHTTPDASH.DESCRIPTION))
                return false;
            return true;
        }).forEach(x -> {
            ResourceHTTPDASH r = (ResourceHTTPDASH) x;
            RepresentationDASH representationDASH = r.representation4URL(x);
            if (representationDASH != null) {
                // prefetch Representation
                pool.execute(new RepresentationPrefecther(representationDASH, proxy));
            }
        });

        return super.createResource(resourceDASH == null ? resourceHTTP : resourceDASH);
    }

    class MPDParser implements Runnable {
        private String xml;
        private ResourceHTTPDASH resource;

        public MPDParser(String xml, ResourceHTTPDASH resource) {
            this.xml = xml;
        }

        private RepresentationDASH parseRepresentation(Node item) {
            int id = 0;
            int width = 0;
            int height = 0;
            int frameRate = 0;
            long bandwidth = 0L;
            String codec = "";
            String mimetype = "";

            NamedNodeMap attributes = item.getAttributes();
            id = Integer.parseInt(attributes.getNamedItem("id").getNodeValue());
            width = Integer.parseInt(attributes.getNamedItem("width").getNodeValue());
            height = Integer.parseInt(attributes.getNamedItem("height").getNodeValue());
            frameRate = Integer.parseInt(attributes.getNamedItem("frameRate").getNodeValue());
            bandwidth = Long.parseLong(attributes.getNamedItem("bandwidth").getNodeValue());
            codec = attributes.getNamedItem("codec").getNodeValue();

            RepresentationDASH r = new RepresentationDASH(id, width, height, frameRate, bandwidth, codec, mimetype);
            NodeList childNodes = item.getChildNodes();
            for (int idx = 0; idx < childNodes.getLength(); idx++) {
                Node child = childNodes.item(idx);
                if (child.getNodeName().equalsIgnoreCase("SegmentURL")) {
                    Node media = child.getAttributes().getNamedItem("media");
                    String url = media.getNodeValue();
                    r.putResource(url);
                }
            }


            return r;
        }

        @Override
        public void run() {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = null;
            log.info("Parsing: {}", xml);
            try {
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new StringReader(xml)));
                NodeList baseURL = doc.getElementsByTagName("BaseURL");
                if (baseURL.getLength() == 0)
                    log.error("Unable to recover BaseURL from MPD");

                NodeList representation = doc.getElementsByTagName("Representation");
                for (int i = 0; i < representation.getLength(); i++) {
                    Node item = representation.item(i);
                    log.info("Representation: {}", item);

                    RepresentationDASH representationDASH = parseRepresentation(item);
                    resource.putRepresentation(representationDASH.id, representationDASH);
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

    class RepresentationPrefecther implements Runnable {

        RepresentationDASH rep;
        Proxy proxy;

        public RepresentationPrefecther(RepresentationDASH rep, Proxy p) {
            this.rep = rep;
            this.proxy = p;
        }

        public void postHTTP(String uri) {
            try {
                URL url = new URL("http://" + proxy.getIpaddr()+"/prefetch");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                connection.setDoOutput(true);
                connection.setRequestMethod("POST");

                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode paramurl = objectMapper.createObjectNode();
                paramurl.put("url", uri);

                try( DataOutputStream wr = new DataOutputStream( connection.getOutputStream())) {
                    wr.write( paramurl.toString().getBytes() );
                }


            } catch (MalformedURLException e) {
                log.error("Malformed URL: {}", uri);
                e.printStackTrace();
            } catch (IOException e) {
                log.error("Impossible to connect {}", uri);
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            rep.getFullUrls().parallelStream().forEach(url -> {
                postHTTP(url);
            });
        }
    }
}
