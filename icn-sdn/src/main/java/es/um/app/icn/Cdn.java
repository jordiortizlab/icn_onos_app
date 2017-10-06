package es.um.app.icn;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;

import java.util.Collection;

/**
 * Created by nenjordi on 6/10/17.
 */
public interface Cdn {
    Cache findCacheForNewResource(CdnService service,
                                            String resourceName, DeviceId sw, PortNumber inPort);
    Cache findCacheForExistingResource(CdnService service,
                                                 String resourceName, DeviceId sw, PortNumber inPort);

    Collection<Provider> retrieveProviders();
    Provider retrieveProvider(String name);
    Provider createProvider(Provider provider);
    Provider updateProvider(Provider provider);
    Provider removeProvider(String name);
    Collection<Cache> retrieveCaches();
    Cache retrieveCache(String name);
    Cache createCache(Cache cache);
    Cache updateCache(Cache cache);
    Cache removeCache(String name);
    Collection<Resource> retrieveResources();
    Resource retrieveResource(String id);
    Resource createResource(Resource resource);
    Resource updateResource(Resource resource);
    Resource removeResource(String name);
    String getName();
    void setName(String name);
    String getDescription();
    void setDescription(String description);
}
