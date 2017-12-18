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
import javafx.util.Pair;
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
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class IcnClosestCacheDASH extends IcnClosestCache {
    private final Logger log = LoggerFactory.getLogger(getClass());

    static final String DESCRIPTION = "CLOSESTDASH";
    static private final int PREFETCHER_PORT = 8080;
    static private final Long MIN_PREFETCHING_IP = 2886729729L; //172.16.0.1
    static private final Long MAX_PREFETCHING_IP = 2887778303L; //172.31.255.255
    static private final short MIN_PREFETCHING_PORT = 1025;
    static private final short MAX_PREFETCHING_PORT = 31999;
    private final ExecutorService pool;

    private long prefetching_ip = MIN_PREFETCHING_IP;
    private short prefetching_port = MIN_PREFETCHING_PORT;

    private long serviceId = 1L;

    public IcnClosestCacheDASH() {
        super();
        pool = Executors.newFixedThreadPool(1);
    }

    /**
     * Advances first the port until the limit. Then the ip until the limit and rotates.
     * @return
     */
    private synchronized Pair<Long, Short> generateNewPrefetchingIpandPort() {
        prefetching_port++;
        if (prefetching_port == MAX_PREFETCHING_PORT) {
            prefetching_port = MIN_PREFETCHING_PORT;
            prefetching_ip++;
            if (prefetching_ip == MAX_PREFETCHING_IP)
                prefetching_ip = MIN_PREFETCHING_IP;
        }
        return new Pair<>(prefetching_ip, prefetching_port);
    }

    private Long getPrefetchingIp() {
        return prefetching_ip;
    }

    private short getPrefetchingPort() {
        return prefetching_port;
    }

    private String Ip2Str(Long ip) {
            return ((ip >> 24) & 0xFF) + "."
                    + ((ip >> 16) & 0xFF) + "."
                    + ((ip >> 8) & 0xFF) + "."
                    + (ip & 0xFF);

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
            RepresentationDASH representationDASH = r.representation4URL(resourceHTTP);
            if (representationDASH != null && !representationDASH.isPrefetched()) {
                // prefetch Representation if not prefetched already (Avoid two prefetches parallel for same resources)
                pool.execute(new RepresentationPrefecther(this, serviceId, representationDASH, proxy, r));
                representationDASH.setPrefetched(true);
            }
            serviceId += representationDASH.getFullUrls().size() + 1L;
        });

        return super.createResource(resourceDASH == null ? resourceHTTP : resourceDASH);
    }

    public boolean addPrefetchedResource(ResourceHTTP res) {
        this.resources.put(res.getName(), res);
        return true;
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
            String dependencyId = "";
            String mimetype = null;

            NamedNodeMap attributes = item.getAttributes();
            id = Integer.parseInt(attributes.getNamedItem("id").getNodeValue());
            width = Integer.parseInt(attributes.getNamedItem("width").getNodeValue());
            height = Integer.parseInt(attributes.getNamedItem("height").getNodeValue());
            frameRate = Integer.parseInt(attributes.getNamedItem("frameRate").getNodeValue());
            bandwidth = Long.parseLong(attributes.getNamedItem("bandwidth").getNodeValue());
            codec = attributes.getNamedItem("codecs").getNodeValue();

            RepresentationDASH r = new RepresentationDASH(id, width, height, frameRate, bandwidth, codec, mimetype);

            if (attributes.getNamedItem("dependencyId") != null) {
                dependencyId = attributes.getNamedItem("dependencyId").getNodeValue();
                String[] split = dependencyId.split(" ");
                for (String did : split) {
                    r.setDependency(Integer.parseInt(did));
                }
            }

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
                String baseURLStr = baseURL.item(0).getFirstChild().getNodeValue();

                NodeList representation = doc.getElementsByTagName("Representation");
                for (int i = 0; i < representation.getLength(); i++) {
                    Node item = representation.item(i);
                    RepresentationDASH representationDASH = parseRepresentation(item);
                    representationDASH.setBaseURL(baseURLStr);
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
        IcnClosestCacheDASH caller;

        public RepresentationPrefecther(IcnClosestCacheDASH caller, long serviceId, RepresentationDASH rep, Proxy p, ResourceHTTPDASH r) {
            this.serviceId = serviceId;
            this.rep = rep;
            this.proxy = p;
            this.res = r;
            this.caller = caller;
        }

        public boolean postHTTP(String uri, String icnAddress, short icnPort, short cachePort) {
            try {
                log.debug("Prefetch postHTTP {} {} {}", uri, icnAddress, icnPort);
                URL url = new URL("http://10.7.0.18:" + proxy.getPrefetch_port() + "/prefetch");
                log.debug("Connecting... {}", url);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");


                ObjectMapper objectMapper = new ObjectMapper();
                ObjectNode paramurl = objectMapper.createObjectNode();
                paramurl.put("url", uri);
                paramurl.put("server", icnAddress);
                paramurl.put("port", icnPort);
                paramurl.put("cacheport", cachePort);
                connection.setRequestProperty("Content-Length", Integer.valueOf(paramurl.toString().getBytes().length).toString());
                log.debug("Sending prefetch request to {} with {}", url, paramurl.toString());
                try( DataOutputStream wr = new DataOutputStream( connection.getOutputStream())) {
                    wr.writeBytes( paramurl.toString() );
                }
                int responseCode = connection.getResponseCode();

            } catch (MalformedURLException e) {
                log.error("Malformed URL: {}", uri);
                e.printStackTrace();
                return false;
            } catch (IOException e) {
                log.error("Impossible to connect {}", uri);
                e.printStackTrace();
                return false;
            }
            return true;
        }

        @Override
        public void run() {

            LinkedList<String> fullUrls = rep.getFullUrls();
            log.info("Prefetching id {} with {} chunks", rep.getId(), fullUrls.size());
            for (Integer id : rep.getDependencies()) {
                log.info("Prefetching dependant id: {}", id);
                RepresentationDASH representationDASH = res.getRepresentation(id);
                fullUrls.addAll(representationDASH.getFullUrls());
                representationDASH.setPrefetched(true);
            }
            log.info("Total chunks {}", fullUrls.size());
            // fullUrls contains all the urls for all the dependencies, now sort it first by length and after alphabetically
            fullUrls.sort(new LengthFirstComparator());

            int dependenciessize = rep.getDependencies().size() + 1; // The dependencies plus the actual
            for (int idx = 0; idx < fullUrls.size(); idx += dependenciessize + 1) {
                List<String> urlssubset = fullUrls.subList(idx, idx + dependenciessize);
                log.info("Downloading from {} to  {} : {}", idx, idx + dependenciessize, urlssubset);

                urlssubset.parallelStream().forEach(url -> {
                    ResourceHTTP resourceHTTP = retrieveResource(url);
                    Cache c = null;
                    if (resourceHTTP == null) {
                        c = findCacheForNewResource(icnservice, url, DeviceId.deviceId(proxy.getLocation().getDpid()),
                                PortNumber.portNumber(proxy.getLocation().getPort()));
                    } else {
                        log.debug("Content was cached, no need to precache");
                        return;
                    }

                    // Generate a new icn
                    Pair<Long, Short> prefetchipandportpair = generateNewPrefetchingIpandPort();
                    String icnAddressStr = Ip2Str(prefetchipandportpair.getKey());
                    Ip4Address icnAddress = Ip4Address.valueOf(icnAddressStr);
                    short icnPort = prefetchipandportpair.getValue();
                    if (!icnservice.createPrefetchingPath("prefetch" + serviceId, proxy, proxy.location, c, icnAddress, icnPort)) {
                        log.error("Unable to create prefetching path. Aborting\n {} {} {} {} {}",
                                proxy, proxy.location, c, icnAddressStr, icnPort);
                        return;
                    }
                    serviceId++;
                    boolean posted = false;
                    while (!posted) {
                        if (postHTTP(url, icnAddressStr, icnPort, (short) c.getPort())) {
                            //Insert Resource
                            ResourceHTTP res = new ResourceHTTP(UtilIcn.resourceId(caller.getName(), url), url);
                            res.setRequests(1);
                            res.addCache(c);
                            res.setFullurl(url);
                            caller.addPrefetchedResource(res);
                            posted = true;
                        }
                    }
                });
            }
        }

        public class LengthFirstComparator implements Comparator<String> {
            @Override
            public int compare(String o1, String o2) {
                if (o1.length()!=o2.length()) {
                    return o1.length()-o2.length(); //overflow impossible since lengths are non-negative
                }
                return o1.compareTo(o2);
            }
        }

    }
}
