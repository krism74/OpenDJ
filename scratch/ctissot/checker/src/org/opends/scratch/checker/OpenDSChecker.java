package org.opends.scratch.checker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.opends.server.core.DirectoryServer;

/**
 * OpenDSChecker iterates through Checker instances and prints out result.
 * <br>The list of checker can be provided using the -c option (see USAGE).
 * 
 * <p>To plug a new test, write a new class checker implementing the 
 * <code>Checker</code> interface and add your class using the -c option.
 * 
 * @author Sun Microsystems Inc.
 */
public class OpenDSChecker {

  /**
   * Line separator.
   */
  public static String LFCR = System.getProperty("line.separator");
  /**
   * Static logger for convenience.
   */
  public static Logger LOGGER = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
  private static String CLASSNAME = OpenDSChecker.class.getName();

  /**
   * Usage of that class.
   */
  public static String USAGE = "Usage: " + OpenDSChecker.class.getName() +
        " [OPTIONS]"+LFCR+
        "Make sure the OpenDS.jar is in the classpath with all related jars " +
        "(je.jar, build-tools.jar)" +LFCR+LFCR+
        "Options :"+LFCR+
        " -p <public-packages>: specify public packages separated by ':'"+LFCR+
        " -l: list default checker classes"+LFCR+
        " -c <checker-classes>: specify checker classes separated by ':'";

  /**
   * List of packages declared as public.
   */
  public static Collection<String> PUBLIC_PACKAGES =
    Collections.unmodifiableCollection(
      Arrays.asList(
        //"org.opends.server.types",
        //"org.opends.server.types.operation",
        "org.opends.server.api",
        "org.opends.server.api.plugin"
        //"org.opends.messages",
        //"org.opends.server.protocols.internal"
      )
    );

  private static String openDSJar; // the OpenDS jar location

  // A default collection of Checker instances that will be called by this
  // class.
  private static Collection<Class> CHECKERS = new ArrayList<Class>();
  static {
    CHECKERS.add(PublicAPIChecker.class);
    CHECKERS.add(StabilityLevelChecker.class);
  }
        
  /**
   * Creates a new instance of this class and check that the OpenDS.jar file
   * is in the class path.
   */
  public OpenDSChecker() {
    if (OpenDSChecker.openDSJar == null) {
      String classpath = System.getProperty("java.class.path");
      String[] paths = classpath.split(System.getProperty("path.separator"));
      for (String path : paths) {
        if (path.endsWith("OpenDS.jar")) {
          OpenDSChecker.openDSJar = path;
          break;
        }
      }
      if (OpenDSChecker.openDSJar == null) {
        throw new RuntimeException("Unable to find OpenDS jar in classpath.");
      }
    }
  }

  /**
   * Private constructor with a specific list of checkers.
   * @param checkers a list of class that implement the Checker interface.
   */
  private OpenDSChecker(Collection<Class> checkers) {
    this();
    CHECKERS = checkers;
  }

  /*
   * Iterates through all classes contained in the OpenDS jar and call the
   * Checker.checkClass() method for all classes declared in the package name
   * given in argument.
   * 
   * @param checker the checker instance that will perform the check for all
   *                classes of the specified package
   * @param name the name of the package to check
   */
  private void checkPackage(Checker checker, String name) {
    String className = null;
    try {
      // open Jar file and iterate over entries
      JarFile jarFile = new JarFile(OpenDSChecker.openDSJar);
      Enumeration<JarEntry> enumeration = jarFile.entries();
      while (enumeration.hasMoreElements()) {
        JarEntry entry = enumeration.nextElement();

        String entryName = entry.getName();
        // keep only classes entries
        if (!entryName.endsWith(".class")) {
          continue;
        }
        if (entryName.contains("$")) {
          // skip anonymous classes
          continue;
        }

        // remove ".class" and replace '/' with '.'
        className = entryName.substring(0, entryName.length() - 6)
                             .replace("/", ".");
        int index = className.lastIndexOf(".");
        // extract package name
        String packageName = className.substring(0, index);
        // check if this package is desired
        if (!name.equals(packageName)) {
          continue;
        }
        try {
          // instantiate class and call the check method
          Class clazz = Class.forName(className);
          checker.checkClass(clazz);
        } catch (Throwable ex) {
          LOGGER.throwing(CLASSNAME, "checkPackage", ex);
        }
      }
    } catch (Throwable ex) {
      LOGGER.throwing(CLASSNAME, "checkPackage", ex);
    }
  }

  /*
   * Starts a DirectoryServer, call the checkPackage() method for all packages
   * declared as public.
   */
  public void check() {
    LOGGER.entering(CLASSNAME, "check");

    DirectoryServer.bootstrapClient();

    for(Class _checker: CHECKERS) {
      if (!Checker.class.isAssignableFrom(_checker)) {
        continue;
      }
      Checker checker;
      try {
        checker = (Checker) _checker.newInstance();
      } catch (Exception e) {
        continue;
      }
      for (String name : PUBLIC_PACKAGES) {
        checkPackage(checker, name);
      }
      checker.printStatus();
    }

  // DirectoryServer.shutDown(StabilityLevelChecker.class.getName(),
  //                          Message.EMPTY); throws NullPointerException :o(
    LOGGER.exiting(CLASSNAME, "check");
  }

  public static void main(String[] args) {
    args = new String[] {"-c", "org.opends.scratch.checker.StabilityLevelChecker", "-p" , "org.opends.server.types:org.opends.server.types.operation:org.opends.server.api:org.opends.server.api.plugin"};
    Collection<Class> checkerClasses = null;
    try {
      for(int index=0; index < args.length; index++) {
        if ( "-p".equals(args[index]) ) {
          index++;
          PUBLIC_PACKAGES = Collections.unmodifiableCollection(
                  Arrays.asList(args[index].split(":"))
          );
        } else if ( "-l".equals(args[index]) ) {
          System.out.println("Checker classes " + CHECKERS);
          System.exit(1);
        } else if ( "-c".equals(args[index]) ) {
          index++;
          String[] _checkerClasses = args[index].split(":");
          checkerClasses = new ArrayList<Class>(_checkerClasses.length);
          for (String _checkerClass : _checkerClasses) {
            try {
              Class checker = Class.forName(_checkerClass);
              if ( Checker.class.isAssignableFrom(checker) ) {
                checkerClasses.add(checker);
              }
            } catch (Exception e) {
              continue;
            }
          }
        }
      }
    } catch(Exception e) {
      // parsing error, recall USAGE and exit
      System.out.println(USAGE);
      System.exit(1);
    }

    OpenDSChecker checker;
    if ( checkerClasses == null ) {
      checker = new OpenDSChecker();
    } else {
      checker = new OpenDSChecker(checkerClasses);
    }
    checker.check();
  }
}
