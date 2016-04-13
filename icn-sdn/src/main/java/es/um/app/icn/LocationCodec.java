package es.um.app.icn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Jordi Ortiz on 12/04/16.
 * This class is used to provide json implementation for the Location
 */
public class LocationCodec extends JsonCodec<Location> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String DPID_FIELD = "dpid";
    private static final String PORT_FIELD = "port";

    @Override
    public ObjectNode encode(Location location, CodecContext context) {
        if (location == null)
            throw new NullPointerException("Location is null when generating JSON");
        ObjectNode result = context.mapper().createObjectNode()
        .put(DPID_FIELD, location.getDpid())
        .put(PORT_FIELD, Short.toString(location.getPort()));
        return result;
    }

    @Override
    public Location decode(ObjectNode json, CodecContext context) {
        Location location = new Location();
        location.setDpid(json.get(DPID_FIELD).asText());
        location.setPort(Short.parseShort(json.get(PORT_FIELD).asText()));
        return location;
    }
}
