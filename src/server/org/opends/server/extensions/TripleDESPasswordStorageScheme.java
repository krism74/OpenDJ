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
 *      Portions Copyright 2013-2014 ForgeRock AS.
 */
package org.opends.server.extensions;



import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.TripleDESPasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.config.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.util.Base64;

import java.util.Arrays;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server password storage scheme that will
 * encode values using the triple-DES (DES/EDE) reversible encryption algorithm.
 * This implementation supports only the user password syntax and not the auth
 * password syntax.
 */
public class TripleDESPasswordStorageScheme
       extends PasswordStorageScheme<TripleDESPasswordStorageSchemeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();



  // The reference to the Directory Server crypto manager that we will use to
  // handle the encryption/decryption.
  private CryptoManager cryptoManager;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the {@code initializePasswordStorageScheme} method.
   */
  public TripleDESPasswordStorageScheme()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
                   TripleDESPasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    cryptoManager = DirectoryServer.getCryptoManager();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_3DES;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    byte[] plaintextBytes = null;
    try
    {
      // TODO: Can we avoid this copy?
      plaintextBytes = plaintext.toByteArray();
      byte[] encodedBytes = cryptoManager.encrypt(CIPHER_TRANSFORMATION_3DES,
                                                  KEY_SIZE_3DES,
                                                  plaintextBytes);
      return ByteString.valueOf(Base64.encode(encodedBytes));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage m = ERR_PWSCHEME_CANNOT_ENCRYPT.get(STORAGE_SCHEME_NAME_3DES,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
    finally
    {
      if (plaintextBytes != null)
        Arrays.fill(plaintextBytes, (byte) 0);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_3DES);
    buffer.append('}');
    byte[] plaintextBytes = null;

    try
    {
      // TODO: Can we avoid this copy?
      plaintextBytes = plaintext.toByteArray();
      byte[] encodedBytes = cryptoManager.encrypt(CIPHER_TRANSFORMATION_3DES,
                                                  KEY_SIZE_3DES,
                                                  plaintextBytes);
      buffer.append(Base64.encode(encodedBytes));
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage m = ERR_PWSCHEME_CANNOT_ENCRYPT.get(STORAGE_SCHEME_NAME_3DES,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
    finally
    {
      if (plaintextBytes != null)
        Arrays.fill(plaintextBytes, (byte) 0);
    }

    return ByteString.valueOf(buffer.toString());
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    try
    {
      ByteString decryptedPassword =
          ByteString.wrap(cryptoManager.decrypt(
               Base64.decode(storedPassword.toString())));
      return plaintextPassword.equals(decryptedPassword);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return false;
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isReversible()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    try
    {
      byte[] decryptedPassword =
           cryptoManager.decrypt(Base64.decode(storedPassword.toString()));
      return ByteString.wrap(decryptedPassword);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage m = ERR_PWSCHEME_CANNOT_DECRYPT.get(STORAGE_SCHEME_NAME_3DES,
                                                  getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                   m, e);
    }
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsAuthPasswordSyntax()
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    // This storage scheme does not support the authentication password syntax.
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_DOES_NOT_SUPPORT_AUTH_PASSWORD.get(getStorageSchemeName());
    throw new DirectoryException(ResultCode.UNWILLING_TO_PERFORM, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {
    // This password storage scheme should be considered secure.
    return true;
  }
}

