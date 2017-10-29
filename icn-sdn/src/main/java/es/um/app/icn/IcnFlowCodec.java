package es.um.app.icn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Jordi Ortiz on 18/04/16.
 */
public class IcnFlowCodec extends JsonCodec<IcnFlow> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String SMAC_FIELD = "smac";
    private static final String SADDR_FIELD = "saddr";
    private static final String DADDR_FIELD = "daddr";
    private static final String PROTO_FIELD = "proto";
    private static final String SPORT_FIELD = "sport";
    private static final String DPORT_FIELD = "dport";

    @Override
    public ObjectNode encode(IcnFlow icnFlow, CodecContext context) {
        if (icnFlow == null)
            throw new NullPointerException("IcnFlow was null when jsonizing");
        ObjectNode result = context.mapper().createObjectNode()
                .put(SMAC_FIELD, icnFlow.getSmac())
                .put(SADDR_FIELD, icnFlow.getSaddr())
                .put(DADDR_FIELD, icnFlow.getDaddr())
                .put(PROTO_FIELD, icnFlow.getProto())
                .put(SPORT_FIELD, icnFlow.getSport())
                .put(DPORT_FIELD, icnFlow.getDport());
        return result;
    }

    @Override
    public IcnFlow decode(ObjectNode json, CodecContext context) {
        IcnFlow icnFlow = new IcnFlow();
        icnFlow.setSmac(json.get(SMAC_FIELD).asText());
        icnFlow.setSaddr(json.get(SADDR_FIELD).asText());
        icnFlow.setDaddr(json.get(DADDR_FIELD).asText());
        icnFlow.setProto(json.get(PROTO_FIELD).asText());
        icnFlow.setSport(json.get(SPORT_FIELD).asText());
        icnFlow.setDport(json.get(DPORT_FIELD).asText());
        return icnFlow;
    }
}
