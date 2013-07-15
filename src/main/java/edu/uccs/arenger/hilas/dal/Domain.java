package edu.uccs.arenger.hilas.dal;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Domain {
   private static final Logger LOGGER = LoggerFactory.getLogger(Domain.class);

   private static final Set<String> BIG7;

   static {
      BIG7 = new HashSet<String>();
      BIG7.add("biz"); BIG7.add("com"); BIG7.add("edu"); BIG7.add("gov");
      BIG7.add("mil"); BIG7.add("net"); BIG7.add("org");
   };

   private String id;
   private String domain;
   
   private static ConcurrentMap<String,String> cache
      = new ConcurrentHashMap<String,String>(); //domain->id
   private static Set<String> mains
      = Collections.newSetFromMap(new ConcurrentHashMap<String,Boolean>());

   private static final String INS = "insert into domain values (?, ?, ?)";
   private static final String SEL_DOM =
      "select id from domain where domain = ?";
   private static final String SEL_SBS =
      "select * from domain d " +
      "left join safebrowseresult r on d.id = r.domainid " +
      "group by d.id having (sum(ifnull(r.sbsid,0)) & ?) = 0 limit ?";
   private static final String SEL_COUNT = "select count(*) from domain";
   private static final String MAIN_EXISTS =
      "select 1 from domain where main = ? limit 1";

   private static final Pattern onlyDigits = Pattern.compile("\\d+");

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
            mains.add(getMain(domain));
            return new Domain(id, domain);
         }
      }
      return insert(domain);
   }

   public static String getMain(URL url) {
      return getMain(url.getHost());
   }

   // a rough attempt to identify the main part of this domain -
   public static String getMain(String domain) {
      if (domain == null) { return null; }
      String[] parts = domain.toLowerCase().split("\\.");
      if (parts.length < 2) { return domain; }
      String last = parts[parts.length - 1];
      String stl  = parts[parts.length - 2];
      if (onlyDigits.matcher(last).matches()) {
         return domain; // assume it's an IP address
      }
      if (parts.length == 2) {
         return domain;
      }
      String main = null;
      String test = stl + "." + last;
      if (test.length() < 6) {
         main = parts[parts.length - 3] + "." + test;
      } else if ((last.length() == 2) && (BIG7.contains(stl))) {
         main = parts[parts.length - 3] + "." + test;
      } else {
         main = test;
      }
      return main;
   }

   public static boolean seenMain(URL url) throws DalException {
      String main = getMain(url.getHost());
      if (mains.contains(main)) { return true; }
      boolean ret = false;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(MAIN_EXISTS)) {
         ps.setString(1, main);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            ret = true;
            mains.add(main);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      }
      return ret;
   }

   private static Domain insert(String domain) throws DalException {
      Domain ret = null;
      String id = UUID.randomUUID().toString();
      String main = getMain(domain);
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setString(1, id);
         ps.setString(2, domain);
         ps.setString(3, main);
         ps.executeUpdate();
         cache.put(domain,id);
         mains.add(main);
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

   public static void main(String[] args) throws Exception {
      if (args.length < 1) {
         System.out.println("see usage");
         System.exit(1);
      }
      try (BufferedReader in
         = new BufferedReader(new FileReader(args[0]))) {
         String dom;
         while ((dom = in.readLine()) != null) {
            String main = getMain(dom);
            System.out.printf("%30s : %30s  %s\n", dom, main,
               (dom.length() != main.length()) ? "*" : ""
            );
         }
      }
   }
}
