package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.uccs.arenger.hilas.dal.Css;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.JavaScript;
import edu.uccs.arenger.hilas.dal.PkViolation;
import edu.uccs.arenger.hilas.dal.Site;
import edu.uccs.arenger.hilas.dal.SiteResource;

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

   public  static final int SEC_BTWN  = 1; //seconds between runs
   private static final int MAX_DEPTH = 5; //frames within frames
   private Set<String> subSiteIds;

   private void linkRsrcEntry(
      String siteId, URL url, Class<? extends SiteResource> type) {
      try {
         String content = Util.getContent(url);
         String md5 = Util.md5(content);
         SiteResource rsrc = SiteResource.get(type, md5);
         if (rsrc == null) {
            rsrc = SiteResource.create(type, url, md5, content.length());
            rsrc.insert();
         }
         try {
            rsrc.linkToSite(siteId);
         } catch (PkViolation e) {
            LOGGER.debug(
               "rsrc already linked: {} - {}", siteId, rsrc.getId());
         }
      } catch (IOException e) {
         LOGGER.error("problem visiting rsrc: {}", e.getMessage());
      } catch (DalException e) {
         LOGGER.error("problem storing rsrc info", e);
      }
   }

   private void fishForResource(Site site, TagNode root, ResourceNet net) {
      // find the css link tags -
      TagNode[] arr = root.getElementsByName(net.tagName(), true);
      for (TagNode tn : arr) {
         if (net.catches(tn)) {
            String href = tn.getAttributeByName(net.attName());
            if ((href != null) && (href.length() > 0)) {
               try {
                  linkRsrcEntry(site.getId(),
                     new URL(site.getUrl(), href), net.dtoType());
               } catch (MalformedURLException e) {
                  LOGGER.warn("malformed rsrc url: {}", href);
               }
            }
         } else {
            //TODO gather in-page resource content?
         }
      }
   }

   private interface ResourceNet {
      Class<? extends SiteResource> dtoType();
      String tagName();
      String attName();
      boolean catches(TagNode tn);
   }

   private class JsNet implements ResourceNet {
      public Class<JavaScript> dtoType() { return JavaScript.class; }
      public String tagName() { return "script"; }
      public String attName() { return "src"; }
      public boolean catches(TagNode tn) {
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
   }

   private class CssNet implements ResourceNet {
      public Class<Css> dtoType() { return Css.class; }
      public String tagName() { return "link"; }
      public String attName() { return "href"; }
      public boolean catches(TagNode tn) {
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

   private void visitChildren(Site parent, TagNode root, int depth)
      throws DalException {
      List<URL> children = new ArrayList<URL>();
      children.addAll(getRefs(parent.getUrl(), root, "frame" , "src"));
      children.addAll(getRefs(parent.getUrl(), root, "iframe", "src"));
      for (URL url : children) {
         Site site = Site.forUrl(url);
         if (site != null) {
            LOGGER.info("already visited: {}", url);
            subSiteIds.add(site.getId());
         } else {
            site = new Site(url, "hilas:sub");
            try {
               site.insert();
               subSiteIds.add(site.getId());
               visit(site, depth + 1);
            } catch (DalException e) {
               LOGGER.error("error inserting new site", e);
            }
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
         } catch (IOException e) {
            LOGGER.error("problem loading url: {}", e.getMessage());
            site.setState(Site.State.ERROR);
            site.update();
            return;
         }

         HtmlCleaner hc     = new HtmlCleaner();
         JsNet       jsNet  = new JsNet();
         CssNet      cssNet = new CssNet();
         if (html != null) {
            site.setSize(html.length());
            TagNode root = hc.clean(html);
            fishForResource(site, root, jsNet);
            fishForResource(site, root, cssNet);
            visitChildren(site, root, depth);
         } else {
            site.setSize(0);
         }

         site.setState(Site.State.VISITED);
         site.update();
      } catch (DalException e) {
         LOGGER.error("problem while visiting {}", site.getUrl());
      }
   }

   private void wrappedRun() throws DalException {
      Site site = Site.nextUnvisited();
      subSiteIds = new HashSet<String>();
      if (site == null) {
         LOGGER.info("no sites to visit");
         return;
      }
      visit(site, 0);
      if (subSiteIds.size() > 0) {
         Site.saveSubSiteIds(site.getId(),subSiteIds);
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
