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

import org.onlab.packet.Ip4Address;
import org.onosproject.net.Port;

/**
 * Private API exported to proxies.
 * 
 * @author Francisco J. Ros
 */
public interface IIcnPrivateService {

    /**
     * Handle resource requested as notified by a proxy.
     * @param req Request forwarded by the proxy.
     */
    public boolean processResourceRequest(ProxyRequest req);

    public boolean createPrefetchingPath(IMiddlebox proxy, Location origin, IMiddlebox mbox,
                                         Ip4Address icnAddress, Port icnPort);
}
