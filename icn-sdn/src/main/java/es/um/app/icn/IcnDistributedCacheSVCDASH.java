package es.um.app.icn;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IcnDistributedCacheSVCDASH extends IcnClosestCacheDASH {
    private final static Logger log = LoggerFactory.getLogger(IcnDistributedCacheSVCDASH.class);
    static final String DESCRIPTION = "DISTRIBUTEDSVCDASH";

    @Override
    public Cache findCacheForNewResource(IcnService service, String resourceName, DeviceId sw, PortNumber inPort) {
        ResourceHTTP resourceHTTP = resources.get(resourceName);
        if (resourceHTTP == null)
            return null;
        Map<IMiddlebox, Integer> middleBoxesDistance = service.getMiddleBoxesDistance(caches.values(), sw);
        Stream<Map.Entry<IMiddlebox, Integer>> sorted = middleBoxesDistance.entrySet().stream().sorted(new IntegerValueComparator());

        // IF it is an MPD just use the nearest cache
        if (resourceHTTP.getFilename().endsWith(".mpd") || resourceHTTP.getFilename().endsWith("*.MPD")) {
            Map.Entry<IMiddlebox, Integer> iMiddleboxIntegerEntry = middleBoxesDistance.entrySet().stream().min(new IntegerValueComparator()).orElse(null);
            if (iMiddleboxIntegerEntry != null)
                return (Cache)iMiddleboxIntegerEntry.getKey();
            return null;
        }

        Optional<ResourceHTTP> resourceOpt = resources.values().parallelStream().filter(x -> {
            if (!x.getType().equals(ResourceHTTPDASH.DESCRIPTION))
                return false;
            ResourceHTTPDASH r = (ResourceHTTPDASH) x;
            RepresentationDASH representationDASH = r.representation4URL(resourceHTTP);
            if (representationDASH != null)
                return true;
            return false;
        }).findFirst();

        ResourceHTTPDASH rfull = (ResourceHTTPDASH)resourceOpt.orElse(null);
        RepresentationDASH representationDASH = rfull.representation4URL(resourceHTTP);
        Integer representationCount = rfull.getRepresentationCount();
        Double dependencycacheratio = Math.ceil(representationCount / caches.size()); // Next integer
        log.debug("DependenciesCache Ratio: {}", dependencycacheratio);
        List<Integer> representationIds = rfull.getRepresentationIds();
        int representationIdx = representationIds.indexOf(representationDASH.getId());
        log.debug("Request representation index {} of {}", representationIdx, representationCount);
        Double position = Math.floor(representationIdx / dependencycacheratio); // Previous integer
        log.debug("Position selected {}", position.intValue());
        List<IMiddlebox> orderedcachelist = middleBoxesDistance.entrySet().stream().sorted(new IntegerValueComparator()).map(Map.Entry::getKey).collect(Collectors.toList());
        log.debug("Cache list: {}", orderedcachelist.toString());
        IMiddlebox iMiddlebox = orderedcachelist.get(position.intValue());
        log.debug("Selected cache {}", iMiddlebox.getName());
        return (Cache)iMiddlebox;
    }

    class IntegerValueComparator implements Comparator<Map.Entry<IMiddlebox, Integer>> {

        @Override
        public int compare(Map.Entry<IMiddlebox, Integer> o1, Map.Entry<IMiddlebox, Integer> o2) {
            return Integer.compare(o1.getValue(), o2.getValue());
        }
    }
}
