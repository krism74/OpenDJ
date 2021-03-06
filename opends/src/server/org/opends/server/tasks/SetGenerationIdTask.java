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
 *      Copyright 2006-2009 Sun Microsystems, Inc.
 *      Portions Copyright 2013 ForgeRock AS
 */
package org.opends.server.tasks;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.core.DirectoryServer.*;
import static org.opends.server.loggers.debug.DebugLogger.*;

import java.util.List;

import org.opends.messages.Message;
import org.opends.messages.MessageBuilder;
import org.opends.messages.TaskMessages;
import org.opends.server.backends.task.Task;
import org.opends.server.backends.task.TaskState;
import org.opends.server.loggers.debug.DebugTracer;
import org.opends.server.replication.plugin.LDAPReplicationDomain;
import org.opends.server.replication.service.ReplicationDomain;
import org.opends.server.types.*;

/**
 * This class provides an implementation of a Directory Server task that can
 * be used to import data over the replication protocol from another
 * server hosting the same replication domain.
 */
public class SetGenerationIdTask extends Task
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();
  private String  domainString            = null;
  private ReplicationDomain domain        = null;
  private Long generationId = null;

  /**
   * {@inheritDoc}
   */
  @Override
  public Message getDisplayName() {
    return TaskMessages.INFO_TASK_SET_GENERATION_ID_NAME.get();
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void initializeTask() throws DirectoryException
  {
    if (TaskState.isDone(getTaskState()))
    {
      return;
    }

    // FIXME -- Do we need any special authorization here?
    Entry taskEntry = getTaskEntry();

    // Retrieves the eventual generation-ID
    AttributeType typeNewValue =
      getAttributeType(ATTR_TASK_SET_GENERATION_ID_NEW_VALUE, true);
    List<Attribute> attrList = taskEntry.getAttribute(typeNewValue);
    if ((attrList != null) && !attrList.isEmpty())
    {
      try
      {
        generationId = Long.parseLong(TaskUtils.getSingleValueString(attrList));
      }
      catch(Exception e)
      {
        MessageBuilder mb = new MessageBuilder();
        mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_GENERATION_ID.get());
        mb.append(e.getMessage());
        throw new DirectoryException(ResultCode.CLIENT_SIDE_PARAM_ERROR,
            mb.toMessage());
      }
    }

    // Retrieves the replication domain
    AttributeType typeDomainBase =
      getAttributeType(ATTR_TASK_SET_GENERATION_ID_DOMAIN_DN, true);

    attrList = taskEntry.getAttribute(typeDomainBase);
    domainString = TaskUtils.getSingleValueString(attrList);

    try
    {
      DN dn = DN.decode(domainString);
      domain = LDAPReplicationDomain.retrievesReplicationDomain(dn);
    }
    catch(DirectoryException e)
    {
      MessageBuilder mb = new MessageBuilder();
      mb.append(TaskMessages.ERR_TASK_INITIALIZE_INVALID_DN.get());
      mb.append(e.getMessage());
      throw new DirectoryException(ResultCode.INVALID_DN_SYNTAX, e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  protected TaskState runTask()
  {
    if (debugEnabled())
    {
      TRACER.debugInfo("setGenerationIdTask is starting on domain %s"
              + domain.getBaseDNString());
    }

    try
    {
      domain.resetGenerationId(generationId);
    }
    catch(DirectoryException de)
    {
      logError(de.getMessageObject());
      return TaskState.STOPPED_BY_ERROR;
    }

    if (debugEnabled())
    {
      TRACER.debugInfo("setGenerationIdTask is ending SUCCESSFULLY");
    }
    return TaskState.COMPLETED_SUCCESSFULLY;
  }
}
