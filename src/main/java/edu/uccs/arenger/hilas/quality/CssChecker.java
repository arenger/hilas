package edu.uccs.arenger.hilas.quality;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
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

import edu.uccs.arenger.hilas.dal.Css;
import edu.uccs.arenger.hilas.dal.DalException;
import edu.uccs.arenger.hilas.dal.LintMsg;

public class CssChecker implements Runnable {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(CssChecker.class);

   //using a customized "language" to help identify unique error types
   private static final String LANG_FILE = "/CssValidator.hilas.properties";
   private static final String LANG = "hilas";
   private Pattern rawPattern = Pattern.compile("___(.+?)___");

   private Css css;

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

   public CssChecker(Css css) {
      this.css = css;
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
   private Set<LintMsg> checkCss(String url) throws Exception {
      long start = System.currentTimeMillis();
      Set<LintMsg> msgSet = new HashSet<LintMsg>();
      ApplContext cx = new ApplContext(LANG);
      cx.setWarningLevel(Integer.MAX_VALUE);
      DocumentParser parser = new DocumentParser(cx, url);
      StyleSheet style = parser.getStyleSheet();
      style.findConflicts(cx);
      Warning[] warnings = style.getWarnings().getWarnings();
      for (Warning warn : warnings) {
         String raw = rawExtract(warn.getWarningMessage());
         if (raw.length() > 0) {
            msgSet.add(new LintMsg(LintMsg.Subject.CSS, "warn", raw));
         } else {
            msgSet.add(
               new LintMsg(LintMsg.Subject.CSS, "warn", "no message"));
         }
      }
      CssError[] errors = style.getErrors().getErrors();
      for (CssError err : errors) {
         String raw = rawExtract(err.getException().getMessage());
         if (raw.length() > 0) {
            msgSet.add(new LintMsg(LintMsg.Subject.CSS, "error", raw));
         } else {
            msgSet.add(new LintMsg(LintMsg.Subject.CSS,
               "error", err.getException().getClass().getName()));
         }
      }
      LOGGER.debug( "css analysis time: {} sec", String.format("%.3f",
         (double)(System.currentTimeMillis() - start) / 1000));
      return msgSet;
   }

   public void wrappedRun() {
      if (css == null) {
         LOGGER.error("null css");
         return;
      } else {
         LOGGER.info("starting lint for css {}", css.getId());
      }
      try {
         Set<LintMsg> msgSet = null;
         try {
            msgSet = checkCss(css.getUrl().toString());
         } catch (Exception e) { //not sure what checkCss might throw ...
                                 // TODO: if it does NOT throw an exception
                                 // when a url cannot be read, then we need
                                 // to change this code to first get the
                                 // css src and validate it, b/c we need to
                                 // know (and the db needs to reflect) when
                                 // a css URL was not readable...
            LOGGER.warn("{} for css id {} - {}",
               e.getClass().getName(), css.getId(), e.getMessage());
            css.setLintState(LintState.ERROR);
         }
         if (msgSet != null) {
            Set<String> msgIds = new HashSet<String>();
            for (LintMsg msg : msgSet) {
               msgIds.add(LintMsg.idFor(msg));
            }
            LintMsg.associate(LintMsg.Subject.CSS, css.getId(), msgIds);
            css.setLintState(LintState.PROCESSED);
         }
         css.update();
      } catch (DalException e) {
         LOGGER.error("dal problem", e);
      }
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
      CssChecker me = new CssChecker(null);
      Set<LintMsg> msgs = me.checkCss(args[0]);
      for (LintMsg msg : msgs) {
         System.out.println(msg);
      }
   }
}
