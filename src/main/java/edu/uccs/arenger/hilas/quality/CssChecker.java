package edu.uccs.arenger.hilas.quality;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.w3c.css.css.CssValidator;
import org.w3c.css.css.DocumentParser;
import org.w3c.css.css.StyleSheet;
import org.w3c.css.parser.CssError;
import org.w3c.css.util.ApplContext;
import org.w3c.css.util.Messages;
import org.w3c.css.util.Utf8Properties;
import org.w3c.css.util.Warning;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.uccs.arenger.hilas.Worker;

public class CssChecker implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssChecker.class);

   //using a customized "language" to help identify unique error types
   private static final String LANG_FILE = "/Messages.properties.hilas";
   private static final String LANG = "hilas";

   private ScheduledFuture<?> scheduledFuture;

   static {
      try (InputStream in =
         CssChecker.class.getResourceAsStream(LANG_FILE)) {
         if (in != null) {
            Messages.languages_name.add(LANG);
            Utf8Properties props = new Utf8Properties();
            props.load(in);
            Messages.languages.put(LANG, props);
         } else {
            LOGGER.error("could not find css lang file: " + LANG_FILE);
         }
      } catch (Exception e) {
         LOGGER.error("Problem while adding css language file", e);
      }
   }

   public long getDelay() {
      return 1;
   }

   public TimeUnit getTimeUnit() {
      return TimeUnit.SECONDS;
   }

   public void setScheduledFuture(ScheduledFuture<?> scheduledFuture) {
      this.scheduledFuture = scheduledFuture;
   }

   private void stopWorking() {
      if (scheduledFuture != null) {
         LOGGER.info("stopping: {}", this);
         scheduledFuture.cancel(false);
      } else {
         LOGGER.info("scheduledFuture not set for {}", this);
      }
   }

   private Set<String> checkCss(String url) {
      Set<String> msgSet = new HashSet<String>();
      ApplContext cx = new ApplContext(LANG);
      try {
         DocumentParser parser = new DocumentParser(cx, url);
         StyleSheet style = parser.getStyleSheet();
         style.findConflicts(cx);
         Warning[] warnings = style.getWarnings().getWarnings();
         for (Warning warn : warnings) {
            //msgSet.add(warn.getWarningMessage());
            System.out.println(warn.getWarningMessage());
         }
         CssError[] errors = style.getErrors().getErrors();
         for (CssError err : errors) {
            System.out.printf("%s (line %d): %s\n",
               err.getException().getClass().getName(),
               err.getLine(), err.getException().getMessage());
         }
      } catch (Exception e) {
         LOGGER.warn("warning", e);
      }
      return msgSet;
   }

   private void wrappedRun() {
   }

   public void run() {
      try {
         wrappedRun();
      } catch (Exception e) {
         LOGGER.error("thread pool protection catch",e);
      }
   }

   public static void main(String[] args) throws Exception {
      if (args.length != 1) {
         System.out.println("url to css file required as 1st and only arg");
         System.exit(1);
      }
      LOGGER.info("started CssChecker.main");
      CssChecker me = new CssChecker();
      Set<String> msgs = me.checkCss(args[0]);
      for (String msg : msgs) {
         System.out.println(msg);
      }
   }
}
