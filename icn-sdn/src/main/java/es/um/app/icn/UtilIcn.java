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

import java.io.UnsupportedEncodingException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UtilIcn {

	public static final byte IPPROTO_TCP = 6;
	public static short HTTP_PORT = 80;
	private static String DIGEST_ALGORITHM = "SHA-1";
	
	public static int subnetFromCidr(String cidr) throws UnknownHostException {
		String[] parts = cidr.split("/");
		Inet4Address addr = (Inet4Address) InetAddress.getByName(parts[0]);
		byte[] addrBytes = addr.getAddress();
		int res = ((addrBytes[0] & 0xFF) << 24 |
				(addrBytes[1] & 0xFF) << 16 |
				(addrBytes[2] & 0xFF) << 8 |
				(addrBytes[3] & 0xFF) << 0);
		return res;
	}
	
	public static int masklenFromCidr(String cidr) {
		String[] parts = cidr.split("/");
		return Integer.parseInt(parts[1]);
	}
	
	public static String resourceId(String icnName, String resourceName) {
		try {
			MessageDigest md = MessageDigest.getInstance(DIGEST_ALGORITHM);
			md.update(icnName.getBytes("UTF-8"));
			md.update(resourceName.getBytes("UTF-8"));
			return new String(md.digest(), "UTF-8");
		} catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	
}
