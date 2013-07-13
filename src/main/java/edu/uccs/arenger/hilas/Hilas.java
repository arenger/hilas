package edu.uccs.arenger.hilas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Pool;
import edu.uccs.arenger.hilas.dal.Site;
import edu.uccs.arenger.hilas.dal.UkViolation;
import edu.uccs.arenger.hilas.quality.CssvManager;
import edu.uccs.arenger.hilas.quality.HtmlChecker;
import edu.uccs.arenger.hilas.quality.JsHinter;
import edu.uccs.arenger.hilas.security.GoogleSb;
import edu.uccs.arenger.hilas.security.Wot;

public final class Hilas {
   private static final Logger LOGGER = LoggerFactory.getLogger(Hilas.class);
   private static final String PROPS = "hilas.properties";
   private static final int TPOOL_SIZE = 5;
   private static final String USAGE =
      "usage: hilas run\n" +
      "OR     hilas load FILE\n" +
      "OR     hilas crawl seedUrl";

   /* Notes about CRAWL mode:
    * The site.visitState column is "overloaded".  It is used by the
    * SiteVisitor class when in RUN mode, and by DomainFinder when
    * in CRAWL mode.  The only purpose for CRAWL mode is to find new
    * domains.  To properly use CRAWL mode, start from an empty DB.
    * After you've found some stuff, then, before using RUN mode,
    * do this:
    * > delete from site where visitSite = 'ERROR';
    * > update site set visitState = 'NEW';
    */

   enum Mode {
      RUN, LOAD, CRAWL
   };

   private static Properties props;

   private Mode mode;
   private File loadFile;
   private String seedUrl;
   private ScheduledExecutorService multiThredExec;
   private ScheduledExecutorService singleThreadExec;
   private JsHinter jsHinter;

   private Hilas(String[] args) {
      if (args.length == 0) {
         System.out.println(USAGE);
         System.exit(1);
      }
      try {
         mode = Mode.valueOf(args[0].toUpperCase());
      } catch (IllegalArgumentException e) {
         System.out.println("invalid mode: " + args[0]);
         System.out.println("please specify 'run', 'load', or 'crawl'");
         System.exit(1);
      }
      switch (mode) {
      case LOAD:
         if (args.length != 2) {
            System.out.println(USAGE);
            System.exit(1);
         }
         loadFile = new File(args[1]);
         break;
      case CRAWL:
         if (args.length != 2) {
            System.out.println(USAGE);
            System.exit(1);
         }
         seedUrl = args[1];
         break;
      default:
         break;
      }
   }

   public static Properties getProps() {
      Properties ret = null;
      try {
         ret = new Properties();
         URL url = ClassLoader.getSystemResource(PROPS);
         if (url == null) {
            throw new RuntimeException("sys resource not found: "+PROPS);
         }
         ret.load(url.openStream());
      } catch (Exception e) {
         LOGGER.error("Problem loading properties file", e);
         System.err.println("Could not load properties file.  Exiting.");
         System.exit(1);
      }
      return ret;
   }

   public static String getProp(String key) {
      if (props == null) {
         props = getProps();
      }
      return props.getProperty(key);
   }

   private void shutdown() {
      LOGGER.info("Shutting down");
      Pool.shutdown();
      if (multiThredExec != null) {
         multiThredExec.shutdownNow();
      }
      if (singleThreadExec != null) {
         singleThreadExec.shutdownNow();
      }
      if (jsHinter != null) {
         jsHinter.close();
      }
   }

   private void startWorker(ScheduledExecutorService exec, Worker w) {
      exec.scheduleWithFixedDelay(w, 1, w.getDelay(), w.getTimeUnit());
   }

   private void run() {
      try {
         Pool.init(props);
         Util.trustAllSslCerts();
         multiThredExec   = Executors.newScheduledThreadPool(TPOOL_SIZE);
         singleThreadExec = Executors.newSingleThreadScheduledExecutor();

         startWorker(multiThredExec, new SiteVisitor());
         startWorker(multiThredExec, new SiteVisitor());
         //startWorker(multiThredExec, new HtmlChecker());
         startWorker(multiThredExec, new GoogleSb());
         startWorker(multiThredExec, new Wot());

         startWorker(singleThreadExec, jsHinter = new JsHinter());
         startWorker(singleThreadExec, new CssvManager());
      } catch (DalException e) {
         LOGGER.error("problem", e);
      }
   }

   private void load() {
      try (BufferedReader in
         = new BufferedReader(new FileReader(loadFile))) {
         Pool.init(props);
         String source = FilenameUtils.getBaseName(loadFile.getName());
         String line;
         while ((line = in.readLine()) != null) {
            URL url = null;
            try {
               url = new URL(line);
               if (Util.protocolOk(url)) {
                  Site site = new Site(url, source);
                  site.insert();
               } else {
                  LOGGER.error("unsupported protocol: " + url);
               }
            } catch (UkViolation e) {
               LOGGER.warn("already exists: " + url);
            } catch (MalformedURLException e) {
               LOGGER.error("malformed url: " + url);
            }
         }
      } catch (DalException|IOException e) {
         LOGGER.error("problem", e);
      }
   }

   private void crawl() {
      try {
         Pool.init(props);
         Util.trustAllSslCerts();

         boolean proceed = false;
         URL url = null;
         try {
            url = new URL(seedUrl);
            if (Util.protocolOk(url)) {
               Site site = new Site(url, "seedUrl");
               site.insert();
               proceed = true;
            } else {
               LOGGER.error("unsupported protocol: " + url);
            }
         } catch (UkViolation e) {
            LOGGER.warn("already exists: " + url);
         } catch (MalformedURLException e) {
            LOGGER.error("malformed url: " + url);
         }

         if (proceed) {
            multiThredExec = Executors.newScheduledThreadPool(TPOOL_SIZE);
            startWorker(multiThredExec, new DomainCounter());
            startWorker(multiThredExec, new DomainFinder());
            startWorker(multiThredExec, new DomainFinder());
            startWorker(multiThredExec, new DomainFinder());
         }
      } catch (DalException e) {
         LOGGER.error("problem", e);
      }
   }

   private void dispatch() {
      LOGGER.info("Mode: {}", mode);
      Runtime.getRuntime().addShutdownHook(new Thread() {
         public void run() { shutdown(); }
      });
      props = getProps();
      System.out.println("Started.  See hilas.log");
      switch (mode) {
         case RUN:
            run();
            break;
         case LOAD:
            load();
            break;
         case CRAWL:
            crawl();
            break;
      }
   }

   public static void main(String[] args) {
      new Hilas(args).dispatch();
   }
}
