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
 *      Copyright 2014 ForgeRock AS.
 */
package org.opends.server.core;

import org.opends.server.admin.server.ServerManagementContext;
import org.opends.server.types.DirectoryEnvironmentConfig;
import org.opends.server.types.Schema;

/**
 * Context for the server, giving access to global properties of the server.
 */
public interface ServerContext
{

  /**
   * Returns the directory of server instance.
   *
   * @return the instance root directory
   */
  public String getInstanceRoot();

  /**
   * Returns the root directory of server.
   *
   * @return the server root directory
   */
  public String getServerRoot();

  /**
   * Returns the schema of the server.
   *
   * @return the schema
   */
  public Schema getSchema();

  /**
   * Returns the environment of the server.
   *
   * @return the environment
   */
  public DirectoryEnvironmentConfig getEnvironment();

  /**
   * Returns the server management context, which gives
   * an entry point on configuration objects.
   *
   * @return the server management context
   */
  public ServerManagementContext getServerManagementContext();

}
