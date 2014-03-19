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
 *      Portions Copyright 2014 ForgeRock AS
 */
package org.opends.server.types.operation;



import java.util.List;

import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;


/**
 * This class defines a set of methods that are available for use by
 * post-response plugins for modify operations.  Note that this
 * interface is intended only to define an API for use by plugins and
 * is not intended to be implemented by any custom classes.
 */
@org.opends.server.types.PublicAPI(
     stability=org.opends.server.types.StabilityLevel.UNCOMMITTED,
     mayInstantiate=false,
     mayExtend=false,
     mayInvoke=true)
public interface PostResponseModifyOperation
       extends PostResponseOperation
{
  /**
   * Retrieves the raw, unprocessed entry DN as included in the client
   * request.  The DN that is returned may or may not be a valid DN,
   * since no validation will have been performed upon it.
   *
   * @return  The raw, unprocessed entry DN as included in the client
   *          request.
   */
  public ByteString getRawEntryDN();



  /**
   * Retrieves the DN of the entry to modify.
   *
   * @return  The DN of the entry to modify.
   */
  public DN getEntryDN();



  /**
   * Retrieves the set of raw, unprocessed modifications as included
   * in the client request.  Note that this may contain one or more
   * invalid modifications, as no validation will have been performed
   * on this information.  The list returned must not be altered by
   * the caller.
   *
   * @return  The set of raw, unprocessed modifications as included
   *          in the client request.
   */
  public List<RawModification> getRawModifications();



  /**
   * Retrieves the set of modifications for this modify operation.
   Its contents should not be altered.
   *
   * @return  The set of modifications for this modify operation.
   */
  public List<Modification> getModifications();



  /**
   * Retrieves the current entry before any modifications are applied.
   * It should not be modified by the caller.
   *
   * @return  The current entry before any modifications are applied.
   */
  public Entry getCurrentEntry();



  /**
   * Retrieves the modified entry that is to be written to the
   * backend.  It should not be modified by the caller.
   *
   * @return  The modified entry that is to be written to the backend.
   */
  public Entry getModifiedEntry();



  /**
   * Retrieves the set of clear-text current passwords for the user,
   * if available.  This will only be available if the modify
   * operation contains one or more delete elements that target the
   * password attribute and provide the values to delete in the clear.
   * This list should not be altered by the caller.
   *
   * @return  The set of clear-text current password values as
   *          provided in the modify request, or <CODE>null</CODE> if
   *          there were none.
   */
  List<ByteString> getCurrentPasswords();



  /**
   * Retrieves the set of clear-text new passwords for the user, if
   * available.  This will only be available if the modify operation
   * contains one or more add or replace elements that target the
   * password attribute and provide the values in the clear.  This
   * list should not be altered by the caller.
   *
   * @return  The set of clear-text new passwords as provided in the
   *          modify request, or <CODE>null</CODE> if there were none.
   */
  List<ByteString> getNewPasswords();
}

