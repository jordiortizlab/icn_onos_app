package es.um.app.icn;

import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IcnDistributedCacheSVCDASH extends IcnClosestCacheDASH {
    private final static Logger log = LoggerFactory.getLogger(IcnDistributedCacheSVCDASH.class);
    static final String DESCRIPTION = "DISTRIBUTEDSVCDASH";

    private ConcurrentHashMap<String, ConcurrentHashMap<String, IMiddlebox>> precomputedCachesXresourceXurl;

    public IcnDistributedCacheSVCDASH() {
        super();
        precomputedCachesXresourceXurl = new ConcurrentHashMap<>();
    }

    @Override
    public Cache findCacheForNewResource(IcnService service, String uri, DeviceId sw, PortNumber inPort) {
        Map<IMiddlebox, Integer> middleBoxesDistance = service.getMiddleBoxesDistance(caches.values(), sw);
        Stream<Map.Entry<IMiddlebox, Integer>> sorted = middleBoxesDistance.entrySet().stream().sorted(new IntegerValueComparator());

        // IF it is an MPD just use the nearest cache
        if (uri.endsWith(".mpd") || uri.endsWith("*.MPD")) {
            Map.Entry<IMiddlebox, Integer> iMiddleboxIntegerEntry = middleBoxesDistance.entrySet().stream().min(new IntegerValueComparator()).orElse(null);
            if (iMiddleboxIntegerEntry != null)
                return (Cache)iMiddleboxIntegerEntry.getKey();
            return null;
        }

        ResourceHTTP resourceHTTP = new ResourceHTTP();
        resourceHTTP.setFullurl(uri);
        Optional<ResourceHTTP> resourceOpt = resources.values().parallelStream().filter(x -> {
            if (!x.getType().equals(ResourceHTTPDASH.DESCRIPTION))
                return false;
            ResourceHTTPDASH r = (ResourceHTTPDASH) x;
            return r.containsURL(uri);
        }).findFirst();

        ResourceHTTPDASH rfull = (ResourceHTTPDASH)resourceOpt.orElse(null);
        if (rfull == null) {
            log.error("There is no resource available for uri {}", uri);
            return null;
        }

        // Look for precomputed cache
        ConcurrentHashMap<String, IMiddlebox> precomputedCaches = precomputedCachesXresourceXurl.getOrDefault(rfull.getFullurl(), null);
        if (precomputedCaches != null) {
            log.info("Locating precomputed cache");
            IMiddlebox precomputedcache = precomputedCaches.getOrDefault(uri, null);
            if (precomputedcache != null) {
                return (Cache)precomputedcache;
            }
        }

        // Precompute all caches
        log.info("Precomputing caches for {}", rfull.getFullurl());
        ConcurrentHashMap<String, IMiddlebox> cacheXurl = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, Integer> fullUrlsRepresentationIds = rfull.getFullUrlsRepresentationIds();

        Integer representationCount = rfull.getRepresentationCount();
        Double dependencycacheratio = Math.ceil(representationCount / caches.size()); // Next integer
        log.debug("DependenciesCache Ratio: {}", dependencycacheratio);
        List<Integer> representationIds = rfull.getRepresentationIds();
        List<IMiddlebox> orderedcachelist = middleBoxesDistance.entrySet().stream().sorted(new IntegerValueComparator()).map(Map.Entry::getKey).collect(Collectors.toList());
        log.debug("Cache list: {}", orderedcachelist.toString());

        fullUrlsRepresentationIds.entrySet().parallelStream().forEach(x -> {
            int representationIdx = representationIds.indexOf(x.getValue());
            log.debug("Request representation index {} of {}", representationIdx, representationCount);
            Double position = Math.floor(representationIdx / dependencycacheratio); // Previous integer
            log.debug("Position selected {}", position.intValue());
            IMiddlebox iMiddlebox = orderedcachelist.get(position.intValue());
            log.debug("Selected cache {}", iMiddlebox.getName());
            cacheXurl.put(x.getKey(), iMiddlebox);
        });

        precomputedCachesXresourceXurl.put(rfull.getFullurl(), cacheXurl);

        return (Cache)cacheXurl.getOrDefault(uri, null);
    }

    class IntegerValueComparator implements Comparator<Map.Entry<IMiddlebox, Integer>> {

        @Override
        public int compare(Map.Entry<IMiddlebox, Integer> o1, Map.Entry<IMiddlebox, Integer> o2) {
            return Integer.compare(o1.getValue(), o2.getValue());
        }
    }
}
