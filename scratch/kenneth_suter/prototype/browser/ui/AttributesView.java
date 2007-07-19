package org.opends.statuspanel.browser.ui;

import org.opends.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import java.util.Set;
import java.util.Map;

/**
 */
public abstract class AttributesView extends JPanel {

  /**
   * Sets the attributes to be depicted in this view.
   * @param attributes
   */
  abstract void setAttributes(Map<String, Set<String>> attributes);

  /**
   * Gets the attributes represented in this view.
   */
  abstract Map<String, Set<String>> getAttributes();

  /**
   * Called to indicate that the attributes depicted in
   * this view have been modified.
   */
  protected void modified() {

  }

}
