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
 *      Copyright 2008-2011 Sun Microsystems, Inc.
 *      Portions Copyright 2011-2014 ForgeRock AS
 */
package org.opends.server.workflowelement.localbackend;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ModificationType;
import org.forgerock.util.Reject;
import org.forgerock.util.Utils;
import org.opends.server.api.*;
import org.opends.server.api.plugin.PluginResult;
import org.opends.server.controls.*;
import org.opends.server.core.*;
import org.opends.server.schema.AuthPasswordSyntax;
import org.opends.server.schema.UserPasswordSyntax;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.types.operation.PostOperationModifyOperation;
import org.opends.server.types.operation.PostResponseModifyOperation;
import org.opends.server.types.operation.PostSynchronizationModifyOperation;
import org.opends.server.types.operation.PreOperationModifyOperation;

import static org.opends.messages.CoreMessages.*;
import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;

/**
 * This class defines an operation used to modify an entry in a local backend
 * of the Directory Server.
 */
public class LocalBackendModifyOperation
       extends ModifyOperationWrapper
       implements PreOperationModifyOperation, PostOperationModifyOperation,
                  PostResponseModifyOperation,
                  PostSynchronizationModifyOperation
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The backend in which the target entry exists.
   */
  protected Backend backend;

  /** Indicates whether the request included the user's current password. */
  private boolean currentPasswordProvided;

  /**
   * Indicates whether the user's account has been enabled or disabled
   * by this modify operation.
   */
  protected boolean enabledStateChanged;

  /** Indicates whether the user's account is currently enabled. */
  private boolean isEnabled;

  /**
   * Indicates whether the request included the LDAP no-op control.
   */
  protected boolean noOp;

  /**
   * Indicates whether the request included the Permissive Modify control.
   */
  protected boolean permissiveModify = false;

  /**
   * Indicates whether this modify operation includes a password change.
   */
  protected boolean passwordChanged;

  /**
   * Indicates whether the request included the password policy request control.
   */
  protected boolean pwPolicyControlRequested;

  /**
   * Indicates whether the password change is a self-change.
   */
  protected boolean selfChange;

  /**
   * Indicates whether the user's account was locked before this change.
   */
  protected boolean wasLocked = false;

  /**
   * The client connection associated with this operation.
   */
  protected ClientConnection clientConnection;

  /**
   * The DN of the entry to modify.
   */
  protected DN entryDN;

  /**
   * The current entry, before any changes are applied.
   */
  protected Entry currentEntry = null;

  /**
   * The modified entry that will be stored in the backend.
   */
  protected Entry modifiedEntry = null;

  /** The number of passwords contained in the modify operation. */
  private int numPasswords;

  /** The post-read request control, if present.*/
  private LDAPPostReadRequestControl postReadRequest;

  /** The pre-read request control, if present.*/
  private LDAPPreReadRequestControl preReadRequest;

  /** The set of clear-text current passwords (if any were provided).*/
  private List<AttributeValue> currentPasswords = null;

  /** The set of clear-text new passwords (if any were provided).*/
  private List<AttributeValue> newPasswords = null;

  /**
   * The set of modifications contained in this request.
   */
  protected List<Modification> modifications;

  /**
   * The password policy error type for this operation.
   */
  protected PasswordPolicyErrorType pwpErrorType;

  /**
   * The password policy state for this modify operation.
   */
  protected PasswordPolicyState pwPolicyState;



  /**
   * Creates a new operation that may be used to modify an entry in a
   * local backend of the Directory Server.
   *
   * @param modify The operation to enhance.
   */
  public LocalBackendModifyOperation(ModifyOperation modify)
  {
    super(modify);
    LocalBackendWorkflowElement.attachLocalOperation (modify, this);
  }



  /**
   * Retrieves the current entry before any modifications are applied.  This
   * will not be available to pre-parse plugins.
   *
   * @return  The current entry, or <CODE>null</CODE> if it is not yet
   *          available.
   */
  @Override
  public final Entry getCurrentEntry()
  {
    return currentEntry;
  }



  /**
   * Retrieves the set of clear-text current passwords for the user, if
   * available.  This will only be available if the modify operation contains
   * one or more delete elements that target the password attribute and provide
   * the values to delete in the clear.  It will not be available to pre-parse
   * plugins.
   *
   * @return  The set of clear-text current password values as provided in the
   *          modify request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  @Override
  public final List<AttributeValue> getCurrentPasswords()
  {
    return currentPasswords;
  }



  /**
   * Retrieves the modified entry that is to be written to the backend.  This
   * will be available to pre-operation plugins, and if such a plugin does make
   * a change to this entry, then it is also necessary to add that change to
   * the set of modifications to ensure that the update will be consistent.
   *
   * @return  The modified entry that is to be written to the backend, or
   *          <CODE>null</CODE> if it is not yet available.
   */
  @Override
  public final Entry getModifiedEntry()
  {
    return modifiedEntry;
  }



  /**
   * Retrieves the set of clear-text new passwords for the user, if available.
   * This will only be available if the modify operation contains one or more
   * add or replace elements that target the password attribute and provide the
   * values in the clear.  It will not be available to pre-parse plugins.
   *
   * @return  The set of clear-text new passwords as provided in the modify
   *          request, or <CODE>null</CODE> if there were none or this
   *          information is not yet available.
   */
  @Override
  public final List<AttributeValue> getNewPasswords()
  {
    return newPasswords;
  }



  /**
   * Adds the provided modification to the set of modifications to this modify
   * operation.
   * In addition, the modification is applied to the modified entry.
   *
   * This may only be called by pre-operation plugins.
   *
   * @param  modification  The modification to add to the set of changes for
   *                       this modify operation.
   *
   * @throws  DirectoryException  If an unexpected problem occurs while applying
   *                              the modification to the entry.
   */
  @Override
  public void addModification(Modification modification)
    throws DirectoryException
  {
    modifiedEntry.applyModification(modification, permissiveModify);
    super.addModification(modification);
  }



  /**
   * Process this modify operation against a local backend.
   *
   * @param wfe
   *          The local backend work-flow element.
   * @throws CanceledOperationException
   *           if this operation should be cancelled
   */
  public void processLocalModify(final LocalBackendWorkflowElement wfe)
      throws CanceledOperationException
  {
    this.backend = wfe.getBackend();

    clientConnection = getClientConnection();

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    try
    {
      BooleanHolder executePostOpPlugins = new BooleanHolder(false);
      processModify(executePostOpPlugins);

      // If the password policy request control was included, then make sure we
      // send the corresponding response control.
      if (pwPolicyControlRequested)
      {
        addResponseControl(new PasswordPolicyResponseControl(null, 0,
            pwpErrorType));
      }

      // Invoke the post-operation or post-synchronization modify plugins.
      PluginConfigManager pluginConfigManager =
          DirectoryServer.getPluginConfigManager();
      if (isSynchronizationOperation())
      {
        if (getResultCode() == ResultCode.SUCCESS)
        {
          pluginConfigManager.invokePostSynchronizationModifyPlugins(this);
        }
      }
      else if (executePostOpPlugins.value)
      {
        // FIXME -- Should this also be done while holding the locks?
        PluginResult.PostOperation postOpResult =
            pluginConfigManager.invokePostOperationModifyPlugins(this);
        if (!postOpResult.continueProcessing())
        {
          setResultCode(postOpResult.getResultCode());
          appendErrorMessage(postOpResult.getErrorMessage());
          setMatchedDN(postOpResult.getMatchedDN());
          setReferralURLs(postOpResult.getReferralURLs());
          return;
        }
      }
    }
    finally
    {
      LocalBackendWorkflowElement.filterNonDisclosableMatchedDN(this);
    }


    // Register a post-response call-back which will notify persistent
    // searches and change listeners.
    if (getResultCode() == ResultCode.SUCCESS)
    {
      registerPostResponseCallback(new Runnable()
      {

        @Override
        public void run()
        {
          // Notify persistent searches.
          for (PersistentSearch psearch : wfe.getPersistentSearches())
          {
            psearch.processModify(modifiedEntry, getChangeNumber(),
                currentEntry);
          }

          // Notify change listeners.
          for (ChangeNotificationListener changeListener : DirectoryServer
              .getChangeNotificationListeners())
          {
            try
            {
              changeListener
                  .handleModifyOperation(LocalBackendModifyOperation.this,
                      currentEntry, modifiedEntry);
            }
            catch (Exception e)
            {
              logger.traceException(e);

              logger.error(ERR_MODIFY_ERROR_NOTIFYING_CHANGE_LISTENER, getExceptionMessage(e));
            }
          }
        }
      });
    }
  }


  private void processModify(BooleanHolder executePostOpPlugins)
      throws CanceledOperationException
  {
    entryDN = getEntryDN();
    if (entryDN == null)
    {
      return;
    }

    // Process the modifications to convert them from their raw form to the
    // form required for the rest of the modify processing.
    modifications = getModifications();
    if (modifications == null)
    {
      return;
    }

    if (modifications.isEmpty())
    {
      setResultCode(ResultCode.CONSTRAINT_VIOLATION);
      appendErrorMessage(ERR_MODIFY_NO_MODIFICATIONS.get(entryDN));
      return;
    }

    // Check for a request to cancel this operation.
    checkIfCanceled(false);

    // Acquire a write lock on the target entry.
    final Lock entryLock = LockManager.lockWrite(entryDN);

    try
    {
      if (entryLock == null)
      {
        setResultCode(ResultCode.BUSY);
        appendErrorMessage(ERR_MODIFY_CANNOT_LOCK_ENTRY.get(entryDN));
        return;
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // Get the entry to modify. If it does not exist, then fail.
      currentEntry = backend.getEntry(entryDN);

      if (currentEntry == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));

        // See if one of the entry's ancestors exists.
        setMatchedDN(findMatchedDN(entryDN));
        return;
      }

      // Check to see if there are any controls in the request. If so, then
      // see if there is any special processing required.
      processRequestControls();

      // Get the password policy state object for the entry that can be used
      // to perform any appropriate password policy processing. Also, see
      // if the entry is being updated by the end user or an administrator.
      final DN authzDN = getAuthorizationDN();
      selfChange = entryDN.equals(authzDN);

      // Check that the authorizing account isn't required to change its
      // password.
      if ((!isInternalOperation()) && !selfChange
          && getAuthorizationEntry() != null)
      {
        AuthenticationPolicy authzPolicy =
            AuthenticationPolicy.forUser(getAuthorizationEntry(), true);
        if (authzPolicy.isPasswordPolicy())
        {
          PasswordPolicyState authzState =
              (PasswordPolicyState) authzPolicy
                  .createAuthenticationPolicyState(getAuthorizationEntry());
          if (authzState.mustChangePassword())
          {
            pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
            setResultCode(ResultCode.CONSTRAINT_VIOLATION);
            appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD
                .get(authzDN != null ? authzDN : "anonymous"));
            return;
          }
        }
      }

      // FIXME -- Need a way to enable debug mode.
      AuthenticationPolicy policy =
          AuthenticationPolicy.forUser(currentEntry, true);
      if (policy.isPasswordPolicy())
      {
        pwPolicyState =
            (PasswordPolicyState) policy
                .createAuthenticationPolicyState(currentEntry);
      }

      // Create a duplicate of the entry and apply the changes to it.
      modifiedEntry = currentEntry.duplicate(false);

      if (!noOp && !handleConflictResolution())
      {
        return;
      }

      handleSchemaProcessing();

      // Check to see if the client has permission to perform the modify.
      // The access control check is not made any earlier because the handler
      // needs access to the modified entry.

      // FIXME: for now assume that this will check all permissions
      // pertinent to the operation. This includes proxy authorization
      // and any other controls specified.

      // FIXME: earlier checks to see if the entry already exists may have
      // already exposed sensitive information to the client.
      try
      {
        if (!AccessControlConfigManager.getInstance().getAccessControlHandler()
            .isAllowed(this))
        {
          setResultCodeAndMessageNoInfoDisclosure(modifiedEntry,
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_MODIFY_AUTHZ_INSUFFICIENT_ACCESS_RIGHTS.get(entryDN));
          return;
        }
      }
      catch (DirectoryException e)
      {
        setResultCode(e.getResultCode());
        appendErrorMessage(e.getMessageObject());
        return;
      }

      handleInitialPasswordPolicyProcessing();
      performAdditionalPasswordChangedProcessing();

      if ((!passwordChanged) && (!isInternalOperation()) && selfChange
          && pwPolicyState != null && pwPolicyState.mustChangePassword())
      {
        // The user did not attempt to change their password.
        pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
        setResultCode(ResultCode.CONSTRAINT_VIOLATION);
        appendErrorMessage(ERR_MODIFY_MUST_CHANGE_PASSWORD
            .get(authzDN != null ? authzDN : "anonymous"));
        return;
      }

      // If the server is configured to check the schema and the
      // operation is not a synchronization operation,
      // make sure that the new entry is valid per the server schema.
      if ((DirectoryServer.checkSchema()) && (!isSynchronizationOperation()))
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        if (!modifiedEntry.conformsToSchema(null, false, false, false,
            invalidReason))
        {
          setResultCode(ResultCode.OBJECTCLASS_VIOLATION);
          appendErrorMessage(ERR_MODIFY_VIOLATES_SCHEMA.get(entryDN, invalidReason));
          return;
        }
      }

      // Check for a request to cancel this operation.
      checkIfCanceled(false);

      // If the operation is not a synchronization operation,
      // Invoke the pre-operation modify plugins.
      if (!isSynchronizationOperation())
      {
        executePostOpPlugins.value = true;
        PluginResult.PreOperation preOpResult =
            DirectoryServer.getPluginConfigManager()
                .invokePreOperationModifyPlugins(this);
        if (!preOpResult.continueProcessing())
        {
          setResultCode(preOpResult.getResultCode());
          appendErrorMessage(preOpResult.getErrorMessage());
          setMatchedDN(preOpResult.getMatchedDN());
          setReferralURLs(preOpResult.getReferralURLs());
          return;
        }
      }

      // Actually perform the modify operation. This should also include
      // taking care of any synchronization that might be needed.
      if (backend == null)
      {
        setResultCode(ResultCode.NO_SUCH_OBJECT);
        appendErrorMessage(ERR_MODIFY_NO_BACKEND_FOR_ENTRY.get(entryDN));
        return;
      }

      LocalBackendWorkflowElement.checkIfBackendIsWritable(backend, this,
          entryDN, ERR_MODIFY_SERVER_READONLY, ERR_MODIFY_BACKEND_READONLY);

      if (noOp)
      {
        appendErrorMessage(INFO_MODIFY_NOOP.get());
        setResultCode(ResultCode.NO_OPERATION);
      }
      else
      {
        if (!processPreOperation())
        {
          return;
        }

        backend.replaceEntry(currentEntry, modifiedEntry, this);

        // See if we need to generate any account status notifications as a
        // result of the changes.
        handleAccountStatusNotifications();
      }

      // Handle any processing that may be needed for the pre-read and/or
      // post-read controls.
      LocalBackendWorkflowElement.addPreReadResponse(this, preReadRequest,
          currentEntry);
      LocalBackendWorkflowElement.addPostReadResponse(this, postReadRequest,
          modifiedEntry);

      if (!noOp)
      {
        setResultCode(ResultCode.SUCCESS);
      }
    }
    catch (DirectoryException de)
    {
      logger.traceException(de);

      setResponseData(de);
    }
    finally
    {
      if (entryLock != null)
      {
        LockManager.unlock(entryDN, entryLock);
      }
      processSynchPostOperationPlugins();
    }
  }

  private DirectoryException newDirectoryException(Entry entry,
      ResultCode resultCode, LocalizableMessage message) throws DirectoryException
  {
    return LocalBackendWorkflowElement.newDirectoryException(this, entry,
        entryDN, resultCode, message, ResultCode.NO_SUCH_OBJECT,
        ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));
  }

  private void setResultCodeAndMessageNoInfoDisclosure(Entry entry,
      ResultCode realResultCode, LocalizableMessage realMessage) throws DirectoryException
  {
    LocalBackendWorkflowElement.setResultCodeAndMessageNoInfoDisclosure(this,
        entry, entryDN, realResultCode, realMessage, ResultCode.NO_SUCH_OBJECT,
        ERR_MODIFY_NO_SUCH_ENTRY.get(entryDN));
  }

  private DN findMatchedDN(DN entryDN)
  {
    try
    {
      DN matchedDN = entryDN.getParentDNInSuffix();
      while (matchedDN != null)
      {
        if (DirectoryServer.entryExists(matchedDN))
        {
          return matchedDN;
        }

        matchedDN = matchedDN.getParentDNInSuffix();
      }
    }
    catch (Exception e)
    {
      logger.traceException(e);
    }
    return null;
  }

  /**
   * Processes any controls contained in the modify request.
   *
   * @throws  DirectoryException  If a problem is encountered with any of the
   *                              controls.
   */
  protected void processRequestControls() throws DirectoryException
  {
    LocalBackendWorkflowElement.removeAllDisallowedControls(entryDN, this);

    List<Control> requestControls = getRequestControls();
    if ((requestControls != null) && (! requestControls.isEmpty()))
    {
      for (int i=0; i < requestControls.size(); i++)
      {
        Control c   = requestControls.get(i);
        String  oid = c.getOID();

        if (oid.equals(OID_LDAP_ASSERTION))
        {
          LDAPAssertionRequestControl assertControl =
                getRequestControl(LDAPAssertionRequestControl.DECODER);

          SearchFilter filter;
          try
          {
            filter = assertControl.getSearchFilter();
          }
          catch (DirectoryException de)
          {
            logger.traceException(de);

            throw newDirectoryException(currentEntry, de.getResultCode(),
                ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(
                    entryDN, de.getMessageObject()));
          }

          // Check if the current user has permission to make
          // this determination.
          if (!AccessControlConfigManager.getInstance().
            getAccessControlHandler().isAllowed(this, currentEntry, filter))
          {
            throw new DirectoryException(
              ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
              ERR_CONTROL_INSUFFICIENT_ACCESS_RIGHTS.get(oid));
          }

          try
          {
            if (!filter.matchesEntry(currentEntry))
            {
              throw newDirectoryException(currentEntry,
                  ResultCode.ASSERTION_FAILED,
                  ERR_MODIFY_ASSERTION_FAILED.get(entryDN));
            }
          }
          catch (DirectoryException de)
          {
            if (de.getResultCode() == ResultCode.ASSERTION_FAILED)
            {
              throw de;
            }

            logger.traceException(de);

            throw newDirectoryException(currentEntry, de.getResultCode(),
                ERR_MODIFY_CANNOT_PROCESS_ASSERTION_FILTER.get(
                    entryDN, de.getMessageObject()));
          }
        }
        else if (oid.equals(OID_LDAP_NOOP_OPENLDAP_ASSIGNED))
        {
          noOp = true;
        }
        else if (oid.equals(OID_PERMISSIVE_MODIFY_CONTROL))
        {
          permissiveModify = true;
        }
        else if (oid.equals(OID_LDAP_READENTRY_PREREAD))
        {
          preReadRequest =
                getRequestControl(LDAPPreReadRequestControl.DECODER);
        }
        else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
        {
          if (c instanceof LDAPPostReadRequestControl)
          {
            postReadRequest = (LDAPPostReadRequestControl) c;
          }
          else
          {
            postReadRequest =
                getRequestControl(LDAPPostReadRequestControl.DECODER);
            requestControls.set(i, postReadRequest);
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V1))
        {
          // Log usage of legacy proxy authz V1 control.
          addAdditionalLogItem(AdditionalLogItem.keyOnly(getClass(),
              "obsoleteProxiedAuthzV1Control"));

          // The requester must have the PROXIED_AUTH privilege in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV1Control proxyControl =
                  getRequestControl(ProxiedAuthV1Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.rootDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getName());
          }
        }
        else if (oid.equals(OID_PROXIED_AUTH_V2))
        {
          // The requester must have the PROXIED_AUTH privilege in order to
          // be able to use this control.
          if (! clientConnection.hasPrivilege(Privilege.PROXIED_AUTH, this))
          {
            throw new DirectoryException(ResultCode.AUTHORIZATION_DENIED,
                           ERR_PROXYAUTH_INSUFFICIENT_PRIVILEGES.get());
          }

          ProxiedAuthV2Control proxyControl =
              getRequestControl(ProxiedAuthV2Control.DECODER);

          Entry authorizationEntry = proxyControl.getAuthorizationEntry();
          setAuthorizationEntry(authorizationEntry);
          if (authorizationEntry == null)
          {
            setProxiedAuthorizationDN(DN.rootDN());
          }
          else
          {
            setProxiedAuthorizationDN(authorizationEntry.getName());
          }
        }
        else if (oid.equals(OID_PASSWORD_POLICY_CONTROL))
        {
          pwPolicyControlRequested = true;
        }

        // NYI -- Add support for additional controls.
        else if (c.isCritical())
        {
          if ((backend == null) || (! backend.supportsControl(oid)))
          {
            throw newDirectoryException(currentEntry,
                ResultCode.UNAVAILABLE_CRITICAL_EXTENSION,
                ERR_MODIFY_UNSUPPORTED_CRITICAL_CONTROL.get(entryDN, oid));
          }
        }
      }
    }
  }

   /**
   * Handles schema processing for non-password modifications.
   *
   * @throws  DirectoryException  If a problem is encountered that should cause
   *                              the modify operation to fail.
   */
  protected void handleSchemaProcessing() throws DirectoryException
  {

    for (Modification m : modifications)
    {
      Attribute     a = m.getAttribute();
      AttributeType t = a.getAttributeType();


      // If the attribute type is marked "NO-USER-MODIFICATION" then fail unless
      // this is an internal operation or is related to synchronization in some
      // way.
      if (t.isNoUserModification())
      {
        if (! (isInternalOperation() || isSynchronizationOperation() ||
                m.isInternal()))
        {
          throw newDirectoryException(currentEntry,
              ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_ATTR_IS_NO_USER_MOD.get(entryDN, a.getName()));
        }
      }

      // If the attribute type is marked "OBSOLETE" and the modification is
      // setting new values, then fail unless this is an internal operation or
      // is related to synchronization in some way.
      if (t.isObsolete())
      {
        if (!a.isEmpty() &&
                (m.getModificationType() != ModificationType.DELETE))
        {
          if (! (isInternalOperation() || isSynchronizationOperation() ||
                  m.isInternal()))
          {
            throw newDirectoryException(currentEntry,
                ResultCode.CONSTRAINT_VIOLATION,
                ERR_MODIFY_ATTR_IS_OBSOLETE.get(entryDN, a.getName()));
          }
        }
      }


      // See if the attribute is one which controls the privileges available for
      // a user.  If it is, then the client must have the PRIVILEGE_CHANGE
      // privilege.
      if (t.hasName(OP_ATTR_PRIVILEGE_NAME))
      {
        if (! clientConnection.hasPrivilege(Privilege.PRIVILEGE_CHANGE, this))
        {
          throw new DirectoryException(ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                  ERR_MODIFY_CHANGE_PRIVILEGE_INSUFFICIENT_PRIVILEGES.get());
        }
      }

      // If the modification is not updating the password attribute,
      // then perform any schema processing.
      boolean isPassword = (pwPolicyState != null)
          && t.equals(pwPolicyState.getAuthenticationPolicy()
              .getPasswordAttribute());
      if (!isPassword )
      {
        switch (m.getModificationType().asEnum())
        {
          case ADD:
            processInitialAddSchema(a);
            break;

          case DELETE:
            processInitialDeleteSchema(a);
            break;

          case REPLACE:
            processInitialReplaceSchema(a);
            break;

          case INCREMENT:
            processInitialIncrementSchema(a);
            break;
        }
      }
    }
  }

  /**
   * Handles the initial set of password policy  for this modify operation.
   *
   * @throws  DirectoryException  If a problem is encountered that should cause
   *                              the modify operation to fail.
   */
  protected void handleInitialPasswordPolicyProcessing()
          throws DirectoryException
  {
    // Declare variables used for password policy state processing.
    currentPasswordProvided = false;
    isEnabled = true;
    enabledStateChanged = false;

    if (pwPolicyState == null)
    {
      // Account not managed locally so nothing to do.
      return;
    }

    if (currentEntry.hasAttribute(
            pwPolicyState.getAuthenticationPolicy().getPasswordAttribute()))
    {
      // It may actually have more than one, but we can't tell the difference if
      // the values are encoded, and its enough for our purposes just to know
      // that there is at least one.
      numPasswords = 1;
    }
    else
    {
      numPasswords = 0;
    }


    // If it's not an internal or synchronization operation, then iterate
    // through the set of modifications to see if a password is included in the
    // changes.  If so, then add the appropriate state changes to the set of
    // modifications.
    if (! (isInternalOperation() || isSynchronizationOperation()))
    {
      for (Modification m : modifications)
      {
        AttributeType t = m.getAttribute().getAttributeType();
        boolean isPassword = t.equals(pwPolicyState.getAuthenticationPolicy()
            .getPasswordAttribute());
        if (isPassword)
        {
          passwordChanged = true;
          if (! selfChange)
          {
            if (! clientConnection.hasPrivilege(Privilege.PASSWORD_RESET, this))
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
              throw new DirectoryException(
                      ResultCode.INSUFFICIENT_ACCESS_RIGHTS,
                      ERR_MODIFY_PWRESET_INSUFFICIENT_PRIVILEGES.get());
            }
          }

          break;
        }
      }
    }


    for (Modification m : modifications)
    {
      Attribute     a = m.getAttribute();
      AttributeType t = a.getAttributeType();


      // If the modification is updating the password attribute, then perform
      // any necessary password policy processing.  This processing should be
      // skipped for synchronization operations.
      boolean isPassword = t.equals(pwPolicyState.getAuthenticationPolicy()
          .getPasswordAttribute());
      if (isPassword)
      {
        if (!isSynchronizationOperation())
        {
          // If the attribute contains any options and new values are going to
          // be added, then reject it. Passwords will not be allowed to have
          // options. Skipped for internal operations.
          if (!isInternalOperation())
          {
            if (a.hasOptions())
            {
              switch (m.getModificationType().asEnum())
              {
              case REPLACE:
                if (!a.isEmpty())
                {
                  throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                      ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
                }
                // Allow delete operations to clean up after import.
                break;
              case ADD:
                throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                    ERR_MODIFY_PASSWORDS_CANNOT_HAVE_OPTIONS.get());
              default:
                // Allow delete operations to clean up after import.
                break;
              }
            }

            // If it's a self change, then see if that's allowed.
            if (selfChange
                && (!pwPolicyState.getAuthenticationPolicy()
                    .isAllowUserPasswordChanges()))
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  ERR_MODIFY_NO_USER_PW_CHANGES.get());
            }


            // If we require secure password changes, then makes sure it's a
            // secure communication channel.
            if (pwPolicyState.getAuthenticationPolicy()
                .isRequireSecurePasswordChanges()
                && (!clientConnection.isSecure()))
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
              throw new DirectoryException(ResultCode.CONFIDENTIALITY_REQUIRED,
                  ERR_MODIFY_REQUIRE_SECURE_CHANGES.get());
            }


            // If it's a self change and it's not been long enough since the
            // previous change, then reject it.
            if (selfChange && pwPolicyState.isWithinMinimumAge())
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_TOO_YOUNG;
              throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
                  ERR_MODIFY_WITHIN_MINIMUM_AGE.get());
            }
          }

          // Check to see whether this will adding, deleting, or replacing
          // password values (increment doesn't make any sense for passwords).
          // Then perform the appropriate type of processing for that kind of
          // modification.
          switch (m.getModificationType().asEnum())
          {
          case ADD:
          case REPLACE:
            processInitialAddOrReplacePW(m);
            break;

          case DELETE:
            processInitialDeletePW(m);
            break;

          default:
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                ERR_MODIFY_INVALID_MOD_TYPE_FOR_PASSWORD.get(
                    m.getModificationType(), a.getName()));
          }

          // Password processing may have changed the attribute in
          // this modification.
          a = m.getAttribute();
        }

        switch (m.getModificationType().asEnum())
        {
        case ADD:
          processInitialAddSchema(a);
          break;

        case DELETE:
          processInitialDeleteSchema(a);
          break;

        case REPLACE:
          processInitialReplaceSchema(a);
          break;

        case INCREMENT:
          processInitialIncrementSchema(a);
          break;
        }
      }
    }
  }



  /**
   * Performs the initial password policy add or replace processing.
   *
   * @param m
   *          The modification involved in the password change.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialAddOrReplacePW(Modification m)
      throws DirectoryException
  {
    Attribute pwAttr = m.getAttribute();
    int passwordsToAdd = pwAttr.size();

    if (m.getModificationType() == ModificationType.ADD)
    {
      numPasswords += passwordsToAdd;
    }
    else
    {
      numPasswords = passwordsToAdd;
    }

    // If there were multiple password values, then make sure that's
    // OK.
    if ((!isInternalOperation())
        && (!pwPolicyState.getAuthenticationPolicy()
            .isAllowMultiplePasswordValues()) && (passwordsToAdd > 1))
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_MULTIPLE_VALUES_NOT_ALLOWED.get());
    }

    // Iterate through the password values and see if any of them are
    // pre-encoded. If so, then check to see if we'll allow it.
    // Otherwise, store the clear-text values for later validation and
    // update the attribute with the encoded values.
    AttributeBuilder builder = new AttributeBuilder(pwAttr, true);
    for (AttributeValue v : pwAttr)
    {
      if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
      {
        if ((!isInternalOperation())
            && !pwPolicyState.getAuthenticationPolicy()
                .isAllowPreEncodedPasswords())
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
        }
        else
        {
          builder.add(v);
        }
      }
      else
      {
        if (m.getModificationType() == ModificationType.ADD)
        {
          // Make sure that the password value doesn't already exist.
          if (pwPolicyState.passwordMatches(v.getValue()))
          {
            pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
            throw new DirectoryException(ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
                ERR_MODIFY_PASSWORD_EXISTS.get());
          }
        }

        if (newPasswords == null)
        {
          newPasswords = new LinkedList<AttributeValue>();
        }

        newPasswords.add(v);

        for (ByteString s : pwPolicyState.encodePassword(v.getValue()))
        {
          builder.add(AttributeValues.create(
              pwAttr.getAttributeType(), s));
        }
      }
    }

    m.setAttribute(builder.toAttribute());
  }



  /**
   * Performs the initial password policy delete processing.
   *
   * @param m
   *          The modification involved in the password change.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialDeletePW(Modification m) throws DirectoryException
  {
    // Iterate through the password values and see if any of them are
    // pre-encoded. We will never allow pre-encoded passwords for user
    // password changes, but we will allow them for administrators.
    // For each clear-text value, verify that at least one value in the
    // entry matches and replace the clear-text value with the appropriate
    // encoded forms.
    Attribute pwAttr = m.getAttribute();
    AttributeBuilder builder = new AttributeBuilder(pwAttr, true);
    if (pwAttr.isEmpty())
    {
      // Removing all current password values.
      numPasswords = 0;
    }

    for (AttributeValue v : pwAttr)
    {
      if (pwPolicyState.passwordIsPreEncoded(v.getValue()))
      {
        if ((!isInternalOperation()) && selfChange)
        {
          pwpErrorType = PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
          throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
              ERR_MODIFY_NO_PREENCODED_PASSWORDS.get());
        }
        else
        {
          // We still need to check if the pre-encoded password matches
          // an existing value, to decrease the number of passwords.
          List<Attribute> attrList = currentEntry.getAttribute(pwAttr
              .getAttributeType());
          if ((attrList == null) || (attrList.isEmpty()))
          {
            throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
                ERR_MODIFY_NO_EXISTING_VALUES.get());
          }
          boolean found = false;
          for (Attribute attr : attrList)
          {
            for (AttributeValue av : attr)
            {
              if (av.equals(v))
              {
                builder.add(v);
                found = true;
              }
            }
          }
          if (found)
          {
            numPasswords--;
          }
        }
      }
      else
      {
        List<Attribute> attrList = currentEntry.getAttribute(pwAttr
            .getAttributeType());
        if ((attrList == null) || (attrList.isEmpty()))
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
              ERR_MODIFY_NO_EXISTING_VALUES.get());
        }
        boolean found = false;
        for (Attribute attr : attrList)
        {
          for (AttributeValue av : attr)
          {
            if (pwPolicyState.getAuthenticationPolicy().isAuthPasswordSyntax())
            {
              if (AuthPasswordSyntax.isEncoded(av.getValue()))
              {
                StringBuilder[] components = AuthPasswordSyntax
                    .decodeAuthPassword(av.getValue().toString());
                PasswordStorageScheme<?> scheme = DirectoryServer
                    .getAuthPasswordStorageScheme(components[0].toString());
                if (scheme != null)
                {
                  if (scheme.authPasswordMatches(v.getValue(), components[1]
                      .toString(), components[2].toString()))
                  {
                    builder.add(av);
                    found = true;
                  }
                }
              }
              else
              {
                if (av.equals(v))
                {
                  builder.add(v);
                  found = true;
                }
              }
            }
            else
            {
              if (UserPasswordSyntax.isEncoded(av.getValue()))
              {
                String[] components = UserPasswordSyntax.decodeUserPassword(av
                    .getValue().toString());
                PasswordStorageScheme<?> scheme = DirectoryServer
                    .getPasswordStorageScheme(toLowerCase(components[0]));
                if (scheme != null)
                {
                  if (scheme.passwordMatches(v.getValue(), ByteString.valueOf(
                      components[1])))
                  {
                    builder.add(av);
                    found = true;
                  }
                }
              }
              else
              {
                if (av.equals(v))
                {
                  builder.add(v);
                  found = true;
                }
              }
            }
          }
        }

        if (found)
        {
          if (currentPasswords == null)
          {
            currentPasswords = new LinkedList<AttributeValue>();
          }
          currentPasswords.add(v);
          numPasswords--;
        }
        else
        {
          throw new DirectoryException(ResultCode.NO_SUCH_ATTRIBUTE,
              ERR_MODIFY_INVALID_PASSWORD.get());
        }

        currentPasswordProvided = true;
      }
    }

    m.setAttribute(builder.toAttribute());
  }



  /**
   * Performs the initial schema processing for an add modification
   * and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being added.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialAddSchema(Attribute attr)
      throws DirectoryException
  {
    // Make sure that one or more values have been provided for the
    // attribute.
    if (attr.isEmpty())
    {
      throw newDirectoryException(currentEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_ADD_NO_VALUES.get(entryDN, attr.getName()));
    }

    // If the server is configured to check schema and the operation
    // is not a synchronization operation, make sure that all the new
    // values are valid according to the associated syntax.
    if ((DirectoryServer.checkSchema()) && (!isSynchronizationOperation()))
    {
      AcceptRejectWarn syntaxPolicy = DirectoryServer
          .getSyntaxEnforcementPolicy();
      AttributeSyntax<?> syntax = attr.getAttributeType().getSyntax();

      if (syntaxPolicy == AcceptRejectWarn.REJECT)
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        for (AttributeValue v : attr)
        {
          if (!syntax.valueIsAcceptable(v.getValue(), invalidReason))
          {
            if (!syntax.isHumanReadable() || syntax.isBEREncodingRequired())
            {
              // Value is not human-readable
              throw newDirectoryException(currentEntry,
                  ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  ERR_MODIFY_ADD_INVALID_SYNTAX_NO_VALUE.get(entryDN, attr.getName(), invalidReason));
            }
            else
            {
              throw newDirectoryException(currentEntry,
                  ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  ERR_MODIFY_ADD_INVALID_SYNTAX.get(
                      entryDN, attr.getName(), v.getValue(), invalidReason));
            }
          }
        }
      }
      else if (syntaxPolicy == AcceptRejectWarn.WARN)
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        for (AttributeValue v : attr)
        {
          if (!syntax.valueIsAcceptable(v.getValue(), invalidReason))
          {
            // FIXME remove next line of code. According to Matt, since this is
            // just a warning, the code should not set the resultCode
            setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
            if (!syntax.isHumanReadable() || syntax.isBEREncodingRequired())
            {
              // Value is not human-readable
              logger.error(ERR_MODIFY_ADD_INVALID_SYNTAX_NO_VALUE, entryDN, attr.getName(), invalidReason);
            }
            else
            {
              logger.error(ERR_MODIFY_ADD_INVALID_SYNTAX,
                  entryDN, attr.getName(), v.getValue(), invalidReason);
            }
            invalidReason = new LocalizableMessageBuilder();
          }
        }
      }
    }

    // If the attribute to be added is the object class attribute then
    // make sure that all the object classes are known and not
    // obsoleted.
    if (attr.getAttributeType().isObjectClassType())
    {
      validateObjectClasses(attr);
    }

    // Add the provided attribute or merge an existing attribute with
    // the values of the new attribute. If there are any duplicates,
    // then fail.
    List<AttributeValue> duplicateValues = new LinkedList<AttributeValue>();
    modifiedEntry.addAttribute(attr, duplicateValues);
    if (!duplicateValues.isEmpty() && !permissiveModify)
    {
      String duplicateValuesStr = Utils.joinAsString(", ", duplicateValues);

      throw newDirectoryException(currentEntry,
          ResultCode.ATTRIBUTE_OR_VALUE_EXISTS,
          ERR_MODIFY_ADD_DUPLICATE_VALUE.get(entryDN, attr.getName(), duplicateValuesStr));
    }
  }



  /**
   * Ensures that the provided object class attribute contains known
   * non-obsolete object classes.
   *
   * @param attr
   *          The object class attribute to validate.
   * @throws DirectoryException
   *           If the attribute contained unknown or obsolete object
   *           classes.
   */
  private void validateObjectClasses(Attribute attr) throws DirectoryException
  {
    Reject.ifFalse(attr.getAttributeType().isObjectClassType());
    for (AttributeValue v : attr)
    {
      String name = v.getValue().toString();

      String lowerName;
      try
      {
        lowerName = v.getNormalizedValue().toString();
      }
      catch (Exception e)
      {
        logger.traceException(e);

        lowerName = toLowerCase(v.getValue().toString());
      }

      ObjectClass oc = DirectoryServer.getObjectClass(lowerName);
      if (oc == null)
      {
        throw newDirectoryException(currentEntry,
            ResultCode.OBJECTCLASS_VIOLATION,
            ERR_ENTRY_ADD_UNKNOWN_OC.get(name, entryDN));
      }

      if (oc.isObsolete())
      {
        throw newDirectoryException(currentEntry,
            ResultCode.CONSTRAINT_VIOLATION,
            ERR_ENTRY_ADD_OBSOLETE_OC.get(name, entryDN));
      }
    }
  }



  /**
   * Performs the initial schema processing for a delete modification
   * and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being deleted.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialDeleteSchema(Attribute attr)
          throws DirectoryException
  {
    // Remove the specified attribute values or the entire attribute from the
    // value.  If there are any specified values that were not present, then
    // fail.  If the RDN attribute value would be removed, then fail.
    List<AttributeValue> missingValues = new LinkedList<AttributeValue>();
    boolean attrExists = modifiedEntry.removeAttribute(attr, missingValues);

    if (attrExists)
    {
      if (missingValues.isEmpty())
      {
        AttributeType t = attr.getAttributeType();

        RDN rdn = modifiedEntry.getName().rdn();
        if ((rdn !=  null) && rdn.hasAttributeType(t) &&
            (! modifiedEntry.hasValue(t, attr.getOptions(),
                                      rdn.getAttributeValue(t))))
        {
          throw newDirectoryException(currentEntry,
              ResultCode.NOT_ALLOWED_ON_RDN,
              ERR_MODIFY_DELETE_RDN_ATTR.get(entryDN, attr.getName()));
        }
      }
      else
      {
        if (! permissiveModify)
        {
          String missingValuesStr = Utils.joinAsString(", ", missingValues);

          throw newDirectoryException(currentEntry,
              ResultCode.NO_SUCH_ATTRIBUTE,
              ERR_MODIFY_DELETE_MISSING_VALUES.get(entryDN, attr.getName(), missingValuesStr));
        }
      }
    }
    else
    {
      if (! permissiveModify)
      {
        throw newDirectoryException(currentEntry, ResultCode.NO_SUCH_ATTRIBUTE,
            ERR_MODIFY_DELETE_NO_SUCH_ATTR.get(entryDN, attr.getName()));
      }
    }
  }



  /**
   * Performs the initial schema processing for a replace modification
   * and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being replaced.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialReplaceSchema(Attribute attr)
      throws DirectoryException
  {
    // If the server is configured to check schema and the operation
    // is not a synchronization operation, make sure that all the
    // new values are valid according to the associated syntax.
    if ((DirectoryServer.checkSchema()) && (!isSynchronizationOperation()))
    {
      AcceptRejectWarn syntaxPolicy = DirectoryServer
          .getSyntaxEnforcementPolicy();
      AttributeSyntax<?> syntax = attr.getAttributeType().getSyntax();

      if (syntaxPolicy == AcceptRejectWarn.REJECT)
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        for (AttributeValue v : attr)
        {
          if (!syntax.valueIsAcceptable(v.getValue(), invalidReason))
          {
            if (!syntax.isHumanReadable() || syntax.isBEREncodingRequired())
            {
              // Value is not human-readable
              throw newDirectoryException(currentEntry,
                  ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  ERR_MODIFY_REPLACE_INVALID_SYNTAX_NO_VALUE.get(entryDN, attr.getName(), invalidReason));
            }
            else
            {
              throw newDirectoryException(currentEntry,
                  ResultCode.INVALID_ATTRIBUTE_SYNTAX,
                  ERR_MODIFY_REPLACE_INVALID_SYNTAX.get(
                      entryDN, attr.getName(), v.getValue(), invalidReason));
            }
          }
        }
      }
      else if (syntaxPolicy == AcceptRejectWarn.WARN)
      {
        LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
        for (AttributeValue v : attr)
        {
          if (!syntax.valueIsAcceptable(v.getValue(), invalidReason))
          {
            setResultCode(ResultCode.INVALID_ATTRIBUTE_SYNTAX);
            if (!syntax.isHumanReadable() || syntax.isBEREncodingRequired())
            {
              // Value is not human-readable
              logger.error(ERR_MODIFY_REPLACE_INVALID_SYNTAX_NO_VALUE,
                  entryDN, attr.getName(), invalidReason);
            }
            else
            {
              logger.error(ERR_MODIFY_REPLACE_INVALID_SYNTAX,
                  entryDN, attr.getName(), v.getValue(), invalidReason);
            }
            invalidReason = new LocalizableMessageBuilder();
          }
        }
      }
    }

    // If the attribute to be replaced is the object class attribute
    // then make sure that all the object classes are known and not
    // obsoleted.
    if (attr.getAttributeType().isObjectClassType())
    {
      validateObjectClasses(attr);
    }

    // Replace the provided attribute.
    modifiedEntry.replaceAttribute(attr);

    // Make sure that the RDN attribute value(s) has not been removed.
    AttributeType t = attr.getAttributeType();
    RDN rdn = modifiedEntry.getName().rdn();
    if ((rdn != null)
        && rdn.hasAttributeType(t)
        && (!modifiedEntry.hasValue(t, attr.getOptions(), rdn
            .getAttributeValue(t))))
    {
      throw newDirectoryException(modifiedEntry, ResultCode.NOT_ALLOWED_ON_RDN,
          ERR_MODIFY_DELETE_RDN_ATTR.get(entryDN, attr.getName()));
    }
  }



  /**
   * Performs the initial schema processing for an increment
   * modification and updates the entry appropriately.
   *
   * @param attr
   *          The attribute being incremented.
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  private void processInitialIncrementSchema(Attribute attr)
      throws DirectoryException
  {
    // The specified attribute type must not be an RDN attribute.
    AttributeType t = attr.getAttributeType();
    RDN rdn = modifiedEntry.getName().rdn();
    if ((rdn != null) && rdn.hasAttributeType(t))
    {
      throw newDirectoryException(modifiedEntry, ResultCode.NOT_ALLOWED_ON_RDN,
          ERR_MODIFY_INCREMENT_RDN.get(entryDN, attr.getName()));
    }

    // The provided attribute must have a single value, and it must be
    // an integer.
    if (attr.isEmpty())
    {
      throw newDirectoryException(modifiedEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_INCREMENT_REQUIRES_VALUE.get(entryDN, attr.getName()));
    }

    if (attr.size() > 1)
    {
      throw newDirectoryException(modifiedEntry, ResultCode.PROTOCOL_ERROR,
          ERR_MODIFY_INCREMENT_REQUIRES_SINGLE_VALUE.get(entryDN, attr.getName()));
    }

    AttributeValue v = attr.iterator().next();

    long incrementValue;
    try
    {
      incrementValue = Long.parseLong(v.getNormalizedValue().toString());
    }
    catch (Exception e)
    {
      logger.traceException(e);

      throw new DirectoryException(ResultCode.INVALID_ATTRIBUTE_SYNTAX,
          ERR_MODIFY_INCREMENT_PROVIDED_VALUE_NOT_INTEGER.get(entryDN, attr.getName(), v.getValue()), e);
    }

    // Get the attribute that is to be incremented.
    Attribute a = modifiedEntry.getExactAttribute(t, attr.getOptions());
    if (a == null)
    {
      throw newDirectoryException(modifiedEntry,
          ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_INCREMENT_REQUIRES_EXISTING_VALUE.get(entryDN, attr.getName()));
    }

    // Increment each attribute value by the specified amount.
    AttributeBuilder builder = new AttributeBuilder(a, true);
    for (AttributeValue existingValue : a)
    {
      String s = existingValue.getValue().toString();
      long currentValue;
      try
      {
        currentValue = Long.parseLong(s);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        throw new DirectoryException(
            ResultCode.INVALID_ATTRIBUTE_SYNTAX,
            ERR_MODIFY_INCREMENT_REQUIRES_INTEGER_VALUE.get(
                entryDN, a.getName(), existingValue.getValue()),
            e);
      }

      long newValue = currentValue + incrementValue;
      builder.add(AttributeValues.create(t, String.valueOf(newValue)));
    }

    // Replace the existing attribute with the incremented version.
    modifiedEntry.replaceAttribute(builder.toAttribute());
  }



  /**
   * Performs additional preliminary processing that is required for a
   * password change.
   *
   * @throws DirectoryException
   *           If a problem occurs that should cause the modify
   *           operation to fail.
   */
  public void performAdditionalPasswordChangedProcessing()
         throws DirectoryException
  {
    if (pwPolicyState == null)
    {
      // Account not managed locally so nothing to do.
      return;
    }

    if (!passwordChanged)
    {
      // Nothing to do.
      return;
    }

    // If it was a self change, then see if the current password was provided
    // and handle accordingly.
    if (selfChange
        && pwPolicyState.getAuthenticationPolicy()
            .isPasswordChangeRequiresCurrentPassword()
        && (!currentPasswordProvided))
    {
      pwpErrorType = PasswordPolicyErrorType.MUST_SUPPLY_OLD_PASSWORD;

      throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM,
          ERR_MODIFY_PW_CHANGE_REQUIRES_CURRENT_PW.get());
    }


    // If this change would result in multiple password values, then see if
    // that's OK.
    if ((numPasswords > 1)
        && (!pwPolicyState.getAuthenticationPolicy()
            .isAllowMultiplePasswordValues()))
    {
      pwpErrorType = PasswordPolicyErrorType.PASSWORD_MOD_NOT_ALLOWED;
      throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
          ERR_MODIFY_MULTIPLE_PASSWORDS_NOT_ALLOWED.get());
    }


    // If any of the password values should be validated, then do so now.
    if (selfChange
        || (!pwPolicyState.getAuthenticationPolicy()
            .isSkipValidationForAdministrators()))
    {
      if (newPasswords != null)
      {
        HashSet<ByteString> clearPasswords = new HashSet<ByteString>();
        clearPasswords.addAll(pwPolicyState.getClearPasswords());

        if (currentPasswords != null)
        {
          if (clearPasswords.isEmpty())
          {
            for (AttributeValue v : currentPasswords)
            {
              clearPasswords.add(v.getValue());
            }
          }
          else
          {
            // NOTE:  We can't rely on the fact that Set doesn't allow
            // duplicates because technically it's possible that the values
            // aren't duplicates if they are ASN.1 elements with different types
            // (like 0x04 for a standard universal octet string type versus 0x80
            // for a simple password in a bind operation).  So we have to
            // manually check for duplicates.
            for (AttributeValue v : currentPasswords)
            {
              ByteString pw = v.getValue();

              boolean found = false;
              for (ByteString s : clearPasswords)
              {
                if (s.equals(pw))
                {
                  found = true;
                  break;
                }
              }

              if (! found)
              {
                clearPasswords.add(pw);
              }
            }
          }
        }

        for (AttributeValue v : newPasswords)
        {
          LocalizableMessageBuilder invalidReason = new LocalizableMessageBuilder();
          if (! pwPolicyState.passwordIsAcceptable(this, modifiedEntry,
                                   v.getValue(), clearPasswords, invalidReason))
          {
            pwpErrorType =
                 PasswordPolicyErrorType.INSUFFICIENT_PASSWORD_QUALITY;
            throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                         ERR_MODIFY_PW_VALIDATION_FAILED.get(
                                              invalidReason));
          }
        }
      }
    }


    // If we should check the password history, then do so now.
    if (pwPolicyState.maintainHistory())
    {
      if (newPasswords != null)
      {
        for (AttributeValue v : newPasswords)
        {
          if (pwPolicyState.isPasswordInHistory(v.getValue()))
          {
            if (selfChange || (! pwPolicyState.getAuthenticationPolicy().
                                      isSkipValidationForAdministrators()))
            {
              pwpErrorType = PasswordPolicyErrorType.PASSWORD_IN_HISTORY;
              throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION,
                                           ERR_MODIFY_PW_IN_HISTORY.get());
            }
          }
        }

        pwPolicyState.updatePasswordHistory();
      }
    }


    // See if the account was locked for any reason.
    wasLocked = pwPolicyState.lockedDueToIdleInterval() ||
                pwPolicyState.lockedDueToMaximumResetAge() ||
                pwPolicyState.lockedDueToFailures();

    // Update the password policy state attributes in the user's entry.  If the
    // modification fails, then these changes won't be applied.
    pwPolicyState.setPasswordChangedTime();
    pwPolicyState.clearFailureLockout();
    pwPolicyState.clearGraceLoginTimes();
    pwPolicyState.clearWarnedTime();

    if (pwPolicyState.getAuthenticationPolicy().isForceChangeOnAdd() ||
        pwPolicyState.getAuthenticationPolicy().isForceChangeOnReset())
    {
      if (selfChange)
      {
        pwPolicyState.setMustChangePassword(false);
      }
      else
      {
        if ((pwpErrorType == null) &&
            pwPolicyState.getAuthenticationPolicy().isForceChangeOnReset())
        {
          pwpErrorType = PasswordPolicyErrorType.CHANGE_AFTER_RESET;
        }

        pwPolicyState.setMustChangePassword(
             pwPolicyState.getAuthenticationPolicy().isForceChangeOnReset());
      }
    }

    if (pwPolicyState.getAuthenticationPolicy().getRequireChangeByTime() > 0)
    {
      pwPolicyState.setRequiredChangeTime();
    }

    modifications.addAll(pwPolicyState.getModifications());
    modifiedEntry.applyModifications(pwPolicyState.getModifications());
  }



  /**
   * Handles any account status notifications that may be needed as a result of
   * modify processing.
   */
  protected void handleAccountStatusNotifications()
  {
    if (pwPolicyState == null)
    {
      // Account not managed locally, so nothing to do.
      return;
    }

    if (!(passwordChanged || enabledStateChanged || wasLocked))
    {
      // Account managed locally, but unchanged, so nothing to do.
      return;
    }

    if (passwordChanged)
    {
      if (selfChange)
      {
        AuthenticationInfo authInfo = clientConnection.getAuthenticationInfo();
        if (authInfo.getAuthenticationDN().equals(modifiedEntry.getName()))
        {
          clientConnection.setMustChangePassword(false);
        }

        LocalizableMessage message = INFO_MODIFY_PASSWORD_CHANGED.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_CHANGED,
            modifiedEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 currentPasswords, newPasswords));
      }
      else
      {
        LocalizableMessage message = INFO_MODIFY_PASSWORD_RESET.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.PASSWORD_RESET, modifiedEntry,
            message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 currentPasswords, newPasswords));
      }
    }

    if (enabledStateChanged)
    {
      if (isEnabled)
      {
        LocalizableMessage message = INFO_MODIFY_ACCOUNT_ENABLED.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.ACCOUNT_ENABLED,
            modifiedEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 null, null));
      }
      else
      {
        LocalizableMessage message = INFO_MODIFY_ACCOUNT_DISABLED.get();
        pwPolicyState.generateAccountStatusNotification(
            AccountStatusNotificationType.ACCOUNT_DISABLED,
            modifiedEntry, message,
            AccountStatusNotification.createProperties(pwPolicyState, false, -1,
                 null, null));
      }
    }

    if (wasLocked)
    {
      LocalizableMessage message = INFO_MODIFY_ACCOUNT_UNLOCKED.get();
      pwPolicyState.generateAccountStatusNotification(
          AccountStatusNotificationType.ACCOUNT_UNLOCKED, modifiedEntry,
          message,
          AccountStatusNotification.createProperties(pwPolicyState, false, -1,
               null, null));
    }
  }



  /**
   * Handle conflict resolution.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  protected boolean handleConflictResolution() {
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
          try {
              SynchronizationProviderResult result =
                  provider.handleConflictResolution(this);
              if (! result.continueProcessing()) {
                  setResultCodeAndMessageNoInfoDisclosure(modifiedEntry,
                      result.getResultCode(), result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_CONFLICT_RESOLUTION_FAILED,
                  getConnectionID(), getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /**
   * Process pre operation.
   * @return  {@code true} if processing should continue for the operation, or
   *          {@code false} if not.
   */
  protected boolean processPreOperation() {
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
          try {
              SynchronizationProviderResult result =
                  provider.doPreOperation(this);
              if (! result.continueProcessing()) {
                  setResultCode(result.getResultCode());
                  appendErrorMessage(result.getErrorMessage());
                  setMatchedDN(result.getMatchedDN());
                  setReferralURLs(result.getReferralURLs());
                  return false;
              }
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_PREOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return false;
          }
      }
      return true;
  }

  /**
   * Invoke post operation synchronization providers.
   */
  protected void processSynchPostOperationPlugins() {
      for (SynchronizationProvider<?> provider :
          DirectoryServer.getSynchronizationProviders()) {
          try {
              provider.doPostOperation(this);
          } catch (DirectoryException de) {
              logger.traceException(de);
              logger.error(ERR_MODIFY_SYNCH_POSTOP_FAILED, getConnectionID(),
                      getOperationID(), getExceptionMessage(de));
              setResponseData(de);
              return;
          }
      }
  }
}
