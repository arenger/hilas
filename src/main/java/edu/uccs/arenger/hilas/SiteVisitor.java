package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.net.URL;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.JavaScript;
import edu.uccs.arenger.hilas.dal.Site;

import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* The purpose of this class is to populate these tables:
 * JavaScript, Css, SiteJs, SiteCss, and SiteFrame
 * ... and then mark a site as "visted".  Validation/Linting is left
 * for other classes. */
public class SiteVisitor implements Runnable {
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

   private void visitJs(String url, String siteId) {
      try {
         String content = Util.getContent(new URL(url));
         String md5 = Util.md5(content);
         JavaScript js = JavaScript.get(md5);
         if (js == null) {
            js = new JavaScript(url, md5, content.length());
            js.insert();
         }
         js.linkToSite(siteId);
      } catch (IOException e) {
         LOGGER.error("problem visiting js", e);
      } catch (DalException e) {
         LOGGER.error("problem storing js info", e);
      }
   }

   public void wrappedRun() throws DalException {
      Site site = Site.nextUnvisited();
      if (site == null) {
         LOGGER.info("no sites to visit");
         return;
      }
      LOGGER.info("visiting: {}", site.getUrl());

      String html = null;
      try {
         html = Util.getContent(new URL(site.getUrl()));
      } catch (Exception e) {
         LOGGER.error("problem loading url", e);
      }

      HtmlCleaner hc = new HtmlCleaner();
      if (html != null) {
         site.setSize(html.length());
         TagNode root = hc.clean(html);
         // find the javascript source tags -
         TagNode[] arr = root.getElementsByName("script", true);
         for (TagNode tn : arr) {
            if (isJsTag(tn)) {
               String src = tn.getAttributeByName("src");
               if ((src != null) && (src.length() > 0)) {
                  visitJs(Util.fullUrl(site.getUrl(), src), site.getId());
               }
            } else {
               //TODO gather in-page js?
            }
         }

         //TODO visit frames and iframes -
      } else {
         site.setSize(0);
      }

      site.setVisitTime(System.currentTimeMillis());
      site.update();
   }

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("problem",e);
      }
   }
}
