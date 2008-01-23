package org.opends.scratch.checker;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.opends.server.types.PublicAPI;
import org.opends.server.types.StabilityLevel;

/**
 * StabilityLevelChecker checks that the PublicAPI hierarchy is relevant. That
 * means for example that a class tagged as private does not define methods
 * tagged as public.
 * 
 * @author Sun Microsystems Inc.
 */
public class StabilityLevelChecker implements Checker {

  private static String CLASSNAME = PublicAPIChecker.class.getName();

  // hack so that this class is able to provide a PRIVATE stability
  private static PublicAPI PRIVATE =
    (PublicAPI) Proxy.newProxyInstance(PublicAPI.class.getClassLoader(),
    new Class[]{PublicAPI.class},
    new InvocationHandler() {

      public Object invoke(Object proxy, Method method, Object[] args)
              throws Throwable {
        if (method.getName().equals("stability")) {
          return StabilityLevel.PRIVATE;
        }
        return null;
      }
    });
  // hack so that this class is able to provide a PUBLIC stability
  private static PublicAPI PUBLIC =
    (PublicAPI) Proxy.newProxyInstance(PublicAPI.class.getClassLoader(),
    new Class[]{PublicAPI.class},
    new InvocationHandler() {

      public Object invoke(Object proxy, Method method, Object[] args)
              throws Throwable {
        if (method.getName().equals("stability")) {
          return StabilityLevel.COMMITTED;
        }
        return null;
      }
    });

  private ErrorRepository errors = new ErrorRepository();

  /* If a method does not include this annotation, then it should be
   * assumed to inherit the class-level annotation.  If a class does not
   * include this annotation, then it should be assumed to inherit the
   * package-level annotation.  If a package does not include this
   * annotation, then it should be assumed the package is private and
   * should not be used by third-party code.
   */
  /**
   * Returns the PublicAPI for the provided method. The algorithm is the
   * following:
   * <br>try to find and return the PublicAPI annotation.
   * <br>If the method does not define its own annotation, then return the
   * PublicAPI at the class level.
   * 
   * @param m the method to analyze
   * @return the PublicAPI associated to the provided method or the PublicAPI of
   *         its declarig class.
   */
  private PublicAPI getPublicAPI(Method m) {
    PublicAPI result = null;
    for (Annotation annotation : m.getAnnotations()) {
      if (annotation.annotationType().equals(PublicAPI.class)) {
        result = (PublicAPI) annotation;
      }
    }
    if (result == null) {
      result = getPublicAPI(m.getDeclaringClass());
    }

    OpenDSChecker.LOGGER.finest("Method " + m.getName() +
                                " : " + result.stability());

    return result;
  }

  
  /**
   * Returns the PublicAPI for the provided class. The algorithm is the
   * following:
   * <br>try to find and return the PublicAPI annotation.
   * <br>If the class does not define its own annotation, then return the
   * PublicAPI at the package level.
   * 
   * @param c the class to analyze
   * @return the PublicAPI associated to the provided class or the PublicAPI of
   *         its declarig package.
   */
  private PublicAPI getPublicAPI(Class c) {
    PublicAPI result = null;
    for (Annotation annotation : c.getAnnotations()) {
      if (annotation.annotationType().equals(PublicAPI.class)) {
        result = (PublicAPI) annotation;
      }
    }
    if (result == null) {
      if (c.getName().startsWith("org.opends")) {
        result = getPublicAPI(c.getPackage());
      } else {
        result = PRIVATE;
      }
    }

    OpenDSChecker.LOGGER.finest("Class " + c.getName() +
                                " : " + result.stability());

    return result;
  }
  
  /**
   * Returns the PublicAPI for the provided package. The algorithm is the
   * following:
   * <br>try to instantiate the <package-name>.package-info and returns the
   * PublicAPI annotation.
   * <br>If the class does not exist, return a PublicAPI with a PUBLIC stability
   * level if the provided package belongs to the public packages; return a
   * PublicAPI with a PRIVATE stability level otherwise.
   * 
   * @param p the package to analyze
   * @return the PublicAPI associated to the <package-name>.package-info class,
   *         a PublicAPI with a public stability or a PublicAPI with a private
   *         stability.
   */
  private PublicAPI getPublicAPI(Package p) {
    PublicAPI result = null;

    if (p != null) {
      try {
        Class c = Class.forName(p.getName() + ".package-info");
        for (Annotation annotation : c.getAnnotations()) {
          if (annotation.annotationType().equals(PublicAPI.class)) {
            result = (PublicAPI) annotation;
          }
        }
      } catch (Exception ex) {
      }
    }
    if (result == null) {
      if (p != null && OpenDSChecker.PUBLIC_PACKAGES.contains(p.getName())) {
        result = PUBLIC;
      } else {
        result = PRIVATE;
      }
    }

    OpenDSChecker.LOGGER.finest("Package " + 
                                (p==null?"No package":p.getName()) +
                                " : " + result.stability());

    return result;
  }

  /**
   * Returns true if the inner stability handled by the PublicAPI is lower than
   * the outer stability; false otherwise.
   * 
   * @param inner inner PublicAPI (might be method)
   * @param outer outer PublicAPI (might be class defining the inner method)
   * @return true if the inner stability handled by the PublicAPI is lower than
   *              the outer stability; false otherwise.
   */
  private boolean isMoreStable(PublicAPI inner, PublicAPI outer) {
    return inner.stability().compareTo(outer.stability()) > 0;
  }

  public void checkClass(Class c) {
    OpenDSChecker.LOGGER.entering(CLASSNAME, "checkClass");

    PublicAPI capi = getPublicAPI(c);
    for (Method method : c.getDeclaredMethods()) {
      PublicAPI mapi = getPublicAPI(method);
      if (isMoreStable(mapi, capi)) {
        this.errors.addError(c,
                             new StabilityError(method.getName(), mapi, capi));
      }
    }

    OpenDSChecker.LOGGER.exiting(CLASSNAME, "checkClass");
  }

  public void printStatus() {
    OpenDSChecker.LOGGER.entering(CLASSNAME, "printStatus");

    System.out.println(this.errors);

    OpenDSChecker.LOGGER.exiting(CLASSNAME, "printStatus");
  }

  private static class StabilityError extends ErrorRepository.Error {

    private PublicAPI mapi, capi;

    public StabilityError(String name, PublicAPI mapi, PublicAPI capi) {
      super(name);
      this.mapi = mapi;
      this.capi = capi;
    }

    @Override
    public String toString() {
      return this.name + " : method has stability " + this.mapi.stability() +
             " whereas its declaring class has " + this.capi.stability();
    }
  }
}
