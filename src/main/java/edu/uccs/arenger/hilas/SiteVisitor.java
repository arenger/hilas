package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Site;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SiteVisitor implements Runnable {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(SiteVisitor.class);

   public static final int SEC_BTWN = 3; //seconds between runs

   private boolean isJsTag(TagNode tn) {
      String type = tn.getAttributeByName("type");
      if ((type != null) && (type.equalsIgnoreCase("text/javascript"))) {
         return true;
      }
      String lang = tn.getAttributeByName("language");
      if ((lang != null) && (lang.equalsIgnoreCase("javascript"))) {
         return true;
      }
      return false;
   }

   public void wrappedRun() throws DalException, IOException {
      Site site = Site.nextUnvisited();
      if (site == null) {
         LOGGER.info("no sites to visit");
         return;
      }
      LOGGER.info("visiting: {}", site.getUrl());
      HtmlCleaner hc = new HtmlCleaner();

      TagNode root = null;
      try {
         root = hc.clean(new URL(site.getUrl()));
      } catch (Exception e) {
         LOGGER.error("problem while cleaning", e);
      }

      if (root != null) {
         // find the javascript source tags -
         TagNode[] arr = root.getElementsByName("script", true);
         for (TagNode tn : arr) {
            if (isJsTag(tn)) {
               String src = tn.getAttributeByName("src");
               if ((src != null) && (src.length() > 0)) {
                  LOGGER.debug("js: {}", src);
               }
            }
         }

         //TODO visit frames and iframes -
      }

      site.setVisitTime(System.currentTimeMillis());
      site.update();
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

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("problem",e);
      }
   }
}
