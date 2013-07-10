package edu.uccs.arenger.hilas.dal;

public enum Sbs {
   GOOGLE (1),
   MCAFEE (2),
   NORTON (4),
   WOT    (8);

   private int id;

   Sbs(int id) {
      //Note that each Sbs should have a unique bit.  i.e., the id's
      //shouldl start at one, with each successive id being a left
      //shift of the previous.
      this.id = id;
   }

   public int getId() { return id; }
}
