
//'input' is set from within Java.  make sure it's a javascript string:
input = input.toString();

//set options:
options = {
   asi: true, supernew: true, boss: true, loopfunc: true,
   maxerr: 1000, expr: true, shadow: true, eqeqeq: false,
   eqnull: true
};

if (!JSHINT(input, options)) {
   for (var i = 0; i < JSHINT.errors.length; i += 1) {
      var e = JSHINT.errors[i];
      if (e) { raws.add( e.raw); }
   }
}
