package org.opends.statuspanel.browser.ui;

/**
 * Interface for listeners to browser tree events.
 */
public interface BrowseTreeListener {

  /**
   * Called when an entry has been selected in the browser tree.
   * @param evt event associated with the action.
   */
  public void entrySeleted(BrowseTreeEvent evt);

  /**
   * Called when the user has invoked the browser tree's context
   * menu.
   * @param evt event associated with the action.
   */
  public void menuInvoked(BrowseTreeEvent evt);

}
