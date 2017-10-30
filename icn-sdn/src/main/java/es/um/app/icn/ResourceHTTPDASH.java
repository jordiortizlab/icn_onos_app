package es.um.app.icn;

import java.util.HashMap;

/**
 * Created by Jordi Ortiz on 30/10/17.
 */
public class ResourceHTTPDASH extends ResourceHTTP {
    private static String DESCRIPTION = "DASH";
    HashMap<Integer, RepresentationDASH> representations;

    public ResourceHTTPDASH(ResourceHTTP original) {
        this.setId(original.getId());
        this.setCaches(original.getCaches());
        this.setFullurl(original.getFullurl());
        this.setName(original.getName());
        this.setRequests(original.getRequests());
    }

    public void putRepresentation(Integer id, RepresentationDASH r) {
        if(!representations.containsKey(id))
            representations.put(id, r);
    }

}
