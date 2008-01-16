/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE
 * or https://OpenDS.dev.java.net/OpenDS.LICENSE.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opends/resource/legal-notices/OpenDS.LICENSE.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.scratch;



import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



/**
 * An application which determines the set of org.opends dependencies
 * for the named class and methods.
 */
public final class DependencyChecker {

  /**
   * Compares classes based on their names.
   */
  private static class ClassComparator implements Comparator<Class<?>> {

    /**
     * {@inheritDoc}
     */
    public int compare(Class<?> o1, Class<?> o2) {
      String n1 = o1.getName();
      String n2 = o2.getName();
      return n1.compareTo(n2);
    }

  }



  /**
   * Main application.
   *
   * @param args
   *          The command-line arguments.
   * @throws Exception
   *           Any exception that was thrown.
   */
  public static void main(String[] args) throws Exception {
    if (args.length < 1) {
      displayUsageAndExit();
    }

    int i;
    List<Pattern> ignoredPatterns = new LinkedList<Pattern>();
    for (i = 0; i < args.length - 1; i++) {
      if (args[i].startsWith("-")) {
        if (args[i].charAt(1) == 'i') {
          String pattern = args[i + 1];
          ignoredPatterns.add(Pattern.compile(pattern));
          i++;
        } else {
          displayUsageAndExit();
        }
      } else {
        break;
      }
    }

    String className = args[i++];

    List<Pattern> methodPatterns = new LinkedList<Pattern>();
    for (; i < args.length; i++) {
      methodPatterns.add(Pattern.compile(args[i]));
    }

    Class<?> clazz = Class.forName(className);
    DependencyChecker dependencyChecker = new DependencyChecker(clazz,
        ignoredPatterns);

    System.out.println("Dependency Tree:");
    System.out.println();

    Set<Class<?>> classes = new TreeSet<Class<?>>(new ClassComparator());

    for (Constructor<?> constructor : clazz.getConstructors()) {
      String name = constructor.getName();

      if (methodPatterns.isEmpty()) {
        dependencyChecker.checkConstructor(constructor, classes);
      } else {
        for (Pattern pattern : methodPatterns) {
          Matcher matcher = pattern.matcher(name);
          if (matcher.matches()) {
            dependencyChecker.checkConstructor(constructor, classes);
            break;
          }
        }
      }
    }

    for (Method method : clazz.getMethods()) {
      String name = method.getName();

      if (methodPatterns.isEmpty()) {
        dependencyChecker.checkMethod(method, classes);
      } else {
        for (Pattern pattern : methodPatterns) {
          Matcher matcher = pattern.matcher(name);
          if (matcher.matches()) {
            dependencyChecker.checkMethod(method, classes);
            break;
          }
        }
      }
    }

    for (Class<?> child : classes) {
      dependencyChecker.checkClass(child, 1);
    }

    System.out.println();
    System.out.println("Summary:");
    System.out.println();

    dependencyChecker.dump();
  }



  private static void displayUsageAndExit() {
    System.err
        .println("Usage: DependencyChecker [-i ignorePattern] className [methodPattern ...]");
    System.exit(1);
  };

  // The complete set of dependencies.
  private final Set<Class<?>> dependencies = new TreeSet<Class<?>>(
      new ClassComparator());

  // List of classes to be ignored.
  private final List<Pattern> ignoredPatterns;



  /**
   * Creates a new dependency checker.
   *
   * @param clazz
   *          The initial class.
   * @param ignoredPatterns
   *          Ignore classes matching these patterns.
   */
  public DependencyChecker(Class<?> clazz, List<Pattern> ignoredPatterns) {
    this.dependencies.add(clazz);
    this.ignoredPatterns = ignoredPatterns;
  }



  /**
   * Determine the set of dependencies for a class.
   *
   * @param clazz
   *          The class.
   * @param depth
   *          Recursion depth.
   */
  public void checkClass(Class<?> clazz, int depth) {
    if (!clazz.getName().startsWith("org.opends.")) {
      return;
    }

    for (Pattern pattern : ignoredPatterns) {
      Matcher matcher = pattern.matcher(clazz.getName());
      if (matcher.matches()) {
        return;
      }
    }

    if (dependencies.contains(clazz)) {
      printClass(clazz, depth, "dupe");
      return;
    }

    // Got a new class.
    dependencies.add(clazz);
    printClass(clazz, depth, null);

    Set<Class<?>> classes = new TreeSet<Class<?>>(new ClassComparator());

    for (Constructor<?> constructor : clazz.getConstructors()) {
      checkConstructor(constructor, classes);
    }

    for (Method method : clazz.getMethods()) {
      checkMethod(method, classes);
    }

    for (Class<?> child : classes) {
      checkClass(child, depth + 1);
    }
  }



  // Display a class.
  private void printClass(Class<?> clazz, int depth, String suffix) {
    for (int i = 0; i < depth; i++) {
      System.out.print("  ");
    }

    if (suffix != null) {
      System.out.println(clazz.getName() + " [" + suffix + "]");
    } else {
      System.out.println(clazz.getName());
    }
  }



  /**
   * Determine the set of dependencies for a constructor.
   *
   * @param constructor
   *          The constructor.
   * @param classes
   *          Collection in which classes should be put.
   */
  public void checkConstructor(Constructor<?> constructor,
      Collection<Class<?>> classes) {
    for (Class<?> clazz : constructor.getParameterTypes()) {
      classes.add(clazz);
    }

    for (Class<?> clazz : constructor.getExceptionTypes()) {
      classes.add(clazz);
    }
  }



  /**
   * Determine the set of dependencies for a method.
   *
   * @param method
   *          The method.
   * @param classes
   *          Collection in which classes should be put.
   */
  public void checkMethod(Method method, Collection<Class<?>> classes) {
    classes.add(method.getReturnType());

    for (Class<?> clazz : method.getParameterTypes()) {
      classes.add(clazz);
    }

    for (Class<?> clazz : method.getExceptionTypes()) {
      classes.add(clazz);
    }
  }



  /**
   * Dump the dependencies to stdout.
   */
  public void dump() {
    for (Class<?> clazz : dependencies) {
      System.out.println("  " + clazz.getName());
    }
  }

}
