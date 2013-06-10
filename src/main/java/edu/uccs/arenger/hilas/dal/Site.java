package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Site {
   private static final Logger LOGGER = LoggerFactory.getLogger(Site.class);

   private String  id;
   private String  domainId;
   private String  url;
   private String  source;
   private Long    visitTime;
   private Integer size;
   private boolean htmlValidated;

   private static final String SEL_UNVISITED = 
      "select * from site where visitTime is null limit 1";
   private static final String INS =
      "insert into site values (?,?,?,?,?,?,?)";

   // only the visitTime, size, and htmlValidated are
   // expected to change after insertion:
   private static final String UPD = 
      "update site set visitTime = ?, size = ?, htmlValidated = ? " +
      "where id = ?";

   public Site(String url, String source) {
      id = UUID.randomUUID().toString();
      this.url = url;
      this.source = source;
      htmlValidated = false;
   }

   private Site(ResultSet rs) throws SQLException {
      id = rs.getString("id");
      domainId = rs.getString("domainId");
      url = rs.getString("url");
      source = rs.getString("source");
      Timestamp ts = rs.getTimestamp("visitTime");
      if (ts != null) { visitTime = ts.getTime(); }
      size = rs.getInt("size"); if (rs.wasNull()) { size = null; }
      htmlValidated = rs.getBoolean("htmlValidated");
   }

   public void insert() throws DalException {
      Util.notNull(id, "id");
      Util.notNull(url, "url");
      Util.notNull(source, "source");
      if (domainId == null) {
         domainId = Domain.getFromUrl(url).getId();
      }

      // Note: The close method of BoneCp's ConnectionHandle doesn't
      // actually CLOSE the connection -- it just releases it back to
      // the pool, as I understand.  Further, ConnectionHandle implements
      // java.sql.Connection, so I assume that when it's used with JSE7,
      // the release-to-pool will happen as part of the try-with-resources
      // construct, which is nice...
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setString(1, id);
         ps.setString(2, domainId);
         ps.setString(3, url);
         ps.setString(4, source);
         if (visitTime != null) {
            ps.setTimestamp(5, new Timestamp(visitTime));
         } else {
            ps.setNull(5, Types.DATE);
         }
         if (size != null) {
            ps.setInt(6, size);
         } else {
            ps.setNull(6, Types.INTEGER);
         }
         ps.setBoolean(7, htmlValidated);
         ps.executeUpdate();
         LOGGER.info("inserted new site: {} - {}", id, url);
      } catch (SQLException e) {
         if ((e.getErrorCode() == Util.MYSQL_DUP_CODE) &&
             !e.getMessage().contains("PRIMARY")) {
            LOGGER.warn(e.getMessage());
         } else {
            throw new DalException("Error code " + e.getErrorCode(), e);
         }
      }
   }

   public void update() throws DalException {
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(UPD)) {
         if (visitTime != null) {
            ps.setTimestamp(1, new Timestamp(visitTime));
         } else {
            ps.setNull(1, Types.DATE);
         }
         if (size != null) {
            ps.setInt(2, size);
         } else {
            ps.setNull(2, Types.INTEGER);
         }
         ps.setBoolean(3, htmlValidated);
         ps.setString(4, id);
         ps.executeUpdate();
      } catch (SQLException e) {
         throw new DalException(e);
      }
   }

   public static Site nextUnvisited() throws DalException {
      Site site = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL_UNVISITED)) {
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            site = new Site(rs);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      }
      return site;
   }

   public String getUrl() {
      return url;
   }

   public void setVisitTime(Long visitTime) {
      this.visitTime = visitTime;
   }

   public void setSize(Integer size) {
      this.size = size;
   }

   public void setHtmlValidated(boolean htmlValidated) {
      this.htmlValidated = htmlValidated;
   }
}
