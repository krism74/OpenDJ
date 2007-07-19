package org.opends.statuspanel.browser.ui;

import org.opends.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import java.util.List;
import java.awt.*;

/**
 */
public interface TreeAdapter {

  Object createNode(Entry entry);

  void setChildren(Entry parent, List<Entry> children);

  Object getNodeValue(Object node, Object key);

  void setNodeValue(Object node, Object key, Object value);

  void removeAllChildren(Object node);

  Entry getNodeEntry(Object node);

  Object getNode(Object o);

  void setRoot(Entry entry);

  Entry getRootEntry();

  void addChild(Entry parent, Entry child);

  void setSelected(Entry entry);

  void remove(Entry entry);

  Entry getSelected();

  Component getTree();

  void addBrowseTreeListener(BrowseTreeListener listener);
}
