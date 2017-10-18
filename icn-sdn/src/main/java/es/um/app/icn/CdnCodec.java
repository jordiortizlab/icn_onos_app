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
public class CdnCodec  extends JsonCodec<Cdn> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String PROVIDERS_FIELD = "providers";
    private static final String CACHES_FIELD = "caches";
    private static final String RESOURCES_FIELD = "resources";
    private static final String TYPE_FIELD = "type";


    @Override
    public ObjectNode encode(Cdn cdn, CodecContext context) {
        if (cdn == null)
            throw new NullPointerException("The Cdn to jsonize is null");
        String type = "";
        switch ( cdn.getType()) {
            case CdnClosestCache.DESCRIPTION:
                type = CdnClosestCache.DESCRIPTION;
                break;
            default:
                log.error("Unknown CDN Type: {}", cdn.getType());
                return null;
        }

        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, cdn.getName())
                .put(DESCRIPTION_FIELD, cdn.getDescription())
                .put(TYPE_FIELD, type);
        ArrayNode providersarray = result.putArray(PROVIDERS_FIELD);
        Collection<Provider> providers = cdn.retrieveProviders();
        ProviderCodec pc = new ProviderCodec();
        for (Provider provider : providers) {
            ObjectNode encodedprovider = pc.encode(provider, context);
            providersarray.add(encodedprovider);
        }
        ArrayNode cachesarray = result.putArray(CACHES_FIELD);
        Collection<Cache> caches = cdn.retrieveCaches();
        CacheCodec cc = new CacheCodec();
        for (Cache cache : caches) {
            ObjectNode encodedcache = cc.encode(cache, context);
            cachesarray.add(encodedcache);
        }
        ArrayNode resourcesarray = result.putArray(RESOURCES_FIELD);
        Collection<Resource> resources = cdn.retrieveResources();
        ResourceCodec rc = new ResourceCodec();
        for (Resource resource : resources) {
            ObjectNode encodedresource = rc.encode(resource, context);
            resourcesarray.add(encodedresource);
        }
        return result;
    }

    @Override
    public Cdn decode(ObjectNode json, CodecContext context) {
        Cdn cdn = null;
        if (json.get(TYPE_FIELD) != null) {
            switch (json.get(TYPE_FIELD).asText()) {
                case CdnClosestCache.DESCRIPTION:
                    cdn = new CdnClosestCache();
                    break;
                case CdnClosestCacheDASH.DESCRIPTION:
                    cdn = new CdnClosestCacheDASH();
                    break;
                default:
                    log.error("Unknown CDN Type: {}", json.get(TYPE_FIELD).asText());
                    return null;
            }
        } else {
            log.warn("No CDN Type set, defaulting to {}", CdnClosestCache.DESCRIPTION);
            cdn = new CdnClosestCache();
        }

        cdn.setName(json.get(NAME_FIELD).asText());
        cdn.setDescription(json.get(DESCRIPTION_FIELD).asText());

        ArrayNode providersarray = (ArrayNode) json.get(PROVIDERS_FIELD);
        ProviderCodec pc = new ProviderCodec();
        if (providersarray != null)
            for (JsonNode provider : providersarray) {
                try {
                    ObjectNode providerobject = (ObjectNode) context.mapper().readTree(provider.toString());
                    Provider provider1 = pc.decode(providerobject, context);
                    cdn.createProvider(provider1);
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
                    cdn.createCache(cache1);
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
                    Resource resource1 = rc.decode(resourceobject, context);
                    cdn.createResource(resource1);
                } catch (IOException e) {
                    e.printStackTrace();
                    log.error("Unable to convert Resource JsonNode to ObjectNode");
                }
            }

        return cdn;
    }
}
