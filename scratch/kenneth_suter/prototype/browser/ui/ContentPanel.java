package org.opends.guitools.statuspanel.browser.ui;

import org.opends.quicksetup.ui.UIFactory;
import org.opends.guitools.statuspanel.browser.ldap.Entry;
import org.opends.guitools.statuspanel.browser.ui.EntryListener;
import org.opends.messages.Message;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.CardLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

/**
 * The main right-hand panel of the browser representing entry contents.
 * This panel hosts tool bars for operating on the entry as well as
 * viewers for representing the entry visually.
 */
public class ContentPanel extends JPanel implements ActionListener, EntryListener {

  /** Action command string for new child entries. */
  static public final String CMD_NEW_CHILD = "new child";

  /** Action command string for deleting an entry. */
  static public final String CMD_DELETE = "delete";

  /** Action command string for saving an existing entry. */
  static public final String CMD_SAVE_EXISTING = "save existing";

  /** Action command string for saving a new entry. */
  static public final String CMD_SAVE_NEW = "save new";

  /**
   * Fired if the user presses the cancel button.
   */
  static public final String CMD_CANCEL = "cancel";

  /**
   * Fired if the user indicates they don't care about the current
   * entry in response to another entry being set.
   */
  static public final String CMD_ABANDON = "abandon";

  private static final long serialVersionUID = -8011564990796028367L;

  static private final String NEW_TOOLS = "new";
  static private final String EXISTING_TOOLS = "existing";

  private JLabel      entryName;
  // JEditorPane entryArea;
  // private JTable      tblEntry;
  // private EntryTableModel tm;
  private JPanel      pnlAttributes;
  private AttributesView attrsView;
  private JPanel      toolbar;
  private JButton     butSaveExisting;
  private JButton     butDelete;
  private Entry currentEntry;

  private String currentToolbarName;

  private Set<ContentListener> contentListeners =
          new HashSet<ContentListener>();

  private Set<EntryListener> entryListeners =
          new HashSet<EntryListener>();

  private String ENTRY_DETAILS = "Entry Details - "; // TODO: i18n

  /**
   * Creates an instance.
   */
  public ContentPanel() {
    init();
  }

  /**
   * Adds a listener to this panel's events.
   * @param listener of events
   */
  public void addContentListener(ContentListener listener) {
    contentListeners.add(listener);
  }

  /**
   * Gets the entry currently represented in this panel.
   * @return the current entry
   */
  public Entry getEntry() {
    return this.currentEntry;
  }

  /**
   * Sets the entry to be represented in this panel.
   * @param entry to be represented in this panel
   * @return boolean where true indicates that this panel accepted
   *         the entry
   */
  public boolean setEntry(Entry entry) {
    boolean accepted;
    if (entry != null && !entry.equals(currentEntry)) {
      System.out.println("new entry set");
      if (!isDeletedEntry() && (isModifiedEntry() || isNewEntry())) {
        System.out.println("new entry is modified/new");
        if (JOptionPane.YES_OPTION ==
                JOptionPane.showConfirmDialog(
                        this,
                        "Lose unsaved changes?",
                        "Confirm Lose Changes",
                        JOptionPane.YES_NO_OPTION
                )) {
          currentEntry.delete();
          fireContentEvent(CMD_ABANDON);
        } else {
           return false;
        }
      }
      this.currentEntry = entry;
      setButtonState(entry);
      setModifiedEntry(false);
      String label = "<html><b>" + ENTRY_DETAILS + "</b>" + entry.getDn() + "</html>";
      entryName.setText(label);
      attrsView.setEntry(entry);
      showAttributes(true);
      //tm = new EntryTableModel(entry);
      //tblEntry.setModel(tm);
      if (entry.isNew()) {
        showTools(NEW_TOOLS);
      } else {
        showTools(EXISTING_TOOLS);
      }
  //    StringBuilder sb = new StringBuilder();
  //    Map<String, Set<String>> attrs = entry.getAttributes();
  //    Set<String> attrNames = attrs.keySet();
  //    for (String attrName : attrNames) {
  //      Set<String> values = attrs.get(attrName);
  //      for (String value : values) {
  //        sb.append(attrName);
  //        sb.append(":");
  //        sb.append("\t");
  //        sb.append(value);
  //        sb.append("\n");
  //      }
  //    }
  //    entryArea.setText(sb.toString());
      accepted = true;
    } else {
      accepted = true;
    }
    return accepted;
  }

  /**
   * {@inheritDoc}
   */
  public void actionPerformed(ActionEvent e) {
    boolean notify = true;
    String cmd = e.getActionCommand();
    if (CMD_NEW_CHILD.equals(cmd)) {
      if (!isModifiedEntry() || JOptionPane.YES_OPTION ==
                JOptionPane.showConfirmDialog(
                this,
                "Lose unsaved changes?",
                "Confirm Lose Changes",
                JOptionPane.YES_NO_OPTION
                )) {
        // showTools(NEW_TOOLS);
        // TODO: create clean content area
      } else {
        notify = false;
      }
    } else if (CMD_DELETE.equals(cmd)) {
      if (JOptionPane.YES_OPTION ==
              JOptionPane.showConfirmDialog(
              this,
              "Delete this entry?",
              "Confirm Delete",
              JOptionPane.YES_NO_OPTION
              )) {
        // TODO: delete
        // TODO: clean content area
      } else {
        notify = false;
      }
    } else if (CMD_SAVE_EXISTING.equals(cmd)) {
      if (JOptionPane.YES_OPTION ==
              JOptionPane.showConfirmDialog(
              this,
              "Save changes to entry??",
              "Confirm Save",
              JOptionPane.YES_NO_OPTION
              )) {
        // TODO: save changes
        setModifiedEntry(false);
      } else {
        notify = false;
      }
    } else if (CMD_CANCEL.equals(cmd)) {
      if (JOptionPane.YES_OPTION ==
              JOptionPane.showConfirmDialog(
              this,
              "Lose new entry?",
              "Confirm Lose Changes",
              JOptionPane.YES_NO_OPTION
              )) {
        currentEntry.delete();
      } else {
        notify = false;
      }
    } else if (CMD_SAVE_NEW.equals(cmd)) {
      if (JOptionPane.YES_OPTION ==
              JOptionPane.showConfirmDialog(
              this,
              "Save changes to entry??",
              "Confirm Save",
              JOptionPane.YES_NO_OPTION
              )) {
        // TODO: save changes
        setModifiedEntry(false);
      } else {
        notify = false;
      }
    }
    if (notify) {
      fireContentEvent(cmd);
    }
  }

  /**
   * Notifies listeners of content events.
   * @param command describing the cause for the event
   */
  protected void fireContentEvent(String command) {
    Map<String,Set<String>> attrs = attrsView.getAttributes();
    for (ContentListener listener : contentListeners) {
      listener.actionPerformed(new ContentEvent(currentEntry, attrs, command));
    }
  }

//  private List<AttributesView> createAttributesViews() {
//    List<AttributesView> lstAttrViews = new ArrayList<AttributesView>();
//
//    EmptyAttributesView empty = new EmptyAttributesView();
//    lstAttrViews.add(new EmptyAttributesView());
//
//    TableAttributesView table = new TableAttributesView();
//    table.addPropertyChangeListener(this);
//    lstAttrViews.add(new TableAttributesView());
//
//    return lstAttrViews;
//  }

  private void init() {
    entryName = UIFactory.makeJLabel(null,
            Message.raw(ENTRY_DETAILS),
            UIFactory.TextStyle.INSTRUCTIONS);
    attrsView = new TableAttributesView();
    attrsView.addEntryListener(this);
    pnlAttributes = new JPanel();
    CardLayout cl = new CardLayout();
    pnlAttributes.setLayout(cl);
    pnlAttributes.add("empty", new EmptyAttributesView());
    pnlAttributes.add("table", attrsView);


//    tblEntry = new JTable();
//    tblEntry.setShowGrid(false);
    // tblEntry.setOpaque(false);
//    entryArea = UIFactory.makeTextPane("Heres an entry",
//            UIFactory.TextStyle.INSTRUCTIONS);
//    entryArea.setEditable(true);
//    entryArea.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
//    entryArea.addKeyListener(new KeyAdapter() {
//      public void keyTyped(KeyEvent e) {
//        super.keyTyped(e);
//        changes = true;
//        butSaveExisting.setEnabled(true);
//      }
//    });

    setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.insets.top = 10;
    gbc.insets.left = 10;
    gbc.insets.right = 10;
    gbc.weightx = 1.0;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(entryName, gbc);
    gbc.gridy++;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weighty = 1.0;
//    add(new JScrollPane(entryArea), gbc);
    add(new JScrollPane(pnlAttributes), gbc);

    gbc.gridy++;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weighty = 0;
    gbc.insets.bottom = 10;
    add(toolbar = createToolBar(), gbc);
  }

  private JPanel createToolBar() {
    JPanel toolbar = new JPanel();
    toolbar.setOpaque(false);
    toolbar.setLayout(new CardLayout());

    JPanel existingEntryButtons = new JPanel();
    existingEntryButtons.setOpaque(false);
    existingEntryButtons.setLayout(new BoxLayout(existingEntryButtons,
            BoxLayout.LINE_AXIS));
    existingEntryButtons.add(createButton("New Child Entry", CMD_NEW_CHILD));
    existingEntryButtons.add(Box.createHorizontalStrut(5));
    existingEntryButtons.add(butDelete = createButton("Delete Entry", CMD_DELETE));
    existingEntryButtons.add(Box.createHorizontalStrut(5));
    existingEntryButtons.add(Box.createHorizontalGlue());
    existingEntryButtons.add(butSaveExisting = createButton("Save Changes", CMD_SAVE_EXISTING));
    butSaveExisting.setEnabled(false);

    JPanel newEntryButtons = new JPanel();
    newEntryButtons.setOpaque(false);
    newEntryButtons.setLayout(new BoxLayout(newEntryButtons,
            BoxLayout.LINE_AXIS));
    newEntryButtons.add(Box.createHorizontalGlue());
    newEntryButtons.add(createButton("Cancel", CMD_CANCEL));
    newEntryButtons.add(Box.createHorizontalStrut(5));
    newEntryButtons.add(createButton("Save", CMD_SAVE_NEW));

    toolbar.add(EXISTING_TOOLS, existingEntryButtons);
    toolbar.add(NEW_TOOLS, newEntryButtons);

    return toolbar;
  }

  private JButton createButton(String text, String command) {
    JButton btn = UIFactory.makeJButton(Message.raw(text), Message.raw(""));
    btn.setActionCommand(command);
    btn.addActionListener(this);
    return btn;
  }

  private void showAttributes(boolean show) {
    CardLayout cl = (CardLayout)pnlAttributes.getLayout();
    if (show) {
      cl.show(pnlAttributes, "table");
    } else {
      cl.show(pnlAttributes, "empty");
    }
  }

  private void showTools(String key) {
    CardLayout cl = (CardLayout)toolbar.getLayout();
    cl.show(toolbar, key);
    if (EXISTING_TOOLS.equals(key)) {
      butSaveExisting.setEnabled(false);
    }
    currentToolbarName = key;
  }

  private void setModifiedEntry(boolean changes) {
    butSaveExisting.setEnabled(changes);
  }

  private boolean isModifiedEntry() {
    return butSaveExisting.isEnabled();
  }

  private boolean isDeletedEntry() {
    return currentEntry != null && currentEntry.isDeleted();
  }

  private boolean isNewEntry() {
    return currentEntry != null && currentEntry.isNew();
  }

  private void setButtonState(Entry entry) {
    butDelete.setEnabled(entry.canDelete());    
  }

  public void actionPerformed(ContentEvent evt) {
    setModifiedEntry(true);
  }

  public void entryModified(EntryModifiedEvent evt) {
    setModifiedEntry(true);
  }

  public void entryDeleted(EntryDeletedEvent evt) {
  }

  public void entryAdded(EntryAddedEvent evt) {
  }
}
