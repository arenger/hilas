package edu.uccs.arenger.hilas.dal;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Domain {
   private static final Logger LOGGER = LoggerFactory.getLogger(Domain.class);

   private String id;
   private String domain;
   
   private static ConcurrentMap<String,String> cache
      = new ConcurrentHashMap<String,String>(); //domain->id

   private static final String INS = "insert into domain values (?, ?)";
   private static final String SEL_DOM =
      "select id from domain where domain = ?";
   private static final String SEL_SBS =
      "select * from domain d " +
      "left join safebrowseresult r on d.id = r.domainid " +
      "group by d.id having (sum(ifnull(r.sbsid,0)) & ?) = 0";
   private static final String SEL_COUNT = "select count(*) from domain";

   private Domain(String id, String domain) {
      this.id = id;
      this.domain = domain;
   }

   public static Domain get(URL url) throws DalException {
      return get(url.getHost());
   }

   public static Domain get(String domain) throws DalException {
      String id = cache.get(domain);
      if (id != null) {
         return new Domain(id, domain);
      } else {
         id = getFromDb(domain);
         if (id != null) {
            cache.put(domain,id);
            return new Domain(id, domain);
         }
      }
      return insert(domain);
   }

   /* If the domain specified by url is new, then return its ID.
    * Otherwise (if we've already seen it), then return null. */
   public static String getIdIfNew(URL url) throws DalException {
      String domain = url.getHost();
      String id = cache.get(domain);
      if (id != null) {
         return null;
      } else {
         id = getFromDb(domain);
         if (id != null) {
            cache.put(domain,id);
            return null;
         }
      }
      return insert(domain).id;
   }

   private static Domain insert(String domain) throws DalException {
      Domain ret = null;
      String id = UUID.randomUUID().toString();
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
         //LOGGER.debug("db lookup for {} - {}", domain,
         //   id == null ? "not found" : "found" );
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   public String getId() {
      return id;
   }
   
   public String getDomain() {
      return domain;
   }

   public static List<Domain> getUnvetted(Sbs sbs, int limit)
      throws DalException {
      List<Domain> ret = new ArrayList<Domain>();
      if (limit == 0) { return ret; }
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL_SBS)) {
         ps.setInt(1, sbs.getId());
         ps.setInt(2, limit);
         ResultSet rs = ps.executeQuery();
         while (rs.next()) {
            ret.add(new Domain(rs.getString("id"), rs.getString("domain")));
         }
      } catch (SQLException e) { throw new DalException(e); }
      return ret;
   }

   public static int getCount() throws DalException {
      int ret = -1;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL_COUNT)) {
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            ret = rs.getInt(1);
         }
      } catch (SQLException e) { throw new DalException(e); }
      return ret;
   }
}
