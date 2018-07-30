package es.um.app.icn;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
        Optional<RepresentationDASH> optionalRepresentationDASH = representations.values().parallelStream()
                .filter(x -> x.containsResource(res.getFullurl()))
                .findFirst();
        return optionalRepresentationDASH.orElse(null);
    }

    public RepresentationDASH getRepresentation(Integer id) {
        return representations.get(id);
    }

    public Integer getRepresentationCount() {return representations.size();}

    public List<Integer> getRepresentationIds() {return representations.keySet().stream().sorted().collect(Collectors.toList());}

    @Override
    public String getType() {
        return DESCRIPTION;
    }

    @Override
    public String toString() {
        return super.toString() + "DASH representations: " + representations.size();
    }
}
