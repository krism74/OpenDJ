package org.opends.guitools.statuspanel.browser.ui;

import org.opends.guitools.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

/**
 */
public abstract class AttributesView extends JPanel {

  private Entry entry;

  private List<EntryListener> listeners =
          new ArrayList<EntryListener>();

  /**
   * Sets the attributes to be depicted in this view.
   * @param attributes
   */
  abstract protected void setAttributes(Map<String, Set<String>> attributes);

  /**
   * Gets the attributes represented in this view.
   */
  abstract Map<String, Set<String>> getAttributes();

  public void setEntry(Entry entry) {
    this.entry = entry;
    setAttributes(entry.getAttributes());
  }

  public void addEntryListener(EntryListener el) {
    listeners.add(el);
  }

  public void removeEntryListener(EntryListener el) {
    listeners.remove(el);
  }

  /**
   * Called to indicate that the attributes depicted in
   * this view have been modified.
   * @param attr that changed
   * @param old value
   * @param neu value
   */
  synchronized protected void modified(String attr, Object old, Object neu) {
    EntryModifiedEvent evt = new EntryModifiedEvent(entry,
            EntryModifiedEvent.Action.MODIFY, attr, old, neu);
    for (EntryListener el : listeners) {
      el.entryModified(evt);
    }
  }

}
