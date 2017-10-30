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
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flowobjective.DefaultForwardingObjective;
import org.onosproject.net.flowobjective.FlowObjectiveService;
import org.onosproject.net.flowobjective.ForwardingObjective;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentId;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
    protected static final int DEFAULT_FLOW_TIMEOUT = 100;

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


    /** APPId */
    private ApplicationId appId;

	/** We need to register with the provider to receive OF messages */
	protected HashMap<String, Icn> icns;
	protected HashMap<String, Proxy> proxies;
	protected HashMap<IntentId, Intent> installedIntents;
	
    private IcnPacketProcessor icnPacketProcessor = new IcnPacketProcessor();

    @Activate
    public void activate() {

        appId = coreService.registerApplication("es.um.app.icn");

        // Initialize our data structures
        icns = new HashMap<String, Icn>();
        proxies = new HashMap<String, Proxy>();
        installedIntents = new HashMap<>();
        // Install Processor
        packetService.addProcessor(icnPacketProcessor, PacketProcessor.director(PROCESSOR_PRIORITY));

        requestPackets();

    }

    @Deactivate
    public void deactivate() {
        installedIntents.forEach( (k,v) -> {
            log.info("Withdrawing icn intent: {}", v.toString());
            intentService.withdraw(v);
        });
        installedIntents.clear();
        withdrawIntercepts();
        packetService.removeProcessor(icnPacketProcessor);
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

    protected static boolean createPath(ApplicationId appId, PathService pathService, FlowObjectiveService flowObjectiveService,
                                        int matchIpsrc, int matchIpDst, boolean matchPortSrc, short srcport, boolean matchPortDst, short dstport,
                                        Ethernet ethIn, IPv4 ipIn, TCP tcpIn,
                                        ConnectPoint source, ConnectPoint destination,
                                        boolean rewriteSourceIP, IpAddress rwsourceAddr,
                                        boolean rewriteSourceMAC, MacAddress rwsourcel2Addr,
                                        boolean rewriteSourcePort, TpPort rwsourceport,
                                        boolean rewriteDestinationIP, IpAddress rwdestinationAddr,
                                        boolean rewriteDestinationMAC, MacAddress rwdestinationl2Addr,
                                        boolean rewriteDestinationPort, TpPort rwdestport) {
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
            Set<Path> paths = pathService.getPaths(source.elementId(), destination.elementId());
            if (paths.isEmpty()) {
                log.error("Unable to locate any path");
                return false;
            }


            Iterator<Path> iterator = paths.iterator();
            if (iterator.hasNext()) {
                Path path = iterator.next(); // Get one path
                for (Link link : path.links()) {
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
                            .makeTemporary(DEFAULT_FLOW_TIMEOUT)
                            .fromApp(appId)
                            .withFlag(ForwardingObjective.Flag.SPECIFIC);
                    flowObjectiveService.forward(link.src().deviceId(), fobuilder.add());
                    log.debug("Preparing path: {} {} {}", link.src().deviceId(), selector, treatment);
                    sourceport = link.dst().port();
                }
            }
            // Now we need to treat last jump
            if (source.deviceId().equals(destination.deviceId())) {
                log.debug("Same device");
                sourceport = source.port();
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
                    .makeTemporary(DEFAULT_FLOW_TIMEOUT)
                    .fromApp(appId)
                    .withFlag(ForwardingObjective.Flag.SPECIFIC);
            flowObjectiveService.forward(destination.deviceId(), fobuilder.add());
            log.debug("Preparing final jump: {} {}", selector, treatment);
        }

        return true;
    }

    private class IcnPacketProcessor implements PacketProcessor {

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
            boolean toproxy = IcnService.createPath(appId, pathService, flowObjectiveService,
                    ipv4Pkt.getSourceAddress(), ipv4Pkt.getDestinationAddress(),
                    false, (short)0,true, UtilIcn.HTTP_PORT,
                    ethPkt, ipv4Pkt, tcpPkt,
                    sourceConnectPoint, destinationConnectPoint, false,
                    null, false, null, false, null,
                    true, outaddress, true, outl2address, false, null);
            log.info("Path created toproxy {}", toproxy);
            // Create return intent
            boolean fromproxy = IcnService.createPath(appId, pathService, flowObjectiveService,
                    outaddress.getIp4Address().toInt(), ipv4Pkt.getSourceAddress(),
                    true, UtilIcn.HTTP_PORT,false, (short) 0,
                    ethPkt, ipv4Pkt, tcpPkt,
                    destinationConnectPoint, sourceConnectPoint, true,
                    dstAddr, false, dstl2Addr, false, null,
                    false, null, false, null, false, null);
            log.info("Path created fromproxy {}", fromproxy);
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
        for (Provider provider : providers) {
            log.info("Checking provider: {}", provider.getName());
            String uri = provider.matchUriPattern(req.uri);

            Optional<Icn> icnfirst = icns.values().stream().filter(x -> {
                Cache c = null;
                if (!x.retrieveProviders().contains(provider))
                    return false;
                if (provider.matchUriPattern(req.uri) == null)
                    return false;
                if (provider.matchHostPattern(req.getHostname()) == null)
                    return false;
                if ((c = x.findCacheForNewResource(this,
                        uri, DeviceId.deviceId(p.getLocation().getDpid()),
                        PortNumber.portNumber(p.getLocation().getPort()))) == null) {
                    log.warn("No cache in ICN {} for new resourceHTTP {}",
                            x.getName(), uri);
                    return false;
                }
                if (c.getMacaddr() == null) {
                    log.warn("No MAC address for cache {}",
                            c.name);
                    return false;
                }
                return true;
            }).findFirst();
            if (!icnfirst.isPresent()) {
                log.error("No ICN found ");
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
                // TODO: Increment resourceHTTP requests
                req.flow.setDmac(c.macaddr);
            } else {
                // New resourceHTTP
                c =  icn.findCacheForNewResource(this,
                        uri, DeviceId.deviceId(p.getLocation().getDpid()),
                        PortNumber.portNumber(p.getLocation().getPort()));

                ResourceHTTP res = new ResourceHTTP();
                res.setId(UtilIcn.resourceId(icn.getName(), uri));
                res.setName(uri);
                res.setRequests(1);
                res.addCache(c);
                res.setFullurl("http://" + req.getHostname() + "/" + req.getUri()); // TODO: Make this more dynamic
                icn.createResource(res, p);
                log.info("New resourceHTTP {} in ICN {} to cache {}",
                        res, icn.getName(), c.name);
                req.flow.setDmac(c.macaddr);
            }
            log.info("Program path to cache {} flow {}",
                    c.name, req.flow.toString());
            if (programPath(req.flow, p, p.getLocation(), c)) {
                log.info("Created path from proxy to cache");
                return true;
            } else {
                log.error("Unable to create path from proxy to cache");
            }
        }
        return false;
    }

    /**
     * Compute route from an input switch to a middlebox's switch, and issue
     * corresponding FlowMod messages for the given flow.
     * @param origin Input location
     * @param mbox Middlebox where the flow is directed.
     * @return Ouput port that must be used by the input switch.
     */
    private boolean programPath(IcnFlow originalreq, IMiddlebox proxy, Location origin, IMiddlebox mbox) {
        log.info("Creating connection for middlebox {} <-> {}", origin.toString(), mbox.getLocation().toString());
        log.debug("Original req: {}", originalreq);
        log.debug("REST Request:  creating paths");
        log.debug("origin: {}, mbox {}", origin.toString(), mbox.toString());
        //TODO: What about ipv6??
        int sourceprefix = Ip4Address.valueOf(proxy.getIpaddr()).toInt();
        int destprefix = Ip4Address.valueOf(originalreq.daddr).toInt();
        int cacheprefix = Ip4Address.valueOf(mbox.getIpaddr()).toInt();
        IpAddress ipcacheprefix = Ip4Address.valueOf(mbox.getIpaddr());
        IpAddress ipdestprefix = Ip4Address.valueOf(originalreq.daddr);
        log.debug("prefix origin (proxy) {} destination (provider) {}", sourceprefix, destprefix);
        createPath(appId, pathService, flowObjectiveService,
                sourceprefix,
                destprefix,
                false, (short) 0, true, UtilIcn.HTTP_PORT,
                null, null, null,
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                false, null,
false,null,
                false, null,
                true, ipcacheprefix,
true, MacAddress.valueOf(mbox.getMacaddr()),
                true, TpPort.tpPort(3128)); //TODO: Add port to cache definition in json

        createPath(appId, pathService, flowObjectiveService,
                cacheprefix,
                sourceprefix,
                true, (short) 3128, false, (short) 0,
                null, null, null,
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                new ConnectPoint(DeviceId.deviceId(origin.getDpid()), PortNumber.portNumber(origin.getPort())),
                true, ipdestprefix,
                true, MacAddress.valueOf(originalreq.getDmac()),
                true, TpPort.tpPort(UtilIcn.HTTP_PORT),
                false, null,
                false, null,
                false, null);

        return true;
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
	
}
