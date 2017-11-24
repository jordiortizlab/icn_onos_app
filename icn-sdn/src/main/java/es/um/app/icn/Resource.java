package es.um.app.icn;

import java.util.Collection;

/**
 * Created by Jordi Ortiz on 30/10/17.
 */
public interface Resource {
    public String getId();
    public void setId(String id);
    public String getName();
    public void setName(String name);
    public long getRequests();
    public void setRequests(long requests);
    public void incrRequests();
    public Collection<Cache> getCaches();
    public void setCaches(Collection<Cache> caches);
    public String getFullurl();
    public void setFullurl(String fullurl);
    public String getBasePathUrl();
    public String getFilename();
    public Cache addCache(Cache cache);
    public Cache removeCache(Cache cache);
    public String getType();
}
