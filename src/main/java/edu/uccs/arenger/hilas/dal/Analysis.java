package edu.uccs.arenger.hilas.dal;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;

//Just to make things easier...
public final class Analysis {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Analysis.class);

   private Connection conn = null;

   private static final String INS_ERR =
      "insert into analysis (domainId, domain) values (?, 'error')";
   private String insertSql;

   public Analysis() {
      try {
         insertSql = IOUtils.toString(
            ClassLoader.getSystemResourceAsStream("analyze.sql"));
      } catch (IOException e) {
         LOGGER.error("error loading resource: {}", e.getMessage());
      }
   }

   private void insErr(String domainId) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(INS_ERR)) {
         ps.setString(1, domainId);
         if (ps.executeUpdate() != 1) {
            throw new SQLException("expected return of 1");
         }
      }
   }

   private void sbsSum(String domainId) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
         for (int i = 1; i <= 7; i++ ) {
            ps.setString(i, domainId);
         }
         if (ps.executeUpdate() != 1) {
            LOGGER.warn("no insert for {}", domainId);
            insErr(domainId);
         } else {
            LOGGER.info("added row for {}", domainId);
         }
      }
   }

   public void go() throws DalException {
      String domainId;
      try {
         conn = Pool.getConnection();
         while ((domainId = Domain.getUnanalyzedId()) != null) {
            sbsSum(domainId);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         if (conn != null) { Util.close(conn); }
      }
   }
}
