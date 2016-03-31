**ICN over SDN UMU App**

# Service adaptation
## Interesting onos services
* ResourceAdminService <- It could be useful to store any kind of Resource. Indeed in the original implementation there is a Resource class.
    * register(resources) - Register specified resources.
* PathService <- Path precomputation. To substitute the routingEngine from FloodLight
* TopologyService <- More information about topology
    * getPaths(topology, source, destination) - Returns the set of all shortest paths, precomputed in terms of hop-count, between the specified source and destination devices.
* DeviceService <- Interact with Device inventory. To substitute the deviceManager from FloodLight
* ProxyArpService, PacketService <- It could serve as a basis to capture the packets for the icn service
* HostService <- Information about hosts. In our case caches and proxies would be shown as hosts.
* FlowObjectiveService <- Allows installing flows into devices
    * filter()
    * forward()
    * next()
* FlowRuleService <- Creates a virtual flow table so that the flows in the devices are cached.
* EdgePortService <- Service for interacting with an inventory of network edge ports. A port is considered an edge port if it is an active port and does not have an infrastructure link associated with it.
* DeviceService <- Service for interacting with the inventory of infrastructure devices.

