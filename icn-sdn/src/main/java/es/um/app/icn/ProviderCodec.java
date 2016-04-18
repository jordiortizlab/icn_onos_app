package es.um.app.icn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Jordi Ortiz on 18/04/16.
 * This class is used to provide json implementation for the Provider
 */
public class ProviderCodec extends JsonCodec<Provider> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String NAME_FIELD = "name";
    private static final String DESCRIPTION_FIELD = "description";
    private static final String NETWORK_FIELD = "network";
    private static final String URIPATTERN_FIELD = "uripattern";
    private static final String HOSTPATTERN_FIELD = "hostpattern";

    @Override
    public ObjectNode encode(Provider provider, CodecContext context) {
        if (provider == null)
            throw new NullPointerException("Provider is null when generating JSON");
        ObjectNode result = context.mapper().createObjectNode()
                .put(NAME_FIELD, provider.getName())
                .put(DESCRIPTION_FIELD, provider.getDescription())
                .put(NETWORK_FIELD, provider.getNetwork())
                .put(URIPATTERN_FIELD, provider.getUripattern())
                .put(HOSTPATTERN_FIELD, provider.getHostpattern());
        return result;
    }

    @Override
    public Provider decode(ObjectNode json, CodecContext context) {
        Provider p =  new Provider();
        p.setName(json.get(NAME_FIELD).asText());
        p.setDescription(json.get(DESCRIPTION_FIELD).asText());
        p.setNetwork(json.get(NETWORK_FIELD).asText());
        p.setUripattern(json.get(URIPATTERN_FIELD).asText());
        p.setHostpattern(json.get(HOSTPATTERN_FIELD).asText());
        return p;
    }
}
