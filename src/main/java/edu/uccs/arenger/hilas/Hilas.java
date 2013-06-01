package edu.uccs.arenger.hilas;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Hilas {
   private static final Logger LOGGER = LoggerFactory.getLogger(Hilas.class);
   private static final String USAGE = 
      "usage: hilas run\n" +
      "OR     hilas load FILE\n" +
      "OR     hilas check URL";

   enum Mode {RUN, LOAD, CHECK};

   private Mode   mode;
   private File   loadFile;
   private String urlToCheck;

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

   private void run() {
      switch (mode) {
         case RUN:
            break;
         case LOAD:
            break;
         case CHECK:
            break;
      }
      LOGGER.info("hello");
   }

   public static void main(String[] args) {
      new Hilas(args).run();
   }
}
