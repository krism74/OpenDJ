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
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */

package org.opends.statuspanel.browser.ui;

import org.opends.statuspanel.browser.ldap.Entry;
import org.opends.statuspanel.browser.ldap.DirectoryFacade;
import org.opends.statuspanel.browser.ui.EntryLoaderListener;
import org.opends.statuspanel.browser.ldap.EntryManager;

import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.util.concurrent.ExecutionException;
import java.util.WeakHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

/**
 *
 */
public class SwingTreeEntryLoader implements TreeExpansionListener, EntryLoader {

  TreeAdapter treeAdapter;
  DirectoryFacade provider;
  Map<Entry, EntryLoader> loaders =
          Collections.synchronizedMap(new WeakHashMap<Entry, EntryLoader>());
  Status status;
  Set<EntryLoaderListener> listeners = new HashSet<EntryLoaderListener>();

  public SwingTreeEntryLoader(TreeAdapter tm, DirectoryFacade provider,
                         Status status) {
    // TODO:  fix this
    JTree tree = (JTree)tm.getTree();

    tree.addTreeExpansionListener(this);
    this.treeAdapter = tm;
    this.provider = provider;
    this.status = status;
  }

  public void addEntryLoaderListener(EntryLoaderListener listener) {
    this.listeners.add(listener);
  }

  public void reload() {
    Entry entry = treeAdapter.getRootEntry();
    EntryManager.markForReloading(entry);
    setRoot(entry);    
  }

  public void setRoot(String name) {
    try {
      DirectoryFacade.SearchOperation op = provider.getEntry(name);
      List<Entry> le = op.getResult();
      if (le.size() > 0) {
        Entry entry = le.get(0);
        fireEntriesLoaded(Arrays.asList(entry));
        setRoot(entry);
      }
      status.error(op.getLocalizedErrorMessage());
      status.warning(op.getLocalizedWarningMessage());
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void setRoot(Entry entry) {
    treeAdapter.setRoot(entry);
    startLoader(entry, 2);
  }

  public void loadRoot() {
    try {
      DirectoryFacade.SearchOperation op = provider.getRoot();
      List<Entry> le = op.getResult();
      Entry entry = le.get(0);
      fireEntriesLoaded(Arrays.asList(entry));
      setRoot(entry);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public void treeExpanded(TreeExpansionEvent event) {
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)
            event.getPath().getLastPathComponent();
    if (node == null) return;
    Entry entry = (Entry) node.getUserObject();
    entryExpanded(entry);
  }

  protected void entryExpanded(Entry entry) {
    EntryLoader loader;
    if (null != (loader = loaders.get(entry))) {
      loader.setPriority(Thread.MAX_PRIORITY);
    } else {
      startLoader(entry, 2);
    }
    if (entry.getChildrenSizeLimitExceeded()) {
      status.warning(new StringBuilder().
              append("Maximum ").
              append(provider.getMaxEntries()).
              append(" Displayed.  Try applying a filter").
              toString());
    }
  }

  public void treeCollapsed(TreeExpansionEvent event) {

  }

  protected void fireEntriesLoaded(List<Entry> entries) {
    for (EntryLoaderListener listener : listeners) {
      listener.entryLoaded(entries);
    }
  }

  /**
   * Starts loading of <code>entry</code> as well as loading
   * children of <code>entry</code> to a depth of <code>depth</code>.
   * Note that <code>entry</code> itself may not require loading at
   * all.
   * @param entry
   * @param depth
   */
  synchronized private void startLoader(Entry entry, int depth) {
    EntryLoader loader = new EntryLoader(entry, depth, status);
    loaders.put(entry,  loader);
    loader.start();
  }

  class EntryLoader extends SwingWorker<List<Entry>> {

    Entry parent;
    Long msgId;
    int depth;
    Status status;
    boolean loadPerformed;

    public EntryLoader(Entry parent, int depth, Status status) {
      this.parent = parent;
      this.depth = depth;
      this.status = status;
    }

    protected List<Entry> construct() throws Exception {
      List<Entry> children = null;
      try {
        if (parent.childrenNeedLoading()) {
          msgId = status.wait("Loading children for " + parent);
          DirectoryFacade.SearchOperation so = provider.getChildren(parent);
          children = so.getResult();
          EntryManager.setChildren(parent, children, so.sizeLimitExceeded());
          fireEntriesLoaded(children);
          loadPerformed = true;
        } else {
          System.out.println("Entry " + parent + " already loaded.");
          children = parent.getChildren();
          loadPerformed = false;
        }
      } catch (Exception e) {
        // status.handleThrowable(e, getThrowableMessageInterpreter());
      } finally {
        // status.clear(msgId);
      }
      return children;
    }

    protected void finished() {
      try {
        List<Entry> children = get();
        // Update the tree if something was actually loaded.
        if (loadPerformed) {
          treeAdapter.setChildren(parent, children);
        }
        if (depth > 0) {
          for (Entry child : children) {
            startLoader(child, depth - 1);
          }
        }
        loaders.remove(parent);
        status.done(msgId);
      } catch (InterruptedException ex) {
        ex.printStackTrace();
      } catch (ExecutionException ex) {
        ex.printStackTrace();
      }
    }
  }

}
