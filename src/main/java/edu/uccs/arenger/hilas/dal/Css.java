package edu.uccs.arenger.hilas.dal;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;

/* This class is a whole lot like the JavaScript class, and the DB
 * schema could be better normalized with a "Resource" table that holds
 * both js and css resources with a type... but after designing down that
 * road for a bit, it seemed that it could affect the speed of analysis,
 * which is the whole point of hilas... so, my appologies to Dr. Codd. */
public class Css extends SiteResource {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Css.class);

   private String  id;
   private URL     url;
   private int     size;
   private boolean validated;

   private static final String SEL =
      "select * from css where id = ?";
   private static final String INSERT =
      "insert into css values (?,?,?,?)";
   private static final String LINK2SITE =
      "insert into sitecss values (?,?)";

   private Css() {}

   public Css(URL url, String content) {
      id = Util.md5(content);
      this.url = url;
      size = content.length();
      validated = false;
   }

   public static Css get(String content) throws DalException {
      Css ret = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL)) {
         ps.setString(1, Util.md5(content));
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            Css css = new Css();
            css.id = rs.getString("id");
            try {
               css.url = new URL(rs.getString("url"));
            } catch (MalformedURLException e) {
               LOGGER.warn("malformed url for id {}", css.id);
            }
            css.size = rs.getInt("size");
            css.validated = rs.getBoolean("validated");
            ret = css;
         }
      } catch (SQLException e) { throw new DalException(e); }
      return ret;
   }

   public void insert() throws DalException {
      Util.notNull(url, "url");
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INSERT)) {
         ps.setString(1, id);
         ps.setString(2, url.toString());
         ps.setInt(3, size);
         ps.setBoolean(4, validated);
         ps.executeUpdate();
         LOGGER.info("inserted new css: {} - {}", id, url);
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
         LOGGER.debug("new SiteCss entry: {} - {}", siteId, id);
      } catch (SQLException e) { throw DalException.of(e); }
   }

   public String getId() {
      return id;
   }

   public void setValidated(boolean validated) {
      this.validated = validated;
   }
}
