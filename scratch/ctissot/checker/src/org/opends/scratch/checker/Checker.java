package org.opends.scratch.checker;

/**
 * Interface that must be implemented by all checkers.
 * 
 * @author Sun Microsystems Inc.
 */
public interface Checker {

  /**
   * Checks the class in argument and returns the check result.
   * @param c The class to be checked by the checker implementation.
   */
  void checkClass(Class c);

  /**
   * Prints the status of all errors registered during the checkClass call.
   */
  void printStatus();
}
