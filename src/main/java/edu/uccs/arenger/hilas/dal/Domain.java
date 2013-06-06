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
   
   private static ConcurrentMap<String,String> cache; //domain->id
   private static Pattern domainFromUrl =
      Pattern.compile("https?:\\/\\/(.+?)\\/");

   private static final String INS = "insert into domain values (?, ?)";
   private static final String SEL = "select id, domain from domain";

   private Domain(String id, String domain) {
      this.id = id;
      this.domain = domain;
   }

   public static Domain getFromUrl(String url) throws DalException {
      Matcher m = domainFromUrl.matcher(url);
      String domain = null;
      if (m.matches()) {
         domain = m.group(1);
      } else {
         throw new DalException("could not parse url");
      }
      return get(domain);
   }

   public static Domain get(String domain) throws DalException {
      String id = cache.get(domain);
      if (id != null) {
         return new Domain(id, domain);
      } else {
         id = UUID.randomUUID().toString();
      }

      Domain ret = null;
      Connection conn = null; 
      PreparedStatement ps = null; 
      try {
         conn = Pool.getConnection();
         ps = conn.prepareStatement(INS);
         ps.setString(1, id);
         ps.setString(2, domain);
         ps.executeUpdate();
         cache.put(domain,id);
         ret = new Domain(id, domain);
         LOGGER.info("inserted new domain: {}", id);
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         Util.close(ps);
         Util.close(conn);
      }
      return ret;
   }

   public static void initCache() throws DalException {
      LOGGER.info("Loading cache");
      cache = new ConcurrentHashMap<String,String>();
      Connection conn = null; 
      PreparedStatement ps = null; 
      ResultSet rs = null;
      try {
         conn = Pool.getConnection();
         ps = conn.prepareStatement(SEL);
         rs = ps.executeQuery();
         while (rs.next()) {
            cache.put(rs.getString(2),rs.getString(1));
         }
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         Util.close(rs);
         Util.close(ps);
         Util.close(conn);
      }
   }

   public String getId() {
      return id;
   }
   
   public String getDomain() {
      return domain;
   }
}
