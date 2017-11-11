package es.um.app.icn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by Jordi Ortiz on 12/04/16.
 * This class is used to provide json implementation for the Cache
 */
public class CacheCodec extends JsonCodec<Cache>{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String IPADDR_FIELD = "ipaddr";
    private static final String PORT_FIELD = "port";
    private static final String MACADDR_FIELD = "macaddr";
    private static final String LOCATION_FIELD = "location";

    @Override
    public ObjectNode encode(Cache cache, CodecContext context) {
        if (cache == null)
            throw new NullPointerException("Cache is null when generating JSON");
        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, cache.getName())
                .put(DESCRIPTION_FIELD, cache.getDescription())
                .put(IPADDR_FIELD, cache.getIpaddr())
                .put(PORT_FIELD, cache.getPort())
                .put(MACADDR_FIELD,cache.getMacaddr());
        if (cache.getLocation() != null) {
            ObjectNode locationobject = new LocationCodec().encode(cache.getLocation(), context);
            try {
                JsonNode locationjson = context.mapper().readTree(locationobject.toString());
                result.set(LOCATION_FIELD, locationjson);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Unable to jsonize the cache Location");
                return null;
            }
        }
        else
        {
            result.set(LOCATION_FIELD, null);
        }
        return result;
    }

    @Override
    public Cache decode(ObjectNode json, CodecContext context) {
        Cache cache = new Cache();
        cache.setName(json.get(NAME_FIELD).asText());
        cache.setDescription(json.get(DESCRIPTION_FIELD).asText());
        if (json.get(IPADDR_FIELD) != null)
            cache.setIpaddr(json.get(IPADDR_FIELD).asText());
        if (json.get(PORT_FIELD) != null)
            cache.setPort(json.get(PORT_FIELD).asInt());
        else
            cache.setPort(3128);
        cache.setMacaddr(json.get(MACADDR_FIELD).asText());
        JsonNode locationjson = json.get(LOCATION_FIELD);
        if (locationjson != null) {
            try {
                ObjectNode locationobject = (ObjectNode) context.mapper().readTree(locationjson.toString());
                cache.setLocation(new LocationCodec().decode(locationobject, context));
            } catch (IOException e) {
                log.error("Unable to dejsonize the cache Location");
                e.printStackTrace();
                return null;
            }
        }
        return cache;
    }
}
