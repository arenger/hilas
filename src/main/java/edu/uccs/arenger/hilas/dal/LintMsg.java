package edu.uccs.arenger.hilas.dal;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;

public final class LintMsg {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(LintMsg.class);

   private static ConcurrentMap<String,String> cache
      = new ConcurrentHashMap<String,String>(); //msg->id

   private static final String INS =
      "insert into lintmsg (id, message) values (?, ?)";
   private static final String SEL =
      "select id from lintmsg where message = ?";

   private static final String LINT_CLEAR =
      "delete from %s where %s = ?";
   private static final String LINT_LINK =
      "insert into %s values (?, ?)";

   public enum Subject { JS, CSS, HTML }

   /* Not trying to match the DB fields here... */
   private Subject subject;
   private String type;
   private String message;
   private String formattedMsg;

   public LintMsg(Subject subject, String type, String message) {
      this.subject = subject;
      this.type = type;
      this.message = message;
      formattedMsg = String.format("%s: %s: %s",
         subject.toString().toLowerCase(), type.toLowerCase(), message);
   }

   public String toString() {
      return formattedMsg;
   }

   public boolean equals(Object o) {
      if ((o == null) || !(o instanceof LintMsg)) {
         return false;
      } else {
         return ((LintMsg)o).formattedMsg.equals(formattedMsg);
      }
   }

   public int hashCode() {
      return formattedMsg.hashCode();
   }

   public static String idFor(LintMsg message) throws DalException {
      String id = cache.get(message.toString());
      if (id != null) {
         return id;
      }
      id = idFor(message.toString());
      if (id != null) {
         cache.put(message.toString(),id);
         return id;
      }

      id = UUID.randomUUID().toString();
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(INS)) {
         ps.setString(1, id);
         ps.setString(2, message.toString());
         ps.executeUpdate();
         cache.put(message.toString(),id);
         LOGGER.info("new jslint message: {}", message);
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   private static String idFor(String message) throws DalException {
      String id = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL)) {
         ps.setString(1, message);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            id = rs.getString(1);
         }
      } catch (SQLException e) { throw new DalException(e); }
      return id;
   }

   /* This method would need to change if JsLint were also incorporated...
    * but I'll keep it simple, since that might not happen.  */
   public static void associate(
      Subject subject, String subjectId, Set<String> msgIds)
      throws DalException {
      if (subjectId == null) { return; }
      if (msgIds == null) { msgIds = new HashSet<String>(); }

      String clearSql = null;
      String linkSql  = null;
      switch (subject) {
         case JS:
            clearSql = String.format(LINT_CLEAR, "jshint", "jsid");
            linkSql  = String.format(LINT_LINK , "jshint");
            break;
         case CSS:
            clearSql = String.format(LINT_CLEAR, "cssvalid", "cssid");
            linkSql  = String.format(LINT_LINK , "cssvalid");
            break;
         case HTML:
            clearSql = String.format(LINT_CLEAR, "htmlvalid", "siteid");
            linkSql  = String.format(LINT_LINK , "htmlvalid");
            break;
      }

      Connection conn = null;
      PreparedStatement delps = null;
      PreparedStatement insps = null;
      try {
         conn = Pool.getConnection();
         delps = conn.prepareStatement(clearSql);
         insps = conn.prepareStatement(linkSql);
         conn.setAutoCommit(false);
         delps.setString(1, subjectId);
         delps.executeUpdate();

         if (msgIds.size() > 0) {
            insps.setString(1, subjectId);
            for (String id : msgIds) {
               insps.setString(2, id);
               insps.addBatch();
            }
            insps.executeBatch();
         }
         conn.commit();
         LOGGER.info("{} {} is linked with {} message(s)",
            subject, subjectId, msgIds.size());
      } catch (SQLException e) {
         if (conn != null) {
            try { conn.rollback(); } catch(SQLException ex) {
               LOGGER.error("rollback problem", ex.getMessage());
            }
         }
         throw new DalException(e);
      } finally {
         Util.close(delps);
         Util.close(insps);
         Util.setAutoCommit(conn, true);
         Util.close(conn);
      }
   }

}
