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
 *      Copyright 2006-2008 Sun Microsystems, Inc.
 *      Portions Copyright 2014-2015 ForgeRock AS
 */
package org.opends.quicksetup.ui;

import org.forgerock.i18n.LocalizableMessage;
import static org.opends.messages.QuickSetupMessages.*;

/**
 * This panel is used to show the application is finished.
 */
public class FinishedPanel extends ProgressPanel
{
  private static final long serialVersionUID = 8129325068133356170L;

  /**
   * FinishedPanel constructor.
   * @param application Application this panel represents
   */
  public FinishedPanel(GuiApplication application)
  {
    super(application);
  }

  /** {@inheritDoc} */
  protected LocalizableMessage getTitle()
  {
    return INFO_FINISHED_PANEL_TITLE.get();
  }
}
