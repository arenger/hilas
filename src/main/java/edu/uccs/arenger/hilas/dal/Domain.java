package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Domain {
   private static final Logger LOGGER = LoggerFactory.getLogger(Domain.class);

   private String id;
   private String domain;
   
   private static ConcurrentMap<String,String> cache
      = new ConcurrentHashMap<String,String>(); //domain->id
   private static Pattern domainFromUrl =
      Pattern.compile("^https?:\\/\\/(.+?)\\/");

   private static final String INS = "insert into domain values (?, ?)";
   private static final String SEL_DOM =
      "select id from domain where domain = ?";

   private Domain(String id, String domain) {
      this.id = id;
      this.domain = domain;
   }

   public static Domain getFromUrl(String url) throws DalException {
      Matcher m = domainFromUrl.matcher(url);
      String domain = null;
      if (m.find()) {
         domain = m.group(1);
      } else {
         throw new DalException("could not parse url");
      }
      return get(domain);
   }

   public static Domain get(String domain) throws DalException {
      String id = cache.get(domain);
      if (id != null) {
         LOGGER.debug("cache hit: {}", domain);
         return new Domain(id, domain);
      } else {
         id = getFromDb(domain);
         if (id != null) {
            cache.put(domain,id);
            return new Domain(id, domain);
         }
      }

      id = UUID.randomUUID().toString();
      Domain ret = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setString(1, id);
         ps.setString(2, domain);
         ps.executeUpdate();
         cache.put(domain,id);
         ret = new Domain(id, domain);
         LOGGER.info("inserted new domain: {} - {}", id, domain);
      } catch (SQLException e) {
         throw new DalException(e);
      }
      return ret;
   }

   private static String getFromDb(String domain) throws DalException {
      String id = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL_DOM)) {
         ps.setString(1, domain);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            id = rs.getString(1);
         }
         LOGGER.debug("db lookup for {} - {}", domain,
            id == null ? "not found" : "found" );
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   public String getId() {
      return id;
   }
   
   public String getDomain() {
      return domain;
   }
}
