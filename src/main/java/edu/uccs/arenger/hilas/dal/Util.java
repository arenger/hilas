package edu.uccs.arenger.hilas.dal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Util {
   private static final Logger LOGGER = LoggerFactory.getLogger(Util.class);

   private Util() {}

   public static void notNull(Object o, String name) throws DalException {
      if (o == null) {
         throw new DalException(name + " can't be null");
      }
   }
}
