package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Site {
   private static final Logger LOGGER = LoggerFactory.getLogger(Domain.class);

   private String  id;
   private String  domainId;
   private String  url;
   private String  source;
   private Long    visitTime;
   private Integer size;

   private static final String SEL = 
      "select id from site where id = ?";
   private static final String INS =
      "insert into site values (?,?,?,?,?,?)";
   //only the visitTime and size are expected to change after insertion:
   private static final String UPD = 
      "update site set visitTime = ?, size = ? where id = ?";

   public Site(String url, String source) {
      id = UUID.randomUUID().toString();
      this.url = url;
      this.source = source;
   }

   public void save() throws DalException {
      Util.notNull(id, "id");
      Util.notNull(url, "url");
      Util.notNull(source, "source");
      if (domainId == null) {
         domainId = Domain.getFromUrl(url).getId();
      }

      // BoneCp.getConnection returns a JSE 6 Connection, which does
      // not implement java.lang.AutoCloseable, so we can't use the
      // helpful try-with-resources statement here... (BoneCp 0.7.1)
      Connection conn = null; 
      try {
         conn = Pool.getConnection();
         if (Util.simplePkExists(conn, SEL, id)) {
            update(conn);
         } else {
            insert(conn);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         Util.close(conn);
      }
   }

   private void insert(Connection conn) throws DalException {
      PreparedStatement ps = null; 
      try {
         ps = conn.prepareStatement(INS);
         ps.setString(1, id);
         ps.setString(2, domainId);
         ps.setString(3, url);
         ps.setString(4, source);
         if (visitTime != null) {
            ps.setDate(5, new Date(visitTime));
         } else {
            ps.setNull(5, Types.DATE);
         }
         if (visitTime != null) {
            ps.setInt(6, size);
         } else {
            ps.setNull(6, Types.INTEGER);
         }
         ps.executeUpdate();
         LOGGER.info("inserted new site: {}", id);
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         Util.close(ps);
      }
   }

   private void update(Connection conn) throws DalException {
      //TODO
   }
}
