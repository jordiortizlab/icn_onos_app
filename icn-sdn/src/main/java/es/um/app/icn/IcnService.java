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

import java.nio.ByteBuffer;
import java.util.*;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.*;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleEvent;
import org.onosproject.net.flow.FlowRuleListener;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;


@Component(immediate = true)
@Service
public class IcnService implements
        IIcnService, IIcnPrivateService {

    protected static final Logger log = LoggerFactory.getLogger(IcnService.class);
    protected static final String IPV4_ETHERTYPE = "0x0800";
    protected static final short PUNT_OFIDLE_TIMEOUT = 0;			// infinite
    protected static final short PUNT_OFHARD_TIMEOUT = 0;			// infinite
    protected static final short PUNT_OFPRIO = 15000;
    protected static final short PATH_OFIDLE_TIMEOUT = 5;			// 5 sec
    protected static final short PATH_OFHARD_TIMEOUT = 0;			// infinite
    protected static final short PATH_OFPRIO = 30000;
    protected static final int OFMESSAGE_DAMPER_CAPACITY = 10000;	// ms
    protected static final int OFMESSAGE_DAMPER_TIMEOUT = 250;		// ms
    protected static final boolean BIDIRECTIONAL_FLOW = true;
    protected static final int PROCESSOR_PRIORITY = 2;
    protected static final int INTENT_PRIORITY_HIGH = 3000;
    protected static final int INTENT_PRIORITY_LOW = 100;
    protected static final int DEFAULT_FLOW_TIMEOUT = 10;
    protected static final int PROXYPATH_FLOW_TIMEOUT = 60;

    /** Onos Services */
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowObjectiveService flowObjectiveService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected TopologyService topologyService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;



    /** APPId */
    private ApplicationId appId;

    /** We need to register with the provider to receive OF messages */
    protected HashMap<String, Icn> icns;
    protected HashMap<String, Proxy> proxies;
    protected HashMap<PathIndex, InternalIcnFlow> flows;

    private IcnPacketProcessor icnPacketProcessor = new IcnPacketProcessor();
    private InternalFlowListener flowListener = new InternalFlowListener();

    private long proxyReqServiceId = 1L;
    @Activate
    public void activate() {

        appId = coreService.registerApplication("es.um.app.icn");

        // Initialize our data structures
        icns = new HashMap<String, Icn>();
        proxies = new HashMap<String, Proxy>();
        flows  = new HashMap<>();
        // Install Processor
        packetService.addProcessor(icnPacketProcessor, PacketProcessor.director(PROCESSOR_PRIORITY));

        flowRuleService.addListener(flowListener);

        requestPackets();

    }

    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        packetService.removeProcessor(icnPacketProcessor);
        flowRuleService.removeListener(flowListener);
        clearFlows();
    }

    @Modified
    public void modified() {
        requestPackets();
    }

    /**
     * Request packet in via PacketService.
     */
    private void requestPackets() {
        TrafficSelector.Builder selectorBuilder =
                DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        selectorBuilder.matchIPProtocol(IPv4.PROTOCOL_TCP);
        selectorBuilder.matchTcpDst(TpPort.tpPort(UtilIcn.HTTP_PORT));
        packetService.requestPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);
        // TODO: Missing IPv6
    }

    /**
     * Cancel requested packet in via packet service.
     */
    private void withdrawIntercepts() {
        TrafficSelector.Builder selectorBuilder =
                DefaultTrafficSelector.builder();
        selectorBuilder.matchEthType(Ethernet.TYPE_IPV4);
        selectorBuilder.matchIPProtocol(IPv4.PROTOCOL_TCP);
        selectorBuilder.matchTcpDst(TpPort.tpPort(UtilIcn.HTTP_PORT));
        packetService.cancelPackets(selectorBuilder.build(), PacketPriority.REACTIVE, appId);
        // TODO: Missing IPv6
    }


    private void clearFlows() {
        flows.forEach((k,v) -> removeFlowPath(k,v));
    }

    private boolean removeFlowPath(PathIndex idx, InternalIcnFlow flow) {
        DefaultForwardingObjective.Builder builder = DefaultForwardingObjective.builder()
                .withSelector(flow.getSelector())
                .withTreatment(flow.getTreatment())
                .withPriority(INTENT_PRIORITY_HIGH)
                .makeTemporary(DEFAULT_FLOW_TIMEOUT)
                .fromApp(appId)
                .withFlag(ForwardingObjective.Flag.SPECIFIC);
        flowObjectiveService.forward(idx.getNode(), builder.remove());
        return true;
    }

    protected boolean createPath(String service, int timeout, ApplicationId appId, PathService pathService, FlowObjectiveService flowObjectiveService,
                                        int matchIpsrc, int matchIpDst, boolean matchPortSrc, int srcport, boolean matchPortDst, int dstport,
                                        Ethernet ethIn, IPv4 ipIn, TCP tcpIn,
                                        ConnectPoint source, ConnectPoint destination,
                                        boolean rewriteSourceIP, IpAddress rwsourceAddr,
                                        boolean rewriteSourceMAC, MacAddress rwsourcel2Addr,
                                        boolean rewriteSourcePort, TpPort rwsourceport,
                                        boolean rewriteDestinationIP, IpAddress rwdestinationAddr,
                                        boolean rewriteDestinationMAC, MacAddress rwdestinationl2Addr,
                                        boolean rewriteDestinationPort, TpPort rwdestport) {
        if (source.equals(destination)) {
            log.error("Source = Destination {}", source);
            return false;
        }
        log.debug("Creating path matchIpSrc {} matchIpDst {} matchPortSrc {}:{} matchPorDst {}:{} source {} destination {} ",
                matchIpsrc, matchIpDst, matchPortSrc, srcport, matchPortDst, dstport, source, destination);
        log.debug("rewriteSource {} {} {}", rewriteSourceIP, rwsourceAddr, rwsourcel2Addr);
        log.debug("rewriteDestination {} {} {}", rewriteDestinationIP, rwdestinationAddr, rwdestinationl2Addr);
        PortNumber sourceport = source.port();
        PortNumber destinationport = destination.port();

        TrafficSelector.Builder trafficSelectorBuilder = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP);
        if (matchPortDst) {
            trafficSelectorBuilder
                    .matchTcpDst(TpPort.tpPort(dstport));
        }
        if (matchPortSrc) {
            trafficSelectorBuilder
                    .matchTcpSrc(TpPort.tpPort(srcport));
        }

        if (!source.deviceId().equals(destination.deviceId())) {
            log.debug("Indirect connection, Looking for paths");
            Set<Path> paths = pathService.getPaths(source.elementId(), destination.elementId());
            if (paths.isEmpty()) {
                log.error("Unable to locate any path");
                return false;
            }


            Iterator<Path> iterator = paths.iterator();
            if (iterator.hasNext()) {
                Path path = iterator.next(); // Get one path
                for (Link link : path.links()) {
                    PathIndex idx = new PathIndex(service, link.src().deviceId(), appId, matchIpsrc, matchIpDst, matchPortSrc, srcport, matchPortDst, dstport, ethIn, ipIn, tcpIn,
                            source, destination,
                            rewriteSourceIP, rwsourceAddr, rewriteSourceMAC, rwsourcel2Addr, rewriteSourcePort, rwsourceport,
                            rewriteDestinationIP, rwdestinationAddr, rewriteDestinationMAC, rwdestinationl2Addr, rewriteDestinationPort, rwdestport);
                    if (flows.containsKey(idx)) {
                        log.debug("Flow {} was already requested, ignoring.", idx);
                        continue;
                    }
                    destinationport = link.src().port();
                    log.debug("Treating link {} for device {} inport {} outport {}",
                            link, link.src().deviceId(), sourceport, destinationport);
                    TrafficSelector selector = trafficSelectorBuilder
                            .matchIPSrc(IpPrefix.valueOf(matchIpsrc, 32))
                            .matchIPDst(IpPrefix.valueOf(matchIpDst, 32))
                            .matchInPort(sourceport)
                            .build();


                    TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
                    builder.setOutput(destinationport);
                    TrafficTreatment treatment = builder.build();

                    ForwardingObjective.Builder fobuilder = DefaultForwardingObjective.builder()
                            .withSelector(selector)
                            .withTreatment(treatment)
                            .withPriority(INTENT_PRIORITY_HIGH)
                            .fromApp(appId)
                            .withFlag(ForwardingObjective.Flag.SPECIFIC);
                    if (timeout > 0) {
                        fobuilder.makeTemporary(timeout);
                    }
                    flowObjectiveService.forward(link.src().deviceId(), fobuilder.add());
                    log.debug("Preparing path: {} {} {}", link.src().deviceId(), selector, treatment);
                    InternalIcnFlow icnflow = new InternalIcnFlow(selector, treatment);
                    flows.put(idx, icnflow);
                    sourceport = link.dst().port();
                }
            }
        } else {
            log.debug("Direct connection, same switch");
        }
        // Now we need to treat last jump
        if (source.deviceId().equals(destination.deviceId())) {
            log.debug("Same device");
            sourceport = source.port();
        }
        PathIndex idx = new PathIndex(service, destination.deviceId(), appId, matchIpsrc, matchIpDst, matchPortSrc, srcport, matchPortDst, dstport, ethIn, ipIn, tcpIn,
                source, destination,
                rewriteSourceIP, rwsourceAddr, rewriteSourceMAC,rwsourcel2Addr, rewriteSourcePort, rwsourceport,
                rewriteDestinationIP, rwdestinationAddr, rewriteDestinationMAC, rwdestinationl2Addr, rewriteDestinationPort, rwdestport);
        if (flows.containsKey(idx)) {
            log.debug("Flow {} was already requested, ignoring.", idx);
            return true;
        }

        destinationport = destination.port();
        TrafficSelector selector = trafficSelectorBuilder
                .matchInPort(sourceport)
                .matchIPSrc(IpPrefix.valueOf(matchIpsrc, 32))
                .matchIPDst(IpPrefix.valueOf(matchIpDst, 32))
                .build();

        TrafficTreatment treatment = null;



        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();

        if (rewriteSourceIP) {
            builder.setIpSrc(rwsourceAddr);
            builder.immediate();
        }
        if (rewriteSourceMAC) {
            builder.setEthSrc(rwsourcel2Addr);
            builder.immediate();
        }
        if (rewriteSourcePort) {
            builder.setTcpSrc(rwsourceport);
            builder.immediate();
        }
        if (rewriteDestinationIP) {
            builder.setIpDst(rwdestinationAddr);
            builder.immediate();
        }
        if(rewriteDestinationMAC) {
            builder.setEthDst(rwdestinationl2Addr);
            builder.immediate();
        }
        if(rewriteDestinationPort) {
            builder.setTcpDst(rwdestport);
            builder.immediate();
        }
        builder.setOutput(destinationport);
        treatment = builder.build();

        ForwardingObjective.Builder fobuilder = DefaultForwardingObjective.builder()
                .withSelector(selector)
                .withTreatment(treatment)
                .withPriority(INTENT_PRIORITY_HIGH)
                .fromApp(appId)
                .withFlag(ForwardingObjective.Flag.SPECIFIC);
        if (timeout > 0) {
            fobuilder.makeTemporary(timeout);
        }
        flowObjectiveService.forward(destination.deviceId(), fobuilder.add());
        log.debug("Preparing final jump: {} {}", selector, treatment);
        InternalIcnFlow icnflow = new InternalIcnFlow(selector, treatment);
        flows.put(idx, icnflow);

        return true;
    }

    public boolean flowExpired(DeviceId device, InternalIcnFlow flow) {
        Map<PathIndex, InternalIcnFlow> collectedEntries = flows.entrySet().parallelStream().filter(e -> e.getKey().getNode().equals(device) && e.getValue().equals(flow)).collect(toMap(HashMap.Entry::getKey, HashMap.Entry::getValue));

        if (collectedEntries.size() == 0) {
            log.warn("Internal flow expired and not found {}", flow);
            return false;
        }
        if (collectedEntries.size() != 1) {
            log.warn("More than one internal flow coincide {}", collectedEntries.keySet());
        }
        flows.keySet().removeAll(collectedEntries.keySet());

        return true;
    }

    /**
     * Program required flows when a proxy informs that a resource is being
     * requested.
     * @param req
     */
    public boolean processResourceRequest(ProxyRequest req) {
        log.info("Process resource request from proxy {} for host {} and flow {}",
                req.proxy, req.hostname, req.flow.toString());

        Optional<Proxy> optproxy = proxies.values().stream().filter(p -> p.getMacaddr().equalsIgnoreCase(req.getProxy())).findFirst();
        if (!optproxy.isPresent()) {
            log.error("Unable to find a proxy in icn for mac{}", req.getProxy());
            return false;
        }


        // Get the providers related to this request (if any)
        Collection<Provider> providers =
                findProvidersFromAddress(IPv4.toIPv4Address(req.flow.daddr));
        if (providers.isEmpty()) {
            log.warn("No provider for proxy request: proxy {} hostname {}",
                    req.proxy, req.hostname);
            return false;
        }

        String resourceName = req.uri;
        int idx = resourceName.indexOf('?');
        if (idx > 0)
            resourceName = resourceName.substring(0, idx);

        Proxy p = optproxy.get();
        List<Provider> providerList = providers.parallelStream()
                .filter(prov -> (prov.matchUriPattern(req.uri) != null) &&
                        (prov.matchHostPattern(req.getHostname()) != null))
                .limit(1).collect(toList());
        if (providerList.isEmpty()) {
            log.info("Not matching provider for url {}", req.uri);
            return false;
        }
        Provider provider = providerList.get(0);
        log.info("Checking provider: {}", provider.getName());
        String uri = provider.matchUriPattern(req.uri);

        Optional<Icn> icnfirst = icns.values().parallelStream().filter(x -> {
            Cache c = null;
            if (!x.retrieveProviders().contains(provider))
                return false;
            return true;
        }).findFirst();
        if (!icnfirst.isPresent()) {
            log.error("No ICN found for provider {}", provider);
            return false;
        }

        // We have in icnfirst the first ICN that accomplishes previous checks
        Icn icn = icnfirst.get();

        Cache c = null;
        ResourceHTTP resourceHTTP = null;
        if ( (resourceHTTP = icn.retrieveResource(uri)) != null) {
            // ResourceHTTP already cached
            c =  icn.findCacheForExistingResource(this,
                    uri, DeviceId.deviceId(p.getLocation().getDpid()),
                    PortNumber.portNumber(p.getLocation().getPort()));
            log.info("Existing resourceHTTP {} in ICN {} to cache {}",
                    uri, icn.getName(), c.name);
        } else {
            // New resourceHTTP
            c = icn.findCacheForNewResource(this,
                    uri, DeviceId.deviceId(p.getLocation().getDpid()),
                    PortNumber.portNumber(p.getLocation().getPort()));
        }

        log.info("Program path to cache {} flow {}",
                c.name, req.flow.toString());
        if (programProxyPath("proxyReq" + proxyReqServiceId++, req.flow, p, p.getLocation(), c)) {
            log.info("Created path from proxy to cache");
        } else {
            log.error("Unable to create path from proxy to cache");
            return false;
        }

        // Resource management
        if (resourceHTTP != null) {
            resourceHTTP.incrRequests();
            req.flow.setDmac(c.macaddr);
        } else {
            ResourceHTTP res = new ResourceHTTP(UtilIcn.resourceId(icn.getName(), uri), uri);
            res.setRequests(1);
            res.addCache(c);
            if (!req.getUri().contains("http://"))
                res.setFullurl("http://" + req.getHostname() + "/" + req.getUri()); // TODO: Make this more dynamic
            else
                res.setFullurl(req.getUri());
            icn.createResource(res, p);
            log.info("New resourceHTTP {} in ICN {} to cache {}",
                    res, icn.getName(), c.name);
            req.flow.setDmac(c.macaddr);
        }

        return true;
    }

    /**
     * Compute route from an input switch to a middlebox's switch, and issue
     * corresponding FlowMod messages for the given flow.
     * @param origin Input location
     * @param mbox Middlebox where the flow is directed.
     * @return Ouput port that must be used by the input switch.
     */
    private boolean programProxyPath(String service, IcnFlow originalreq, IMiddlebox proxy, Location origin, IMiddlebox mbox) {
        log.info("Creating connection for middlebox {} <-> {}", origin.toString(), mbox.getLocation().toString());
        log.debug("Original req: {}", originalreq);
        log.debug("REST Request:  creating paths");
        log.debug("origin: {}, mbox {}", origin.toString(), mbox.toString());
        //TODO: What about ipv6??
        int proxyprefix = Ip4Address.valueOf(proxy.getIpaddr()).toInt();
        int originaldestprefix = Ip4Address.valueOf(originalreq.daddr).toInt();
        int cacheprefix = Ip4Address.valueOf(mbox.getIpaddr()).toInt();
        int proxysrcport = Integer.parseInt(originalreq.getSport());
        int proxydstport = Integer.parseInt(originalreq.getDport());
        MacAddress originalMac = originalreq.getDmac() != null ? MacAddress.valueOf(originalreq.getDmac()) : null;
        MacAddress cacheMac = MacAddress.valueOf(mbox.getMacaddr());

        IpAddress ipcacheprefix = Ip4Address.valueOf(mbox.getIpaddr());
        IpAddress ipdestprefix = Ip4Address.valueOf(originalreq.daddr);
        log.debug("prefix origin (proxy) {} destination (provider) {}", proxyprefix, originaldestprefix);

        if (!createPath(service, DEFAULT_FLOW_TIMEOUT, appId, pathService, flowObjectiveService,
                cacheprefix,
                proxyprefix,
                true, mbox.getPort(), true, proxysrcport,
                null, null, null,
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                true, ipdestprefix,
                true, cacheMac,
                true, TpPort.tpPort(UtilIcn.HTTP_PORT),
                false, null,
                false, null,
                false, null)) {
            log.error("programProxyPath(): Unable to create path from cache to proxy");
            return false;
        }


        if(!createPath(service, DEFAULT_FLOW_TIMEOUT, appId, pathService, flowObjectiveService,
                proxyprefix,
                originaldestprefix,
                true, proxysrcport, true, UtilIcn.HTTP_PORT,
                null, null, null,
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                false, null,
false,null,
                false, null,
                true, ipcacheprefix,
true, cacheMac,
                true, TpPort.tpPort(mbox.getPort()))) {
            log.error("programProxyPath(): Unable to create path from proxy to cache");
        return false;
        }

        log.debug("Proxy paths created successfully");

        return true;
    }

    @Override
    public boolean createPrefetchingPath(String service, IMiddlebox proxy, Location origin, IMiddlebox mbox, Ip4Address icnAddress, short icnPort) {
        int proxyprefix = Ip4Address.valueOf(proxy.getIpaddr()).toInt();
        int cacheprefix = Ip4Address.valueOf(mbox.getIpaddr()).toInt();
        MacAddress cacheMac = MacAddress.valueOf(mbox.getMacaddr());

        IpAddress ipcacheprefix = Ip4Address.valueOf(mbox.getIpaddr());

        if(!createPath(service, DEFAULT_FLOW_TIMEOUT, appId, pathService, flowObjectiveService, proxyprefix, icnAddress.toInt(), false, (short)0,
                true, icnPort, null, null, null,
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                true, icnAddress, false, null, true, TpPort.tpPort(icnPort),
                false, null, false, null, false, null)) {
            log.error("createPrefetchingPath: Unable to create path between cache and proxy");
            return false;
        }

        if(!createPath(service, DEFAULT_FLOW_TIMEOUT, appId, pathService, flowObjectiveService, proxyprefix, icnAddress.toInt(), false, (short)0,
                true, icnPort, null, null, null,
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                false, null, false, null, false, null,
                true, ipcacheprefix, true, cacheMac, true, TpPort.tpPort(3128))) {
            log.error("createPrefetchingPath: Unable to create path between proxy and cache");
            return false;
        }


        return false;
    }

    private IcnFlow reverseFlow(IcnFlow flow) {
        IcnFlow reverseFlow = new IcnFlow();
        if (flow.dltype != null)
            reverseFlow.dltype = flow.dltype;
        if (flow.proto != null)
            reverseFlow.proto = flow.proto;
        if (flow.saddr != null)
            reverseFlow.daddr = flow.saddr;
        if (flow.daddr != null)
            reverseFlow.saddr = flow.daddr;
        if (flow.sport != null)
            reverseFlow.dport = flow.sport;
        if (flow.dport != null)
            reverseFlow.sport = flow.dport;

        return reverseFlow;
    }

    protected Collection<Provider> findProvidersFromAddress(int ip) {
        Collection<Provider> providers = new HashSet<Provider>();
        for (Icn c : icns.values()) {
            for (Provider p: c.retrieveProviders()) {
                if (p.containsIpAddress(ip))
                    providers.add(p);
            }
        }
        return providers;
    }

    protected Proxy findProxy(String macaddr) {
        for (Proxy p: proxies.values()) {
            if (macaddr.equalsIgnoreCase(p.macaddr))
                return p;
        }
        return null;
    }

    protected IMiddlebox findClosestMiddlebox(Collection<? extends IMiddlebox> middleboxes,
                                              DeviceId sw, PortNumber inPort) {
        IMiddlebox mbox = null;
        int minLen = Integer.MAX_VALUE;

        for (IMiddlebox m: middleboxes) {
            String mboxDeviceId = null;
            if (m.getLocation() == null) {
                // There was no info in the config json about location. Try to find the host
                Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(m.getMacaddr()));
                if (hostsByMac.isEmpty())
                    continue;
                if (hostsByMac.size() > 1)
                    log.warn("More than one host per mac, multiple links for same host? {}", m.getMacaddr());
                for (Host host : hostsByMac) {
                    mboxDeviceId = host.location().deviceId().toString();
                }
            } else {
                // Get info about middlebox location from config
                mboxDeviceId = m.getLocation().dpid;
            }

            Topology topology = topologyService.currentTopology();
            log.debug("Paths in topology: {}", topologyService.getPaths(topology, sw, DeviceId.deviceId(mboxDeviceId)));

            Set<Path> paths = topologyService.getPaths(topology, sw, DeviceId.deviceId(mboxDeviceId));
            if (sw.toString().equals(mboxDeviceId))
            {
                // Middlebox and host on the same device. No path needed. No need to look for best path.
                mbox = m;
                continue;
            } else {
                for (Path path : paths) {
                    if (path.links().size() < minLen) { // TODO: Here we could take into account other metrics rather than number of links
                        minLen = path.links().size();
                        mbox = m;
                    }
                }
            }
        }
        return mbox;
    }

    protected Cache findCache(String macaddr) {
        for (Icn icn : icns.values()) {
            for (Cache cache: icn.retrieveCaches()) {
                if (macaddr.equalsIgnoreCase(cache.macaddr))
                    return cache;
            }
        }
        return null;
    }

    @Override
    public Collection<Icn> retrieveIcns() {
        return icns.values();
    }

    @Override
    public Icn retrieveIcn(String name) {
        return icns.get(name);
    }

    @Override
    public Icn createIcn(Icn icn) {
        icn.setIcnService(this);
        icns.put(icn.getName(), icn);
        return icn;
    }

    @Override
    public Icn updateIcn(Icn icn) {
        icns.put(icn.getName(), icn);
        return icn;
    }

    @Override
    public Icn removeIcn(String name) {
        return icns.remove(name);
    }

    @Override
    public Collection<Provider> retrieveProviders(Icn icn) {
        return icn.retrieveProviders();
    }

    @Override
    public Provider retrieveProvider(Icn icn, String name) {
        return icn.retrieveProvider(name);
    }

    @Override
    public Provider createProvider(Icn icn, Provider provider) {
        return icn.createProvider(provider);
    }

    @Override
    public Provider updateProvider(Icn icn, Provider provider) {
        return icn.updateProvider(provider);
    }

    @Override
    public Provider removeProvider(Icn icn, String name) {
        return icn.removeProvider(name);
    }

    @Override
    public Collection<Cache> retrieveCaches(Icn icn) {
        return icn.retrieveCaches();
    }

    @Override
    public Cache retrieveCache(Icn icn, String name) {
        return icn.retrieveCache(name);
    }

    @Override
    public Cache createCache(Icn icn, Cache cache) {
        return icn.createCache(cache);
    }

    @Override
    public Cache updateCache(Icn icn, Cache cache) {
        return icn.updateCache(cache);
    }

    @Override
    public Cache removeCache(Icn icn, String name) {
        return icn.removeCache(name);
    }

    @Override
    public Collection<ResourceHTTP> retrieveResources(Icn icn) {
        return icn.retrieveResources();
    }

    @Override
    public ResourceHTTP retrieveResource(Icn icn, String id) {
        return icn.retrieveResource(id);
    }

    @Override
    public Collection<Proxy> retrieveProxies() {
        return proxies.values();
    }

    @Override
    public Proxy retrieveProxy(String name) {
        return proxies.get(name);
    }

    @Override
    public Proxy createProxy(Proxy proxy) {
        proxies.put(proxy.name, proxy);
        return proxy;
    }

    @Override
    public Proxy updateProxy(Proxy proxy) {
        proxies.put(proxy.name, proxy);
        return proxy;
    }

    @Override
    public Proxy removeProxy(String name) {
        return proxies.remove(name);
    }

    private class IcnPacketProcessor implements PacketProcessor {
        long serviceId = 1;

        @Override
        /**
         * If the payload is of interest to any of our ICNs, then let's decide the
         * destination proxy and program the appropriate paths.
         */
        public void process(PacketContext context) {

            // WE DO ACTUALLY NEED TO PARSE PACKETS ALREADY PARSED IF WE WANT
            // TO SUPERSEED E.G, the FWD app
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }


            // Only continue processing if HTTP traffic is received
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            IpAddress inAddr = null, dstAddr = null;
            MacAddress inl2Addr = null, dstl2Addr = null;
            if (!(ethPkt.getEtherType() == Ethernet.TYPE_IPV4) && !(ethPkt.getEtherType() == Ethernet.TYPE_IPV6)) {
                log.trace("Packet is not IPv4 neither v6, ignoring");
                return;
            }
            IPv4 ipv4Pkt = null;
            TCP tcpPkt = null;
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                ipv4Pkt = (IPv4) ethPkt.getPayload();
                inAddr = IpAddress.valueOf(ipv4Pkt.getSourceAddress());
                dstAddr = IpAddress.valueOf(ipv4Pkt.getDestinationAddress());
                dstl2Addr = ethPkt.getDestinationMAC();
                inl2Addr = ethPkt.getSourceMAC();
                if (ipv4Pkt.getProtocol() != IPv4.PROTOCOL_TCP) {
                    log.trace("IPv4 Packet is not TCP, ignoring");
                    return;
                }
                tcpPkt = (TCP) ipv4Pkt.getPayload();
                if ( tcpPkt.getDestinationPort() != UtilIcn.HTTP_PORT) {
                    log.trace("IPv4 Packet is not HTTP, ignoring");
                    return;
                }
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                //TODO: Missing IPv6
                return;
            }

            if((tcpPkt.getFlags() & 0x2) == 0x0) {
                log.debug("[{}] Packet is not TCP SYN: {}", context.inPacket().receivedFrom(), ethPkt);
                return;
            }

            log.debug("ICN Process PACKET_IN from switch {}", context.inPacket().receivedFrom().toString());
            log.debug("{}", ethPkt);
            DeviceId indeviceId = context.inPacket().receivedFrom().deviceId();
            PortNumber inport = context.inPacket().receivedFrom().port();

            DeviceId outdeviceId = null;
            PortNumber outport = null;
            IpAddress outaddress = null;
            MacAddress outl2address = null;


            if (ethPkt == null) {
                return;
            }

            HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());

            // Check that src is a registered client
            IcnFlow flow = new IcnFlow();
            flow.setSmac(ethPkt.getSourceMAC().toString());
            flow.setDmac(ethPkt.getDestinationMAC().toString());
            // TODO: Check if this restriction should be used
            if (findProxy(flow.getSmac()) != null || findCache(flow.getDmac()) != null) {
                log.info("Ignoring device {}: Not a client", flow.getSmac());
                log.debug("ETH: {} IP: {} TCP: {}",ethPkt, ipv4Pkt, tcpPkt);
                return;
            }

            // Nothing to do if we don't have any ICN or proxy
            if (icns.isEmpty() || proxies.isEmpty()) {
                log.error("Ignoring flow: No available ICNs and/or proxies");
                return;
            }

            // Program path between client and closest proxy for HTTP traffic
            Proxy proxy = (Proxy) findClosestMiddlebox(proxies.values(),
                    indeviceId, inport);
            if (proxy == null) {
                log.error("Could not program path to proxy: No proxy available");
                return;
            }

            if (proxy.getLocation() == null) {
                // We don't know where the proxy is located. Use the hostprovider app info
                Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(proxy.getMacaddr()));

                if (hostsByMac.isEmpty()) {
                    log.error("No Host for proxy mac");
                    return;
                }
                if (hostsByMac.size() > 1)
                    log.warn("More than one host per mac, multiple links for same proxy? {}", proxy.getMacaddr());

                for (Host host : hostsByMac) {
                    outdeviceId = host.location().deviceId();
                    outport = host.location().port();
                    outaddress = (IpAddress) host.ipAddresses().toArray()[0];
                    outl2address = host.mac();
                    break;
                }
            } else {
                // If the location was set in the json there is no need to look up for the host
                outdeviceId = DeviceId.deviceId(proxy.getLocation().dpid);
                outport = PortNumber.portNumber(proxy.getLocation().port);
                outaddress = IpAddress.valueOf(proxy.getIpaddr());
                outl2address = MacAddress.valueOf(proxy.getMacaddr());
            }

            // Using Intent for path creation
            ConnectPoint sourceConnectPoint = new ConnectPoint(indeviceId, inport);
            ConnectPoint destinationConnectPoint = new ConnectPoint(outdeviceId, outport);
            log.debug("Packet Processor creating paths");
            // Create path from host to proxy
            // Create return intent
            boolean fromproxy = createPath("pprocess" + serviceId, PROXYPATH_FLOW_TIMEOUT, appId, pathService, flowObjectiveService,
                    outaddress.getIp4Address().toInt(), ipv4Pkt.getSourceAddress(),
                    true, proxy.getPort(),false, (short) 0,
                    ethPkt, ipv4Pkt, tcpPkt,
                    destinationConnectPoint, sourceConnectPoint, true,
                    dstAddr, false, dstl2Addr, true, TpPort.tpPort(UtilIcn.HTTP_PORT),
                    false, null, false, null, false, null);
            log.info("Path created fromproxy {}", fromproxy);
            boolean toproxy = createPath("pprocess" + serviceId, PROXYPATH_FLOW_TIMEOUT, appId, pathService, flowObjectiveService,
                    ipv4Pkt.getSourceAddress(), ipv4Pkt.getDestinationAddress(),
                    false, (short)0,true, UtilIcn.HTTP_PORT,
                    ethPkt, ipv4Pkt, tcpPkt,
                    sourceConnectPoint, destinationConnectPoint, false,
                    null, false, null, false, null,
                    true, outaddress, true, outl2address, true, TpPort.tpPort(proxy.getPort()));
            log.info("Path created toproxy {}", toproxy);
            serviceId++;
            // Take care of actual package
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                    .setOutput(outport)
                    .build();

            Ethernet inethpkt = (Ethernet) context.inPacket().parsed().clone();
            inethpkt.setDestinationMACAddress(outl2address);
            IPv4 newpayload = (IPv4) inethpkt.getPayload().clone();
            newpayload.setDestinationAddress(outaddress.getIp4Address().toInt());
            ((IPv4)inethpkt.getPayload()).setDestinationAddress(outaddress.getIp4Address().toInt());
            ((IPv4)inethpkt.getPayload()).resetChecksum();
            ((TCP)(((IPv4)inethpkt.getPayload())).getPayload()).resetChecksum();
            log.debug("packet: {}", inethpkt);

            OutboundPacket packet = new DefaultOutboundPacket(
                    outdeviceId,
                    treatment,
                    ByteBuffer.wrap(inethpkt.serialize()));
            packetService.emit(packet);
            log.info("sending packet: {}", packet);
        }
    }

    class InternalFlowListener implements FlowRuleListener {

        @Override
        public void event(FlowRuleEvent flowRuleEvent) {
            FlowRule flowRule = flowRuleEvent.subject();
            if (flowRuleEvent.type().equals(FlowRuleEvent.Type.RULE_REMOVED) &&
                    flowRule.appId() == appId.id()) {
                // One of our rules has been removed
                InternalIcnFlow icnFlow = new InternalIcnFlow(flowRule.selector(), flowRule.treatment());
                log.debug("Expiring flow: {}", icnFlow);
                flowExpired(flowRule.deviceId(), icnFlow);
            }
        }
    }
    
    class PathIndex {
        DeviceId node;
        ApplicationId appId;
        int matchIpsrc;
        int matchIpDst;
        boolean matchPortSrc;
        int srcport;
        boolean matchPortDst;
        int dstport;
        Ethernet ethIn;
        IPv4 ipIn;
        TCP tcpIn;
        ConnectPoint source;
        ConnectPoint destination;
        boolean rewriteSourceIP;
        IpAddress rwsourceAddr;
        boolean rewriteSourceMAC;
        MacAddress rwsourcel2Addr;
        boolean rewriteSourcePort;
        TpPort rwsourceport;
        boolean rewriteDestinationIP;
        IpAddress rwdestinationAddr;
        boolean rewriteDestinationMAC;
        MacAddress rwdestinationl2Addr;
        boolean rewriteDestinationPort;
        TpPort rwdestport;
        String service;

        public PathIndex(String service, DeviceId node, ApplicationId appId, int matchIpsrc, int matchIpDst, boolean matchPortSrc, int srcport, boolean matchPortDst, int dstport, Ethernet ethIn, IPv4 ipIn, TCP tcpIn, ConnectPoint source, ConnectPoint destination, boolean rewriteSourceIP, IpAddress rwsourceAddr, boolean rewriteSourceMAC, MacAddress rwsourcel2Addr, boolean rewriteSourcePort, TpPort rwsourceport, boolean rewriteDestinationIP, IpAddress rwdestinationAddr, boolean rewriteDestinationMAC, MacAddress rwdestinationl2Addr, boolean rewriteDestinationPort, TpPort rwdestport) {
            this.node = node;
            this.service = service;
            this.appId = appId;
            this.matchIpsrc = matchIpsrc;
            this.matchIpDst = matchIpDst;
            this.matchPortSrc = matchPortSrc;
            this.srcport = srcport;
            this.matchPortDst = matchPortDst;
            this.dstport = dstport;
            this.ethIn = ethIn;
            this.ipIn = ipIn;
            this.tcpIn = tcpIn;
            this.source = source;
            this.destination = destination;
            this.rewriteSourceIP = rewriteSourceIP;
            this.rwsourceAddr = rwsourceAddr;
            this.rewriteSourceMAC = rewriteSourceMAC;
            this.rwsourcel2Addr = rwsourcel2Addr;
            this.rewriteSourcePort = rewriteSourcePort;
            this.rwsourceport = rwsourceport;
            this.rewriteDestinationIP = rewriteDestinationIP;
            this.rwdestinationAddr = rwdestinationAddr;
            this.rewriteDestinationMAC = rewriteDestinationMAC;
            this.rwdestinationl2Addr = rwdestinationl2Addr;
            this.rewriteDestinationPort = rewriteDestinationPort;
            this.rwdestport = rwdestport;
        }

        public DeviceId getNode() {
            return node;
        }

        public void setNode(DeviceId node) {
            this.node = node;
        }

        public ApplicationId getAppId() {
            return appId;
        }

        public void setAppId(ApplicationId appId) {
            this.appId = appId;
        }

        public int getMatchIpsrc() {
            return matchIpsrc;
        }

        public void setMatchIpsrc(int matchIpsrc) {
            this.matchIpsrc = matchIpsrc;
        }

        public int getMatchIpDst() {
            return matchIpDst;
        }

        public void setMatchIpDst(int matchIpDst) {
            this.matchIpDst = matchIpDst;
        }

        public boolean isMatchPortSrc() {
            return matchPortSrc;
        }

        public void setMatchPortSrc(boolean matchPortSrc) {
            this.matchPortSrc = matchPortSrc;
        }

        public int getSrcport() {
            return srcport;
        }

        public void setSrcport(int srcport) {
            this.srcport = srcport;
        }

        public boolean isMatchPortDst() {
            return matchPortDst;
        }

        public void setMatchPortDst(boolean matchPortDst) {
            this.matchPortDst = matchPortDst;
        }

        public int getDstport() {
            return dstport;
        }

        public void setDstport(int dstport) {
            this.dstport = dstport;
        }

        public Ethernet getEthIn() {
            return ethIn;
        }

        public void setEthIn(Ethernet ethIn) {
            this.ethIn = ethIn;
        }

        public IPv4 getIpIn() {
            return ipIn;
        }

        public void setIpIn(IPv4 ipIn) {
            this.ipIn = ipIn;
        }

        public TCP getTcpIn() {
            return tcpIn;
        }

        public void setTcpIn(TCP tcpIn) {
            this.tcpIn = tcpIn;
        }

        public ConnectPoint getSource() {
            return source;
        }

        public void setSource(ConnectPoint source) {
            this.source = source;
        }

        public ConnectPoint getDestination() {
            return destination;
        }

        public void setDestination(ConnectPoint destination) {
            this.destination = destination;
        }

        public boolean isRewriteSourceIP() {
            return rewriteSourceIP;
        }

        public void setRewriteSourceIP(boolean rewriteSourceIP) {
            this.rewriteSourceIP = rewriteSourceIP;
        }

        public IpAddress getRwsourceAddr() {
            return rwsourceAddr;
        }

        public void setRwsourceAddr(IpAddress rwsourceAddr) {
            this.rwsourceAddr = rwsourceAddr;
        }

        public boolean isRewriteSourceMAC() {
            return rewriteSourceMAC;
        }

        public void setRewriteSourceMAC(boolean rewriteSourceMAC) {
            this.rewriteSourceMAC = rewriteSourceMAC;
        }

        public MacAddress getRwsourcel2Addr() {
            return rwsourcel2Addr;
        }

        public void setRwsourcel2Addr(MacAddress rwsourcel2Addr) {
            this.rwsourcel2Addr = rwsourcel2Addr;
        }

        public boolean isRewriteSourcePort() {
            return rewriteSourcePort;
        }

        public void setRewriteSourcePort(boolean rewriteSourcePort) {
            this.rewriteSourcePort = rewriteSourcePort;
        }

        public TpPort getRwsourceport() {
            return rwsourceport;
        }

        public void setRwsourceport(TpPort rwsourceport) {
            this.rwsourceport = rwsourceport;
        }

        public boolean isRewriteDestinationIP() {
            return rewriteDestinationIP;
        }

        public void setRewriteDestinationIP(boolean rewriteDestinationIP) {
            this.rewriteDestinationIP = rewriteDestinationIP;
        }

        public IpAddress getRwdestinationAddr() {
            return rwdestinationAddr;
        }

        public void setRwdestinationAddr(IpAddress rwdestinationAddr) {
            this.rwdestinationAddr = rwdestinationAddr;
        }

        public boolean isRewriteDestinationMAC() {
            return rewriteDestinationMAC;
        }

        public void setRewriteDestinationMAC(boolean rewriteDestinationMAC) {
            this.rewriteDestinationMAC = rewriteDestinationMAC;
        }

        public MacAddress getRwdestinationl2Addr() {
            return rwdestinationl2Addr;
        }

        public void setRwdestinationl2Addr(MacAddress rwdestinationl2Addr) {
            this.rwdestinationl2Addr = rwdestinationl2Addr;
        }

        public boolean isRewriteDestinationPort() {
            return rewriteDestinationPort;
        }

        public void setRewriteDestinationPort(boolean rewriteDestinationPort) {
            this.rewriteDestinationPort = rewriteDestinationPort;
        }

        public TpPort getRwdestport() {
            return rwdestport;
        }

        public void setRwdestport(TpPort rwdestport) {
            this.rwdestport = rwdestport;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PathIndex)) return false;

            PathIndex pathIndex = (PathIndex) o;

            if (getMatchIpsrc() != pathIndex.getMatchIpsrc()) return false;
            if (getMatchIpDst() != pathIndex.getMatchIpDst()) return false;
            if (isMatchPortSrc() != pathIndex.isMatchPortSrc()) return false;
            if (getSrcport() != pathIndex.getSrcport()) return false;
            if (isMatchPortDst() != pathIndex.isMatchPortDst()) return false;
            if (getDstport() != pathIndex.getDstport()) return false;
            if (isRewriteSourceIP() != pathIndex.isRewriteSourceIP())
                return false;
            if (isRewriteSourceMAC() != pathIndex.isRewriteSourceMAC())
                return false;
            if (isRewriteSourcePort() != pathIndex.isRewriteSourcePort())
                return false;
            if (isRewriteDestinationIP() != pathIndex.isRewriteDestinationIP())
                return false;
            if (isRewriteDestinationMAC() != pathIndex.isRewriteDestinationMAC())
                return false;
            if (isRewriteDestinationPort() != pathIndex.isRewriteDestinationPort())
                return false;
            if (!getNode().equals(pathIndex.getNode())) return false;
            if (!getAppId().equals(pathIndex.getAppId())) return false;
            if (getEthIn() != null ? !getEthIn().equals(pathIndex.getEthIn()) : pathIndex.getEthIn() != null)
                return false;
            if (getIpIn() != null ? !getIpIn().equals(pathIndex.getIpIn()) : pathIndex.getIpIn() != null)
                return false;
            if (getTcpIn() != null ? !getTcpIn().equals(pathIndex.getTcpIn()) : pathIndex.getTcpIn() != null)
                return false;
            if (getSource() != null ? !getSource().equals(pathIndex.getSource()) : pathIndex.getSource() != null)
                return false;
            if (getDestination() != null ? !getDestination().equals(pathIndex.getDestination()) : pathIndex.getDestination() != null)
                return false;
            if (getRwsourceAddr() != null ? !getRwsourceAddr().equals(pathIndex.getRwsourceAddr()) : pathIndex.getRwsourceAddr() != null)
                return false;
            if (getRwsourcel2Addr() != null ? !getRwsourcel2Addr().equals(pathIndex.getRwsourcel2Addr()) : pathIndex.getRwsourcel2Addr() != null)
                return false;
            if (getRwsourceport() != null ? !getRwsourceport().equals(pathIndex.getRwsourceport()) : pathIndex.getRwsourceport() != null)
                return false;
            if (getRwdestinationAddr() != null ? !getRwdestinationAddr().equals(pathIndex.getRwdestinationAddr()) : pathIndex.getRwdestinationAddr() != null)
                return false;
            if (getRwdestinationl2Addr() != null ? !getRwdestinationl2Addr().equals(pathIndex.getRwdestinationl2Addr()) : pathIndex.getRwdestinationl2Addr() != null)
                return false;
            return getRwdestport() != null ? getRwdestport().equals(pathIndex.getRwdestport()) : pathIndex.getRwdestport() == null;
        }

        public String service() {
            return service;
        }

        @Override
        public int hashCode() {
            int result = getNode().hashCode();
            result = 31 * result + getAppId().hashCode();
            result = 31 * result + getMatchIpsrc();
            result = 31 * result + getMatchIpDst();
            result = 31 * result + (isMatchPortSrc() ? 1 : 0);
            result = 31 * result + getSrcport();
            result = 31 * result + (isMatchPortDst() ? 1 : 0);
            result = 31 * result + getDstport();
            result = 31 * result + (getEthIn() != null ? getEthIn().hashCode() : 0);
            result = 31 * result + (getIpIn() != null ? getIpIn().hashCode() : 0);
            result = 31 * result + (getTcpIn() != null ? getTcpIn().hashCode() : 0);
            result = 31 * result + (getSource() != null ? getSource().hashCode() : 0);
            result = 31 * result + (getDestination() != null ? getDestination().hashCode() : 0);
            result = 31 * result + (isRewriteSourceIP() ? 1 : 0);
            result = 31 * result + (getRwsourceAddr() != null ? getRwsourceAddr().hashCode() : 0);
            result = 31 * result + (isRewriteSourceMAC() ? 1 : 0);
            result = 31 * result + (getRwsourcel2Addr() != null ? getRwsourcel2Addr().hashCode() : 0);
            result = 31 * result + (isRewriteSourcePort() ? 1 : 0);
            result = 31 * result + (getRwsourceport() != null ? getRwsourceport().hashCode() : 0);
            result = 31 * result + (isRewriteDestinationIP() ? 1 : 0);
            result = 31 * result + (getRwdestinationAddr() != null ? getRwdestinationAddr().hashCode() : 0);
            result = 31 * result + (isRewriteDestinationMAC() ? 1 : 0);
            result = 31 * result + (getRwdestinationl2Addr() != null ? getRwdestinationl2Addr().hashCode() : 0);
            result = 31 * result + (isRewriteDestinationPort() ? 1 : 0);
            result = 31 * result + (getRwdestport() != null ? getRwdestport().hashCode() : 0);
            return result;
        }
    }

    class InternalIcnFlow {
        TrafficSelector selector;
        TrafficTreatment treatment;

        public InternalIcnFlow(TrafficSelector selector, TrafficTreatment treatment) {
            this.selector = selector;
            this.treatment = treatment;
        }

        public TrafficSelector getSelector() {
            return selector;
        }

        public TrafficTreatment getTreatment() {
            return treatment;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InternalIcnFlow)) return false;

            InternalIcnFlow that = (InternalIcnFlow) o;

            if (!that.getSelector().toString().equals(selector.toString()))
                return false;
            if (!that.getTreatment().toString().equals(treatment.toString()))
                return false;
            return true;

        }

        @Override
        public int hashCode() {
            int result = selector.hashCode();
            result = 31 * result + treatment.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "InternalIcnFlow{" +
                    "selector=" + selector +
                    ", treatment=" + treatment +
                    '}';
        }
    }

}
