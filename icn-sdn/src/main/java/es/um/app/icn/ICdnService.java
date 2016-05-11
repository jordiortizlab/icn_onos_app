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

import java.util.Collection;

/**
 * CRUD operations for the CDN resources: 'cdn', 'cache', 'provider', 'resource'.
 * Note that every operation is allowed for all resource types.
 * 
 * @author Francisco J. Ros
 */
public interface ICdnService {

	public Collection<Cdn> retrieveCdns();
	
	public Cdn retrieveCdn(String name);
	
	public Cdn createCdn(Cdn cdn);
	
	public Cdn updateCdn(Cdn cdn);
	
	public Cdn removeCdn(String name);
	
	public Collection<Provider> retrieveProviders(Cdn cdn);
	
	public Provider retrieveProvider(Cdn cdn, String name);
	
	public Provider createProvider(Cdn cdn, Provider provider);
	
	public Provider updateProvider(Cdn cdn, Provider provider);
	
	public Provider removeProvider(Cdn cdn, String name);
	
	public Collection<Cache> retrieveCaches(Cdn cdn);
	
	public Cache retrieveCache(Cdn cdn, String name);
	
	public Cache createCache(Cdn cdn, Cache cache);
	
	public Cache updateCache(Cdn cdn, Cache cache);
	
	public Cache removeCache(Cdn cdn, String name);
	
	public Collection<Resource> retrieveResources(Cdn cdn);
	
	public Resource retrieveResource(Cdn cdn, String id);
	
	public Collection<Proxy> retrieveProxies();
	
	public Proxy retrieveProxy(String name);
	
	public Proxy createProxy(Proxy proxy);
	
	public Proxy updateProxy(Proxy proxy);
	
	public Proxy removeProxy(String name);
	
}
