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
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onlab.packet.Ip4Address;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
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
import java.io.BufferedInputStream;
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

    static final String DESCRIPTION = "CLOSESTDASH";
    static private final int PREFETCHER_PORT = 8080;
    static private final Long MIN_PREFETCHING_IP = 2886729729L; //172.16.0.1
    static private final Long MAX_PREFETCHING_IP = 2887778303L; //172.31.255.255
    static private final short MIN_PREFETCHING_PORT = 1;
    static private final short MAX_PREFETCHING_PORT = Short.MAX_VALUE;
    private final ExecutorService pool;

    private long prefetching_ip = 167772160;
    private short prefetching_port = 1;

    private long serviceId = 1L;

    public IcnClosestCacheDASH() {
        super();
        pool = Executors.newFixedThreadPool(1);
    }

    /**
     * Advances first the port until the limit. Then the ip until the limit and rotates.
     * @return
     */
    private void generateNewPrefetchingIpandPort() {
        prefetching_port++;
        if (prefetching_port == MAX_PREFETCHING_PORT) {
            prefetching_port = MIN_PREFETCHING_PORT;
            prefetching_ip++;
            if (prefetching_ip == MAX_PREFETCHING_IP)
                prefetching_ip = MIN_PREFETCHING_IP;
        }
    }

    private Long getPrefetchingIp() {
        return prefetching_ip;
    }

    private short getPrefetchingPort() {
        return prefetching_port;
    }

    private String getPrefetchingIpStr() {
            return ((prefetching_ip >> 24) & 0xFF) + "."
                    + ((prefetching_ip >> 16) & 0xFF) + "."
                    + ((prefetching_ip >> 8) & 0xFF) + "."
                    + (prefetching_ip & 0xFF);

    }

    @Override
    public ResourceHTTP createResource(ResourceHTTP resourceHTTP, Proxy proxy) {
        ResourceHTTPDASH resourceDASH = null;
        if (resourceHTTP.getFilename().endsWith(".mpd") || resourceHTTP.getFilename().endsWith("*.MPD")) {
            // It is an MPD. Let's download it and populate caches
            log.info("Downloading MPD file {}", resourceHTTP.getFullurl());
            try {
                URL url = new URL(resourceHTTP.getFullurl());

                resourceDASH = new ResourceHTTPDASH(resourceHTTP);
                // Here we can make actions for the mpd in url form
                pool.execute(new MPDParser(url, resourceDASH));
            } catch (MalformedURLException e) {
                log.error("Malformed URL: {}", resourceHTTP.getFullurl());
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
                pool.execute(new RepresentationPrefecther(serviceId, representationDASH, proxy, r));
            }
            serviceId += representationDASH.getFullUrls().size() + 1L;
        });

        return super.createResource(resourceDASH == null ? resourceHTTP : resourceDASH);
    }

    class MPDParser implements Runnable {
        private URL url;
        private ResourceHTTPDASH resource;

        public MPDParser(URL url, ResourceHTTPDASH resource) {
            this.url = url;
            this.resource = resource;
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
            codec = attributes.getNamedItem("codecs").getNodeValue();

            RepresentationDASH r = new RepresentationDASH(id, width, height, frameRate, bandwidth, codec, mimetype);
            NodeList childNodes = item.getChildNodes();
            for (int idx = 0; idx < childNodes.getLength(); idx++) {
                Node child = childNodes.item(idx);
                if (child.getNodeName().equals("SegmentList")) {
                    for (int idx2 = 0; idx2 < child.getChildNodes().getLength(); idx2++) {
                        Node child2 = child.getChildNodes().item(idx2);
                        if (child2.getNodeName().equalsIgnoreCase("SegmentURL")) {
                            Node media = child2.getAttributes().getNamedItem("media");
                            String url = media.getNodeValue();
                            r.putResource(url);
                        }
                    }
                }
            }


            return r;
        }

        @Override
        public void run() {
            log.info("Start download and parse: {}", url);
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = null;
            StringBuilder mpd = new StringBuilder("");
            byte []buffer = new byte[5192];
            buffer[5191] = '\0';
            try {
                URLConnection connection = url.openConnection();
                connection.connect();

                BufferedInputStream bis = new BufferedInputStream(connection.getInputStream());
                int numberOfBytes;
                do {
                    numberOfBytes = bis.read(buffer, 0, 5191);
                    if (numberOfBytes > 0)
                        mpd.append(new String(buffer, 0, numberOfBytes));
                } while (numberOfBytes >= 0);
                bis.close();
            } catch (IOException e) {
                log.error("Impossible to connect {}", url);
                return;
            }

            try {
                db = dbf.newDocumentBuilder();
                Document doc = db.parse(new InputSource(new StringReader(mpd.toString())));
                NodeList baseURL = doc.getElementsByTagName("BaseURL");
                if (baseURL.getLength() == 0)
                    log.error("Unable to recover BaseURL from MPD");

                NodeList representation = doc.getElementsByTagName("Representation");
                for (int i = 0; i < representation.getLength(); i++) {
                    Node item = representation.item(i);
                    RepresentationDASH representationDASH = parseRepresentation(item);
                    resource.putRepresentation(representationDASH.id, representationDASH);
                }
            } catch (ParserConfigurationException e) {
                log.error("MPD:: Impossible to configure XML Parser {}", e);
            } catch (SAXException e) {
                log.error("MPD:: Parse error {}", e);
            } catch (IOException e) {
                log.error("MPD:: Problem with MPD in XML form {}", e);
            }
            log.info("Finish download and parse: {}", url);
        }
    }

    class RepresentationPrefecther implements Runnable {

        long serviceId;
        RepresentationDASH rep;
        Proxy proxy;
        ResourceHTTPDASH res;

        public RepresentationPrefecther(long serviceId, RepresentationDASH rep, Proxy p, ResourceHTTPDASH r) {
            this.serviceId = serviceId;
            this.rep = rep;
            this.proxy = p;
            this.res = r;
        }

        public void postHTTP(String uri) {
            try {
                //TODO: Port of the prefetcher should be configurable
                URL url = new URL("http://" + proxy.getIpaddr()+":" + proxy.getPrefetch_port() + "/prefetch");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                connection.setDoOutput(true);
                connection.setRequestMethod("POST");

                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode paramurl = objectMapper.createObjectNode();
                paramurl.put("url", uri);
                paramurl.put("server", proxy.getIpaddr());
                paramurl.put("port", PREFETCHER_PORT);

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
                ResourceHTTP resourceHTTP = retrieveResource(url);
                Cache c = null;
                if (resourceHTTP == null) {
                    c = findCacheForNewResource(icnservice, url, DeviceId.deviceId(proxy.getLocation().getDpid()),
                            PortNumber.portNumber(proxy.getLocation().getPort()));
                } else {
                    c = findCacheForExistingResource(icnservice, url, DeviceId.deviceId(proxy.getLocation().getDpid()),
                            PortNumber.portNumber(proxy.getLocation().getPort()));
                }

                // Generate a new icn
                generateNewPrefetchingIpandPort();
                Ip4Address icnAddress = Ip4Address.valueOf(getPrefetchingIpStr());
                short icnPort = getPrefetchingPort();
                if(!icnservice.createPrefetchingPath("prefetch" + serviceId, proxy, proxy.location, c, icnAddress, icnPort)){
                    log.error("Unable to create prefetching path. Aborting\n {} {} {} {} {}",
                            proxy, proxy.location, c, icnAddress, icnPort);
                    return;
                }
                serviceId++;
                postHTTP(url);
            });
        }
    }
}
