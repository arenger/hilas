package edu.uccs.arenger.hilas.dal;

import java.net.MalformedURLException;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util;

public class JavaScript extends SiteResource {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(JavaScript.class);

   private String id;
   private URL    url;
   private int    size;
   private State  lintState;
   private State  hintState;

   private static final String SEL =
      "select * from javascript where id = ?";
   private static final String INSERT =
      "insert into javascript values (?,?,?,?,?,?)";
   private static final String LINK2SITE =
      "insert into sitejs values (?,?)";
   private static final String SEL_UNHINTED =
      "select * from javascript where hintState = 'UNPROCESSED'";
   private static final String UPD =
      "update JavaScript set lintState = ?, hintState = ? " +
      "where id = ?";
   private static final String LINT_CLEAR =
      "delete from jslint where jsid = ?";
   private static final String LINT_LINK =
      "insert into jslint values (?, ?)";
   private static final String HINT_CLEAR =
      "delete from jshint where jsid = ?";
   private static final String HINT_LINK =
      "insert into jshint values (?, ?)";

   public enum State { UNPROCESSED, PROCESSING, PROCESSED, ERROR }

   public JavaScript(URL url, String content) {
      id = Util.md5(content);
      this.url = url;
      size = content.length();
      lintState = State.UNPROCESSED;
      hintState = State.UNPROCESSED;
   }

   private JavaScript(ResultSet rs) throws SQLException {
      id = rs.getString("id");
      try {
         url = new URL(rs.getString("url"));
      } catch (MalformedURLException e) {
         LOGGER.warn("malformed url for id {}", id);
      }
      size = rs.getInt("size");
      lintState = State.valueOf(rs.getString("lintState"));
      hintState = State.valueOf(rs.getString("hintState"));
   }

   public static JavaScript get(String content) throws DalException {
      JavaScript ret = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL)) {
         ps.setString(1, Util.md5(content));
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            ret = new JavaScript(rs);
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
         ps.setInt(4, size);
         ps.setString(5, lintState.toString());
         ps.setString(6, hintState.toString());
         ps.executeUpdate();
         LOGGER.info("inserted new js: {} - {}", id, url);
      } catch (SQLException e) {
         id = null;
         throw DalException.of(e);
      }
   }

   public void update() throws DalException {
      Util.notNull(id, "id");
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(UPD)) {
         ps.setString(1, lintState.toString());
         ps.setString(2, hintState.toString());
         ps.setString(3, id);
         ps.executeUpdate();
      } catch (SQLException e) { throw new DalException(e); }
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

   public static synchronized JavaScript nextUnhinted()
      throws DalException {
      JavaScript js = null;
      try (Connection conn = Pool.getConnection();
           PreparedStatement ps = conn.prepareStatement(SEL_UNHINTED)) {
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            js = new JavaScript(rs);
         }
      } catch (SQLException e) { throw new DalException(e); }
      if (js != null) {
         js.setHintState(State.PROCESSING);
         js.update();
      }
      return js;
   }

   public static void linkHintMessages(String jsId, Set<String> msgs)
      throws DalException {
      linkMessages(HINT_CLEAR, HINT_LINK, jsId, msgs);
   }

   public static void linkLintMessages(String jsId, Set<String> msgs)
      throws DalException {
      linkMessages(LINT_CLEAR, LINT_LINK, jsId, msgs);
   }

   private static void linkMessages( String clearSql, String linkSql,
      String jsId, Set<String> msgs) throws DalException {
      if (jsId == null) { return; }
      //translate messages to message id's
      Set<String> msgIdSet = new HashSet<String>();
      if (msgs != null) {
         for (String msg : msgs) {
            msgIdSet.add(JsLintMsg.idForMsg(msg));
         }
      }
      Connection conn = null;
      PreparedStatement delps = null;
      PreparedStatement insps = null;
      try {
         conn = Pool.getConnection();
         delps = conn.prepareStatement(clearSql);
         insps = conn.prepareStatement(linkSql);
         conn.setAutoCommit(false);
         delps.setString(1, jsId);
         delps.executeUpdate();

         if (msgIdSet.size() > 0) {
            insps.setString(1, jsId);
            for (String msgId : msgIdSet) {
               insps.setString(2, msgId);
               insps.addBatch();
            }
            insps.executeBatch();
         }
         conn.commit();
         LOGGER.info("js {} is linked with {} messages",
            jsId, msgIdSet.size());
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

   public String getId() {
      return id;
   }

   public URL getUrl() {
      return url;
   }

   public void setLintState(State lintState) {
      this.lintState = lintState;
   }

   public void setHintState(State hintState) {
      this.hintState = hintState;
   }

}
