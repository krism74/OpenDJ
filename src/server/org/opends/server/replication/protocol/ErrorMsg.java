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
 *      Copyright 2006-2010 Sun Microsystems, Inc.
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.replication.protocol;
import org.forgerock.i18n.LocalizableMessage;

import static org.opends.server.util.StaticUtils.stackTraceToSingleLineString;

import java.io.UnsupportedEncodingException;
import java.util.zip.DataFormatException;

import org.forgerock.i18n.slf4j.LocalizedLogger;

/**
 * This message is part of the replication protocol.
 * This message is sent by a server or a replication server when an error
 * is detected in the context of a total update.
 */
public class ErrorMsg extends RoutableMsg
{
  // The tracer object for the debug logger
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  // Specifies the messageID built from the error that was detected
  private String msgID;

  // Specifies the complementary details about the error that was detected
  private LocalizableMessage details = null;

  // The time of creation of this message.
  //                                        protocol version previous to V4
  private Long creationTime = System.currentTimeMillis();

  /**
   * Creates an ErrorMsg providing the destination server.
   *
   * @param sender The server ID of the server that send this message.
   * @param destination The destination server or servers of this message.
   * @param details The message containing the details of the error.
   */
  public ErrorMsg(int sender, int destination,
                      LocalizableMessage details)
  {
    super(sender, destination);
    this.msgID  = getMessageId(details);
    this.details = details;
    this.creationTime = System.currentTimeMillis();

    if (logger.isTraceEnabled())
      logger.trace(" Creating error message" + this
          + " " + stackTraceToSingleLineString(new Exception("trace")));
  }

  /**
   * Creates an ErrorMsg.
   *
   * @param i replication server id
   * @param details details of the error
   */
  public ErrorMsg(int i, LocalizableMessage details)
  {
    super(-2, i);
    this.msgID  = getMessageId(details);
    this.details = details;
    this.creationTime = System.currentTimeMillis();

    if (logger.isTraceEnabled())
      logger.trace("new ErrorMsg=%s", this);
  }

  /**
   * Returns the unique message Id.
   */
  private String getMessageId(LocalizableMessage details)
  {
    return details.resourceName() + "-" + details.ordinal();
  }

  /**
   * Creates a new ErrorMsg by decoding the provided byte array.
   *
   * @param  in A byte array containing the encoded information for the LocalizableMessage
   * @param version The protocol version to use to decode the msg.
   * @throws DataFormatException If the in does not contain a properly
   *                             encoded message.
   */
  public ErrorMsg(byte[] in, short version)
  throws DataFormatException
  {
    super();
    try
    {
      /* first byte is the type */
      if (in[0] != MSG_TYPE_ERROR)
        throw new DataFormatException("input is not a valid " +
            this.getClass().getCanonicalName());
      int pos = 1;

      // sender
      int length = getNextLength(in, pos);
      String senderString = new String(in, pos, length, "UTF-8");
      senderID = Integer.valueOf(senderString);
      pos += length +1;

      // destination
      length = getNextLength(in, pos);
      String serverIdString = new String(in, pos, length, "UTF-8");
      destination = Integer.valueOf(serverIdString);
      pos += length +1;

      // MsgID
      length = getNextLength(in, pos);
      msgID = new String(in, pos, length, "UTF-8");
      pos += length +1;

      // Details
      length = getNextLength(in, pos);
      details = LocalizableMessage.raw(new String(in, pos, length, "UTF-8"));
      pos += length +1;

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        // Creation Time
        length = getNextLength(in, pos);
        String creationTimeString = new String(in, pos, length, "UTF-8");
        creationTime = Long.valueOf(creationTimeString);
        pos += length +1;
      }
    }
    catch (UnsupportedEncodingException e)
    {
      throw new DataFormatException("UTF-8 is not supported by this jvm.");
    }
  }

  /**
   * Get the details from this message.
   *
   * @return the details from this message.
   */
  public LocalizableMessage getDetails()
  {
    return details;
  }

  /**
   * Get the msgID from this message.
   *
   * @return the msgID from this message.
   */
  public String getMsgID()
  {
    return msgID;
  }

  // ============
  // Msg encoding
  // ============

  /**
   * {@inheritDoc}
   */
  @Override
  public byte[] getBytes(short version)
  {
    try {
      byte[] byteSender = String.valueOf(senderID).getBytes("UTF-8");
      byte[] byteDestination = String.valueOf(destination).getBytes("UTF-8");
      byte[] byteErrMsgId = String.valueOf(msgID).getBytes("UTF-8");
      byte[] byteDetails = details.toString().getBytes("UTF-8");
      byte[] byteCreationTime = null;

      int length = 1 + byteSender.length + 1
                     + byteDestination.length + 1
                     + byteErrMsgId.length + 1
                     + byteDetails.length + 1;

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        byteCreationTime = creationTime.toString().getBytes("UTF-8");
        length += byteCreationTime.length + 1;
      }

      byte[] resultByteArray = new byte[length];

      // put the type of the operation
      resultByteArray[0] = MSG_TYPE_ERROR;
      int pos = 1;

      // sender
      pos = addByteArray(byteSender, resultByteArray, pos);

      // destination
      pos = addByteArray(byteDestination, resultByteArray, pos);

      // MsgId
      pos = addByteArray(byteErrMsgId, resultByteArray, pos);

      // details
      pos = addByteArray(byteDetails, resultByteArray, pos);

      if (version >= ProtocolVersion.REPLICATION_PROTOCOL_V4)
      {
        // creation time
        pos = addByteArray(byteCreationTime, resultByteArray, pos);
      }

      return resultByteArray;
    }
    catch (UnsupportedEncodingException e)
    {
      return null;
    }
  }

  /**
   * Returns a string representation of the message.
   *
   * @return the string representation of this message.
   */
  public String toString()
  {
    return "ErrorMessage=["+
      " sender=" + this.senderID +
      " destination=" + this.destination +
      " msgID=" + this.msgID +
      " details=" + this.details +
      " creationTime=" + this.creationTime + "]";
  }

  /**
   * Get the creation time of this message.
   * When several attempts of initialization are done sequentially, it helps
   * sorting the good ones, from the ones that relate to ended initialization
   * when they are received.
   *
   * @return the creation time of this message.
   */
  public Long getCreationTime()
  {
    return creationTime;
  }

  /**
   * Get the creation time of this message.
   * @param creationTime the creation time of this message.
   */
  public void setCreationTime(long creationTime)
  {
    this.creationTime = creationTime;
  }

}
