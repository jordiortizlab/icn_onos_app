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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.collect.Lists;
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
import org.onosproject.net.intent.ConnectivityIntent;
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentId;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.x509.IPAddressName;


@Component(immediate = true)
@Service
public class CdnService implements
	ICdnService, ICdnPrivateService{

    protected static final Logger log = LoggerFactory.getLogger(CdnService.class);
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
	protected HashMap<String, Cdn> cdns;
	protected HashMap<String, Proxy> proxies;
	protected HashMap<IntentId, Intent> installedIntents;
	
    private CdnPacketProcessor cdnPacketProcessor = new CdnPacketProcessor();

    @Activate
    public void activate() {

        appId = coreService.registerApplication("es.um.app.icn");

        // Initialize our data structures
        cdns = new HashMap<String, Cdn>();
        proxies = new HashMap<String, Proxy>();
        installedIntents = new HashMap<>();
        // Install Processor
        packetService.addProcessor(cdnPacketProcessor, PacketProcessor.director(PROCESSOR_PRIORITY));

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
        packetService.removeProcessor(cdnPacketProcessor);
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
        selectorBuilder.matchTcpDst(TpPort.tpPort(UtilCdn.HTTP_PORT));
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
        selectorBuilder.matchTcpDst(TpPort.tpPort(UtilCdn.HTTP_PORT));
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

    private class CdnPacketProcessor implements PacketProcessor {

        @Override
        /**
         * If the payload is of interest to any of our CDNs, then let's decide the
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
                if ( tcpPkt.getDestinationPort() != UtilCdn.HTTP_PORT) {
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
            CdnFlow flow = new CdnFlow();
            flow.setSmac(ethPkt.getSourceMAC().toString());
            flow.setDmac(ethPkt.getDestinationMAC().toString());
            // TODO: Check if this restriction should be used
            if (findProxy(flow.getSmac()) != null || findCache(flow.getDmac()) != null) {
                log.info("Ignoring device {}: Not a client", flow.getSmac());
                log.debug("ETH: {} IP: {} TCP: {}",ethPkt, ipv4Pkt, tcpPkt);
                return;
            }

            // Nothing to do if we don't have any CDN or proxy
            if (cdns.isEmpty() || proxies.isEmpty()) {
                log.error("Ignoring flow: No available CDNs and/or proxies");
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
            boolean toproxy = CdnService.createPath(appId, pathService, flowObjectiveService,
                    ipv4Pkt.getSourceAddress(), ipv4Pkt.getDestinationAddress(),
                    false, (short)0,true, UtilCdn.HTTP_PORT,
                    ethPkt, ipv4Pkt, tcpPkt,
                    sourceConnectPoint, destinationConnectPoint, false,
                    null, false, null, false, null,
                    true, outaddress, true, outl2address, false, null);
            log.info("Path created toproxy {}", toproxy);
            // Create return intent
            boolean fromproxy = CdnService.createPath(appId, pathService, flowObjectiveService,
                    outaddress.getIp4Address().toInt(), ipv4Pkt.getSourceAddress(),
                    true, UtilCdn.HTTP_PORT,false, (short) 0,
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
	public void processResourceRequest(ProxyRequest req) {
		log.info("Process resource request from proxy {} for host {} and flow {}",
				new Object[] {req.proxy, req.hostname, req.flow.toString()} );
		
		// Get proxy's attachment points
        Host proxy = null;
        Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(req.getProxy()));
        if (hostsByMac.size() != 1) {
            log.warn("Unexpected number of hosts for the same mac {} : {}", req.getProxy(), hostsByMac.size());
        }
        proxy = hostsByMac.iterator().next();
        Collection<HostLocation> proxylocations = new LinkedList<>();
        for (Host host : hostsByMac) {
            proxylocations.add(host.location());
        }
        if (proxylocations.size() == 0)
        {
            log.warn("No attachment point for proxy {}", req.getProxy());
            return;
        }

		// Get the providers related to this request (if any)
		Collection<Provider> providers =
			findProvidersFromAddress(IPv4.toIPv4Address(req.flow.daddr));
		if (providers.isEmpty()) {
			log.warn("No provider for proxy request: proxy {} hostname {}",
					req.proxy, req.hostname);
			return;
		}
		
		for (Cdn cdn: cdns.values()) {
			for (Provider provider: cdn.retrieveProviders()) {
				// This is a candidate provider if the destination
				// address belong to its network
				if (providers.contains(provider)) {
					// By default the resource name is the URI
					// after stripping the parameters
					String resourceName = req.uri;
					int idx = resourceName.indexOf('?'); 
					if (idx > 0)
						resourceName = resourceName.substring(0, idx);
					
					// This is the appropriate provider if no patterns
					// are provided or both match
					boolean match = true;
					if (provider.uripattern != null) {
						Pattern pattern = Pattern.compile(provider.uripattern);
						Matcher matcher = pattern.matcher(req.uri);
						if (matcher.find())
							resourceName = matcher.group(1);
						else
							match = false;
					}
					if (match && provider.hostpattern != null) {
						Pattern pattern = Pattern.compile(provider.hostpattern);
						Matcher matcher = pattern.matcher(req.hostname);
						if (!matcher.find())
							match = false;
					}
					
					if (match) {
						Resource resource = cdn.retrieveResource(resourceName);
						Cache cache = null;
                        HostLocation location = null;
                        DeviceId deviceId = null;
                        PortNumber portNumber = null;
						boolean foundCache = false;
						if (resource == null) {
							// Resource requested for the first time, find a
							// new cache for the content
							resource = new Resource();
							resource.id = UtilCdn.resourceId(cdn.name, resourceName);
							resource.name = resourceName;
							resource.requests = 1;
                            for (HostLocation proxylocation : proxylocations) {
                                location = proxylocation;
                                deviceId= proxylocation.deviceId();

						        cache = cdn.findCacheForNewResource(this,
						        		resourceName, deviceId, portNumber);
								if (cache == null) {
									log.warn("No cache in CDN {} for new resource {}",
											cdn.name, resourceName);
									continue;
								} else if (cache.macaddr == null) {
									log.warn("No MAC address for cache {}",
											cache.name);
									continue;
								}
								resource.addCache(cache);
								cdn.createResource(resource);
								log.info("New resource {} in CDN {} to cache {}",
										new Object[] {resourceName, cdn.name, cache.name});
								foundCache = true;
								break;
							}
						} else {
							// Existing resource, find the closest cache that
							// hosts the content
							resource.requests += 1;
                            for (HostLocation proxylocation : proxylocations) {
                                location = proxylocation;
                                deviceId = proxylocation.deviceId();
                                portNumber = proxylocation.port();
						        
						        cache = cdn.findCacheForExistingResource(this,
						        		resourceName, deviceId, portNumber);
						        if (cache == null) {
						        	log.warn("No cache in CDN {} for existing resource {}",
											cdn.name, resourceName);
						        	continue;
						        } else if (cache.macaddr == null) {
						        	log.warn("No MAC address for cache {}",
						        			cache.name);
						        	continue;
						        }
						        log.info("Existing resource {} in CDN {} to cache {}",
										new Object[] {resourceName, cdn.name, cache.name});
						        foundCache = true;
						        break;
							}
						}
						// Establish path to cache
						if (foundCache) {
							log.info("Program path to cache {} flow {}",
									cache.name, req.flow.toString());
                            if (programPath(req.flow, proxy, location, cache)) {
                                log.info("Created path from proxy to cache");
                            } else {
                                log.error("Unable to create path from proxy to cache");
                            }
                        }
						return;
					}
				}
			}
		}
	}
	
	/**
	 * Compute route from an input switch to a middlebox's switch, and issue
	 * corresponding FlowMod messages for the given flow.
	 * @param origin Input location
	 * @param mbox Middlebox where the flow is directed.
	 * @return Ouput port that must be used by the input switch.
	 */
	private boolean programPath(CdnFlow originalreq, Host proxy, HostLocation origin, IMiddlebox mbox) {
        log.debug("Creating connection for middlebox {} <-> {}", origin.toString(), mbox.getLocation().toString());
        log.debug("Original req: {}", originalreq);
        Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(mbox.getMacaddr()));
        if (hostsByMac.size() != 1)
        {
            log.debug("hostsByMac: {}", hostsByMac);
            if (hostsByMac.size() > 1) {
                log.error("Unexpected number of hosts for the same mac {}. Too many hosts", mbox.getMacaddr());
            } else {
                log.error("Unexpected number of hosts for the same mac {}. NO HOST", mbox.getMacaddr());
            }
            return false;
        }
        Host mboxhost = null;
        for (Host host : hostsByMac) {
            mboxhost = host;
        }

        log.debug("REST Request:  creating paths");
        log.debug("origin: {}, mbox {}", origin.toString(), mbox.toString());
        //TODO: What about ipv6??
        int sourceprefix = proxy.ipAddresses().iterator().next().getIp4Address().toInt();
        int destprefix = Ip4Address.valueOf(originalreq.daddr).toInt();
        IpAddress cacheprefix = Ip4Address.valueOf(mbox.getIpaddr());
        log.debug("prefix origin (proxy) {} destination (provider) {}", sourceprefix, destprefix);
        createPath(appId, pathService, flowObjectiveService,
                sourceprefix,
                destprefix,
                false, (short) 0, true, UtilCdn.HTTP_PORT,
                null, null, null,
                new ConnectPoint(origin.deviceId(), origin.port()),
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                false, null,
false,null,
                false, null,
                true, cacheprefix,
true, MacAddress.valueOf(mbox.getMacaddr()),
                true, TpPort.tpPort(3128)); //TODO: Add port to cache definition in json

        createPath(appId, pathService, flowObjectiveService,
                destprefix,
                sourceprefix,
                true, (short) 3128, false, (short) 0,
                null, null, null,
                new ConnectPoint(DeviceId.deviceId(mbox.getLocation().getDpid()), PortNumber.portNumber(mbox.getLocation().getPort())),
                new ConnectPoint(origin.deviceId(), origin.port()),
                true, cacheprefix,
                true, MacAddress.valueOf(originalreq.getDmac()),
                true, TpPort.tpPort(UtilCdn.HTTP_PORT),
                false, null,
                false, null,
                false, null);

        return true;
	}
	
	private CdnFlow reverseFlow(CdnFlow flow) {
		CdnFlow reverseFlow = new CdnFlow();
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
		for (Cdn c : cdns.values()) {
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
		for (Cdn cdn: cdns.values()) {
			for (Cache cache: cdn.caches.values()) {
				if (macaddr.equalsIgnoreCase(cache.macaddr))
					return cache;
			}
		}
		return null;
	}
	
	@Override
	public Collection<Cdn> retrieveCdns() {
		return cdns.values();
	}
	
	@Override
	public Cdn retrieveCdn(String name) {
		return cdns.get(name);
	}
		
	@Override
	public Cdn createCdn(Cdn cdn) {
		cdns.put(cdn.name, cdn);
		return cdn;
	}
	
	@Override
	public Cdn updateCdn(Cdn cdn) {
		cdns.put(cdn.name, cdn);
		return cdn;
	}
	
	@Override
	public Cdn removeCdn(String name) {
		return cdns.remove(name);
	}
	
	@Override
	public Collection<Provider> retrieveProviders(Cdn cdn) {
		return cdn.retrieveProviders();
	}
	
	@Override
	public Provider retrieveProvider(Cdn cdn, String name) {
		return cdn.retrieveProvider(name);
	}
		
	@Override
	public Provider createProvider(Cdn cdn, Provider provider) {
		return cdn.createProvider(provider);
	}
	
	@Override
	public Provider updateProvider(Cdn cdn, Provider provider) {
		return cdn.updateProvider(provider);
	}
	
	@Override
	public Provider removeProvider(Cdn cdn, String name) {
		return cdn.removeProvider(name);
	}
	
	@Override
	public Collection<Cache> retrieveCaches(Cdn cdn) {
		return cdn.retrieveCaches();
	}
	
	@Override
	public Cache retrieveCache(Cdn cdn, String name) {
		return cdn.retrieveCache(name);
	}
	
	@Override
	public Cache createCache(Cdn cdn, Cache cache) {
		return cdn.createCache(cache);
	}
	
	@Override
	public Cache updateCache(Cdn cdn, Cache cache) {
		return cdn.updateCache(cache);
	}
	
	@Override
	public Cache removeCache(Cdn cdn, String name) {
		return cdn.removeCache(name);
	}
	
	@Override
	public Collection<Resource> retrieveResources(Cdn cdn) {
		return cdn.retrieveResources();
	}
	
	@Override
	public Resource retrieveResource(Cdn cdn, String id) {
		return cdn.retrieveResource(id);
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
