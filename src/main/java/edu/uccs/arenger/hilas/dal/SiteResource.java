package edu.uccs.arenger.hilas.dal;

import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SiteResource {
   private static final Logger LOGGER
      = LoggerFactory.getLogger(SiteResource.class);

   public static SiteResource get(
      Class<? extends SiteResource> type, String md5) throws DalException {
      if (type.equals(JavaScript.class)) {
         return JavaScript.get(md5);
      } else if (type.equals(Css.class)) {
         return Css.get(md5);
      } else {
         LOGGER.error("unexpected rsrc type: {}", type);
         return null;
      }
   }
   public static SiteResource create(Class<? extends SiteResource> type,
      URL url, String md5, int size) throws DalException {
      if (type.equals(JavaScript.class)) {
         return new JavaScript(url, md5, size);
      } else if (type.equals(Css.class)) {
         return new Css(url, md5, size);
      } else {
         LOGGER.error("unexpected rsrc type: {}", type);
         return null;
      }
   }
   public abstract void insert() throws DalException;
   public abstract void linkToSite(String siteId) throws DalException;
   public abstract String getId();
}
