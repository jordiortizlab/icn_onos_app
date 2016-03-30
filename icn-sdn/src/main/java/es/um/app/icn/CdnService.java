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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import es.um.app.icn.Cdn;
import org.openflow.protocol.OFFlowMod;
import org.openflow.protocol.OFMatch;
import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFPacketIn.OFPacketInReason;
import org.openflow.protocol.OFPacketOut;
import org.openflow.protocol.OFPort;
import org.openflow.protocol.OFType;
import org.openflow.protocol.action.OFAction;
import org.openflow.protocol.action.OFActionOutput;
import org.openflow.util.HexString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.counter.ICounterStoreService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.SwitchPort;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Route;
import net.floodlightcontroller.staticflowentry.StaticFlowEntries;
import net.floodlightcontroller.topology.NodePortTuple;
import net.floodlightcontroller.util.MACAddress;
import net.floodlightcontroller.util.OFMessageDamper;


public class CdnService implements
	IFloodlightModule, IOFMessageListener, net.floodlightcontroller.cdn.ICdnService, ICdnPrivateService {

	/** We need to register with the provider to receive OF messages */
	protected IFloodlightProviderService ofProvider;
	protected IRestApiService restApi;
	protected IDeviceService deviceManager;
	protected IRoutingService routingEngine;
	protected ICounterStoreService counterStore;
	protected OFMessageDamper messageDamper;
	protected HashMap<String, Cdn> cdns;
	protected HashMap<String, Proxy> proxies;
	
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
    
	@Override
	public String getName() {
		return CdnService.class.getSimpleName();
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		// Let the module loader know that we provide CDN services
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
	    l.add(ICdnService.class);
	    l.add(ICdnPrivateService.class);
	    return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		// Let the module loader know that this is the class that implements
		// CDN services
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
	    m.put(ICdnService.class, this);
	    m.put(ICdnPrivateService.class, this);
	    return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// Let the module loader know that we depend on the Floodlight provider
		// and the REST API service
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IDeviceService.class);
		l.add(IRoutingService.class);
		l.add(ICounterStoreService.class);
		return l;
	}
	
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// We probably want to be called AFTER these modules
		// (same case as in the 'loadbalancer' module)
		return (type.equals(OFType.PACKET_IN) && 
				(name.equals("topology") ||
				 name.equals("devicemanager") ||
				 name.equals("virtualizer")));
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// We probably want to be called BEFORE these modules
		return (type.equals(OFType.PACKET_IN) &&
				(name.equals("forwarding") ||
				 name.equals("loadbalancer")));
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// Get required references
		ofProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApi = context.getServiceImpl(IRestApiService.class);
		deviceManager = context.getServiceImpl(IDeviceService.class);
		routingEngine = context.getServiceImpl(IRoutingService.class);
		counterStore = context.getServiceImpl(ICounterStoreService.class);
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
				EnumSet.of(OFType.FLOW_MOD),
				OFMESSAGE_DAMPER_TIMEOUT);
		// Initialize our data structures
		cdns = new HashMap<String, Cdn>();
		proxies = new HashMap<String, Proxy>();
	}

	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// We want to receive PACKET_IN messages and register our REST API
		ofProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApi.addRestletRoutable(new CdnServiceRestRoutable());
		restApi.addRestletRoutable(new CdnPrivateServiceRestRoutable());
	}
	
	@Override
	public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext ctx) {
		switch (msg.getType()) {
			case PACKET_IN:
				return processPacketIn(sw, (OFPacketIn) msg, ctx);
			default:
				break;
        }
		log.warn("Received unexpected message {}", msg);
		return Command.CONTINUE;
	}

	/**
	 * Process PACKET_IN.
	 * 
	 * If the payload is of interest to any of our CDNs, then let's decide the
	 * destination proxy and program the appropriate paths.
	 *  
	 * @param sw
	 * @param pktIn
	 * @param ctx
	 * @return STOP if handled by this module, CONTINUE otherwise.
	 */
	private Command processPacketIn(IOFSwitch sw, OFPacketIn pktIn, FloodlightContext ctx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(
				ctx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		log.debug("Process PACKET_IN from switch {}", sw.getStringId());
		CdnFlow flow = new CdnFlow();
		flow.smac = eth.getSourceMAC().toString();
		flow.dmac = eth.getDestinationMAC().toString();
		
		// Only handle PACKET_IN generated by a client
		if (findProxy(flow.smac) != null || findCache(flow.smac) != null) {
			log.debug("Ignoring device {}: Not a client", flow.smac);
			return Command.CONTINUE;
		}
		
		// Given that this is a client-generated frame, let's add a flow
		// with intermediate priority to punt HTTP traffic to the controller
		// (in case it doesn't exist yet)
		if (pktIn.getReason() == OFPacketInReason.NO_MATCH) {
			CdnFlow puntFlow = new CdnFlow();
			puntFlow.smac = flow.smac;
			puntFlow.dltype = IPV4_ETHERTYPE;
			puntFlow.proto = Byte.toString(UtilCdn.IPPROTO_TCP);
			puntFlow.dport = Short.toString(UtilCdn.HTTP_PORT);
			log.debug("Program punt flow {} at switch {}",
					puntFlow.toString(), sw.getStringId());
			pushFlowMod(sw, puntFlow, pktIn.getInPort(), OFPort.OFPP_CONTROLLER.getValue(),
					null, PUNT_OFPRIO, PUNT_OFIDLE_TIMEOUT, PUNT_OFHARD_TIMEOUT, 
					ctx);
		}
		
		// Nothing to do if we don't have any CDN or proxy
		if (cdns.isEmpty() || proxies.isEmpty()) {
			log.debug("Ignoring flow: No available CDNs and/or proxies");
			return Command.CONTINUE;
		}
		
		// Let other modules handle broadcast and multicast traffic and we
		// focus on HTTP over IPv4 (TODO: IPv6 support)
		IPacket pkt = eth.getPayload();
		if (eth.isBroadcast() || eth.isMulticast()) {
			log.debug("Ignoring broadcast/multicast traffic");
			return Command.CONTINUE;
		}
		if (!(pkt instanceof IPv4)) {
			log.debug("Ignoring non IPv4 traffic");
			return Command.CONTINUE;
		}
		
		IPv4 ipPkt = (IPv4) pkt;
		flow.dltype = IPV4_ETHERTYPE;
		flow.saddr = IPv4.fromIPv4Address(ipPkt.getSourceAddress());
		flow.daddr = IPv4.fromIPv4Address(ipPkt.getDestinationAddress());
		
		// Check if the destination address belongs to a content provider's CDN
		if (findProvidersFromAddress(ipPkt.getDestinationAddress()).isEmpty()) {
			// For demo purposes, the provider might be already using a CDN
			// (e.g. youtube, dailymotion). So the dst address might belong
			// to a completely different network address. As a workaround,
			// use the default network "0.0.0.0/0".
			log.debug("Ignoring dst {}: Not a provider", flow.daddr);
			return Command.CONTINUE;
		}
		
		// Process HTTP traffic
		flow.proto = Byte.toString(ipPkt.getProtocol());
		IPacket payload = ipPkt.getPayload();
		if (!(payload instanceof TCP)) {
			log.debug("Ignoring non TCP traffic");
			return Command.CONTINUE;
		}
		
		TCP tcpPkt = (TCP) payload;
		flow.dport = Short.toString(tcpPkt.getDestinationPort());
		if (UtilCdn.HTTP_PORT != Short.valueOf(flow.dport)) {
			log.debug("Ignoring non HTTP traffic");
			return Command.CONTINUE;
		}
		
		// Program path between client and closest proxy for HTTP traffic
		Proxy proxy = (Proxy) findClosestMiddlebox(proxies.values(),
				sw, pktIn.getInPort());
		if (proxy == null) {
			log.warn("Could not program path to proxy: No proxy available");
			return Command.CONTINUE;
		}
		
		short outPort = programPath(sw, flow, pktIn.getInPort(), proxy, ctx);
		if (outPort != OFPort.OFPP_NONE.getValue()) {
			log.info("Program path to proxy {} flow {}",
					proxy.name, flow.toString());
			pushPacketOut(sw, pkt, pktIn.getBufferId(),
					pktIn.getInPort(), outPort, ctx);
			return Command.STOP;
		}
		log.warn("Could not program path to proxy {} flow {}",
				proxy.name, flow.toString());
		return Command.CONTINUE;
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
		Collection<SwitchPort> attachPoints = getAttachmentPoints(req.proxy);
		if (attachPoints.isEmpty()) {
			log.warn("No attachment point for proxy {}", req.proxy);
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
						IOFSwitch proxySwitch = null;
						short proxyPort = 0;
						boolean foundCache = false;
						if (resource == null) {
							// Resource requested for the first time, find a
							// new cache for the content
							resource = new Resource();
							resource.id = UtilCdn.resourceId(cdn.name, resourceName);
							resource.name = resourceName;
							resource.requests = 1;
							for (SwitchPort attachPoint : attachPoints) {
								long proxyDpid = attachPoint.getSwitchDPID();
						        proxySwitch = ofProvider.getSwitch(proxyDpid);
						        proxyPort = (short) attachPoint.getPort();
						        
						        cache = cdn.findCacheForNewResource(this,
						        		resourceName, proxySwitch, proxyPort);
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
							for (SwitchPort attachPoint : attachPoints) {
								long proxyDpid = attachPoint.getSwitchDPID();
						        proxySwitch = ofProvider.getSwitch(proxyDpid);
						        proxyPort = (short) attachPoint.getPort();
						        
						        cache = cdn.findCacheForExistingResource(this,
						        		resourceName, proxySwitch, proxyPort);
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
							programPath(proxySwitch, req.flow, proxyPort, cache,
									new FloodlightContext());
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
	 * @param sw Input switch.
	 * @param inPort Input port.
	 * @param flow Flow to program.
	 * @param mbox Middlebox where the flow is directed.
	 * @param ctx Floodlight context.
	 * @return Ouput port that must be used by the input switch.
	 */
	private short programPath(IOFSwitch sw, CdnFlow flow, short inPort, IMiddlebox mbox,
			FloodlightContext ctx) {
		long dpid = sw.getId();
		
		Collection<SwitchPort> attachPoints = getAttachmentPoints(mbox.getMacaddr());
		if (attachPoints.isEmpty())
			return OFPort.OFPP_NONE.getValue();
        
        for (SwitchPort attachPoint: attachPoints) {
        	long dstDpid = attachPoint.getSwitchDPID();
            short dstPort = (short) attachPoint.getPort();
            
        	// Compute forward path from an input switch to the middlebox's switch
        	Route route = routingEngine.getRoute(dpid, inPort, dstDpid, dstPort, 0);
        	if (route == null) {
        		log.debug("No route from {} to {}",
        				HexString.toHexString(dpid),
        				HexString.toHexString(dstDpid));
        		continue;
        	}
        	// Issue FLOW_MOD commands to all switches in the path
        	pushPathFlowMod(route, sw, flow,
        			PATH_OFPRIO, PATH_OFIDLE_TIMEOUT, PATH_OFHARD_TIMEOUT,
        			mbox, BIDIRECTIONAL_FLOW, ctx);
        	
        	return route.getPath().get(1).getPortId();
        }
        return OFPort.OFPP_NONE.getValue();
	}
	
	/**
	 * Send FlowMod message to set up the given flow.
	 * @param sw Switch where the flow is to be installed.
	 * @param flow Flow to install.
	 * @param inPort Input port related to the flow.
	 * @param outPort Output port to be used in the action.
	 * @param dstMacToSet Unless null, rewrite dst mac address with this value.
	 * @param prio Priority for the flow.
	 * @param idleTimeout Idle timeout for the flow.
	 * @param hardTimeout Hard timeout for the flow.
	 * @param ctx Floodlight context.
	 */
	private void pushFlowMod(IOFSwitch sw, CdnFlow flow,
			short inPort, short outPort, String dstMacToSet, short prio,
			short idleTimeout, short hardTimeout,
			FloodlightContext ctx) {
		long dpid = sw.getId();
		
		OFFlowMod fm = (OFFlowMod) ofProvider.getOFMessageFactory()
				.getMessage(OFType.FLOW_MOD);
		fm.setIdleTimeout(idleTimeout);
		fm.setHardTimeout(hardTimeout);
		fm.setBufferId(OFPacketOut.BUFFER_ID_NONE);
		fm.setCommand((short) 0);
		fm.setFlags((short) 0);
		fm.setOutPort(OFPort.OFPP_NONE.getValue());
		fm.setCookie((long) 0); // NOTE: set cookie?
		fm.setPriority(prio);
		
		String swString = HexString.toHexString(dpid);
		String matchString = buildFlowMatchString(flow, inPort);
		String actionString = buildFlowActionString(outPort, dstMacToSet);
		
		StaticFlowEntries.parseActionString(fm, actionString, log);
        OFMatch match = new OFMatch();
        try {
        	match.fromString(matchString);
        } catch (IllegalArgumentException e) {
        	log.warn("Ignoring flow entry in switch {}: {}",
        			swString, matchString);
        }
        fm.setMatch(match);
        try {
			messageDamper.write(sw, fm, ctx, true);
			counterStore.updatePktOutFMCounterStoreLocal(sw, fm);
		} catch (IOException e) {
			log.error("Cannot send FLOW_MOD: ", e);
		}
	}
	
	/**
	 * Send FlowMod messages to program the given route.
	 * @param route Route to program.
	 * @param flow Match fields.
	 * @param sw Switch attached to the client.
	 * @param prio Priority for the flow.
	 * @param idleTimeout Idle timeout for the flow.
	 * @param hardTimeout Hard timeout for the flow.
	 * @param mbox Middlebox where flow is directed, mac address is overwritten.
	 * @param isBidirectional True if an appropriate reverse flow is to be pushed.
	 * @param ctx Floodlight context.
	 */
	private void pushPathFlowMod(Route route, IOFSwitch sw, CdnFlow flow,
			short prio, short idleTimeout, short hardTimeout,
			IMiddlebox mbox, boolean isBidirectional, FloodlightContext ctx) {
		long dpid = sw.getId();
		
		List<NodePortTuple> path = route.getPath();
		if (path.size() == 0)
			return;
		
		// The flow to be installed at the edge is given, but at the core
		// it's only based on dst mac address (that of the middlebox)
		CdnFlow flowNxtHops = new CdnFlow();
		flowNxtHops.dmac = mbox.getMacaddr();
		
		// Issue FLOW_MODs in path reverse order
		for (int i = path.size() - 2; i >= 0; i -= 2) {
			long currentSwDpid = path.get(i).getNodeId();
			IOFSwitch currentSwitch = ofProvider.getSwitch(currentSwDpid);
			
			if (currentSwDpid == dpid)
				pushFlowMod(currentSwitch, flow,
						path.get(i).getPortId(), path.get(i+1).getPortId(),
						mbox.getMacaddr(), prio, idleTimeout, hardTimeout, ctx);
			else
				pushFlowMod(currentSwitch, flowNxtHops,
						path.get(i).getPortId(), path.get(i+1).getPortId(),
						null, prio, idleTimeout, hardTimeout, ctx);
		}
		
		if (isBidirectional) {
			// The reverse flow to be installed at the edge can be computed,
			// but at the core it's only based on dst mac address
			CdnFlow revFlowNxtHops = new CdnFlow();
			revFlowNxtHops.dmac = flow.smac;
			
			// Issue FLOW_MODs in path reverse order
			for (int i = 1; i < path.size(); i += 2) {
				long currentSwDpid = path.get(i).getNodeId();
				IOFSwitch currentSwitch = ofProvider.getSwitch(currentSwDpid);
				
				if (i == path.size() - 1)
					pushFlowMod(currentSwitch, reverseFlow(flow),
							path.get(i).getPortId(), path.get(i-1).getPortId(),
							flow.smac, prio, idleTimeout, hardTimeout, ctx);
				else
					pushFlowMod(currentSwitch, revFlowNxtHops,
							path.get(i).getPortId(), path.get(i-1).getPortId(),
							null, prio, idleTimeout, hardTimeout, ctx);
			}
		}
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
	
	@SuppressWarnings("unused")
	private String buildFlowEntryName(CdnFlow flow, long sw, String mboxMacAddress) {
		StringBuilder entryName = new StringBuilder();
		entryName.append("switch-" + sw);
		entryName.append("-mbox-" + mboxMacAddress);
		if (flow.smac != null)
			entryName.append("-smac-" + flow.smac);
		if (flow.dmac != null)
			entryName.append("-dmac-" + flow.dmac);
		if (flow.dltype != null)
			entryName.append("-dltype-" + flow.dltype);
		if (flow.saddr != null)
			entryName.append("-saddr-" + flow.saddr);
		if (flow.daddr != null)
			entryName.append("-daddr-" + flow.daddr);
		if (flow.proto != null)
			entryName.append("-proto-" + flow.proto);
		if (flow.sport != null)
			entryName.append("-sport-" + flow.sport);
		if (flow.dport != null)
			entryName.append("-dport-" + flow.dport);
		return entryName.toString();
	}
	
	private String buildFlowMatchString(CdnFlow flow, short inPort) {
		StringBuilder matchString = new StringBuilder();
		if (flow.smac != null)
			matchString.append("dl_src=" + flow.smac + ",");
		if (flow.dmac != null)
			matchString.append("dl_dst=" + flow.dmac + ",");
		if (flow.dltype != null)
			matchString.append("dl_type=" + flow.dltype + ",");
		if (flow.saddr != null)
			matchString.append("nw_src=" + flow.saddr + ",");
		if (flow.daddr != null)
			matchString.append("nw_dst=" + flow.daddr + ",");
		if (flow.proto != null)
			matchString.append("nw_proto=" + flow.proto + ",");
		if (flow.sport != null)
			matchString.append("tp_src=" + flow.sport + ",");
		if (flow.dport != null)
			matchString.append("tp_dst=" + flow.dport + ",");
		matchString.append("in_port=" + inPort);
		return matchString.toString();
	}
	
	private String buildFlowActionString(short outPort, String dstMacToSet) {
		StringBuilder actionString = new StringBuilder();
		if (dstMacToSet != null)
			actionString.append("set-dst-mac=" + MACAddress.valueOf(dstMacToSet) + ",");
		if (outPort == OFPort.OFPP_CONTROLLER.getValue())
			actionString.append("output=controller");
		else
			actionString.append("output=" + outPort);
		
		return actionString.toString();
	}
	
	/**
	 * Send PacketOut.
	 * @param sw Switch to send the PacketOut.
	 * @param pkt Packet to send, can be null if bufferId != OFPacketOut.BUFFER_ID_NONE.
	 * @param bufferId Identifier of the buffered packet at the switch.
	 * @param inPort Input port where the packet was received.
	 * @param outPort Output port where the packet must be sent.
	 * @param ctx Floodlight context.
	 */
	private void pushPacketOut(IOFSwitch sw, IPacket pkt, int bufferId,
			short inPort, short outPort, FloodlightContext ctx) {
		log.debug("PACKET_OUT switch={} in_port={} out_port={}",
				new Object[] { sw.getStringId(), inPort, outPort } );
		
		OFPacketOut pktOut = (OFPacketOut) ofProvider.getOFMessageFactory()
				.getMessage(OFType.PACKET_OUT);
		
		List<OFAction> actions = new ArrayList<OFAction>();
		actions.add(new OFActionOutput(outPort));
		pktOut.setActions(actions).setActionsLength((short) OFActionOutput.MINIMUM_LENGTH);
		short pktOutLen = (short) (pktOut.getActionsLength() + OFPacketOut.MINIMUM_LENGTH);
		
		pktOut.setInPort(inPort);
		pktOut.setBufferId(bufferId);
		if (bufferId == OFPacketOut.BUFFER_ID_NONE) {
			if (pkt == null) {
				log.error("Cannot send PACKET_OUT (no buffer_id, null packet) "
						+ "switch={} in_port={} out_port={}",
						new Object[] { sw.getStringId(), inPort, outPort } );
				return;
			}
			byte[] pktData = pkt.serialize();
			pktOutLen += pktData.length;
			pktOut.setPacketData(pktData);
		}
		pktOut.setLength(pktOutLen);
		
		try {
			messageDamper.write(sw, pktOut, ctx, true);
			counterStore.updatePktOutFMCounterStoreLocal(sw, pktOut);
		} catch (IOException e) {
			log.error("Cannot send PACKET_OUT: ", e);
		}
	}
	
	private Collection<SwitchPort> getAttachmentPoints(String macaddr) {
		Collection<? extends IDevice> allDevices = deviceManager.getAllDevices();
        IDevice dev = null;
        for (IDevice d : allDevices) {
        	if (d.getMACAddressString().equalsIgnoreCase(macaddr)) {
        		dev = d;
        		break;
        	}
        }
        if (dev == null) {
        	log.warn("No device found for {}", macaddr);
        	return null;
        }
        
        SwitchPort[] attachPoints = dev.getAttachmentPoints();
        if (attachPoints.length == 0) {
        	log.warn("No attachment point for {}", macaddr);
        	return null;
        }
        return Arrays.asList(attachPoints);
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
			IOFSwitch sw, short inPort) {
		IMiddlebox mbox = null;
		int minLen = Integer.MAX_VALUE;
		
		for (IMiddlebox m: middleboxes) {
			Collection<SwitchPort> attachPoints = getAttachmentPoints(m.getMacaddr());
			if (attachPoints.isEmpty())
				continue;
			
			for (SwitchPort attachPoint : attachPoints) {
				long dstDpid = attachPoint.getSwitchDPID();
				short dstPort = (short) attachPoint.getPort();
				
				Route route = routingEngine.getRoute(sw.getId(), inPort,
						dstDpid, dstPort, 0);
				if (route != null) {
					int len = route.getPath().size();
					if (len < minLen) {
						minLen = len;
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
