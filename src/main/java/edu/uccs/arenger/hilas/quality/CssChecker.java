package edu.uccs.arenger.hilas.quality;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import edu.uccs.arenger.hilas.dal.CssValidMsg;

public class CssChecker implements Worker {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssChecker.class);

   //using a customized "language" to help identify unique error types
   private static final String LANG_FILE = "/Messages.properties.hilas";
   private static final String LANG = "hilas";
   private Pattern rawPattern = Pattern.compile("___(.+?)___");

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
   
   private String rawExtract(String msg) {
      String ret = "";
      if (msg != null) {
         Matcher m = rawPattern.matcher(msg);
         if (m.find()) {
            ret = m.group(1);
         }
      }
      return ret;
   }

   // return a Set of error TYPES -- strings similar to the "raw" jshint msgs
   private Set<CssValidMsg> checkCss(String url) {
      Set<CssValidMsg> msgSet = new HashSet<CssValidMsg>();
      ApplContext cx = new ApplContext(LANG);
      try {
         cx.setWarningLevel(Integer.MAX_VALUE);
         DocumentParser parser = new DocumentParser(cx, url);
         StyleSheet style = parser.getStyleSheet();
         style.findConflicts(cx);
         Warning[] warnings = style.getWarnings().getWarnings();
         for (Warning warn : warnings) {
            String raw = rawExtract(warn.getWarningMessage());
            if (raw.length() > 0) {
               msgSet.add(new CssValidMsg(CssValidMsg.Type.WARN, raw));
            } else {
               msgSet.add(new CssValidMsg(CssValidMsg.Type.WARN,
                  "no message"));
            }
         }
         CssError[] errors = style.getErrors().getErrors();
         for (CssError err : errors) {
            String raw = rawExtract(err.getException().getMessage());
            if (raw.length() > 0) {
               msgSet.add(new CssValidMsg(CssValidMsg.Type.ERROR, raw));
            } else {
               msgSet.add(new CssValidMsg(CssValidMsg.Type.ERROR,
                  err.getException().getClass().getName()));
            }
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
      Set<CssValidMsg> msgs = me.checkCss(args[0]);
      for (CssValidMsg msg : msgs) {
         System.out.println(msg);
      }
   }
}
