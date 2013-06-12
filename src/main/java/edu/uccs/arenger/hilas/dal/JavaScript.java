package edu.uccs.arenger.hilas.dal;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaScript extends SiteResource {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(JavaScript.class);

   private String  id;
   private URL     url;
   private String  md5;
   private int     size;
   private boolean jsLinted;
   private boolean jsHinted;

   private static final String MD5_GET =
      "select * from javascript where md5 = ?";
   private static final String INSERT =
      "insert into javascript values (?,?,?,?,?,?)";
   private static final String LINK2SITE =
      "insert into sitejs values (?,?)";

   private JavaScript() {}

   public JavaScript(URL url, String md5, int size) {
      // leave as null until successful write to db
      this.url = url;
      this.md5 = md5;
      this.size = size;
      jsLinted = false;
      jsHinted = false;
   }

   public static JavaScript get(String md5) throws DalException {
      JavaScript ret = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(MD5_GET)) {
         ps.setString(1, md5);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            JavaScript js = new JavaScript();
            js.id = rs.getString("id");
            try {
               js.url = new URL(rs.getString("url"));
            } catch (MalformedURLException e) {
               LOGGER.warn("malformed url for id {}", js.id);
            }
            js.md5 = md5;
            js.size = rs.getInt("size");
            js.jsLinted = rs.getBoolean("jsLinted");
            js.jsHinted = rs.getBoolean("jsHinted");
            ret = js;
         }
      } catch (SQLException e) { throw new DalException(e); }
      return ret;
   }

   public void insert() throws DalException {
      id = UUID.randomUUID().toString();
      Util.notNull(url, "url");
      Util.notNull(md5, "md5");
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INSERT)) {
         ps.setString(1, id);
         ps.setString(2, url.toString());
         ps.setString(3, md5);
         ps.setInt(4, size);
         ps.setBoolean(5, jsLinted);
         ps.setBoolean(6, jsHinted);
         ps.executeUpdate();
         LOGGER.info("inserted new js: {} - {}", id, url);
      } catch (SQLException e) {
         id = null;
         throw DalException.of(e);
      }
   }

   public void linkToSite(String siteId) throws DalException {
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(LINK2SITE)) {
         ps.setString(1, siteId);
         ps.setString(2, id);
         ps.executeUpdate();
         LOGGER.debug("new SiteJs entry: {} - {}", siteId, id);
      } catch (SQLException e) { throw DalException.of(e); }
   }

   public String getId() {
      return id;
   }

   public void setJsHinted(boolean jsHinted) {
      this.jsHinted = jsHinted;
   }
}
