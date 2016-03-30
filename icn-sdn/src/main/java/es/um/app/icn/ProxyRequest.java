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

/**
 * Resource request as notified by a proxy.
 * 
 * @author Francisco J. Ros
 */
public class ProxyRequest {

	/** Proxy ID (MAC address). */
	protected String proxy;
	/** Host where the resource is being requested. */
	protected String hostname;
	/** Resource URI. */
	protected String uri;
	/** Flow to be programmed for this request. */
	protected CdnFlow flow;
	
	public ProxyRequest() {
		
	}

	public String getProxy() {
		return proxy;
	}

	public void setProxy(String proxy) {
		this.proxy = proxy;
	}

	public String getHostname() {
		return hostname;
	}

	public void setHostname(String hostname) {
		this.hostname = hostname;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String uri) {
		this.uri = uri;
	}

	public CdnFlow getFlow() {
		return flow;
	}

	public void setFlow(CdnFlow flow) {
		this.flow = flow;
	}
	
}
