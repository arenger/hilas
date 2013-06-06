package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Site {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

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

   public Site() {
      id = UUID.randomUUID().toString();
   }

   public void save() throws DalException {
      Util.notNull(id, "id");
      Util.notNull(url, "url");
      Util.notNull(source, "source");
      Util.notNull(domainId, "domainId");

      // BoneCp.getConnection returns a JSE 6 Connection, which does
      // not implement java.lang.AutoCloseable, so we can't use the
      // helpful try-with-resources statement here... (BoneCp 0.7.1)
      Connection conn = null; 
      try {
         conn = Pool.getConnection();
      } catch (SQLException e) {
         LOGGER.error("problem",e);
      } finally {
         Util.close(conn);
      }
   }
}
