package es.um.app.icn;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Created by Jordi Ortiz on 30/10/17.
 */
public class RepresentationDASH {
    private int id;
    private int width;
    private int height;
    private int frameRate;
    private long bandwidth;
    private String codec;
    private String mimeType;
    private String baseURL;
    private ConcurrentLinkedQueue<Integer> dependencies;
    private ConcurrentLinkedQueue<String> urls;
    private boolean prefetched;

    public RepresentationDASH(int id, int width, int height, int frameRate, long bandwidth, String codec, String mimeType) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.bandwidth = bandwidth;
        this.codec = codec;
        this.mimeType = mimeType;
        dependencies = new ConcurrentLinkedQueue<>();
        urls = new ConcurrentLinkedQueue<>();
        prefetched = false;
    }

    public int getId() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getFrameRate() {
        return frameRate;
    }

    public long getBandwidth() {
        return bandwidth;
    }

    public String getCodec() {
        return codec;
    }

    public String getMimeType() {
        return mimeType;
    }

    public List<Integer> getDependencies() {
        return dependencies.stream().collect(Collectors.toList());
    }


    public String getBaseURL() {
        return baseURL;
    }

    public void setBaseURL(String baseURL) {
        this.baseURL = baseURL;
    }

    public boolean isPrefetched() {
        return prefetched;
    }

    public void setPrefetched(boolean prefetched) {
        this.prefetched = prefetched;
    }

    public List<String> getFullUrls() {
        ConcurrentLinkedQueue<String> fullurls = new ConcurrentLinkedQueue<>();
        urls.parallelStream().forEach(x -> fullurls.add(baseURL + x));
        return fullurls.stream().collect(Collectors.toList());
    }

    public List<String> getUrls() {
        return urls.stream().collect(Collectors.toList());
    }

    public boolean containsResource(String url) {
        return urls.stream().anyMatch(x -> url.equals(baseURL + x));
    }

    public void putResource(String url) {
        if (!urls.contains(url)) {
            urls.add(url);
        }
    }

    public void setDependency(Integer dep) {
        if (!dependencies.contains(dep)) {
            dependencies.add(dep);
        }
    }

}
