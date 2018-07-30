package es.um.app.icn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;

/**
 * Created by Jordi Ortiz on 11/05/16.
 */
public class IcnCodec extends JsonCodec<Icn> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PROVIDERS_FIELD = "providers";
    private static final String CACHES_FIELD = "caches";
    private static final String RESOURCES_FIELD = "resources";
    private static final String TYPE_FIELD = "type";


    @Override
    public ObjectNode encode(Icn icn, CodecContext context) {
        if (icn == null)
            throw new NullPointerException("The ICN to jsonize is null");
        String type = "";
        switch ( icn.getType()) {
            case IcnClosestCache.DESCRIPTION:
                type = IcnClosestCache.DESCRIPTION;
                break;
            default:
                log.error("Unknown ICN Type: {}", icn.getType());
                return null;
        }

        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, icn.getName())
                .put(DESCRIPTION_FIELD, icn.getDescription())
                .put(TYPE_FIELD, type);
        ArrayNode providersarray = result.putArray(PROVIDERS_FIELD);
        Collection<Provider> providers = icn.retrieveProviders();
        ProviderCodec pc = new ProviderCodec();
        for (Provider provider : providers) {
            ObjectNode encodedprovider = pc.encode(provider, context);
            providersarray.add(encodedprovider);
        }
        ArrayNode cachesarray = result.putArray(CACHES_FIELD);
        Collection<Cache> caches = icn.retrieveCaches();
        CacheCodec cc = new CacheCodec();
        for (Cache cache : caches) {
            ObjectNode encodedcache = cc.encode(cache, context);
            cachesarray.add(encodedcache);
        }
        ArrayNode resourcesarray = result.putArray(RESOURCES_FIELD);
        Collection<ResourceHTTP> resourceHTTPS = icn.retrieveResources();
        ResourceCodec rc = new ResourceCodec();
        for (ResourceHTTP resourceHTTP : resourceHTTPS) {
            ObjectNode encodedresource = rc.encode(resourceHTTP, context);
            resourcesarray.add(encodedresource);
        }
        return result;
    }

    @Override
    public Icn decode(ObjectNode json, CodecContext context) {
        Icn icn = null;
        if (json.get(TYPE_FIELD) != null) {
            switch (json.get(TYPE_FIELD).asText()) {
                case IcnClosestCache.DESCRIPTION:
                    log.info("ICN Type {}", IcnClosestCache.DESCRIPTION);
                    icn = new IcnClosestCache();
                    break;
                case IcnClosestCacheDASH.DESCRIPTION:
                    log.info("ICN Type {}", IcnClosestCacheDASH.DESCRIPTION);
                    icn = new IcnClosestCacheDASH();
                    break;
                case IcnDistributedCacheSVCDASH.DESCRIPTION:
                    log.info("ICN Type {}", IcnDistributedCacheSVCDASH.DESCRIPTION);
                    icn = new IcnDistributedCacheSVCDASH();
                    break;
                default:
                    log.error("Unknown ICN Type: {}", json.get(TYPE_FIELD).asText());
                    return null;
            }
        } else {
            log.warn("No ICN Type set, defaulting to {}", IcnClosestCache.DESCRIPTION);
            icn = new IcnClosestCache();
        }

        icn.setName(json.get(NAME_FIELD).asText());
        icn.setDescription(json.get(DESCRIPTION_FIELD).asText());

        ArrayNode providersarray = (ArrayNode) json.get(PROVIDERS_FIELD);
        ProviderCodec pc = new ProviderCodec();
        if (providersarray != null)
            for (JsonNode provider : providersarray) {
                try {
                    ObjectNode providerobject = (ObjectNode) context.mapper().readTree(provider.toString());
                    Provider provider1 = pc.decode(providerobject, context);
                    icn.createProvider(provider1);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Unable to convert Provider JsonNode to ObjectNode");
                }
            }

        ArrayNode cachesarray = (ArrayNode) json.get(CACHES_FIELD);
        CacheCodec cc = new CacheCodec();
        if (cachesarray != null)
            for (JsonNode cache : cachesarray) {
                try {
                    ObjectNode cacheobject = (ObjectNode) context.mapper().readTree(cache.toString());
                    Cache cache1 = cc.decode(cacheobject, context);
                    icn.createCache(cache1);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Unable to convert Cahce JsonNode to ObjectNode");
                }
            }

        ArrayNode resourcesarray = (ArrayNode) json.get(RESOURCES_FIELD);
        ResourceCodec rc = new ResourceCodec();
        if (resourcesarray != null)
            for (JsonNode resource : resourcesarray) {
                try {
                    ObjectNode resourceobject = (ObjectNode) context.mapper().readTree(resource.toString());
                    ResourceHTTP resourceHTTP1 = rc.decode(resourceobject, context);
                    icn.createResource(resourceHTTP1);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Unable to convert ResourceHTTP JsonNode to ObjectNode");
                }
            }

        return icn;
    }
}
