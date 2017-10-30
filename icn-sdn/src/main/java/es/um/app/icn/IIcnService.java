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
 * CRUD operations for the ICN resources: 'icn', 'cache', 'provider', 'resource'.
 * Note that every operation is allowed for all resource types.
 * 
 * @author Francisco J. Ros
 */
public interface IIcnService {

	public Collection<Icn> retrieveIcns();
	
	public Icn retrieveIcn(String name);
	
	public Icn createIcn(Icn icn);
	
	public Icn updateIcn(Icn icn);
	
	public Icn removeIcn(String name);
	
	public Collection<Provider> retrieveProviders(Icn icn);
	
	public Provider retrieveProvider(Icn icn, String name);
	
	public Provider createProvider(Icn icn, Provider provider);
	
	public Provider updateProvider(Icn icn, Provider provider);
	
	public Provider removeProvider(Icn icn, String name);
	
	public Collection<Cache> retrieveCaches(Icn icn);
	
	public Cache retrieveCache(Icn icn, String name);
	
	public Cache createCache(Icn icn, Cache cache);
	
	public Cache updateCache(Icn icn, Cache cache);
	
	public Cache removeCache(Icn icn, String name);
	
	public Collection<ResourceHTTP> retrieveResources(Icn icn);
	
	public ResourceHTTP retrieveResource(Icn icn, String id);
	
	public Collection<Proxy> retrieveProxies();
	
	public Proxy retrieveProxy(String name);
	
	public Proxy createProxy(Proxy proxy);
	
	public Proxy updateProxy(Proxy proxy);
	
	public Proxy removeProxy(String name);
	
}
