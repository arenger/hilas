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

/* Yes, this class is a whole lot like the JavaScript class, and the DB
 * schema could be better normalized with a "Resource" table that holds
 * both js and css resources with a type... but after designing down that
 * road for a bit, it seemed that it could affect the speed of analysis,
 * which is the whole point of hilas... so, my appologies to Dr. Codd. */
public class Css {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Css.class);

   private String  id;
   private URL     url;
   private String  md5;
   private int     size;
   private boolean validated;

   private static final String MD5_GET =
      "select * from css where md5 = ?";
   private static final String INSERT =
      "insert into css values (?,?,?,?,?)";
   private static final String LINK2SITE =
      "insert into sitecss values (?,?)";

   private Css() {}

   public Css(URL url, String md5, int size) {
      id = UUID.randomUUID().toString();
      this.url = url;
      this.md5 = md5;
      this.size = size;
      validated = false;
   }

   public static Css get(String md5) throws DalException {
      Css ret = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(MD5_GET)) {
         ps.setString(1, md5);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            Css css = new Css();
            css.id = rs.getString("id");
            try {
               css.url = new URL(rs.getString("url"));
            } catch (MalformedURLException e) {
               LOGGER.warn("malformed url for id {}", css.id);
            }
            css.md5 = md5;
            css.size = rs.getInt("size");
            css.validated = rs.getBoolean("validated");
            ret = css;
         }
      } catch (SQLException e) { throw new DalException(e); }
      return ret;
   }

   public void insert() throws DalException {
      Util.notNull(id, "id");
      Util.notNull(url, "url");
      Util.notNull(md5, "md5");
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INSERT)) {
         ps.setString(1, id);
         ps.setString(2, url.toString());
         ps.setString(3, md5);
         ps.setInt(4, size);
         ps.setBoolean(5, validated);
         ps.executeUpdate();
         LOGGER.info("inserted new css: {} - {}", id, url);
      } catch (SQLException e) { Util.warnIfDup(e); }
   }

   public void linkToSite(String siteId) throws DalException {
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(LINK2SITE)) {
         ps.setString(1, siteId);
         ps.setString(2, id);
         ps.executeUpdate();
         LOGGER.debug("new SiteCss entry: {} - {}", siteId, id);
      } catch (SQLException e) {
         if (e.getErrorCode() == Util.MYSQL_DUP_CODE) {
            LOGGER.debug("already linked: {} - {}", siteId, id);
         } else {
            throw new DalException("Error code " + e.getErrorCode(), e);
         }
      }
   }

   public void setValidated(boolean validated) {
      this.validated = validated;
   }
}
