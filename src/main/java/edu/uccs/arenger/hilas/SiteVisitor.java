package edu.uccs.arenger.hilas;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import edu.uccs.arenger.hilas.Util.TypedContent;
import edu.uccs.arenger.hilas.dal.Css;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;
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
public class SiteVisitor extends Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(SiteVisitor.class);

   private static final int MAX_DEPTH = 5; //frames within frames
   private static final int MAX_SUBSITES = 12; //max number of subsites 
   private static final int TARGET_SPD = 3; //target # of sites per dom
   private Set<String> subSiteIds;

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private void linkRsrcEntry(
      String siteId, URL url, Class<? extends SiteResource> type) {
      try {
         String content = Util.getTypedContent(url).content;
         if (content.length() == 0) {
            throw new IOException("zero length rsrc at " + url);
         }
         SiteResource rsrc = SiteResource.get(type, content);
         if (rsrc == null) {
            rsrc = SiteResource.create(type, url, content);
            rsrc.insert();
         }
         try {
            rsrc.linkToSite(siteId);
         } catch (PkViolation e) {
            LOGGER.debug(
               "rsrc already linked: {} - {}", siteId, rsrc.getId());
         }
      } catch (IOException e) {
         LOGGER.warn("problem visiting rsrc: {}", e.getMessage());
      } catch (DalException e) {
         LOGGER.error("problem storing rsrc info", e);
      } catch (IllegalArgumentException e) {
         LOGGER.error("problem storing rsrc info: {}", e.getMessage());
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
                  LOGGER.debug("unsupported ref protocol: {}", url);
               }
            } catch (MalformedURLException e) {
               LOGGER.debug("malformed ref url: {}", href);
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
         if (subSiteIds.size() >= MAX_SUBSITES) {
            LOGGER.warn("reached max subsite count. skipping: {}", url);
         } else {
            Site site = Site.forUrl(url);
            if (site != null) {
               LOGGER.info("already visited: {}", url);
               subSiteIds.add(site.getId());
            } else {
               site = new Site(url, "hilas:sub");
               site.setState(Site.VisitState.VISITING);
               try {
                  site.insert();
                  subSiteIds.add(site.getId());
               } catch (DalException e) {
                  LOGGER.error("error inserting new site", e);
               }
               visit(site, depth + 1);
            }
         }
      }
   }

   private boolean samePage(URL u1, URL u2) {
      String a = u1.toString();
      String b = u2.toString();
      //ignore anchors and any trailing slash -
      a = a.replaceAll("#.*$",""); a = a.replaceAll("/$","");
      b = b.replaceAll("#.*$",""); b = b.replaceAll("/$","");
      return a.equals(b);
   }

   private void addPeers(Site site, TagNode root) throws DalException {
      int spd = Domain.siteCountFor(site.getDomainId());
      if (spd > TARGET_SPD) { return; }
      List<URL> links = getRefs(site.getUrl(), root, "a" , "href");
      int i = 0;
      while ((i < links.size()) && (spd < TARGET_SPD)) {
         URL link = links.get(i);
         if (!samePage(site.getUrl(), link) &&
             link.getHost().equals(site.getUrl().getHost())) {
            Site peer = new Site(link, "hilas:peer");
            try {
               peer.insert();
               spd++;
            } catch (PkViolation e) {
               LOGGER.debug("skipping duplicate peer: {}", link);
            }
         }
         i++;
      }
   }

   private String urlFromMetaContentAttr(String content) {
      if (content == null) { return null; }
      String url = null;
      String parts[] = content.split("[\\s;]+");
      int i = 0;
      while ((i < parts.length) && (url == null)) {
         if (parts[i].toLowerCase().startsWith("url=")) {
            url = parts[i].substring(4).replaceAll("['\"]","");
         }
         i++;
      }
      return url;
   }

   private void addForward(Site site, TagNode root) throws DalException {
      TagNode[] arr = root.getElementsByName("meta", true);
      for (TagNode tn : arr) {
         String equiv   = tn.getAttributeByName("http-equiv");
         String content = tn.getAttributeByName("content");
         if ((equiv != null) && (content != null) &&
            equiv.toLowerCase().equals("refresh")) {
            String urlStr = urlFromMetaContentAttr(content);
            if (urlStr != null) {
               try {
                  URL url = new URL(site.getUrl(), urlStr);
                  if (!samePage(site.getUrl(), url)) {
                     Site target = new Site(url, site.getSource());
                     target.insert();
                     site.setFwdTo(target.getId());
                     LOGGER.info("added forward from {} to {}",
                        site.getUrl(), url);
                     return; //only one http-equiv should be processed
                  }
               } catch (MalformedURLException e) {
                  LOGGER.error("malformed http-equiv fwd using {} and {}",
                     site, urlStr);
               } catch (PkViolation e) {
                  LOGGER.debug("attemped fwd insert already exists.");
               }
            }
         }
      }
   }

   private void visit(Site site, int depth) {
      if (depth > MAX_DEPTH) {
         LOGGER.warn(
            "visit depth exceeded. skipping visit to: {}", site.getUrl());
         try {
            site.setState(Site.VisitState.ERROR);
            site.update();
         } catch (DalException e) {
            LOGGER.error("error updating site", e);
         }
         return;
      }
      LOGGER.info("depth {}, visiting {}", depth, site.getUrl());
      try {
         site.setVisitTime(System.currentTimeMillis());
         site.update();

         TypedContent tc = null;
         try {
            tc = Util.getTypedContent(site.getUrl());
         } catch (Exception e) {
            LOGGER.warn("problem loading url. msg: {}", e.getMessage());
            site.setState(Site.VisitState.ERROR);
            site.update();
            return;
         }
         // The study will be constrained to pages whose content type
         // contains "text/html", which will allow them to be submitted
         // to the html validator...
         if (tc.type.toLowerCase().indexOf("text/html") == -1) {
            LOGGER.warn("skipping {} because ContentType = {}",
               site.getUrl(), tc.type);
            site.setState(Site.VisitState.ERROR);
            site.update();
            return;
         }

         HtmlCleaner hc     = new HtmlCleaner();
         JsNet       jsNet  = new JsNet();
         CssNet      cssNet = new CssNet();
         if (tc.content != null) {
            int size = tc.content.length();
            if ((size > 0) && (size < 512)) {
               LOGGER.debug("SMALL-SITE:\n{}", tc.content);
            }
            site.setSize(size);
            TagNode root = hc.clean(tc.content);
            fishForResource(site, root, jsNet);
            fishForResource(site, root, cssNet);
            visitChildren(site, root, depth);
            addPeers(site, root);
            addForward(site, root);
         } else {
            site.setSize(0);
         }

         site.setState(Site.VisitState.VISITED);
         site.update();
      } catch (DalException e) {
         LOGGER.error("problem while visiting {}", site.getUrl());
      }
   }

   protected void wrappedRun() {
      try {
         Site site = Site.nextUnvisited();
         subSiteIds = new HashSet<String>();
         if (site == null) {
            if (!paused) {
               LOGGER.info("{} - PAUSING (no sites to visit)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }
         visit(site, 0);
         if (subSiteIds.size() > 0) {
            Site.saveSubSiteIds(site.getId(),subSiteIds);
         }
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

}
