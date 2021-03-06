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
 *      Copyright 2008 Sun Microsystems, Inc.
 */

package org.opends.quicksetup.installer;

import java.util.HashSet;
import java.util.Set;

/**
 * Class used to know what has been modified in the configuration of a
 * replication domain.
 * This class provides a read only view of what has been configured.
 *
 */
class ConfiguredDomain
{
  private String domainName;
  private boolean isCreated;
  Set<String> addedReplicationServers;

  /**
   * Constructor of the ConfiguredDomain object.
   * @param domainName the name of the domain.
   * @param isCreated whether the domain has been created or not.
   * @param addedReplicationServers the set of replication servers added to
   * the replication server configuration.
   */
  ConfiguredDomain(String domainName, boolean isCreated,
      Set<String> addedReplicationServers)
  {
    this.domainName = domainName;
    this.isCreated = isCreated;
    this.addedReplicationServers = new HashSet<String>();
    this.addedReplicationServers.addAll(addedReplicationServers);
  }

  /**
   * Returns a set of replication servers added to the replication domain
   * configuration.
   * @return a set of replication servers added to the replication domain
   * configuration.
   */
  Set<String> getAddedReplicationServers()
  {
    return addedReplicationServers;
  }

  /**
   * Returns the domain name.
   * @return the domain name.
   */
  String getDomainName()
  {
    return domainName;
  }

  /**
   * Returns <CODE>true</CODE> if the Replication domain was created and
   * <CODE>false</CODE> otherwise.
   * @return <CODE>true</CODE> if the Replication domain was created and
   * <CODE>false</CODE> otherwise.
   */
  boolean isCreated()
  {
    return isCreated;
  }
}
