package org.opends.scratch.checker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * This class is a small repository for handling errors. The format is a map
 * with classnames where the error occured as keys and a collection of errors
 * attached to the classname.
 * All errors handled must extend the ErrorRepository.Error.
 * 
 * @author Sun Microsystems Inc.
 */
public class ErrorRepository {

  private Map<String, Collection<Error>> errors = new TreeMap<String, Collection<Error>>();
  private Map<String, Collection<Error>> _errors = Collections.unmodifiableMap(errors);
  private Collection<String> _classes = Collections.unmodifiableCollection(_errors.keySet());

  private int count; // total of errors in this repository

  /**
   * Creates a new repository for errors.
   */
  public ErrorRepository() {
  }

  /**
   * Returns a String that list all erros in this instance. Output format is
   * <br>Classname where one or more errors occured.
   * <br>All errors occured within that class.
   *
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Entry<String, Collection<Error>> entry : errors.entrySet()) {
      sb.append(entry.getKey()).append(OpenDSChecker.LFCR);
      Collection<Error> collection = entry.getValue();
      for (Error error : collection) {
        sb.append("  ").append(error).append(OpenDSChecker.LFCR);
      }
    }
    return sb.toString();
  }

  /**
   * Adds an error associated to a given class.
   * @param c the class where this error occured
   * @param e the error associated to the class in argument
   */
  public void addError(Class c, Error e) {
    String classname = c.getName();
    Collection<Error> collection = errors.get(classname);
    if (collection == null) {
      collection = new ArrayList<Error>();
      errors.put(c.getName(), collection);
    }
    collection.add(e);
    this.count++;
  }

  /**
   * Returns the whole map of errors: keys are class names where errors occured
   * and values are collection of errors. The map returned is immutable.
   * @return the whole map of errors.
   */
  public Map<String, Collection<Error>> getAllErrors() {
    return this._errors;
  }

  /**
   * Returns the list of class names that contain an error.
   * @return the list of class names that contain an error.
   */
  public Collection<String> getErrorClasses() {
    return this._classes;
  }

  /**
   * Returns the number of errors in this repository.
   * @return the number of errors in this repository.
   */
  public int getErrorCount() {
    return this.count;
  }

  /**
   * Default implementation of an error.
   */
  public static class Error {
    /**
     * name of this error.
     */
    protected String name;

    /**
     * A generic error with a name.
     * @param name name associated with this error.
     */
    public Error(String name) {
      this.name = name;
    }

    @Override
    public String toString() {
      return this.name;
    }
  }
}
