package com.paulfernandosr.possystembackend.trustedclient;

import com.maxmind.geoip2.DatabaseReader;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.File;
import java.net.InetAddress;
import java.time.Instant;
import java.util.List;

/** Local MMDB only: login never sends addresses to an external HTTP service. */
@Service
public class GeoLocationService {
    public record Location(String status,String country,String region,String city,Integer accuracyRadiusKm,
                           String source,String databaseVersion,Instant observedAt) {}
    private final DatabaseReader database;
    private final String version;
    public GeoLocationService(@Value("${security.geoip.enabled:false}") boolean enabled,
            @Value("${security.geoip.database-path:}") String path) {
        DatabaseReader opened=null; String built=null;
        if(enabled) {
            try {
                if(path.isBlank()) throw new IllegalArgumentException();
                opened=new DatabaseReader.Builder(new File(path)).locales(List.of("es","en")).build();
                built=opened.getMetadata().buildTime().toString();
            } catch(Exception ignored) { throw new IllegalArgumentException("GeoIP database is not readable; check configuration"); }
        }
        this.database=opened; this.version=built;
    }
    public boolean isEnabled() { return database!=null; }
    public Location locate(String rawIp) {
        Instant now=Instant.now();
        if(database==null) return unknown("DISABLED",now);
        String ip=TrustedClientResolver.numericIp(rawIp);
        if(ip==null) return unknown("UNKNOWN",now);
        try {
            InetAddress address=InetAddress.getByName(ip);
            if(address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isSiteLocalAddress()
                    || address.isLinkLocalAddress() || address.isMulticastAddress()) return unknown("PRIVATE",now);
            var found=database.tryCity(address);
            if(found.isEmpty()) return unknown("UNKNOWN",now);
            var result=found.get();
            return new Location("ESTIMATED",result.getCountry().getIsoCode(),result.getMostSpecificSubdivision().getName(),
                    result.getCity().getName(),result.getLocation().getAccuracyRadius(),"MAXMIND_MMDB",version,now);
        } catch(Exception ignored) { return unknown("UNAVAILABLE",now); }
    }
    private Location unknown(String status,Instant now) { return new Location(status,null,null,null,null,"MAXMIND_MMDB",version,now); }
    @PreDestroy public void close() throws Exception { if(database!=null) database.close(); }
}
