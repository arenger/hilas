package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;

public final class SafeBrowseResult {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(SafeBrowseResult.class);

   private static final String INS =
      "insert into SafeBrowseResult (sbsId, domainId, result, extra) " +
      "values (?, ?, ?, ?)";

   private Sbs     sbs;
   private String  domainId;
   private Result  result;
   private String  extra;

   public enum Result {
      OK(0), WARN(1), BAD(2), ERROR(-1);
      private int code;
      Result(int code) {
         this.code = code;
      }
      public int getCode() {
         return code;
      }
   }

   public SafeBrowseResult(String domainId, Sbs sbs) {
      this.domainId = domainId;
      this.sbs = sbs;
   }

   public void setResult(Result result) {
      this.result = result;
   }

   public void setExtra(String extra) {
      this.extra = extra;
   }

   public void insert() throws DalException {
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setInt(1, sbs.getId());
         ps.setString(2, domainId);
         if (result != null) {
            ps.setInt(3, result.getCode());
         } else { 
            ps.setNull(3, Types.INTEGER);
         }
         if (extra != null) {
            ps.setString(4, extra);
         } else { 
            ps.setNull(4, Types.VARCHAR);
         }
      } catch (SQLException e) {
         throw new DalException(e);
      }
   }

   public static void batchInsert(List<SafeBrowseResult> batch) 
      throws DalException {
      if (batch == null)     { return; }
      if (batch.size() == 0) { return; }
      Connection conn = null;
      PreparedStatement ps = null;
      try {
         conn = Pool.getConnection();
         ps = conn.prepareStatement(INS);
         conn.setAutoCommit(false);

         for (SafeBrowseResult r : batch) {
            ps.setInt(1, r.sbs.getId());
            ps.setString(2, r.domainId);
            if (r.result != null) {
               ps.setInt(3, r.result.getCode());
            } else { 
               ps.setNull(3, Types.INTEGER);
            }
            if (r.extra != null) {
               ps.setString(4, r.extra);
            } else { 
               ps.setNull(4, Types.VARCHAR);
            }
            ps.addBatch();
         }
         ps.executeBatch();
         conn.commit();
         LOGGER.debug("batch insert of {} sbs results", batch.size());
      } catch (SQLException e) {
         if (conn != null) {
            try { conn.rollback(); } catch(SQLException ex) {
               LOGGER.error("rollback problem", ex.getMessage());
            }
         }
         throw new DalException(e);
      } finally {
         Util.close(ps);
         Util.setAutoCommit(conn, true);
         Util.close(conn);
      }
   }
}
