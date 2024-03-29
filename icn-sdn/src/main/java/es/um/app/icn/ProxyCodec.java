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
 * Created by Jordi Ortiz on 18/04/16.
 */
public class ProxyCodec extends JsonCodec<Proxy> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String LOCATION_FIELD = "location";
    private static final String MACADDR_FIELD = "macaddr";
    private static final String IPADDR_FIELD = "ipaddr";
    private static final String PROXYPORT_FIELD = "port";
    private static final String PREFETCHPORT_FIELD = "prefetchport";
    private static final String TYPE_FIELD = "type";
    private static final String REACTIVE = "isReactive";

    @Override
    public ObjectNode encode(Proxy proxy, CodecContext context) {
        if (proxy == null)
            throw new NullPointerException("proxy is null when generating JSON");
        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, proxy.getName())
                .put(DESCRIPTION_FIELD, proxy.getDescription())
                .put(IPADDR_FIELD, proxy.getIpaddr())
                .put(MACADDR_FIELD, proxy.getMacaddr())
                .put(PROXYPORT_FIELD, proxy.getPort())
                .put(TYPE_FIELD, proxy.getType())
                .put(REACTIVE, proxy.isReactive);
        if (proxy.getLocation() != null) {
            ObjectNode locationobject = new LocationCodec().encode(proxy.getLocation(), context);
            try {
                JsonNode locationjson = context.mapper().readTree(locationobject.toString());
                result.set(LOCATION_FIELD, locationjson);
            } catch (IOException e) {
                e.printStackTrace();
                log.error("Unable to jsonize the proxy Location");
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
    public Proxy decode(ObjectNode json, CodecContext context) {
        Proxy proxy = new Proxy();
        proxy.setName(json.get(NAME_FIELD).asText());
        proxy.setDescription(json.get(DESCRIPTION_FIELD).asText());
        if(json.get(IPADDR_FIELD) != null)
            proxy.setIpaddr(json.get(IPADDR_FIELD).asText());
        if(json.get(PROXYPORT_FIELD) != null)
            proxy.setPort(json.get(PROXYPORT_FIELD).asInt());
        if(json.get(PREFETCHPORT_FIELD) != null)
            proxy.setPrefetch_port(json.get(PREFETCHPORT_FIELD).asInt());
        if(json.get(REACTIVE) != null)
            proxy.setReactive(json.get(REACTIVE).asBoolean());
        proxy.setMacaddr(json.get(MACADDR_FIELD).asText());
        proxy.setType(json.get(TYPE_FIELD).asText());
        JsonNode locationjson = json.get(LOCATION_FIELD);
        if (locationjson != null)
            try {
                ObjectNode locationobject = (ObjectNode) context.mapper().readTree(locationjson.toString());
                proxy.setLocation(new LocationCodec().decode(locationobject, context));
            } catch (IOException e) {
                log.error("Unable to dejsonize the proxy Location");
                e.printStackTrace();
                return null;
            }

        return proxy;

    }
}
