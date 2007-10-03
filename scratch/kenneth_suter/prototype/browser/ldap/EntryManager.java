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

package org.opends.guitools.statuspanel.browser.ldap;

import org.opends.server.types.DN;
import org.opends.server.types.DirectoryException;

import java.util.List;

/**
 *
 */
public class EntryManager {

  static public Entry createEntry(DN dn, Object payload) throws DirectoryException {
    return new Entry(dn, payload);
  }

  static public void setChildren(Entry parent, List<Entry> children) {
    setChildren(parent, children, false);
  }

  static public void setChildren(Entry parent, List<Entry> children,
                                 boolean sizeExceeded) {
    parent.setChildren(children);
    parent.setChildrenSizeLimitExceeded(sizeExceeded);
    for (Entry child : children) {
      child.setParent(parent);
    }    
  }

  static public void addChild(Entry parent, Entry child) {
    parent.addChild(child);
    child.setParent(parent);
  }

  public static void markForReloading(Entry entry) {
    entry.setChildrenNeedReloading(true);
  }

}
