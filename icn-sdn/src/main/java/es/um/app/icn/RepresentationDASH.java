package es.um.app.icn;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Created by Jordi Ortiz on 30/10/17.
 */
public class RepresentationDASH {
    int id;
    int width;
    int height;
    int frameRate;
    long bandwidth;
    String codec;
    String mimeType;
    HashMap<Integer, RepresentationDASH> dependencies;
    LinkedList<String> fullUrls;

    public RepresentationDASH(int id, int width, int height, int frameRate, long bandwidth, String codec, String mimeType) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.frameRate = frameRate;
        this.bandwidth = bandwidth;
        this.codec = codec;
        this.mimeType = mimeType;
        dependencies = new HashMap<>();
        fullUrls = new LinkedList<>();
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

    public HashMap<Integer, RepresentationDASH> getDependencies() {
        return dependencies;
    }

    public LinkedList<String> getFullUrls() {
        return fullUrls;
    }

    public boolean containsResource(String url) {
        return fullUrls.contains(url);
    }

    public void putResource(String url) {
        if (!fullUrls.contains(url)) {
            fullUrls.add(url);
        }
    }

    public void setDependencie(Integer dep, RepresentationDASH r) {
        if (!dependencies.containsKey(dep)) {
            dependencies.put(dep, r);
        }
    }

}
