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

public interface IMiddlebox {

	public String getName();

	public void setName(String name);

	public String getDescription();

	public void setDescription(String description);

	public Location getLocation();

	public void setLocation(Location location);

	public String getMacaddr();

	public void setMacaddr(String macaddr);

	public String getIpaddr();

	public void setIpaddr(String ipaddr);

	public void setPort(int port);

	public int getPort();
	
}
