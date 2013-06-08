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
   private static final Logger LOGGER = LoggerFactory.getLogger(Site.class);
   private static final int MYSQL_DUP_CODE = 1062;

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

   public void insert() throws DalException {
      Util.notNull(id, "id");
      Util.notNull(url, "url");
      Util.notNull(source, "source");
      if (domainId == null) {
         domainId = Domain.getFromUrl(url).getId();
      }
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
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
         LOGGER.info("inserted new site: {} - {}", id, url);
      } catch (SQLException e) {
         if ((e.getErrorCode() == MYSQL_DUP_CODE) &&
             !e.getMessage().contains("PRIMARY")) {
            LOGGER.warn(e.getMessage());
         } else {
            throw new DalException("Error code " + e.getErrorCode(), e);
         }
      }
   }

   public void update() throws DalException {
      //TODO
   }
}
