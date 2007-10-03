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

package org.opends.guitools.statuspanel.browser.ui;

import org.opends.guitools.statuspanel.browser.ldap.Entry;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
   * Implementation of a popup menu invoked by right-clicking
 * entries in the browse tree.
 */
public class BrowseTreeMenu extends JPopupMenu {

  private static final long serialVersionUID = -8608265863768581808L;

  JMenuItem miNewChild;
  JMenuItem miDelete;
  JMenuItem miRefresh;

  BrowseTreeMenu(final Browser browser) {
    ActionListener al = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if (Browser.CMD_REFRESH.equals(cmd)) {
          browser.refresh();
        } else if (Browser.CMD_NEW_CHILD_ENTRY.equals(cmd)) {
          browser.newChildForSelectedEntry();
        } else if (Browser.CMD_DELETE_ENTRY.equals(cmd)) {
          if (JOptionPane.YES_OPTION ==
                  JOptionPane.showConfirmDialog(
                          browser,
                  "Delete this entry?",
                  "Confirm Delete",
                  JOptionPane.YES_NO_OPTION
                  )) {
            browser.deleteSelectedEntry();
          }
        }
      }
    };

    miNewChild = new JMenuItem("New Child Entry");
    miNewChild.setActionCommand(Browser.CMD_NEW_CHILD_ENTRY);
    miNewChild.addActionListener(al);
    add(miNewChild);

    miDelete = new JMenuItem("Delete Entry");
    miDelete.setActionCommand(Browser.CMD_DELETE_ENTRY);
    miDelete.addActionListener(al);
    add(miDelete);

    miRefresh = new JMenuItem("Refresh");
    miRefresh.setActionCommand(Browser.CMD_REFRESH);
    miRefresh.addActionListener(al);
    add(miRefresh);
  }

  public void setState(Entry entry) {
    miDelete.setEnabled(entry != null && entry.canDelete());
  }

}
