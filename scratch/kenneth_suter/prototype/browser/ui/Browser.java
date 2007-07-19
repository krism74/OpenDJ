package org.opends.statuspanel.browser.ui;

import org.opends.quicksetup.event.MinimumSizeComponentListener;
import org.opends.quicksetup.ui.UIFactory;
import org.opends.quicksetup.ui.Utilities;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DN;
import org.opends.server.types.RDN;
import org.opends.server.types.DirectoryException;
import org.opends.statuspanel.browser.ldap.Entry;
import org.opends.statuspanel.browser.ldap.DirectoryFacade;
import org.opends.statuspanel.browser.ldap.JndiDirectoryFacade;
import org.opends.statuspanel.browser.ldap.LdapServerInfo;
import org.opends.statuspanel.browser.ldap.EntryManager;
import org.opends.statuspanel.browser.BrowserProperties;

import javax.swing.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Properties;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * The main Browser window.  This class servers as the main launching
 * point for the browser, contructs the browser from the browser components
 * and reacts to events from the browser components.
 */
public class Browser extends JFrame {

  private static final Logger LOG = Logger.getLogger(Browser.class.getName());

  private static final long serialVersionUID = -651282065343750689L;

  private static final String CMD_REFRESH = "refresh";
  private static final String CMD_NEW_CHILD_ENTRY = "new child entry";
  private static final String CMD_DELETE_ENTRY = "delete child entry";

  private final String LDAP_FILTER = "LDAP Filter:";

  private TreeAdapter treeAdapter;

  private DirectoryFacade directory;

  private EntryLoader treeEntryLoader;

  private StatusPanel status;

  private LdapServerInfo ldapInfo;

  private Properties properties;

  /**
   * Constructs a new instance.  In order for this browser to
   * display entry nodes in the tree a call to
   * <code>setServerInfo</code> should be made after this method.
   */
  public Browser() {
    DirectoryServer.bootstrapClient();
    Utilities.setFrameIcon(this);
    setTitle("OpenDS Entry Browser");
    init();
  }

  /**
   * Configures this browser with information about the directory
   * whose nodes are being browsed.
   * @param info containing directory server information
   */
  public void setServerInfo(LdapServerInfo info) {
    this.ldapInfo = info;
    try {
      directory.setLdapServerInfo(info);
      treeEntryLoader.loadRoot();
    } catch (Exception e) {
      treeAdapter.setRoot(Entry.NULL_ENTRY);      
      LOG.log(Level.INFO, "Error initializing LDAP server info", e);
      status.error("Error establishing context with server " + info);
    }
  }

  /**
   * Sets credentials for accessing entries from the directory
   * @param name of a directory user
   * @param password of <code>user</code>
   */
  public void setCredentials(String name, String password) {
    try {
      directory.setCredentials(name, password);
    } catch (Exception e) {
      LOG.log(Level.INFO, "Error setting credentials", e);
      status.error(e.getLocalizedMessage());
    }
  }

  /**
   * Constructs the browser UI.
   */
  private void init() {

    final BrowseTreeMenu treeMenu = new BrowseTreeMenu();

    status = new StatusPanel();
    // status.setBorder(BorderFactory.createLineBorder(Color.BLUE));

    final JComboBox cboBase = UIFactory.makeJComboBox();
    BrowserProperties props = BrowserProperties.getInstance();
    List<String> baseDns = props.getBaseDnList();
    for (String dn : baseDns) {
      cboBase.addItem(new BaseDnItem(dn));
    }
    cboBase.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        BaseDnItem bdi = (BaseDnItem) cboBase.getSelectedItem();
        Entry entry = bdi.entry;
        if (entry != null) {
          treeEntryLoader.setRoot(entry);
        } else {
          treeEntryLoader.setRoot(bdi.name);
        }
      }
    });

    final JComboBox cboFilterAttr = UIFactory.makeJComboBox();
    cboFilterAttr.addItem("");
    List<String> filterAttrs = props.getFilterAttributeList();
    for (String attr : filterAttrs) {
      cboFilterAttr.addItem(attr);
    }
    cboFilterAttr.addItem(LDAP_FILTER);
    final JTextField tfFileterValue = new JTextField();
    JButton butApplyFilter = new JButton("Apply");
    butApplyFilter.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String filter;
        String attr = cboFilterAttr.getSelectedItem().toString();
        if (LDAP_FILTER.equals(attr)) {
          filter = tfFileterValue.getText();
        } else if ("".equals(attr)) {
          filter = null;
        } else {
          String value = tfFileterValue.getText();
          String op = "=";
          filter = new StringBuilder().append(attr).
                  append(op).append(value).toString();
        }
        directory.setFilter(filter);
        treeEntryLoader.reload();
      }
    });

    final ContentPanel pnlContent = new ContentPanel();
    pnlContent.addContentListener(new ContentListener() {

      public void actionPerformed(ContentEvent evt) {
        Entry entry = evt.getCurrentEntry();
        Map<String, Set<String>> attrs = evt.getCurrentAttributes();
        if (ContentPanel.CMD_NEW_CHILD.equals(evt.getAction())) {
          createNewChildEntry(entry);
        } else if (ContentPanel.CMD_DELETE.equals(evt.getAction())) {
          deleteEntry(entry);
        } else if (ContentPanel.CMD_SAVE_EXISTING.equals(evt.getAction())) {
          modifyEntry(entry, attrs);
        } else if (ContentPanel.CMD_SAVE_NEW.equals(evt.getAction())) {

        } else if (ContentPanel.CMD_CANCEL.equals(evt.getAction())) {
          if (entry.isNew()) {
            treeAdapter.remove(entry);
            treeAdapter.setSelected(entry.getParent());
          }
        } else if (ContentPanel.CMD_ABANDON.equals(evt.getAction())) {
          if (entry.isNew()) {
            treeAdapter.remove(entry);
          }
        }
      }
    });

    directory = new JndiDirectoryFacade();
    treeAdapter = new SwingTreeAdapter();
    treeAdapter.addBrowseTreeListener(new BrowseTreeListener() {

      public void entrySeleted(BrowseTreeEvent e) {
        pnlContent.setEntry(e.getEntry());
      }

      public void menuInvoked(BrowseTreeEvent e) {
        treeMenu.setState(e.getEntry());
        treeMenu.show(treeAdapter.getTree(), e.getX(), e.getY());
      }
    });


    treeEntryLoader = new SwingTreeEntryLoader(treeAdapter, directory, status);

    // Add a listener to update the Base DN combo box with
    // non-leaf entries
    // TODO: this is not part of the prototype, remove?
    treeEntryLoader.addEntryLoaderListener(new EntryLoaderListener() {
      public void entryLoaded(List<Entry> entries) {
        for (Entry entry : entries) {
          final Entry e = entry;
          new Thread(new Runnable() {
            public void run() {
              if (!e.isLeaf()) {
                SwingUtilities.invokeLater(new Runnable() {
                  public void run() {
                    cboBase.addItem(new BaseDnItem(e.toString(), e));
                  }
                });
              }
            }
          }).start();
        }
      }
    });

    setLayout(new BorderLayout());
    add(createTopPanel(cboBase, cboFilterAttr, tfFileterValue, butApplyFilter),
            BorderLayout.NORTH);
    add(createSplitPane(treeAdapter.getTree(), pnlContent), BorderLayout.CENTER);
    add(createBottomPanel(status), BorderLayout.SOUTH);
  }

  private JPanel createTopPanel(JComboBox cboBase,
                                JComboBox cboFilterAttr,
                                JTextField tfFilterValue,
                                JButton butApplyFilter) {
    JPanel topPanel = new JPanel();
    topPanel.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();

    tfFilterValue.setPreferredSize(cboBase.getPreferredSize());

    gbc.insets.left = 5;
    gbc.insets.top = 7;
    gbc.insets.bottom = 7;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    topPanel.add(UIFactory.makeJLabel(null, "Base DN:", UIFactory.TextStyle.SECONDARY_FIELD_VALID), gbc);

    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    topPanel.add(cboBase, gbc);

    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.insets.left = 15;
    topPanel.add(UIFactory.makeJLabel(null, "Filter:", UIFactory.TextStyle.SECONDARY_FIELD_VALID), gbc);

    gbc.insets.left = 5;
    gbc.weightx = .75;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    topPanel.add(cboFilterAttr, gbc);

    gbc.weightx = 1.0;
    gbc.insets.left = 3;
    gbc.insets.right = 5;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    topPanel.add(tfFilterValue, gbc);

    gbc.weightx = 0.0;
    gbc.insets.left = 5;
    gbc.insets.right = 5;
    gbc.fill = GridBagConstraints.NONE;
    topPanel.add(butApplyFilter, gbc);

    return topPanel;
  }

  private JSplitPane createSplitPane(Component tree, JPanel content) {
    JSplitPane sp =
            new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                    false,
                    new JScrollPane(tree),
                    content);
    sp.setOneTouchExpandable(true);
    sp.setDividerLocation(150);

    //Provide minimum sizes for the two components in the split pane
    Dimension minimumSize = new Dimension(100, 50);
    tree.setMinimumSize(minimumSize);
    content.setMinimumSize(minimumSize);
    return sp;
  }

  private JPanel createBottomPanel(StatusPanel status) {
    JPanel bottom = new JPanel();
    JButton btnClose = UIFactory.makeJButton("Close", "Close entry browser");
    btnClose.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        setVisible(false);
        dispose();
      }
    });

    bottom.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    bottom.setOpaque(false);
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.LINE_AXIS));
    bottom.add(status);
    bottom.add(Box.createHorizontalGlue());
    bottom.add(btnClose);
    return bottom;
  }

  private void createNewChildEntry(Entry parent) {
    DN parentName = parent.getDn();
    try {
      RDN childRdn = RDN.decode("uid=New Entry");
      DN childName = parentName.concat(childRdn);
      Entry child = EntryManager.createEntry(childName, null);
      EntryManager.addChild(parent, child);
      treeAdapter.addChild(parent, child);
      treeAdapter.setSelected(child);
    } catch (DirectoryException e) {
      e.printStackTrace();
    }
  }

  private void deleteEntry(final Entry entry) {
    new Thread(new Runnable() {
      public void run() {
        DirectoryFacade.DeleteOperation dop = directory.deleteEntry(entry);
        if (dop.perform()) {
          treeAdapter.remove(entry);
          treeAdapter.setSelected(entry.getParent());
        }
        status.error(dop.getLocalizedErrorMessage());
      }
    }, "Delete " + entry).start();
  }

  private void modifyEntry(final Entry entry,
                           final Map<String,Set<String>> attrs) {
    new Thread(new Runnable() {
      public void run() {
        DirectoryFacade.ModifyOperation dop =
                directory.modifyEntry(entry, attrs);
        if (dop.perform()) {
          entry.setAttributes(attrs);
        }
        treeAdapter.setSelected(entry);
        status.error(dop.getLocalizedErrorMessage());
      }
    }, "Modify " + entry).start();
  }

  /**
   * Implementation of a popup menu invoked by right-clicking
   * entries in the browse tree.
   */
  private class BrowseTreeMenu extends JPopupMenu {

    JMenuItem miNewChild;
    JMenuItem miDelete;
    JMenuItem miRefresh;

    BrowseTreeMenu() {

      ActionListener al = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          String cmd = e.getActionCommand();
          if (CMD_REFRESH.equals(cmd)) {
            setServerInfo(ldapInfo);
          } else if (CMD_NEW_CHILD_ENTRY.equals(cmd)) {
            createNewChildEntry(treeAdapter.getSelected());
          } else if (CMD_DELETE_ENTRY.equals(cmd)) {
            if (JOptionPane.YES_OPTION ==
                    JOptionPane.showConfirmDialog(
                    Browser.this,
                    "Delete this entry?",
                    "Confirm Delete",
                    JOptionPane.YES_NO_OPTION
                    )) {
              deleteEntry(treeAdapter.getSelected());
            }
          }
        }
      };

      miNewChild = new JMenuItem("New Child Entry");
      miNewChild.setActionCommand(CMD_NEW_CHILD_ENTRY);
      miNewChild.addActionListener(al);
      add(miNewChild);

      miDelete = new JMenuItem("Delete Entry");
      miDelete.setActionCommand(CMD_DELETE_ENTRY);
      miDelete.addActionListener(al);
      add(miDelete);

      miRefresh = new JMenuItem("Refresh");
      miRefresh.setActionCommand(CMD_REFRESH);
      miRefresh.addActionListener(al);
      add(miRefresh);
    }

    public void setState(Entry entry) {
      miDelete.setEnabled(entry != null && entry.canDelete());
    }

  }

  /**
   * Class representing menu items in the root DN
   * combobox that allows the user to set the root
   * of the browse tree.
   */
  private class BaseDnItem {

    String name;
    Entry entry;

    /**
     * Creates a new items using only a name.
     * @param name to appear on the menu
     */
    BaseDnItem(String name) {
      this(name, null);
    }

    /**
     * Creates a new item using a display name and an entry
     * that has already been loaded.
     * @param name to appear on the menu
     * @param entry representing the root
     */
    BaseDnItem(String name, Entry entry) {
      this.name = name;
      this.entry = entry;
    }

    /**
     * {@inheritDoc}
     */
    public String toString() {
      return this.name;
    }

    /**
     * {@inheritDoc}
     */
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      BaseDnItem that = (BaseDnItem) o;

      if (name != null ? !name.equals(that.name) : that.name != null)
        return false;

      return true;
    }

    /**
     * {@inheritDoc}
     */
    public int hashCode() {
      return (name != null ? name.hashCode() : 0);
    }
  }

  /**
   * @param args
   */
  public static void main(final String[] args) {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Browser browser = new Browser();
        LdapServerInfo info = new LdapServerInfo(args[0],
                Integer.parseInt(args[1]));
        browser.setServerInfo(info);
        if (args.length > 3) {
          try {
            browser.setCredentials(args[2],
                  args[3]);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
        browser.addComponentListener(new MinimumSizeComponentListener(browser,
                600, 400));
        browser.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        browser.pack();
        browser.setVisible(true);
      }
    });
  }

}
