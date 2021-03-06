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
 *      Portions Copyright 2013-2015 ForgeRock AS
 */
package org.opends.server.extensions;



import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Random;

import org.forgerock.i18n.LocalizableMessage;
import org.opends.server.admin.std.server.SaltedMD5PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.forgerock.opendj.config.server.ConfigException;
import org.opends.server.core.DirectoryServer;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.opends.server.types.*;
import org.forgerock.opendj.ldap.ResultCode;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ByteSequence;
import org.opends.server.util.Base64;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class defines a Directory Server password storage scheme based on the
 * MD5 algorithm defined in RFC 1321.  This is a one-way digest algorithm so
 * there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  The values
 * that it generates are also salted, which protects against dictionary attacks.
 * It does this by generating a 64-bit random salt which is appended to the
 * clear-text value.  A MD5 hash is then generated based on this, the salt is
 * appended to the hash, and then the entire value is base64-encoded.
 */
public class SaltedMD5PasswordStorageScheme
       extends PasswordStorageScheme<SaltedMD5PasswordStorageSchemeCfg>
{
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
       "org.opends.server.extensions.SaltedMD5PasswordStorageScheme";



  /**
   * The number of bytes of random data to use as the salt when generating the
   * hashes.
   */
  private static final int NUM_SALT_BYTES = 8;

  /** The number of bytes MD5 algorithm produces. */
  private static final int MD5_LENGTH = 16;


  /** The message digest that will actually be used to generate the MD5 hashes. */
  private MessageDigest messageDigest;

  /** The lock used to provide threadsafe access to the message digest. */
  private Object digestLock;

  /** The secure random number generator to use to generate the salt values. */
  private Random random;



  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public SaltedMD5PasswordStorageScheme()
  {
    super();
  }



  /** {@inheritDoc} */
  @Override
  public void initializePasswordStorageScheme(
                   SaltedMD5PasswordStorageSchemeCfg configuration)
         throws ConfigException, InitializationException
  {
    try
    {
      messageDigest = MessageDigest.getInstance(MESSAGE_DIGEST_ALGORITHM_MD5);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      LocalizableMessage message = ERR_PWSCHEME_CANNOT_INITIALIZE_MESSAGE_DIGEST.get(MESSAGE_DIGEST_ALGORITHM_MD5, e);
      throw new InitializationException(message, e);
    }


    digestLock = new Object();
    random     = new Random();
  }



  /** {@inheritDoc} */
  @Override
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_SALTED_MD5;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodePassword(ByteSequence plaintext)
         throws DirectoryException
  {
    int plainBytesLength = plaintext.length();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plainBytesLength + NUM_SALT_BYTES];

    plaintext.copyTo(plainPlusSalt);

    byte[] digestBytes;

    synchronized (digestLock)
    {
      try
      {
        // Generate the salt and put in the plain+salt array.
        random.nextBytes(saltBytes);
        System.arraycopy(saltBytes,0, plainPlusSalt, plainBytesLength,
                         NUM_SALT_BYTES);

        // Create the hash from the concatenated value.
        digestBytes = messageDigest.digest(plainPlusSalt);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
            CLASS_NAME, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
      finally
      {
        Arrays.fill(plainPlusSalt, (byte) 0);
      }
    }

    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);

    return ByteString.valueOf(Base64.encode(hashPlusSalt));
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
         throws DirectoryException
  {
    StringBuilder buffer = new StringBuilder();
    buffer.append('{');
    buffer.append(STORAGE_SCHEME_NAME_SALTED_MD5);
    buffer.append('}');

    int plainBytesLength = plaintext.length();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plainBytesLength + NUM_SALT_BYTES];

    plaintext.copyTo(plainPlusSalt);

    byte[] digestBytes;

    synchronized (digestLock)
    {
      try
      {
        // Generate the salt and put in the plain+salt array.
        random.nextBytes(saltBytes);
        System.arraycopy(saltBytes,0, plainPlusSalt, plainBytesLength,
                         NUM_SALT_BYTES);

        // Create the hash from the concatenated value.
        digestBytes = messageDigest.digest(plainPlusSalt);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
            CLASS_NAME, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
      finally
      {
        Arrays.fill(plainPlusSalt, (byte) 0);
      }
    }

    // Append the salt to the hashed value and base64-the whole thing.
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(digestBytes, 0, hashPlusSalt, 0, digestBytes.length);
    System.arraycopy(saltBytes, 0, hashPlusSalt, digestBytes.length,
                     NUM_SALT_BYTES);
    buffer.append(Base64.encode(hashPlusSalt));

    return ByteString.valueOf(buffer.toString());
  }



  /** {@inheritDoc} */
  @Override
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {
    // Base64-decode the stored value and take the last 8 bytes as the salt.
    byte[] saltBytes = new byte[NUM_SALT_BYTES];
    byte[] digestBytes = new byte[MD5_LENGTH];
    int saltLength = 0;
    try
    {
      byte[] decodedBytes = Base64.decode(storedPassword.toString());

      saltLength = decodedBytes.length - MD5_LENGTH;
      if (saltLength <= 0)
      {
        logger.error(ERR_PWSCHEME_INVALID_BASE64_DECODED_STORED_PASSWORD, storedPassword);
        return false;
      }
      saltBytes = new byte[saltLength];
      System.arraycopy(decodedBytes, 0, digestBytes, 0, MD5_LENGTH);
      System.arraycopy(decodedBytes, MD5_LENGTH, saltBytes, 0,
                       saltLength);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD, storedPassword, e);
      return false;
    }


    // Use the salt to generate a digest based on the provided plain-text value.
    int plainBytesLength = plaintextPassword.length();
    byte[] plainPlusSalt = new byte[plainBytesLength + saltLength];
    plaintextPassword.copyTo(plainPlusSalt);
    System.arraycopy(saltBytes, 0, plainPlusSalt, plainBytesLength,
                     saltLength);

    byte[] userDigestBytes;

    synchronized (digestLock)
    {
      try
      {
        userDigestBytes = messageDigest.digest(plainPlusSalt);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        return false;
      }
      finally
      {
        Arrays.fill(plainPlusSalt, (byte) 0);
      }
    }

    return Arrays.equals(digestBytes, userDigestBytes);
  }



  /** {@inheritDoc} */
  @Override
  public boolean supportsAuthPasswordSyntax()
  {
    // This storage scheme does support the authentication password syntax.
    return true;
  }



  /** {@inheritDoc} */
  @Override
  public String getAuthPasswordSchemeName()
  {
    return AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString encodeAuthPassword(ByteSequence plaintext)
         throws DirectoryException
  {
    int plaintextLength = plaintext.length();
    byte[] saltBytes     = new byte[NUM_SALT_BYTES];
    byte[] plainPlusSalt = new byte[plaintextLength + NUM_SALT_BYTES];

    plaintext.copyTo(plainPlusSalt);

    byte[] digestBytes;

    synchronized (digestLock)
    {
      try
      {
        // Generate the salt and put in the plain+salt array.
        random.nextBytes(saltBytes);
        System.arraycopy(saltBytes,0, plainPlusSalt, plaintextLength,
                         NUM_SALT_BYTES);

        // Create the hash from the concatenated value.
        digestBytes = messageDigest.digest(plainPlusSalt);
      }
      catch (Exception e)
      {
        logger.traceException(e);

        LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
            CLASS_NAME, getExceptionMessage(e));
        throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
                                     message, e);
      }
      finally
      {
        Arrays.fill(plainPlusSalt, (byte) 0);
      }
    }


    // Encode and return the value.
    StringBuilder authPWValue = new StringBuilder();
    authPWValue.append(AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5);
    authPWValue.append('$');
    authPWValue.append(Base64.encode(saltBytes));
    authPWValue.append('$');
    authPWValue.append(Base64.encode(digestBytes));

    return ByteString.valueOf(authPWValue.toString());
  }



  /** {@inheritDoc} */
  @Override
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    byte[] saltBytes;
    byte[] digestBytes;
    try
    {
      saltBytes   = Base64.decode(authInfo);
      digestBytes = Base64.decode(authValue);
    }
    catch (Exception e)
    {
      logger.traceException(e);

      return false;
    }


    int plainBytesLength = plaintextPassword.length();
    byte[] plainPlusSaltBytes = new byte[plainBytesLength + saltBytes.length];
    plaintextPassword.copyTo(plainPlusSaltBytes);
    System.arraycopy(saltBytes, 0, plainPlusSaltBytes, plainBytesLength,
                     saltBytes.length);

    synchronized (digestLock)
    {
      try
      {
        return Arrays.equals(digestBytes,
                                messageDigest.digest(plainPlusSaltBytes));
      }
      finally
      {
        Arrays.fill(plainPlusSaltBytes, (byte) 0);
      }
    }
  }



  /** {@inheritDoc} */
  @Override
  public boolean isReversible()
  {
    return false;
  }



  /** {@inheritDoc} */
  @Override
  public ByteString getPlaintextValue(ByteSequence storedPassword)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_SALTED_MD5);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }



  /** {@inheritDoc} */
  @Override
  public ByteString getAuthPasswordPlaintextValue(String authInfo,
                                                  String authValue)
         throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(AUTH_PASSWORD_SCHEME_NAME_SALTED_MD5);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }



  /** {@inheritDoc} */
  @Override
  public boolean isStorageSchemeSecure()
  {
    // MD5 may be considered reasonably secure for this purpose.
    return true;
  }
}

