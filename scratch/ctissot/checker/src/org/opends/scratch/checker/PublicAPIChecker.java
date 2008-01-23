package org.opends.scratch.checker;

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Set;
import java.util.TreeSet;

/**
 * PublicAPIChecker checks that all public code declared in the public classes
 * only deal with public classes. That includes for constructors and methods,
 * arguments, return types and exceptions thrown.
 * 
 * @author Sun Microsystems Inc.
 */
public class PublicAPIChecker implements Checker {

  //list of non public classes
  private Set<String> CLASSES = new TreeSet<String>();
  // all errors
  private ErrorRepository errors = new ErrorRepository();

  private static String CLASSNAME = PublicAPIChecker.class.getName();

  private boolean isPublicClass(Class c) {
    // return false if this class is defined by OpenDS but does not belong
    // to public packages; otherwise return true.
    if (c.getName().startsWith("org.opends")) {
      if (OpenDSChecker.PUBLIC_PACKAGES.contains(c.getPackage().getName())) {
        return true;
      } else {
        CLASSES.add(c.getName());
        return false;
      }
    }
    return true;
  }

  private boolean isPublicClasses(Type[] types) {
    for (Type type : types) {
      if (!isPublicClass(type)) {
        return false;
      }
    }
    return true;
  }

  private boolean isPublicClass(Type type) {
    if (type instanceof Class) {
      return isPublicClass((Class) type);
    } else if (type instanceof GenericArrayType) {
      GenericArrayType gat = (GenericArrayType) type;
      if (!isPublicClass(gat.getGenericComponentType())) {
        return false;
      }
    } else if (type instanceof ParameterizedType) {
      ParameterizedType pt = (ParameterizedType) type;
      if (!isPublicClass(pt.getRawType())) {
        return false;
      }
      if (!isPublicClasses(pt.getActualTypeArguments())) {
        return false;
      }
      if (!isPublicClass(pt.getOwnerType())) {
        return false;
      }
    } else if (type instanceof TypeVariable) {
      TypeVariable tv = (TypeVariable) type;
      if (!isPublicClasses(tv.getBounds())) {
        return false;
      }
//      if (!isPublic(tv.getGenericDeclaration().getTypeParameters())) {
//        return false;
//      }
    } else if (type instanceof WildcardType) {
      WildcardType wt = (WildcardType) type;
      if (!isPublicClasses(wt.getLowerBounds())) {
        return false;
      }
      if (!isPublicClasses(wt.getUpperBounds())) {
        return false;
      }
    }
    return true;
  }

  public void checkClass(Class c) {
    OpenDSChecker.LOGGER.entering(CLASSNAME, "checkClass");

    for(Constructor constructor : c.getDeclaredConstructors()) {
      String name = constructor.getName();

      Type[] parameters = constructor.getGenericParameterTypes();
      for (int i = 0; i < parameters.length; i++) {
        if (!isPublicClass(parameters[i])) {
          this.errors.addError(c, new ParameterError(name, parameters[i], i));
        }
      }
      Type[] exceptions = constructor.getGenericExceptionTypes();
      for (int i = 0; i < exceptions.length; i++) {
        if (!isPublicClass(exceptions[i])) {
          this.errors.addError(c, new ExceptionError(name, exceptions[i]));
        }
      }
    }
    for (Method method : c.getDeclaredMethods()) {
      String name = method.getName();

      Type returnType = method.getGenericReturnType();
      if (!isPublicClass(returnType)) {
        this.errors.addError(c, new ReturnTypeError(name, returnType));
      }
      Type[] parameters = method.getGenericParameterTypes();
      for (int i = 0; i < parameters.length; i++) {
        if (!isPublicClass(parameters[i])) {
          this.errors.addError(c, new ParameterError(name, parameters[i], i));
        }
      }
      Type[] exceptions = method.getGenericExceptionTypes();
      for (int i = 0; i < exceptions.length; i++) {
        if (!isPublicClass(exceptions[i])) {
          this.errors.addError(c, new ExceptionError(name, exceptions[i]));
        }
      }
    }
    OpenDSChecker.LOGGER.exiting(CLASSNAME, "checkClass");
  }

  public void printStatus() {
    OpenDSChecker.LOGGER.entering(CLASSNAME, "printStatus");
  
    System.out.println("Public classes handling non public classes : " + 
                       this.errors.getAllErrors().size() + OpenDSChecker.LFCR +
                       OpenDSChecker.LFCR);
    System.out.println(this.errors);

    System.out.println(OpenDSChecker.LFCR +
                       "Total methods " + this.errors.getErrorCount());
    System.out.println(OpenDSChecker.LFCR + OpenDSChecker.LFCR +
                       "Non public classes used in public APIS " +
                       CLASSES.size());
    for (String s : CLASSES) {
      System.out.println(s);
    }
    OpenDSChecker.LOGGER.exiting(CLASSNAME, "printStatus");
  }

  /**
   * 
   *
   */
  private static class ReturnTypeError extends ErrorRepository.Error {

    private Type type;

    public ReturnTypeError(String name, Type t) {
      super(name);
      this.type = t;
    }

    @Override
    public String toString() {
      return this.name + " : return type (" + this.type + ")";
    }
  }

  private static class ExceptionError extends ErrorRepository.Error {

    private Type type;

    public ExceptionError(String name, Type t) {
      super(name);
      this.type = t;
    }

    @Override
    public String toString() {
      return this.name + " : exception thrown (" + this.type + ")";
    }
  }

  private static class ParameterError extends ErrorRepository.Error {

    private Type type;
    private int id;

    public ParameterError(String name, Type t, int id) {
      super(name);
      this.type = t;
      this.id = id;
    }

    @Override
    public String toString() {
      return this.name + " : parameter#" + this.id + " (" + this.type + ")";
    }
  }
}
