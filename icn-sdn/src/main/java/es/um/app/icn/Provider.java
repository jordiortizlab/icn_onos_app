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
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Provider {

	protected String name;
	protected String description;
	/** CIDR network address, e.g. 80.120.45.0/24 */
	protected String network; // TODO: better a collection of networks... v1.1?
	/**
	 * Java pattern for the URI to check whether a request belongs to this
	 * provider and obtain the associated resource name (group capturing
	 * required).
	 * @see java.util.regex.Pattern
	 */
	protected String uripattern;
	/**
	 * Java pattern for the Host header to check whether a request belongs to
	 * this provider (no group capturing allowed).
	 * @see java.util.regex.Pattern
	 */
	protected String hostpattern;
	
	public Provider() {
		
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getNetwork() {
		return network;
	}

	public void setNetwork(String network) {
		this.network = network;
	}
	
    public String getUripattern() {
        return uripattern;
    }

    public void setUripattern(String uripattern) {
        this.uripattern = uripattern;
    }

    /**
     *
     * @param urireq the uri to check against pattern
     * @return the pattern matched or null if not match
     */
    public String matchUriPattern(String urireq) {
        if (this.uripattern != null) {
            Pattern pattern = Pattern.compile(this.uripattern);
            Matcher matcher = pattern.matcher(urireq);
            if (matcher.find())
                return matcher.group(0);
            return null;
        }
        return urireq;
    }

    public String getHostpattern() {
        return hostpattern;
    }

    public void setHostpattern(String hostpattern) {
        this.hostpattern = hostpattern;
    }

    /**
     *
     * @param hostreq the host to check against the pattern
     * @return the pattern matched or null if not match
     */
    public String matchHostPattern(String hostreq) {
        if (this.hostpattern != null) {
            Pattern pattern = Pattern.compile(this.hostpattern);
            Matcher matcher = pattern.matcher(hostreq);
            if (matcher.find())
                return matcher.group(0);
            return null;
        }
        return hostreq;
    }

	protected boolean containsIpAddress(int addr) {
		try {
			int subnet = UtilCdn.subnetFromCidr(network);
			int masklen = UtilCdn.masklenFromCidr(network);
			int mask = -1 << (32 - masklen);
			// Strange Java behavior makes me write this special case
			if (masklen == 0)
				mask = 0;
			return ((subnet & mask) == (addr & mask));
		} catch (UnknownHostException e) {
			return false;
		}
	}
	
}
