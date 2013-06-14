package edu.uccs.arenger.hilas.dal;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiteResource {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(SiteResource.class);

   public static SiteResource get( Class<? extends SiteResource> type,
      String content) throws DalException {
      if (type.equals(JavaScript.class)) {
         return JavaScript.get(content);
      } else if (type.equals(Css.class)) {
         return Css.get(content);
      } else {
         LOGGER.error("unexpected rsrc type: {}", type);
         return null;
      }
   }
   public static SiteResource create(Class<? extends SiteResource> type,
      URL url, String content) throws DalException {
      if (type.equals(JavaScript.class)) {
         return new JavaScript(url, content);
      } else if (type.equals(Css.class)) {
         return new Css(url, content);
      } else {
         LOGGER.error("unexpected rsrc type: {}", type);
         return null;
      }
   }
   public abstract void insert() throws DalException;
   public abstract void linkToSite(String siteId) throws DalException;
   public abstract String getId();
}
