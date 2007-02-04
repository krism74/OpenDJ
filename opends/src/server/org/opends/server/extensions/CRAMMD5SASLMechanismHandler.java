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
 * by brackets "[]" replaced with your own identifying * information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.opends.server.api.ClientConnection;
import org.opends.server.api.ConfigurableComponent;
import org.opends.server.api.IdentityMapper;
import org.opends.server.api.SASLMechanismHandler;
import org.opends.server.config.ConfigAttribute;
import org.opends.server.config.ConfigEntry;
import org.opends.server.config.ConfigException;
import org.opends.server.config.DNConfigAttribute;
import org.opends.server.core.BindOperation;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.PasswordPolicyState;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.types.AuthenticationInfo;
import org.opends.server.types.ByteString;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.DN;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.LockManager;
import org.opends.server.types.ResultCode;

import static org.opends.server.config.ConfigConstants.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.loggers.Debug.*;
import static org.opends.server.loggers.Error.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an implementation of a SASL mechanism that uses digest
 * authentication via CRAM-MD5.  This is a password-based mechanism that does
 * not expose the password itself over the wire but rather uses an MD5 hash that
 * proves the client knows the password.  This is similar to the DIGEST-MD5
 * mechanism, and the primary differences are that CRAM-MD5 only obtains random
 * data from the server (whereas DIGEST-MD5 uses random data from both the
 * server and the client), CRAM-MD5 does not allow for an authorization ID in
 * addition to the authentication ID where DIGEST-MD5 does, and CRAM-MD5 does
 * not define any integrity and confidentiality mechanisms where DIGEST-MD5
 * does.  This implementation is  based on the proposal defined in
 * draft-ietf-sasl-crammd5-05.
 */
public class CRAMMD5SASLMechanismHandler
       extends SASLMechanismHandler
       implements ConfigurableComponent
{
  /**
   * The fully-qualified name of this class for debugging purposes.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.CRAMMD5SASLMechanismHandler";



  // An array filled with the inner pad byte.
  private byte[] iPad;

  // An array filled with the outer pad byte.
  private byte[] oPad;

  // The DN of the configuration entry for this SASL mechanism handler.
  private DN configEntryDN;

  // The DN of the identity mapper configuration entry.
  private DN identityMapperDN;

  // The identity mapper that will be used to map ID strings to user entries.
  private IdentityMapper identityMapper;

  // The message digest engine that will be used to create the MD5 digests.
  private MessageDigest md5Digest;

  // The lock that will be used to provide threadsafe access to the message
  // digest.
  private ReentrantLock digestLock;

  // The random number generator that we will use to create the server
  // challenge.
  private SecureRandom randomGenerator;



  /**
   * Creates a new instance of this SASL mechanism handler.  No initialization
   * should be done in this method, as it should all be performed in the
   * <CODE>initializeSASLMechanismHandler</CODE> method.
   */
  public CRAMMD5SASLMechanismHandler()
  {
    super();

    assert debugConstructor(CLASS_NAME);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializeSASLMechanismHandler(ConfigEntry configEntry)
         throws ConfigException, InitializationException
  {
    assert debugEnter(CLASS_NAME, "initializeSASLMechanismHandler",
                      String.valueOf(configEntry));


    this.configEntryDN = configEntry.getDN();


    // Initialize the variables needed for the MD5 digest creation.
    digestLock      = new ReentrantLock();
    randomGenerator = new SecureRandom();

    try
    {
      md5Digest = MessageDigest.getInstance("MD5");
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSASLMechanismHandler", e);

      int    msgID   = MSGID_SASLCRAMMD5_CANNOT_GET_MESSAGE_DIGEST;
      String message = getMessage(msgID, stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    // Create and fill the iPad and oPad arrays.
    iPad = new byte[HMAC_MD5_BLOCK_LENGTH];
    oPad = new byte[HMAC_MD5_BLOCK_LENGTH];
    Arrays.fill(iPad, CRAMMD5_IPAD_BYTE);
    Arrays.fill(oPad, CRAMMD5_OPAD_BYTE);


    // Get the identity mapper that should be used to find users.
    int msgID = MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLCRAMMD5_NO_IDENTITY_MAPPER_ATTR;
        String message = getMessage(msgID, String.valueOf(configEntryDN));
        throw new ConfigException(msgID, message);
      }
      else
      {
        identityMapperDN = mapperAttr.activeValue();
        identityMapper = DirectoryServer.getIdentityMapper(identityMapperDN);
        if (identityMapper == null)
        {
          msgID = MSGID_SASLCRAMMD5_NO_SUCH_IDENTITY_MAPPER;
          String message = getMessage(msgID, String.valueOf(identityMapperDN),
                                      String.valueOf(configEntryDN));
          throw new ConfigException(msgID, message);
        }
      }
    }
    catch (ConfigException ce)
    {
      throw ce;
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "initializeSASLMechanismHandler", e);

      msgID = MSGID_SASLCRAMMD5_CANNOT_GET_IDENTITY_MAPPER;
      String message = getMessage(msgID, String.valueOf(configEntryDN),
                                  stackTraceToSingleLineString(e));
      throw new InitializationException(msgID, message, e);
    }


    DirectoryServer.registerSASLMechanismHandler(SASL_MECHANISM_CRAM_MD5, this);
    DirectoryServer.registerConfigurableComponent(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizeSASLMechanismHandler()
  {
    assert debugEnter(CLASS_NAME, "finalizeSASLMechanismHandler");

    DirectoryServer.deregisterConfigurableComponent(this);
    DirectoryServer.deregisterSASLMechanismHandler(SASL_MECHANISM_CRAM_MD5);
  }




  /**
   * {@inheritDoc}
   */
  @Override()
  public void processSASLBind(BindOperation bindOperation)
  {
    assert debugEnter(CLASS_NAME, "processSASLBind",
                      String.valueOf(bindOperation));


    // The CRAM-MD5 bind process uses two stages.  See if the client provided
    // any credentials.  If not, then we're in the first stage so we'll send the
    // challenge to the client.
    ByteString       clientCredentials = bindOperation.getSASLCredentials();
    ClientConnection clientConnection  = bindOperation.getClientConnection();
    if (clientCredentials == null)
    {
      // The client didn't provide any credentials, so this is the initial
      // request.  Generate some random data to send to the client as the
      // challenge and store it in the client connection so we can verify the
      // credentials provided by the client later.
      byte[] challengeBytes = new byte[16];
      randomGenerator.nextBytes(challengeBytes);
      StringBuilder challengeString = new StringBuilder(18);
      challengeString.append('<');
      for (byte b : challengeBytes)
      {
        challengeString.append(byteToLowerHex(b));
      }
      challengeString.append('>');

      ASN1OctetString challenge =
           new ASN1OctetString(challengeString.toString());
      clientConnection.setSASLAuthStateInfo(challenge);
      bindOperation.setServerSASLCredentials(challenge);
      bindOperation.setResultCode(ResultCode.SASL_BIND_IN_PROGRESS);
      return;
    }


    // If we've gotten here, then the client did provide credentials.  First,
    // make sure that we have a stored version of the credentials associated
    // with the client connection.  If not, then it likely means that the client
    // is trying to pull a fast one on us.
    Object saslStateInfo = clientConnection.getSASLAuthStateInfo();
    if (saslStateInfo == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_NO_STORED_CHALLENGE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    if (! (saslStateInfo instanceof ASN1OctetString))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_INVALID_STORED_CHALLENGE;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    ASN1OctetString  challenge = (ASN1OctetString) saslStateInfo;

    // Wipe out the stored challenge so it can't be used again.
    clientConnection.setSASLAuthStateInfo(null);


    // Now look at the client credentials and make sure that we can decode them.
    // It should be a username followed by a space and a digest string.  Since
    // the username itself may contain spaces but the digest string may not,
    // look for the last space and use it as the delimiter.
    String credString = clientCredentials.stringValue();
    int spacePos = credString.lastIndexOf(' ');
    if (spacePos < 0)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_NO_SPACE_IN_CREDENTIALS;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    String userName = credString.substring(0, spacePos);
    String digest   = credString.substring(spacePos+1);


    // Look at the digest portion of the provided credentials.  It must have a
    // length of exactly 32 bytes and be comprised only of hex characters.
    if (digest.length() != (2*MD5_DIGEST_LENGTH))
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_INVALID_DIGEST_LENGTH;
      String message = getMessage(msgID, digest.length(),
                                  (2*MD5_DIGEST_LENGTH));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }

    byte[] digestBytes;
    try
    {
      digestBytes = hexStringToByteArray(digest);
    }
    catch (ParseException pe)
    {
      assert debugException(CLASS_NAME, "processSASLBind", pe);

      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_INVALID_DIGEST_CONTENT;
      String message = getMessage(msgID, pe.getMessage());
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Get the user entry for the authentication ID.  Allow for an
    // authentication ID that is just a username (as per the CRAM-MD5 spec), but
    // also allow a value in the authzid form specified in RFC 2829.
    Entry  userEntry    = null;
    String lowerUserName = toLowerCase(userName);
    if (lowerUserName.startsWith("dn:"))
    {
      // Try to decode the user DN and retrieve the corresponding entry.
      DN userDN;
      try
      {
        userDN = DN.decode(userName.substring(3));
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "processSASLBind", de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLCRAMMD5_CANNOT_DECODE_USERNAME_AS_DN;
        String message = getMessage(msgID, userName, de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      if (userDN.isNullDN())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLCRAMMD5_USERNAME_IS_NULL_DN;
        String message = getMessage(msgID);
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      DN rootDN = DirectoryServer.getActualRootBindDN(userDN);
      if (rootDN != null)
      {
        userDN = rootDN;
      }

      // Acquire a read lock on the user entry.  If this fails, then so will the
      // authentication.
      Lock readLock = null;
      for (int i=0; i < 3; i++)
      {
        readLock = LockManager.lockRead(userDN);
        if (readLock != null)
        {
          break;
        }
      }

      if (readLock == null)
      {
        bindOperation.setResultCode(DirectoryServer.getServerErrorResultCode());

        int    msgID   = MSGID_SASLCRAMMD5_CANNOT_LOCK_ENTRY;
        String message = getMessage(msgID, String.valueOf(userDN));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }

      try
      {
        userEntry = DirectoryServer.getEntry(userDN);
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "processSASLBind", de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLCRAMMD5_CANNOT_GET_ENTRY_BY_DN;
        String message = getMessage(msgID, String.valueOf(userDN),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
      finally
      {
        LockManager.unlock(userDN, readLock);
      }
    }
    else
    {
      // Use the identity mapper to resolve the username to an entry.
      if (lowerUserName.startsWith("u:"))
      {
        userName = userName.substring(2);
      }

      try
      {
        userEntry = identityMapper.getEntryForID(userName);
      }
      catch (DirectoryException de)
      {
        assert debugException(CLASS_NAME, "processSASLBind", de);

        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int    msgID   = MSGID_SASLCRAMMD5_CANNOT_MAP_USERNAME;
        String message = getMessage(msgID, String.valueOf(userName),
                                    de.getErrorMessage());
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }


    // At this point, we should have a user entry.  If we don't then fail.
    if (userEntry == null)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_NO_MATCHING_ENTRIES;
      String message = getMessage(msgID, userName);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }
    else
    {
      bindOperation.setSASLAuthUserEntry(userEntry);
    }


    // Get the clear-text passwords from the user entry, if there are any.
    List<ByteString> clearPasswords;
    try
    {
      PasswordPolicyState pwPolicyState =
           new PasswordPolicyState(userEntry, false, false);
      clearPasswords = pwPolicyState.getClearPasswords();
      if ((clearPasswords == null) || clearPasswords.isEmpty())
      {
        bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

        int msgID = MSGID_SASLCRAMMD5_NO_REVERSIBLE_PASSWORDS;
        String message = getMessage(msgID, String.valueOf(userEntry.getDN()));
        bindOperation.setAuthFailureReason(msgID, message);
        return;
      }
    }
    catch (Exception e)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_CANNOT_GET_REVERSIBLE_PASSWORDS;
      String message = getMessage(msgID, String.valueOf(userEntry.getDN()),
                                  String.valueOf(e));
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // Iterate through the clear-text values and see if any of them can be used
    // in conjunction with the challenge to construct the provided digest.
    boolean matchFound = false;
    for (ByteString clearPassword : clearPasswords)
    {
      byte[] generatedDigest = generateDigest(clearPassword, challenge);
      if (Arrays.equals(digestBytes, generatedDigest))
      {
        matchFound = true;
        break;
      }
    }

    if (! matchFound)
    {
      bindOperation.setResultCode(ResultCode.INVALID_CREDENTIALS);

      int    msgID   = MSGID_SASLCRAMMD5_INVALID_PASSWORD;
      String message = getMessage(msgID);
      bindOperation.setAuthFailureReason(msgID, message);
      return;
    }


    // If we've gotten here, then the authentication was successful.
    bindOperation.setResultCode(ResultCode.SUCCESS);

    AuthenticationInfo authInfo =
         new AuthenticationInfo(userEntry.getDN(), SASL_MECHANISM_CRAM_MD5,
                                DirectoryServer.isRootDN(userEntry.getDN()));
    bindOperation.setAuthenticationInfo(authInfo);
    return;
  }



  /**
   * Generates the appropriate HMAC-MD5 digest for a CRAM-MD5 authentication
   * with the given information.
   *
   * @param  password   The clear-text password to use when generating the
   *                    digest.
   * @param  challenge  The server-supplied challenge to use when generating the
   *                    digest.
   *
   * @return  The generated HMAC-MD5 digest for CRAM-MD5 authentication.
   */
  private byte[] generateDigest(ByteString password, ByteString challenge)
  {
    assert debugEnter(CLASS_NAME, "generateDigest",
                      String.valueOf(password), String.valueOf(challenge));


    // Get the byte arrays backing the password and challenge.
    byte[] p = password.value();
    byte[] c = challenge.value();


    // Grab a lock to protect the MD5 digest generation.
    digestLock.lock();

    try
    {
      // If the password is longer than the HMAC-MD5 block length, then use an
      // MD5 digest of the password rather than the password itself.
      if (p.length > HMAC_MD5_BLOCK_LENGTH)
      {
        p = md5Digest.digest(p);
      }


      // Create byte arrays with data needed for the hash generation.
      byte[] iPadAndData = new byte[HMAC_MD5_BLOCK_LENGTH + c.length];
      System.arraycopy(iPad, 0, iPadAndData, 0, HMAC_MD5_BLOCK_LENGTH);
      System.arraycopy(c, 0, iPadAndData, HMAC_MD5_BLOCK_LENGTH, c.length);

      byte[] oPadAndHash = new byte[HMAC_MD5_BLOCK_LENGTH + MD5_DIGEST_LENGTH];
      System.arraycopy(oPad, 0, oPadAndHash, 0, HMAC_MD5_BLOCK_LENGTH);


      // Iterate through the bytes in the key and XOR them with the iPad and
      // oPad as appropriate.
      for (int i=0; i < p.length; i++)
      {
        iPadAndData[i] ^= p[i];
        oPadAndHash[i] ^= p[i];
      }


      // Copy an MD5 digest of the iPad-XORed key and the data into the array to
      // be hashed.
      System.arraycopy(md5Digest.digest(iPadAndData), 0, oPadAndHash,
                       HMAC_MD5_BLOCK_LENGTH, MD5_DIGEST_LENGTH);


      // Return an MD5 digest of the resulting array.
      return md5Digest.digest(oPadAndHash);
    }
    finally
    {
      digestLock.unlock();
    }
  }



  /**
   * Retrieves the DN of the configuration entry with which this component is
   * associated.
   *
   * @return  The DN of the configuration entry with which this component is
   *          associated.
   */
  public DN getConfigurableComponentEntryDN()
  {
    assert debugEnter(CLASS_NAME, "getConfigurableComponentEntryDN");

    return configEntryDN;
  }




  /**
   * Retrieves the set of configuration attributes that are associated with this
   * configurable component.
   *
   * @return  The set of configuration attributes that are associated with this
   *          configurable component.
   */
  public List<ConfigAttribute> getConfigurationAttributes()
  {
    assert debugEnter(CLASS_NAME, "getConfigurationAttributes");


    LinkedList<ConfigAttribute> attrList = new LinkedList<ConfigAttribute>();

    int msgID = MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN;
    attrList.add(new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID),
                                       true, false, false, identityMapperDN));

    return attrList;
  }



  /**
   * Indicates whether the provided configuration entry has an acceptable
   * configuration for this component.  If it does not, then detailed
   * information about the problem(s) should be added to the provided list.
   *
   * @param  configEntry          The configuration entry for which to make the
   *                              determination.
   * @param  unacceptableReasons  A list that can be used to hold messages about
   *                              why the provided entry does not have an
   *                              acceptable configuration.
   *
   * @return  <CODE>true</CODE> if the provided entry has an acceptable
   *          configuration for this component, or <CODE>false</CODE> if not.
   */
  public boolean hasAcceptableConfiguration(ConfigEntry configEntry,
                                            List<String> unacceptableReasons)
  {
    assert debugEnter(CLASS_NAME, "hasAcceptableConfiguration",
                      String.valueOf(configEntry), "java.util.List<String>");


    // Look at the identity mapper configuration.
    int msgID = MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLCRAMMD5_NO_IDENTITY_MAPPER_ATTR;
        unacceptableReasons.add(getMessage(msgID,
                                           String.valueOf(configEntryDN)));
        return false;
      }

      DN mapperDN = mapperAttr.pendingValue();
      if (! mapperDN.equals(identityMapperDN))
      {
        IdentityMapper mapper = DirectoryServer.getIdentityMapper(mapperDN);
        if (mapper == null)
        {
          msgID = MSGID_SASLCRAMMD5_NO_SUCH_IDENTITY_MAPPER;
          unacceptableReasons.add(getMessage(msgID, String.valueOf(mapperDN),
                                             String.valueOf(configEntryDN)));
          return false;
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "hasAcceptableConfiguration", e);

      msgID = MSGID_SASLCRAMMD5_CANNOT_GET_IDENTITY_MAPPER;
      unacceptableReasons.add(getMessage(msgID, String.valueOf(configEntryDN),
                                         stackTraceToSingleLineString(e)));
      return false;
    }


    // If we've gotten to this point, then everything must be OK.
    return true;
  }



  /**
   * Makes a best-effort attempt to apply the configuration contained in the
   * provided entry.  Information about the result of this processing should be
   * added to the provided message list.  Information should always be added to
   * this list if a configuration change could not be applied.  If detailed
   * results are requested, then information about the changes applied
   * successfully (and optionally about parameters that were not changed) should
   * also be included.
   *
   * @param  configEntry      The entry containing the new configuration to
   *                          apply for this component.
   * @param  detailedResults  Indicates whether detailed information about the
   *                          processing should be added to the list.
   *
   * @return  Information about the result of the configuration update.
   */
  public ConfigChangeResult applyNewConfiguration(ConfigEntry configEntry,
                                                  boolean detailedResults)
  {
    assert debugEnter(CLASS_NAME, "applyNewConfiguration",
                      String.valueOf(configEntry),
                      String.valueOf(detailedResults));


    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Look at the identity mapper configuration.
    DN newIdentityMapperDN = null;
    IdentityMapper newIdentityMapper = null;
    int msgID = MSGID_SASLCRAMMD5_DESCRIPTION_IDENTITY_MAPPER_DN;
    DNConfigAttribute mapperStub =
         new DNConfigAttribute(ATTR_IDMAPPER_DN, getMessage(msgID), true, false,
                               false);
    try
    {
      DNConfigAttribute mapperAttr =
           (DNConfigAttribute) configEntry.getConfigAttribute(mapperStub);
      if (mapperAttr == null)
      {
        msgID = MSGID_SASLCRAMMD5_NO_IDENTITY_MAPPER_ATTR;
        messages.add(getMessage(msgID, String.valueOf(configEntryDN)));

        resultCode = ResultCode.CONSTRAINT_VIOLATION;
      }
      else
      {
        newIdentityMapperDN = mapperAttr.pendingValue();
        if (! newIdentityMapperDN.equals(identityMapperDN))
        {
          newIdentityMapper =
               DirectoryServer.getIdentityMapper(newIdentityMapperDN);
          if (newIdentityMapper == null)
          {
            msgID = MSGID_SASLCRAMMD5_NO_SUCH_IDENTITY_MAPPER;
            messages.add(getMessage(msgID, String.valueOf(newIdentityMapperDN),
                                    String.valueOf(configEntryDN)));

            resultCode = ResultCode.CONSTRAINT_VIOLATION;
          }
        }
      }
    }
    catch (Exception e)
    {
      assert debugException(CLASS_NAME, "applyNewConfiguration", e);

      msgID = MSGID_SASLCRAMMD5_CANNOT_GET_IDENTITY_MAPPER;
      messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                              stackTraceToSingleLineString(e)));
      resultCode = DirectoryServer.getServerErrorResultCode();
    }


    // If everything has been successful, then apply any changes that were made.
    if (resultCode == ResultCode.SUCCESS)
    {
      if ((newIdentityMapperDN != null) && (identityMapper != null))
      {
        identityMapperDN = newIdentityMapperDN;
        identityMapper   = newIdentityMapper;

        if (detailedResults)
        {
          msgID = MSGID_SASLCRAMMD5_UPDATED_IDENTITY_MAPPER;
          messages.add(getMessage(msgID, String.valueOf(configEntryDN),
                                  String.valueOf(identityMapperDN)));
        }
      }
    }


    // Return the result to the caller.
    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isPasswordBased(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isPasswordBased", String.valueOf(mechanism));

    // This is a password-based mechanism.
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isSecure(String mechanism)
  {
    assert debugEnter(CLASS_NAME, "isSecure", String.valueOf(mechanism));

    // This may be considered a secure mechanism.
    return true;
  }
}

