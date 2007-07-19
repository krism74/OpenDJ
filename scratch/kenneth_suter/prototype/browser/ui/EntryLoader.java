package org.opends.statuspanel.browser.ui;

import org.opends.statuspanel.browser.ldap.Entry;

/**
 */
public interface EntryLoader {
  void addEntryLoaderListener(EntryLoaderListener listener);

  void reload();

  void setRoot(String name);

  void setRoot(Entry entry);

  void loadRoot();
}
