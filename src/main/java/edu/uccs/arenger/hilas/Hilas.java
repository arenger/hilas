package edu.uccs.arenger.hilas;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.Pool;
import edu.uccs.arenger.hilas.dal.Site;

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
   }

   private void corre() {
   }

   private void load() {
      try (BufferedReader in = new BufferedReader(new FileReader(loadFile))) {
         Pool.init(props);
         String source = FilenameUtils.getBaseName(loadFile.getName());
         String line;
         while ((line = in.readLine()) != null) {
            Site site = new Site(line, source);
            site.insert();
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
            break;
      }
   }

   public static void main(String[] args) {
      new Hilas(args).run();
   }
}
