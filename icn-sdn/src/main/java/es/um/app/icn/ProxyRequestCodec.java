package es.um.app.icn;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by Jordi Ortiz on 18/04/16.
 */
public class ProxyRequestCodec extends JsonCodec<ProxyRequest> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String PROXY_FIELD = "proxy";
    private static final String URI_FIELD = "uri";
    private static final String HOSTNAME_FIELD = "hostname";
    private static final String FLOW_FIELD = "flow";

    @Override
    public ObjectNode encode(ProxyRequest proxyRequest, CodecContext context) {
        if (proxyRequest == null)
            throw new NullPointerException("proxyRequest was null while trying to jsonize");
        ObjectNode result = context.mapper().createObjectNode()
                .put(PROXY_FIELD, proxyRequest.getProxy())
                .put(URI_FIELD, proxyRequest.getUri())
                .put(HOSTNAME_FIELD, proxyRequest.getProxy());
        ObjectNode icnflowobject = new IcnFlowCodec().encode(proxyRequest.getFlow(), context);
        try {
            JsonNode proxyreqjson = context.mapper().readTree(icnflowobject.toString());
            result.set(FLOW_FIELD, proxyreqjson);
        } catch (IOException e) {
            e.printStackTrace();
        }
    return result;
    }

    @Override
    public ProxyRequest decode(ObjectNode json, CodecContext context) {
        ProxyRequest proxyRequest = new ProxyRequest();
        proxyRequest.setProxy(json.get(PROXY_FIELD).asText());
        proxyRequest.setUri(json.get(URI_FIELD).asText());
        proxyRequest.setHostname(json.get(HOSTNAME_FIELD).asText());
        JsonNode icnflowjson = json.get(FLOW_FIELD);
        try {
            ObjectNode icnflowobject = (ObjectNode)context.mapper().readTree(icnflowjson.toString());
            proxyRequest.setFlow(new IcnFlowCodec().decode(icnflowobject, context));
        } catch (IOException e) {
            log.error("Unable to dejsonize ProxyRequest");
            e.printStackTrace();
            return null;
        }
        return proxyRequest;
    }
}
