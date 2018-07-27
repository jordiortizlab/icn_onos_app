package es.um.app.icn;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;

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
    private LinkedList<Integer> dependencies;
    private LinkedList<String> urls;
    boolean prefetched;

    public RepresentationDASH(int id, int width, int height, int frameRate, long bandwidth, String codec, String mimeType) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.bandwidth = bandwidth;
        this.codec = codec;
        this.mimeType = mimeType;
        dependencies = new LinkedList<Integer>();
        urls = new LinkedList<>();
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

    public LinkedList<Integer> getDependencies() {
        return dependencies;
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

    public LinkedList<String> getFullUrls() {
        LinkedList<String> fullurls = new LinkedList<>();
        urls.stream().forEach(x -> fullurls.addLast(baseURL + x));
        return fullurls;
    }

    public LinkedList<String> getUrls() {
        return urls;
    }

    public boolean containsResource(String url) {
        Optional<String> first = urls.stream().filter(x -> url.equals(baseURL + x)).findFirst();
        if (first.isPresent())
             return true;
        return false;
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
