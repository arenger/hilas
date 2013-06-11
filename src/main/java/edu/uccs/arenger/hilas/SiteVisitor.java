package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import edu.uccs.arenger.hilas.dal.Css;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.JavaScript;
import edu.uccs.arenger.hilas.dal.Site;
import edu.uccs.arenger.hilas.dal.UkViolation;

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

   public  static final int SEC_BTWN  = 3; //seconds between runs
   private static final int MAX_DEPTH = 5; //frames within frames

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

   private void linkJsEntry(String siteId, URL url) {
      try {
         String content = Util.getContent(url);
         String md5 = Util.md5(content);
         JavaScript js = JavaScript.get(md5);
         if (js == null) {
            js = new JavaScript(url, md5, content.length());
            js.insert();
         }
         js.linkToSite(siteId);
      } catch (IOException e) {
         LOGGER.error("problem visiting js", e);
      } catch (UkViolation e) {
         LOGGER.warn("unique key violation: {}", e.getMessage());
      } catch (DalException e) {
         LOGGER.error("problem storing js info", e);
      }
   }

   private void fishForJs(Site site, TagNode root) {
      // find the javascript source tags -
      TagNode[] arr = root.getElementsByName("script", true);
      for (TagNode tn : arr) {
         if (isJsTag(tn)) {
            String src = tn.getAttributeByName("src");
            if ((src != null) && (src.length() > 0)) {
               try {
                  linkJsEntry(site.getId(), new URL(site.getUrl(), src));
               } catch (MalformedURLException e) {
                  LOGGER.warn("malformed js url: {}", src);
               }
            }
         } else {
            //TODO gather in-page js?
         }
      }
   }

   private boolean isCssTag(TagNode tn) {
      String type = tn.getAttributeByName("type");
      if ((type != null) && (type.equalsIgnoreCase("text/css"))) {
         return true;
      }
      String rel = tn.getAttributeByName("rel");
      if ((rel != null) && (rel.equalsIgnoreCase("stylesheet"))) {
         return true;
      }
      return false;
   }

   private void linkCssEntry(String siteId, URL url) {
      try {
         String content = Util.getContent(url);
         String md5 = Util.md5(content);
         Css css = Css.get(md5);
         if (css == null) {
            css = new Css(url, md5, content.length());
            css.insert();
         }
         css.linkToSite(siteId);
      } catch (IOException e) {
         LOGGER.error("problem visiting css", e);
      } catch (UkViolation e) {
         LOGGER.warn("unique key violation: {}", e.getMessage());
      } catch (DalException e) {
         LOGGER.error("problem storing css info", e);
      }
   }

   private void fishForCss(Site site, TagNode root) {
      // find the css link tags -
      TagNode[] arr = root.getElementsByName("link", true);
      for (TagNode tn : arr) {
         if (isCssTag(tn)) {
            String href = tn.getAttributeByName("href");
            if ((href != null) && (href.length() > 0)) {
               try {
                  linkCssEntry(site.getId(), new URL(site.getUrl(), href));
               } catch (MalformedURLException e) {
                  LOGGER.warn("malformed css url: {}", href);
               }
            }
         } else {
            //TODO gather in-page css?
         }
      }
   }

   private List<URL> getRefs(
      URL base, TagNode root, String tagName, String attName) {
      List<URL> refs = new ArrayList<URL>();
      TagNode[] arr = root.getElementsByName(tagName, true);
      for (TagNode tn : arr) {
         String href = tn.getAttributeByName(attName);
         if ((href != null) && (href.length() > 0)) {
            try {
               URL url = new URL(base, href);
               if (Util.protocolOk(url)) {
                  refs.add(url);
               } else {
                  LOGGER.warn("(i)frame unsupported protocol: {}", url);
               }
            } catch (MalformedURLException e) {
               LOGGER.warn("(i)frame malformed url: {}", href);
            }
         }
      }
      return refs;
   }

   private void visitChildren(Site parent, TagNode root, int depth) {
      List<URL> children = new ArrayList<URL>();
      children.addAll(getRefs(parent.getUrl(), root, "frame", "href"));
      children.addAll(getRefs(parent.getUrl(), root, "iframe", "src"));
      for (URL url : children) {
         Site site = new Site(url, "hilas:sub");
         try {
            site.insert();
            visit(site, depth + 1);
         } catch (UkViolation e) {
            LOGGER.debug("uk violation: {}", e.getMessage());
            LOGGER.info("already visited: {}", url);
         } catch (DalException e) {
            LOGGER.error("error inserting new site", e);
         }
      }
   }

   private void visit(Site site, int depth) {
      if (depth > MAX_DEPTH) {
         LOGGER.warn(
            "visit depth exceeded. skipping visit to: {}", site.getUrl());
         return;
      }
      LOGGER.info("visiting: {}", site.getUrl());
      try {
         site.setVisitTime(System.currentTimeMillis());
         site.setState(Site.State.VISITING);
         site.update();

         String html = null;
         try {
            html = Util.getContent(site.getUrl());
         } catch (Exception e) {
            LOGGER.error("problem loading url", e);
            site.setState(Site.State.ERROR);
            site.update();
            return;
         }

         HtmlCleaner hc = new HtmlCleaner();
         if (html != null) {
            site.setSize(html.length());
            TagNode root = hc.clean(html);
            fishForJs( site, root);
            fishForCss(site, root);
            visitChildren(site, root, depth);
         } else {
            site.setSize(0);
         }

         //TODO recursive call for frames and iframes -

         site.setState(Site.State.VISITED);
         site.update();
      } catch (DalException e) {
         LOGGER.error("problem while visiting {}", site.getUrl());
      }
   }

   private void wrappedRun() throws DalException {
      Site site = Site.nextUnvisited();
      if (site == null) {
         LOGGER.info("no sites to visit");
         return;
      }
      visit(site, 0);
   }

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("problem",e);
      }
   }
}
