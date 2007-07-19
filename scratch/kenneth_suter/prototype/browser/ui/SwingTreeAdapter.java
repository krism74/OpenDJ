package org.opends.statuspanel.browser.ui;

import org.opends.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.*;

/**
 */
public class SwingTreeAdapter implements TreeAdapter {

  JTree tree;
  DefaultTreeModel tm;
  Set<BrowseTreeListener> listeners = new HashSet<BrowseTreeListener>();

  public SwingTreeAdapter() {
    this.tree = createTree();
  }

  public void addBrowseTreeListener(BrowseTreeListener listener) {
    listeners.add(listener);
  }

  public void remove(final Entry entry) {
    final EntryTreeNode node = (EntryTreeNode)entry.getNode();
    final EntryTreeNode parent = (EntryTreeNode)node.getParent();
    if (parent != null) {
      if (SwingUtilities.isEventDispatchThread()) {
        tm.removeNodeFromParent(node);
      } else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            tm.removeNodeFromParent(node);
          }
        });
      }
    }
  }

  public Entry getSelected() {
    TreePath tp = tree.getSelectionPath();
    EntryTreeNode node = (EntryTreeNode)tp.getLastPathComponent();
    return node.getEntry();
  }

  public void setSelected(final Entry entry) {
    final TreePath path = createTreePath(entry);
    if (SwingUtilities.isEventDispatchThread()) {
      tree.scrollPathToVisible(path);
      tree.setSelectionPath(path);
    } else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          tree.scrollPathToVisible(path);
          tree.setSelectionPath(path);
        }
      });
    }
  }

  public Object createNode(Entry entry) {
    EntryTreeNode node = new EntryTreeNode(entry);
    entry.setNode(node);
    return node;
  }

  public void addChild(Entry parent, Entry child) {
    final EntryTreeNode parentNode = (EntryTreeNode)parent.getNode();
    final EntryTreeNode childNode = new EntryTreeNode(child);
    if (SwingUtilities.isEventDispatchThread()) {
      tm.insertNodeInto(childNode, parentNode, parentNode.getChildCount());
    } else {
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          tm.insertNodeInto(childNode, parentNode, parentNode.getChildCount());
        }
      });
    }
  }

  public void setChildren(Entry parent, List<Entry> children) {
    final EntryTreeNode parentNode = (EntryTreeNode)parent.getNode();
    removeAllChildren(parentNode);
    for (Entry child : children) {
      addChild(parent, child);
    }
  }

  public Object getNodeValue(Object node, Object key) {
    return null;
  }

  public void setNodeValue(Object node, Object key, Object value) {
  }

  public void removeAllChildren(Object node) {
    if (node != null) {
      final EntryTreeNode entryNode = (EntryTreeNode)node;
      entryNode.removeAllChildren();
      if (SwingUtilities.isEventDispatchThread()) {
        tm.reload(entryNode);
      } else {
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            tm.reload(entryNode);
          }
        });
      }
    }
  }

  public Entry getNodeEntry(Object node) {
    return (Entry)((EntryTreeNode)node).getUserObject();
  }

  public Object getNode(Object o) {
    return null;
  }

  public void setRoot(Entry entry) {
    TreeNode treeNode = (TreeNode)entry.getNode();
    if (treeNode == null) {
      treeNode = (EntryTreeNode)createNode(entry);
    }
    tm = new DefaultTreeModel(treeNode);
    tree.setModel(tm);
  }

  public Entry getRootEntry() {
    EntryTreeNode node = (EntryTreeNode)tm.getRoot();
    return node.getEntry();    
  }

  public Component getTree() {
    return this.tree;
  }

  private JTree createTree() {
    final JTree tree = new JTree();
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode
            (TreeSelectionModel.SINGLE_TREE_SELECTION);

    DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
    Icon personIcon = null;
    renderer.setLeafIcon(personIcon);
    renderer.setClosedIcon(personIcon);
    renderer.setOpenIcon(personIcon);
    tree.setCellRenderer(renderer);

    tree.addMouseListener(new MouseAdapter() {

      public void mousePressed(MouseEvent e) {
        maybeShowMenu(e);
      }

      public void mouseReleased(MouseEvent e) {
        maybeShowMenu(e);
      }

      private void maybeShowMenu(MouseEvent e) {
        if (e.isPopupTrigger()) {
          fireMenuInvoked(e.getX(), e.getY());
        }
      }
    });

    //Listen for when the selection changes.
    tree.addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath tp = e.getNewLeadSelectionPath();
        if (tp != null) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)
                  tp.getLastPathComponent();
          if (node != null) {
            Entry entry = (Entry) node.getUserObject();
            fireEntrySelected(entry);
            // tree.setSelectionPath(e.getOldLeadSelectionPath());
          }
        }
      }
    });

    return tree;
  }

  protected void fireMenuInvoked(int x, int y) {
    BrowseTreeEvent evt = new BrowseTreeEvent(null, x, y);
    for (BrowseTreeListener listener : listeners) {
      listener.menuInvoked(evt);
    }
  }

  protected void fireEntrySelected(Entry entry) {
    BrowseTreeEvent evt = new BrowseTreeEvent(entry, -1, -1);
    for (BrowseTreeListener listener : listeners) {
      listener.entrySeleted(evt);
    }
  }

  private TreePath createTreePath(Entry entry) {
    TreePath tp = null;
    if (entry != null) {
      TreeNode node = (TreeNode)entry.getNode();
      TreeNode[] pathArray = tm.getPathToRoot(node);
      tp = new TreePath(pathArray);
    }
    return tp;
  }

  class EntryTreeNode extends DefaultMutableTreeNode {

    public EntryTreeNode(Entry entry) {
      super(entry, true);
      entry.setNode(this);
    }

    public Entry getEntry() {
      return (Entry)getUserObject();
    }

  }

}
