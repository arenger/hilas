package edu.uccs.arenger.hilas.dal;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.bytecode.opencsv.CSVWriter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import edu.uccs.arenger.hilas.Util;

//Just to make things easier...
public final class Analysis {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(Analysis.class);
   private static final int NQS = 8;

   private Connection conn = null;
   private ObjectMapper mapper = new ObjectMapper();

   private static final String INS_ERR =
      "insert into analysis (domainId, domain) values (?, 'error')";
   private static final String SEL_WOT_EXT =
      "select extra from safebrowseresult where sbsId = 8 and domainId = ?";
   private static final String UPD_WOT =
      "update analysis set wot0 = ?, wot1 = ?, wot2 = ?, wot4 = ? " +
      "where domainId = ?";
   private static final String UPD_SBS =
      "update analysis set gsb = (" +
      "select result from safebrowseresult where sbsid = 1 and domainid = ?" +
      "), msa = (" +
      "select result from safebrowseresult where sbsid = 2 and domainid = ?" +
      "), nsw = (" +
      "select result from safebrowseresult where sbsid = 4 and domainid = ?" +
      "), wot = (" +
      "select result from safebrowseresult where sbsid = 8 and domainid = ?" +
      ") where domainid = ?";
   private static final String EXPORT = 
      "select * from analysis where domain != 'error'";
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

   private class WotInfo {
      public Integer wot0;
      public Integer wot1;
      public Integer wot2;
      public Integer wot4;
      public String toString() {
         return String.format("%s %s %s %s",wot0, wot1, wot2, wot4);
      }
   }

   private void setTinyIntCol(PreparedStatement ps, int col, Integer value)
      throws SQLException {
      if (value != null) {
         ps.setInt(col, value);
      } else {
         ps.setNull(col, Types.TINYINT);
      }
   }

   private void addWotInfo(String domainId, WotInfo info)
      throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(UPD_WOT)) {
         setTinyIntCol(ps, 1, info.wot0);
         setTinyIntCol(ps, 2, info.wot1);
         setTinyIntCol(ps, 3, info.wot2);
         setTinyIntCol(ps, 4, info.wot4);
         ps.setString(5, domainId);
         ps.executeUpdate();
      }
   }

   private void fillSbsColumns(String domainId) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(UPD_SBS)) {
         ps.setString(1, domainId);
         ps.setString(2, domainId);
         ps.setString(3, domainId);
         ps.setString(4, domainId);
         ps.setString(5, domainId);
         ps.executeUpdate();
      }
   }

   private Integer getWotVal(JsonNode node, String cat) {
      int i = node.path(cat).path(0).asInt(-1);
      return (i < 0) ? null : i;
   }

   private WotInfo parseWotInfo(String extra) throws IOException {
      WotInfo info = new WotInfo();
      if ((extra == null) || (extra.length() < 1)) { return info; }
      JsonNode root = mapper.readTree(extra);
      info.wot0 = getWotVal(root, "0");
      info.wot1 = getWotVal(root, "1");
      info.wot2 = getWotVal(root, "2");
      info.wot4 = getWotVal(root, "4");
      //LOGGER.debug("extra: {} - {}", extra, info);
      return info;
   }

   private void extraWot(String domainId) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(SEL_WOT_EXT)) {
         ps.setString(1, domainId);
         ResultSet rs = ps.executeQuery();
         if (rs.next()) {
            addWotInfo(domainId, parseWotInfo(rs.getString(1)));
         }
      } catch (IOException e) {
         LOGGER.error("problem", e);
      }
   }

   private void newRow(String domainId) throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
         for (int i = 1; i <= NQS; i++ ) {
            ps.setString(i, domainId);
         }
         if (ps.executeUpdate() == 1) {
            LOGGER.info("row added for       {}", domainId);
         } else {
            LOGGER.warn("incomplete data for {}", domainId);
            insErr(domainId);
         }
      }
   }

   private void export() throws SQLException {
      try (PreparedStatement ps = conn.prepareStatement(EXPORT);
           CSVWriter out = new CSVWriter(new FileWriter("analysis.csv"))) {
         ResultSet rs = ps.executeQuery();
         out.writeAll(rs, true);
      } catch (IOException e) {
         LOGGER.error("io error", e);
      }
   }

   public void go() throws DalException {
      String domainId;
      try {
         conn = Pool.getConnection();
         while ((domainId = Domain.getUnanalyzedId()) != null) {
            newRow(domainId);
            extraWot(domainId);
            fillSbsColumns(domainId);
         }
         export();
      } catch (SQLException e) {
         throw new DalException(e);
      } finally {
         if (conn != null) { Util.close(conn); }
      }
   }
}
