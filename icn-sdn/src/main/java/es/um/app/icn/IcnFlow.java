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
 * Fields employed in 'match' clauses to identify flows in the ICN application.
 * NOTE: All fields are strings, so clients of this class must ensure the proper
 *       format for each field is used.
 * 
 * @author Francisco J. Ros
 */
public class IcnFlow {

	protected String smac;	// "xx:xx:xx:xx:xx:xx"
	protected String dmac;	// "xx:xx:xx:xx:xx:xx"
	protected String dltype;// byte e.g. "0x0800"
	protected String saddr;	// e.g. "10.0.0.1"
	protected String daddr;	// e.g. "10.0.0.1"
	protected String proto;	// e.g. "6" for TCP
	protected String sport;	// short
	protected String dport;	// short
	
	public IcnFlow() {
		
	}

	public String getSmac() {
		return smac;
	}

	public void setSmac(String smac) {
		this.smac = smac;
	}

	public String getDmac() {
		return dmac;
	}

	public void setDmac(String dmac) {
		this.dmac = dmac;
	}

	public String getDltype() {
		return dltype;
	}

	public void setDltype(String dltype) {
		this.dltype = dltype;
	}

	public String getSaddr() {
		return saddr;
	}

	public void setSaddr(String saddr) {
		this.saddr = saddr;
	}

	public String getDaddr() {
		return daddr;
	}

	public void setDaddr(String daddr) {
		this.daddr = daddr;
	}

	public String getProto() {
		return proto;
	}

	public void setProto(String proto) {
		this.proto = proto;
	}

	public String getSport() {
		return sport;
	}

	public void setSport(String sport) {
		this.sport = sport;
	}

	public String getDport() {
		return dport;
	}

	public void setDport(String dport) {
		this.dport = dport;
	}
	
	public String toString() {
		StringBuilder builder = new StringBuilder();
		if (smac != null)
			builder.append("smac=" + smac + ";");
		if (dmac != null)
			builder.append("dmac=" + dmac + ";");
		if (dltype != null)
			builder.append("dltype=" + dltype + ";");
		if (saddr != null)
			builder.append("saddr=" + saddr + ";");
		if (daddr != null)
			builder.append("daddr=" + daddr + ";");
		if (proto != null)
			builder.append("proto=" + proto + ";");
		if (sport != null)
			builder.append("sport=" + sport + ";");
		if (dport != null)
			builder.append("dport=" + dport + ";");
		return builder.toString();
	}
}
