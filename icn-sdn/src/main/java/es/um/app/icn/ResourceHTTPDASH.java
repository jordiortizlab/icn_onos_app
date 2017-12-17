package es.um.app.icn;

import java.util.HashMap;

/**
 * Created by Jordi Ortiz on 30/10/17.
 */
public class ResourceHTTPDASH extends ResourceHTTP {
    public static String DESCRIPTION = "DASH";
    HashMap<Integer, RepresentationDASH> representations;

    public ResourceHTTPDASH(ResourceHTTP original) {
        this.setId(original.getId());
        this.setCaches(original.getCaches());
        this.setFullurl(original.getFullurl());
        this.setName(original.getName());
        this.setRequests(original.getRequests());
        representations = new HashMap<>();
    }

    public void putRepresentation(Integer id, RepresentationDASH r) {
        if(!representations.containsKey(id))
            representations.put(id, r);
    }

    public RepresentationDASH representation4URL(Resource res) {
        for (RepresentationDASH rep: representations.values()) {
            if (rep.containsResource(res.getFullurl()))
                return rep;
        }
        return null;
    }

    public RepresentationDASH getRepresentation(Integer id) {
        return representations.get(id);
    }

    @Override
    public String getType() {
        return DESCRIPTION;
    }

    @Override
    public String toString() {
        return super.toString() + "DASH representations: " + representations.size();
    }
}
