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
import org.onosproject.net.intent.HostToHostIntent;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.packet.*;
import org.onosproject.net.topology.PathService;
import org.onosproject.net.topology.Topology;
import org.onosproject.net.topology.TopologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



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
	
    private CdnPacketProcessor cdnPacketProcessor = new CdnPacketProcessor();

    @Activate
    public void activate() {

        appId = coreService.registerApplication("es.um.app.icn");

        // Initialize our data structures
        cdns = new HashMap<String, Cdn>();
        proxies = new HashMap<String, Proxy>();
        // Install Processor
        packetService.addProcessor(cdnPacketProcessor, PacketProcessor.director(PROCESSOR_PRIORITY));

        requestPackets();

    }

    @Deactivate
    public void deactivate() {
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
        packetService.requestPackets(selectorBuilder.build(), PacketPriority.CONTROL, appId);
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
        packetService.cancelPackets(selectorBuilder.build(), PacketPriority.CONTROL, appId);
        // TODO: Missing IPv6
    }

    private class CdnPacketProcessor implements PacketProcessor {

        @Override
        /**
         * If the payload is of interest to any of our CDNs, then let's decide the
         * destination proxy and program the appropriate paths.
         */
        public void process(PacketContext context) {
            // Stop processing if the packet has been handled, since we
            // can't do any more to it.
            if (context.isHandled()) {
                return;
            }

            // Only continue processing if HTTP traffic is received
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (!(ethPkt.getEtherType() == Ethernet.TYPE_IPV4) && !(ethPkt.getEtherType() == Ethernet.TYPE_IPV6)) {
                log.trace("Packet is not IPv4 neither v6, ignoring");
                return;
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV4) {
                IPv4 ipv4Pkt = (IPv4) ethPkt.getPayload();
                if (ipv4Pkt.getProtocol() != IPv4.PROTOCOL_TCP) {
                    log.trace("IPv4 Packet is not TCP, ignoring");
                    return;
                }
                TCP tcpPkt = (TCP) ipv4Pkt.getPayload();
                if ( tcpPkt.getDestinationPort() != UtilCdn.HTTP_PORT) {
                    log.trace("IPv4 Packet is not HTTP, ignoring");
                    return;
                }
            }
            if (ethPkt.getEtherType() == Ethernet.TYPE_IPV6) {
                //TODO: Missing IPv6
                return;
            }

            log.debug("ICN Process PACKET_IN from switch {}", context.inPacket().receivedFrom().toString());
            DeviceId indeviceId = context.inPacket().receivedFrom().deviceId();
            PortNumber inport = context.inPacket().receivedFrom().port();

            DeviceId outdeviceId = null;
            PortNumber outport = null;


            if (ethPkt == null) {
                return;
            }

            HostId srcId = HostId.hostId(ethPkt.getSourceMAC());
            HostId dstId = HostId.hostId(ethPkt.getDestinationMAC());

            // Check that src is a registered client
            CdnFlow flow = new CdnFlow();
            flow.setSmac(ethPkt.getSourceMAC().toString());
            flow.setDmac(ethPkt.getDestinationMAC().toString());
            if (findProxy(flow.getSmac()) != null || findCache(flow.getDmac()) != null) {
                log.info("Ignoring device {}: Not a client", flow.getSmac());
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
                    break;
                }
            } else {
                // If the location was set in the json there is no need to look up for the host
                outdeviceId = DeviceId.deviceId(proxy.getLocation().dpid);
                outport = PortNumber.portNumber(proxy.getLocation().port);
            }

            // Using Intent for path creation
            ConnectPoint sourceConnectPoint = new ConnectPoint(indeviceId, inport);
            ConnectPoint destinationConnectPoint = new ConnectPoint(outdeviceId, outport);
            // Create intent from host to proxy
            Intent toproxy = this.createIntent(sourceConnectPoint, destinationConnectPoint);
            // Create return intent
            Intent fromproxy = this.createIntent(destinationConnectPoint, sourceConnectPoint);

            // Take care of actual package
            // TODO: What happens if multiple paths available, intents could use different path. We might want to ask the intent for its decission.
            Set<Path> paths = pathService.getPaths(indeviceId, outdeviceId);
            Link sourcelink = null;
            for (Path path : paths) {
                sourcelink = path.links().get(0);
            }
            // TODO: Do we need to create a flowObjective?

            TrafficTreatment.Builder builder =
                    DefaultTrafficTreatment.builder();
            // Lets try just to output the packet on the toproxy intent, so
            // make it a new packet on the indeviceid:inport
            builder.setOutput(inport);
            packetService.emit(new DefaultOutboundPacket(
                    indeviceId,
                    builder.build(),
                    ByteBuffer.wrap(ethPkt.serialize())
            ));
//            builder.setOutput(sourcelink.src().port());
//            packetService.emit(new DefaultOutboundPacket(
//                    sourcelink.src().deviceId(),
//                    builder.build(),
//                    ByteBuffer.wrap(ethPkt.serialize())));

        }

        private Intent createIntent(ConnectPoint source, ConnectPoint destination)
        {
            log.trace("Creating Host2HostIntent to Proxy {}", source.toString() + "->" + destination.toString());
            Key key = Key.of(source.toString() + "->" + destination.toString(), appId);
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthType(Ethernet.TYPE_IPV4)
                    .matchIPProtocol(IPv4.PROTOCOL_TCP)
                    .matchTcpDst(TpPort.tpPort(UtilCdn.HTTP_PORT))
                    .build();
            TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

            PointToPointIntent pointIntent = PointToPointIntent.builder()
                    .appId(appId)
                    .key(key)
                    .filteredIngressPoint(new FilteredConnectPoint(source))
                    .filteredEgressPoint(new FilteredConnectPoint(destination))
                    .selector(selector)
                    .treatment(treatment)
                    .priority(INTENT_PRIORITY_HIGH)
                    .build();
            intentService.submit(pointIntent);
            return pointIntent;

        }

    }
	
	/**
	 * Program required flows when a proxy informs that a resource is being
	 * requested.
	 * @param req
	 */
	public void processResourceRequest(ProxyRequest req) {
		log.debug("Process resource request from proxy {} for host {} and flow {}",
				new Object[] {req.proxy, req.hostname, req.flow.toString()} );
		
		// Get proxy's attachment points
        Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(req.getProxy()));
        if (hostsByMac.size() != 1)
        {
            log.warn("Unexpected number of hosts for the same mac {} : {}", req.getProxy(), hostsByMac.size());
        }
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
                            HostToHostIntent hostToHostIntent = programPath(location, cache);
                            log.info("Created intent {}", hostToHostIntent.toString());
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
	private HostToHostIntent programPath(HostLocation origin, IMiddlebox mbox) {
        log.trace("Creating Host2HostIntent for middlebox {} -> {}", origin.toString(), mbox.getLocation().toString());
        Key key = Key.of(origin.toString() + "->" + mbox.toString(), appId);
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(Ethernet.TYPE_IPV4)
                .matchIPProtocol(IPv4.PROTOCOL_TCP)
                .matchTcpDst(TpPort.tpPort(UtilCdn.HTTP_PORT))
                .build();
        TrafficTreatment treatment = DefaultTrafficTreatment.emptyTreatment();

        Set<Host> hostsByMac = hostService.getHostsByMac(MacAddress.valueOf(mbox.getMacaddr()));
        if (hostsByMac.size() != 1)
        {
            log.error("Unexpected number of hosts for the same mac {}", mbox.getMacaddr());
            return null;
        }
        Host mboxhost = null;
        for (Host host : hostsByMac) {
            mboxhost = host;
        }



        HostToHostIntent hostIntent = HostToHostIntent.builder()
                .appId(appId)
                .key(key)
                .one(origin.hostId())
                .two(mboxhost.id())
                .selector(selector)
                .treatment(treatment)
                .priority(INTENT_PRIORITY_HIGH)
                .build();
        intentService.submit(hostIntent);
        return hostIntent;
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
            log.info("Paths in topology: {}", topologyService.getPaths(topology, sw, DeviceId.deviceId(mboxDeviceId)));

            Set<Path> paths = topologyService.getPaths(topologyService.currentTopology(), sw, DeviceId.deviceId(mboxDeviceId));
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
