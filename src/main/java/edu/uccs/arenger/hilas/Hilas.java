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
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Pool;
import edu.uccs.arenger.hilas.dal.Site;
import edu.uccs.arenger.hilas.dal.UkViolation;
import edu.uccs.arenger.hilas.quality.CssChecker;
import edu.uccs.arenger.hilas.quality.JsHinter;

public final class Hilas {
   private static final Logger LOGGER = LoggerFactory.getLogger(Hilas.class);
   private static final String PROPS = "hilas.properties";
   private static final String USAGE =
      "usage: hilas run\n" +
      "OR     hilas load FILE\n" +
      "OR     hilas check URL";

   enum Mode {
      RUN, LOAD, CHECK
   };

   private Mode mode;
   private File loadFile;
   private String urlToCheck;
   private Properties props;
   private ScheduledExecutorService runnerPool;
   private ScheduledExecutorService blockerPool;
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
         System.out.println("please specify 'run', 'load', or 'check'");
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
      case CHECK:
         if (args.length != 2) {
            System.out.println(USAGE);
            System.exit(1);
         }
         urlToCheck = args[1];
         break;
      default:
         break;
      }
   }

   private void getProps() {
      try {
         props = new Properties();
         URL url = ClassLoader.getSystemResource(PROPS);
         if (url == null) {
            throw new RuntimeException("sys resource not found: "+PROPS);
         }
         props.load(url.openStream());
      } catch (Exception e) {
         LOGGER.error("Problem loading properties file", e);
         System.err.println("Could not load properties file.  Exiting.");
         System.exit(1);
      }
   }

   private void shutdown() {
      LOGGER.info("Shutting down");
      Pool.shutdown();
      if (runnerPool != null) {
         runnerPool.shutdownNow();
      }
      if (blockerPool != null) {
         blockerPool.shutdownNow();
      }
      if (jsHinter != null) {
         jsHinter.close();
      }
   }

   private void startWorker(ScheduledExecutorService exec, Worker w) {
      ScheduledFuture<?> future = exec.scheduleWithFixedDelay(
         w, 1, w.getDelay(), w.getTimeUnit());
      w.setScheduledFuture(future);
   }

   private void corre() {
      try {
         Pool.init(props);
         Util.trustAllSslCerts();
         runnerPool = Executors.newScheduledThreadPool(
            Integer.parseInt(props.getProperty("runningPoolSize")));
         blockerPool = Executors.newScheduledThreadPool(
            Integer.parseInt(props.getProperty("blockingPoolSize")));

         startWorker(blockerPool, new SiteVisitor());
         startWorker(blockerPool, new SiteVisitor());
         startWorker(runnerPool, jsHinter = new JsHinter());
         startWorker(runnerPool, new CssChecker());
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

   private void run() {
      LOGGER.info("Mode: {}", mode);
      Runtime.getRuntime().addShutdownHook(new Thread() {
         public void run() { shutdown(); }
      });
      getProps();
      System.out.println("Started.  See hilas.log");
      switch (mode) {
         case RUN:
            corre();
            break;
         case LOAD:
            load();
            break;
         case CHECK:
            LOGGER.info("this mode is not yet implemented."); //TODO
            break;
      }
   }

   public static void main(String[] args) {
      new Hilas(args).run();
   }
}
