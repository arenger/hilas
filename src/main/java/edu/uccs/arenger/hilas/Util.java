package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;

public final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);
   private static final String DEFAULT_CHARSET = "ISO-8859-1";

   private Util() {}

   public static String getContent(URL url) throws IOException {
      StringBuilder sb = new StringBuilder();
      URLConnection conn = url.openConnection();
      Pattern p = Pattern.compile("text/html;\\s+charset=([^\\s]+)\\s*");
      Matcher m = p.matcher(conn.getContentType());
      String charset = m.matches() ? m.group(1) : DEFAULT_CHARSET;
      Reader in = new InputStreamReader(conn.getInputStream(), charset);
      char[] charArray = new char[1024];
      int charsRead = 0;
      do {
         charsRead = in.read(charArray);
         if (charsRead >= 0) {
            sb.append(charArray, 0, charsRead);
         }
      } while (charsRead > 0);
      return sb.toString();
   }

   public static void trustAllSslCerts() {
      TrustManager[] trustAllCerts = new TrustManager[]{
         new X509TrustManager(){
            public X509Certificate[] getAcceptedIssuers(){return null;}
            public void checkClientTrusted(
               X509Certificate[] certs, String authType){}
            public void checkServerTrusted(
               X509Certificate[] certs, String authType){}
      }};

      try {
         SSLContext sc = SSLContext.getInstance("TLS");
         sc.init(null, trustAllCerts, new SecureRandom());
         HttpsURLConnection.setDefaultSSLSocketFactory(
            sc.getSocketFactory());
      } catch (Exception e) {
         LOGGER.error("problem installing trust manager", e);
      }
   }

   public static boolean protocolOk(URL url) {
      String prot = url.getProtocol(); //always returns lowercase
      return prot.equals("http") || prot.equals("https");
   }

   public static String md5(String content) {
      String hash = "error";
      try {
         MessageDigest md = MessageDigest.getInstance("MD5");
         BigInteger bi = new BigInteger(1,md.digest(content.getBytes()));
         hash = bi.toString(16);
      } catch (NoSuchAlgorithmException e) {
         LOGGER.error("MD5 DNE.  That's odd.");
      }
      return hash;
   }

   public static void notNull(Object o, String name) throws DalException {
      if (o == null) {
         throw new DalException(name + " can't be null");
      }
   }

   public static void close(PreparedStatement ps) {
      if (ps != null) {
         try {
            ps.close();
         } catch (SQLException e) {
            LOGGER.error("problem while closing", e);
         }
      }
   }

   public static void close(Connection c) {
      if (c != null) {
         try {
            c.close();
         } catch (SQLException e) {
            LOGGER.error("problem while closing", e);
         }
      }
   }

   public static void setAutoCommit(Connection c, boolean active) {
      if (c != null) {
         try {
            c.setAutoCommit(active);
         } catch (SQLException e) {
            LOGGER.error("problem setting autocommit", e);
         }
      }
   }

}
