# Documentation for ProGuard:
#   http://developer.android.com/guide/developing/tools/proguard.html
#   http://proguard.sourceforge.net/

#-dontshrink # shrinking enabled, see below
#-dontobfuscate # obfuscation enabled for one class (see below)
#-dontoptimize  //need optimization to kill the log statements
-dontpreverify
-keepattributes **
-dontwarn **
-dontnote **

# Rules are defined as negation filters!
# (! = negation filter, ** = all subpackages)
# Keep everything (** {*;}) except...

# * Obfuscate android.support.v7.view.menu.** to fix Samsung Android 4.2 bug
#   https://code.google.com/p/android/issues/detail?id=78377
# * Remove unneeded Spongy Castle packages to be under 64K limit
#   http://developer.android.com/tools/building/multidex.html

-keep class
      io.oversec.one.crypto.**,
      io.oversec.one.common.**,
      net.rehacktive.waspdb.**,
      com.esotericsoftware.kryo.**,
      com.nulabinc.zxcvbn.**,
      com.dlazaro66.qrcodereaderview.**,
      org.sufficientlysecure.**,
      org.openintents.**,
      org.objenesis.**,
      org.ow2.asm.**,
      !android.support.v7.view.menu.**,
      !com.google.protobuf.**,
      !com.google.zxing.**,
      !org.apache.commons.io.**,
      !org.spongycastle.math.ec.**,
      !org.spongycastle.crypto.tls.**,
      !org.spongycastle.crypto.openssl.**,
      !org.spongycastle.pqc.**,
      !org.spongycastle.x509.**,
      org.spongycastle.** {*;}

#-renamesourcefileattribute SourceFile
-dontobfuscate

-assumenosideeffects class roboguice.util.Ln {
  public static *** v(...);
  public static *** i(...);
  public static *** w(...);
  public static *** d(...);

}

