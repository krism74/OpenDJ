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
 *      Portions copyright 2013 ForgeRock AS
 */
package org.opends.server.loggers;

import static org.opends.messages.LoggerMessages.*;
import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.util.StaticUtils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opends.messages.Message;
import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.FreeDiskSpaceLogRetentionPolicyCfg;
import org.opends.server.core.DirectoryServer;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.ResultCode;

/**
 * This class implements a retention policy based on the free disk space
 * available expressed as a percentage.
 */
public class FreeDiskSpaceRetentionPolicy implements
    RetentionPolicy<FreeDiskSpaceLogRetentionPolicyCfg>,
    ConfigurationChangeListener<FreeDiskSpaceLogRetentionPolicyCfg>
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  private long freeDiskSpace = 0;
  private FreeDiskSpaceLogRetentionPolicyCfg config;

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeLogRetentionPolicy(
      FreeDiskSpaceLogRetentionPolicyCfg config)
  {
    this.freeDiskSpace = config.getFreeDiskSpace();
    this.config = config;

    config.addFreeDiskSpaceChangeListener(this);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean isConfigurationChangeAcceptable(
      FreeDiskSpaceLogRetentionPolicyCfg config,
      List<Message> unacceptableReasons)
  {
    // Changes should always be OK
    return true;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public ConfigChangeResult applyConfigurationChange(
      FreeDiskSpaceLogRetentionPolicyCfg config)
  {
    // Default result code.
    ResultCode resultCode = ResultCode.SUCCESS;
    boolean adminActionRequired = false;
    ArrayList<Message> messages = new ArrayList<Message>();

    this.freeDiskSpace = config.getFreeDiskSpace();
    this.config = config;

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public File[] deleteFiles(FileNamingPolicy fileNamingPolicy)
      throws DirectoryException
  {
    File[] files = fileNamingPolicy.listFiles();
    if(files == null)
    {
      Message message =
          ERR_LOGGER_ERROR_LISTING_FILES.get(
              fileNamingPolicy.getInitialName().toString());
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message);
    }

    List<File> filesToDelete = new ArrayList<File>();

    if(files.length <= 0)
    {
      return new File[0];
    }

    long freeSpace = 0;
    try
    {
      freeSpace = files[0].getFreeSpace();
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      Message message =
          ERR_LOGGER_ERROR_OBTAINING_FREE_SPACE.get(files[0].toString(),
              stackTraceToSingleLineString(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   message, e);
    }

    if(debugEnabled())
    {
      TRACER.debugInfo("Current free disk space: %d, Required: %d", freeSpace,
          freeDiskSpace);
    }

    if (freeSpace > freeDiskSpace)
    {
      // No cleaning needed.
      return new File[0];
    }

    long freeSpaceNeeded = freeDiskSpace - freeSpace;

    // Sort files based on last modified time.
    Arrays.sort(files, new FileComparator());

    long freedSpace = 0;
    for (int j = files.length - 1; j < 1; j--)
    {
      freedSpace += files[j].length();
      filesToDelete.add(files[j]);
      if (freedSpace >= freeSpaceNeeded)
      {
        break;
      }
    }

    return filesToDelete.toArray(new File[filesToDelete.size()]);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public String toString()
  {
    return "Free Disk Retention Policy " + config.dn().toString();
  }
}

