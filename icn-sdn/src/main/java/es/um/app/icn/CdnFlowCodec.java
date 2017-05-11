package es.um.app.icn;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.org.apache.bcel.internal.generic.DADD;
import org.onosproject.codec.CodecContext;
import org.onosproject.codec.JsonCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by Jordi Ortiz on 18/04/16.
 */
public class CdnFlowCodec extends JsonCodec<CdnFlow> {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String SMAC_FIELD = "smac";
    private static final String SADDR_FIELD = "saddr";
    private static final String DADDR_FIELD = "daddr";
    private static final String PROTO_FIELD = "proto";
    private static final String SPORT_FIELD = "sport";
    private static final String DPORT_FIELD = "dport";

    @Override
    public ObjectNode encode(CdnFlow cdnFlow, CodecContext context) {
        if (cdnFlow == null)
            throw new NullPointerException("CdnFlow was null when jsonizing");
        ObjectNode result = context.mapper().createObjectNode()
                .put(SMAC_FIELD, cdnFlow.getSmac())
                .put(SADDR_FIELD, cdnFlow.getSaddr())
                .put(DADDR_FIELD, cdnFlow.getDaddr())
                .put(PROTO_FIELD, cdnFlow.getProto())
                .put(SPORT_FIELD, cdnFlow.getSport())
                .put(DPORT_FIELD, cdnFlow.getDport());
        return result;
    }

    @Override
    public CdnFlow decode(ObjectNode json, CodecContext context) {
        CdnFlow cdnFlow = new CdnFlow();
        cdnFlow.setSmac(json.get(SMAC_FIELD).asText());
        cdnFlow.setSaddr(json.get(SADDR_FIELD).asText());
        cdnFlow.setDaddr(json.get(DADDR_FIELD).asText());
        cdnFlow.setProto(json.get(PROTO_FIELD).asText());
        cdnFlow.setSport(json.get(SPORT_FIELD).asText());
        cdnFlow.setDport(json.get(DPORT_FIELD).asText());
        return cdnFlow;
    }
}
