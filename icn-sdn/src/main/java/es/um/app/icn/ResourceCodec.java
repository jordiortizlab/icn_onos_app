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
 * Created by Jordi Ortiz on 19/04/16.
 */
public class ResourceCodec extends JsonCodec<ResourceHTTP> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String ID_FIELD = "id";
    private static final String HOSTNAME_FIELD = "hostname";
    private static final String REQUESTS_FIELD = "requests";
    private static final String CACHES_FIELD = "caches";
    private static final String FULLURL_FIELD = "fullurl";

    @Override
    public ObjectNode encode(ResourceHTTP resourceHTTP, CodecContext context) {
        if (resourceHTTP == null)
            throw new NullPointerException("The resourceHTTP to jsonize is null");
        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, resourceHTTP.getName())
                .put(ID_FIELD, resourceHTTP.getId())
                .put(FULLURL_FIELD, resourceHTTP.getFullurl())
//                .put(HOSTNAME_FIELD, resourceHTTP.getHostName())
                .put(REQUESTS_FIELD, resourceHTTP.getRequests());
        ArrayNode cachesarray = result.putArray(CACHES_FIELD);
        Collection<Cache> caches = resourceHTTP.getCaches();
        CacheCodec cc = new CacheCodec();
        for (Cache cache : caches) {
            ObjectNode encodedcache = cc.encode(cache, context);
            cachesarray.add(encodedcache);
        }

        return result;
    }

    @Override
    public ResourceHTTP decode(ObjectNode json, CodecContext context) {
        ResourceHTTP resourceHTTP = new ResourceHTTP();
        resourceHTTP.setId(json.get(ID_FIELD).asText());
        resourceHTTP.setName(json.get(NAME_FIELD).asText());
        resourceHTTP.setFullurl(json.get(FULLURL_FIELD).asText());
        resourceHTTP.setRequests(json.get(REQUESTS_FIELD).asLong());
        ArrayNode cachesarray = (ArrayNode) json.get(CACHES_FIELD);
        CacheCodec cc = new CacheCodec();
        for (JsonNode cache : cachesarray) {
            try {
                ObjectNode cacheobject = (ObjectNode) context.mapper().readTree(cache.toString());
                Cache cache1 = cc.decode(cacheobject, context);
                resourceHTTP.addCache(cache1);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Unable to convert Cahce JsonNode to ObjectNode");
            }

        }
        return resourceHTTP;
    }
}
