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

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.restserver.RestletRoutable;

public class CdnServiceRestRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context ctx) {
		Router router = new Router(ctx);
		router.attach("/cdn", CdnsNorthbound.class);
		router.attach("/cdn/{name}", CdnNorthbound.class);
		router.attach("/cdn/{name}/provider", ProvidersNorthbound.class);
		router.attach("/cdn/{name}/provider/{pname}", ProviderNorthbound.class);
		router.attach("/cdn/{name}/cache", CachesNorthbound.class);
		router.attach("/cdn/{name}/cache/{cname}", CacheNorthbound.class);
		router.attach("/cdn/{name}/resource", ResourcesNorthbound.class);
		router.attach("/cdn/{name}/resource/{id}", ResourceNorthbound.class);
		router.attach("/cdnproxy", ProxiesNorthbound.class);
		router.attach("/cdnproxy/{name}", ProxyNorthbound.class);
		//router.attachDefault(NoOp.class);
		return router;
	}

	@Override
	public String basePath() {
		return "/wm/cdnmanager/v1.0";
	}

}
