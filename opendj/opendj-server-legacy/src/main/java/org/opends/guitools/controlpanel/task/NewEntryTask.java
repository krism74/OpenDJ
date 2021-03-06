/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at legal-notices/CDDLv1_0.txt.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2008-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */

package org.opends.guitools.controlpanel.task;

import static org.opends.messages.AdminToolMessages.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.ui.ColorAndFontConstants;
import org.opends.guitools.controlpanel.ui.ProgressDialog;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.nodes.BrowserNodeInfo;
import org.opends.guitools.controlpanel.util.Utilities;
import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.config.ConfigConstants;
import org.forgerock.opendj.ldap.ByteString;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;

/**
 * The task launched when we must create an entry.
 *
 */
public class NewEntryTask extends Task
{
  private Entry newEntry;
  private String ldif;
  private Set<String> backendSet;
  private BasicNode parentNode;
  private BrowserController controller;
  private DN dn;
  private boolean useAdminCtx;

  /**
   * Constructor of the task.
   * @param info the control panel information.
   * @param dlg the progress dialog where the task progress will be displayed.
   * @param newEntry the entry containing the new values.
   * @param ldif the LDIF representation of the new entry.
   * @param controller the BrowserController.
   * @param parentNode the parent node in the tree of the entry that we want
   * to create.
   */
  public NewEntryTask(ControlPanelInfo info, ProgressDialog dlg,
      Entry newEntry, String ldif,
      BasicNode parentNode, BrowserController controller)
  {
    super(info, dlg);
    backendSet = new HashSet<String>();
    this.newEntry = newEntry;
    this.ldif = ldif;
    this.parentNode = parentNode;
    this.controller = controller;
    dn = newEntry.getName();
    for (BackendDescriptor backend : info.getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (dn.isDescendantOf(baseDN.getDn()))
        {
          backendSet.add(backend.getBackendID());
        }
      }
    }
  }

  /** {@inheritDoc} */
  public Type getType()
  {
    return Type.NEW_ENTRY;
  }

  /** {@inheritDoc} */
  public Set<String> getBackends()
  {
    return backendSet;
  }

  /** {@inheritDoc} */
  public LocalizableMessage getTaskDescription()
  {
    return INFO_CTRL_PANEL_NEW_ENTRY_TASK_DESCRIPTION.get(dn);
  }

  /** {@inheritDoc} */
  protected String getCommandLinePath()
  {
    return null;
  }

  /** {@inheritDoc} */
  protected ArrayList<String> getCommandLineArguments()
  {
    return new ArrayList<String>();
  }

  /** {@inheritDoc} */
  public boolean canLaunch(Task taskToBeLaunched,
      Collection<LocalizableMessage> incompatibilityReasons)
  {
    if (!isServerRunning()
        && state == State.RUNNING
        && runningOnSameServer(taskToBeLaunched))
    {
      // All the operations are incompatible if they apply to this
      // backend for safety.  This is a short operation so the limitation
      // has not a lot of impact.
      Set<String> backends = new TreeSet<>(taskToBeLaunched.getBackends());
      backends.retainAll(getBackends());
      if (!backends.isEmpty())
      {
        incompatibilityReasons.add(getIncompatibilityMessage(this, taskToBeLaunched));
        return false;
      }
    }
    return true;
  }

  /** {@inheritDoc} */
  public boolean regenerateDescriptor()
  {
    return false;
  }

  /** {@inheritDoc} */
  public void runTask()
  {
    state = State.RUNNING;
    lastException = null;

    try
    {
      InitialLdapContext ctx;

      if (parentNode != null)
      {
        ctx = controller.findConnectionForDisplayedEntry(parentNode);
        useAdminCtx = controller.isConfigurationNode(parentNode);
      }
      else
      {
        ctx = getInfo().getDirContext();
        useAdminCtx = true;
      }
      BasicAttributes attrs = new BasicAttributes();
      BasicAttribute objectclass =
        new BasicAttribute(ConfigConstants.ATTR_OBJECTCLASS);
      for (String oc : newEntry.getObjectClasses().values())
      {
        objectclass.add(oc);
      }
      attrs.put(objectclass);
      for (org.opends.server.types.Attribute attr : newEntry.getAttributes())
      {
        String attrName = attr.getNameWithOptions();
        Set<ByteString> values = new LinkedHashSet<ByteString>();
        Iterator<ByteString> it = attr.iterator();
        while (it.hasNext())
        {
          values.add(it.next());
        }
        BasicAttribute a = new BasicAttribute(attrName);
        for (ByteString value : values)
        {
          a.add(value.toByteArray());
        }
        attrs.put(a);
      }

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          printEquivalentCommand();
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressWithPoints(
                  INFO_CTRL_PANEL_CREATING_ENTRY.get(dn),
                  ColorAndFontConstants.progressFont));
        }
      });

      ctx.createSubcontext(Utilities.getJNDIName(newEntry.getName().toString()),
          attrs);

      SwingUtilities.invokeLater(new Runnable()
      {
        public void run()
        {
          getProgressDialog().appendProgressHtml(
              Utilities.getProgressDone(ColorAndFontConstants.progressFont));
          boolean entryInserted = false;
          if (parentNode != null)
          {
            boolean isReallyParentNode = false;
            try
            {
              DN parentDN = DN.valueOf(parentNode.getDN());
              isReallyParentNode =
                parentDN.equals(newEntry.getName().parent());
            }
            catch (Throwable t)
            {
              // Bug
              t.printStackTrace();
              isReallyParentNode = false;
            }
            if (isReallyParentNode)
            {
              insertNode(parentNode, newEntry.getName(),
                  isBaseDN(newEntry.getName()));
              entryInserted = true;
            }
          }
          if (!entryInserted)
          {
            BasicNode root = (BasicNode)controller.getTreeModel().getRoot();
            BasicNode realParentNode = findParentNode(newEntry.getName(), root);
            if (realParentNode != null)
            {
              insertNode(realParentNode, newEntry.getName(), false);
            }
            else
            {
              if (isBaseDN(newEntry.getName()))
              {
                int nRootChildren = controller.getTreeModel().getChildCount(
                  controller.getTreeModel().getRoot());
                if (nRootChildren > 1)
                {
                  // Insert in the root.
                  insertNode(root, newEntry.getName(), true);
                }
              }
            }
          }
        }
      });
      state = State.FINISHED_SUCCESSFULLY;
    }
    catch (Throwable t)
    {
      lastException = t;
      state = State.FINISHED_WITH_ERROR;
    }
  }

  /**
   * Prints the equivalent command-line in the progress dialog.
   *
   */
  private void printEquivalentCommand()
  {
    ArrayList<String> args = new ArrayList<String>(getObfuscatedCommandLineArguments(
        getConnectionCommandLineArguments(useAdminCtx, true)));
    args.add(getNoPropertiesFileArgument());
    args.add("--defaultAdd");
    String equiv = getEquivalentCommandLine(getCommandLinePath("ldapmodify"),
        args);
    StringBuilder sb = new StringBuilder();
    sb.append(INFO_CTRL_PANEL_EQUIVALENT_CMD_TO_CREATE_ENTRY.get()).append("<br><b>");
    sb.append(equiv);
    sb.append("<br>");
    String[] lines = ldif.split("\n");
    for (String line : lines)
    {
      sb.append(obfuscateLDIFLine(line));
      sb.append("<br>");
    }
    sb.append("</b><br>");
    getProgressDialog().appendProgressHtml(Utilities.applyFont(sb.toString(),
        ColorAndFontConstants.progressFont));
  }

  private BasicNode findParentNode(DN dn, BasicNode root)
  {
    BasicNode parentNode = null;
    int nRootChildren = controller.getTreeModel().getChildCount(root);
    for (int i=0; i<nRootChildren; i++)
    {
      BasicNode node =
        (BasicNode)controller.getTreeModel().getChild(root, i);
      try
      {
        DN nodeDN = DN.valueOf(node.getDN());
        if (dn.isDescendantOf(nodeDN))
        {
          if (dn.size() == nodeDN.size() + 1)
          {
            parentNode = node;
            break;
          }
          else
          {
            parentNode = findParentNode(dn, node);
            break;
          }
        }
      }
      catch (Throwable t)
      {
        // Bug
        throw new RuntimeException("Unexpected error: "+t, t);
      }
    }
    return parentNode;
  }

  private void insertNode(BasicNode parentNode, DN dn, boolean isSuffix)
  {
    TreePath parentPath =
      new TreePath(controller.getTreeModel().getPathToRoot(parentNode));
    if (parentPath != null)
    {
      BrowserNodeInfo nodeInfo =
        controller.getNodeInfoFromPath(parentPath);
      if (nodeInfo != null)
      {
        TreePath newPath;
        if (isSuffix)
        {
          newPath = controller.addSuffix(dn.toString(), parentNode.getDN());
        }
        else
        {
          newPath = controller.notifyEntryAdded(
            controller.getNodeInfoFromPath(parentPath), dn.toString());
        }
        if (newPath != null)
        {
          controller.getTree().setSelectionPath(newPath);
          controller.getTree().scrollPathToVisible(newPath);
        }
      }
    }
  }

  private boolean isBaseDN(DN dn)
  {
    boolean isBaseDN = false;
    for (BackendDescriptor backend :
      getInfo().getServerDescriptor().getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        if (baseDN.getDn().equals(dn))
        {
          isBaseDN = true;
          break;
        }
      }
      if (isBaseDN)
      {
        break;
      }
    }
    return isBaseDN;
  }
}

