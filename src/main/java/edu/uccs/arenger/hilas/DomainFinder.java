package edu.uccs.arenger.hilas;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Util.TypedContent;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Domain;
import edu.uccs.arenger.hilas.dal.Site;

public class DomainFinder extends Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(DomainFinder.class);

   private Pattern urlPat = Pattern.compile("(https?://[^\\s\"']+?)[\"']");
   private UrlKeeper urlk = new UrlKeeper();

   private static final Set<String> SKIPEXT;

   static {
      SKIPEXT = new HashSet<String>();
      //This is far from exhaustive, of course, but it'll help -
      SKIPEXT.add("js");  SKIPEXT.add("css"); SKIPEXT.add("jpg");
      SKIPEXT.add("pdf"); SKIPEXT.add("gif"); SKIPEXT.add("swf");
      SKIPEXT.add("png");
   }

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   private boolean visit(URL url) throws DalException {
      TypedContent tc = null;
      try {
         tc = Util.getTypedContent(url);
      } catch (Exception e) {
         LOGGER.warn("problem loading url. msg: {}", e.getMessage());
         return false;
      }
      if (tc.type.toLowerCase().indexOf("text/html") == -1) {
         //DomainFinder is looking for just html pages...
         LOGGER.debug("discarding: {}", tc.type);
         return false;
      } else {
         LOGGER.debug("processing: {}", tc.type);
      }
      Matcher m = urlPat.matcher(tc.content);
      while (m.find()) {
         try {
            URL newUrl = new URL(m.group(1)); //validate the url
            String lcp = newUrl.getPath().toLowerCase();
            if (SKIPEXT.contains(FilenameUtils.getExtension(lcp))) {
               continue;
            }
            if (!Domain.seenMain(newUrl)) {
               Site site = new Site(newUrl, "DomainFinder");
               site.setVisitTime(System.currentTimeMillis()); // note:
               // using "visitTime" as "foundTime" when in crawl mode,
               // rather than adding another column.  yes, we'll also
               // visit some of the sites that were found, so visitTime
               // should be the time when/if we visit a site, but in crawl
               // mode i don't care when it was visited - only when "found"
               site.insert();
            } else {
               urlk.considerKeeping(newUrl);
            }
         } catch (MalformedURLException e) {}
      }
      return true;
   }

   private void visit(Site site) {
      try {
         if (visit(site.getUrl())) {
            site.setState(Site.VisitState.VISITED);
         } else {
            site.setState(Site.VisitState.ERROR);
         }
         site.update();
      } catch (DalException e) {
         LOGGER.error("problem while visiting {}", site.getUrl());
      }
   }

   protected void wrappedRun() {
      try {
         Site site = Site.nextUnvisited();
         if (site == null) {
            URL url = urlk.remove();
            if (url != null) {
               LOGGER.debug("visiting url from an old domain. urlk.size: {}",
                  urlk.size());
               visit(url);
            } else if (!paused) {
               LOGGER.info("{} - PAUSING (no sites to visit)", this);
               paused = true;
            }
            return;
         }
         if (paused) {
            LOGGER.info("{} - RESUMING", this);
            paused = false;
         }
         visit(site);
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
   }

   /* A data structure to hold urls that stem from domains that Hilas
    * has already seen.  We'll visit these urls (i.e. revisit their
    * domains) only if there are no "NEW" domains to visit.  This 
    * structure should enforce upper limits on its total size as well
    * as the number of urls of the same "main" domain. */
   private class UrlKeeper extends HashSet<URL> {
      private static final long serialVersionUID = 1L;
      private static final int MAX_SIZE = 3000;
      private static final int MAX_PER_DOM = 3;
      private Map<String,Integer> domainCounts
         = new HashMap<String,Integer>();
      public void considerKeeping(URL url) {
         if (this.size() > MAX_SIZE) { return; }
         String domain = Domain.getMain(url);
         Integer count = domainCounts.get(domain);
         if (count != null) {
            if (count > MAX_PER_DOM) { return; }
            domainCounts.put(domain, count + 1);
         } else {
            domainCounts.put(domain, 1);
         }
         add(url);
      }
      public URL remove() {
         URL ret = null;
         Iterator<URL> i = this.iterator();
         if (i.hasNext()) {
            ret = i.next();
            this.remove(ret);
            String dom = Domain.getMain(ret);
            int count = domainCounts.get(dom);
            domainCounts.put(dom, count - 1);
         }
         return ret;
      }
   }

}
