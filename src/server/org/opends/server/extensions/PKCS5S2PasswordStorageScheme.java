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
 *      Portions Copyright Emidio Stani & Andrea Stani
 */
package org.opends.server.extensions;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteSequence;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.ResultCode;
import org.opends.server.admin.std.server.PKCS5S2PasswordStorageSchemeCfg;
import org.opends.server.api.PasswordStorageScheme;
import org.opends.server.core.DirectoryServer;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.InitializationException;
import org.opends.server.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import static org.opends.messages.ExtensionMessages.*;
import static org.opends.server.extensions.ExtensionsConstants.*;
import static org.opends.server.util.StaticUtils.getExceptionMessage;

/**
 * This class defines a Directory Server password storage scheme based on the
 * Atlassian PBKF2-base hash algorithm.  This is a one-way digest algorithm
 * so there is no way to retrieve the original clear-text version of the
 * password from the hashed value (although this means that it is not suitable
 * for things that need the clear-text password like DIGEST-MD5).  Unlike
 * the other PBKF2-base scheme, this implementation uses a fixed number of
 * iterations.
 */
public class PKCS5S2PasswordStorageScheme
    extends PasswordStorageScheme<PKCS5S2PasswordStorageSchemeCfg>
{
    private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME =
      "org.opends.server.extensions.PKCS5S2PasswordStorageScheme";


  /**
   * The number of bytes of random data to use as the salt when generating the
   * hashes.
   */
  private static final int NUM_SALT_BYTES = 16;

  /**
   * The number of bytes the SHA-1 algorithm produces.
   */
  private static final int SHA1_LENGTH = 32;

  /**
   *  Atlassian hardcoded the number of iterations to 10000.
   */
  private static final int iterations = 10000;

  /**
   * The factory used to generate the PKCS5S2 hashes.
   */
  private SecretKeyFactory factory;

  /**
   * The lock used to provide thread-safe access to the message digest.
   */
  private final Object factoryLock = new Object();

  /**
   *  The secure random number generator to use to generate the salt values.
   */
  private SecureRandom random;


  /**
   * Creates a new instance of this password storage scheme.  Note that no
   * initialization should be performed here, as all initialization should be
   * done in the <CODE>initializePasswordStorageScheme</CODE> method.
   */
  public PKCS5S2PasswordStorageScheme()
  {
    super();
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordStorageScheme(
      PKCS5S2PasswordStorageSchemeCfg configuration)
      throws InitializationException
  {
    try
    {
      random = SecureRandom.getInstance(SECURE_PRNG_SHA1);
      factory = SecretKeyFactory.getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2);
    }
    catch (NoSuchAlgorithmException e)
    {
      throw new InitializationException(null);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override()
  public String getStorageSchemeName()
  {
    return STORAGE_SCHEME_NAME_PKCS5S2;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePassword(ByteSequence plaintext)
      throws DirectoryException
  {
    byte[] saltBytes      = new byte[NUM_SALT_BYTES];
    byte[] digestBytes = createRandomSaltAndEncode(plaintext, saltBytes);
    // Append the hashed value to the salt and base64-the whole thing.
    byte[] hashPlusSalt = concatenateSaltPlusHash(saltBytes, digestBytes);

    return ByteString.valueOf(Base64.encode(hashPlusSalt));
  }


  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodePasswordWithScheme(ByteSequence plaintext)
      throws DirectoryException
  {
    return ByteString.valueOf("{" + STORAGE_SCHEME_NAME_PKCS5S2 + '}'
        + encodePassword(plaintext));
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordMatches(ByteSequence plaintextPassword,
                                 ByteSequence storedPassword)
  {

    // Base64-decode the value and take the first 16 bytes as the salt.
    byte[] saltBytes = new byte[NUM_SALT_BYTES];
    final int saltLength = NUM_SALT_BYTES;
    byte[] digestBytes = new byte[SHA1_LENGTH];
    try
    {
      String stored = storedPassword.toString();

      byte[] decodedBytes = Base64.decode(stored);

      if (decodedBytes.length != NUM_SALT_BYTES + SHA1_LENGTH)
      {
        logger.error(ERR_PWSCHEME_INVALID_BASE64_DECODED_STORED_PASSWORD.get(
            storedPassword.toString()));
        return false;
      }
      System.arraycopy(decodedBytes, 0, saltBytes, 0, saltLength);
      System.arraycopy(decodedBytes, saltLength, digestBytes, 0,
          SHA1_LENGTH);
    }
    catch (Exception e)
    {
      logger.traceException(e);
      logger.error(ERR_PWSCHEME_CANNOT_BASE64_DECODE_STORED_PASSWORD.get(
          storedPassword.toString(), String.valueOf(e)));
      return false;
    }

    return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean supportsAuthPasswordSyntax()
  {
    return true;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public String getAuthPasswordSchemeName()
  {
    return AUTH_PASSWORD_SCHEME_NAME_PKCS5S2;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString encodeAuthPassword(ByteSequence plaintext)
      throws DirectoryException
  {
    byte[] saltBytes      = new byte[NUM_SALT_BYTES];
    byte[] digestBytes = createRandomSaltAndEncode(plaintext, saltBytes);
    // Encode and return the value.
    return ByteString.valueOf(AUTH_PASSWORD_SCHEME_NAME_PKCS5S2 + '$' +
        iterations + ':' + Base64.encode(saltBytes) +
        '$' + Base64.encode(digestBytes));
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean authPasswordMatches(ByteSequence plaintextPassword,
                                     String authInfo, String authValue)
  {
    byte[] saltBytes;
    byte[] digestBytes;
    int    iterations;

    try
    {
      int pos = 0;
      int length = authInfo.length();
      while (pos < length && authInfo.charAt(pos) != ':')
      {
        pos++;
      }
      if (pos >= (length - 1) || pos == 0)
        throw new Exception();
      iterations = Integer.parseInt(authInfo.substring(0, pos));
      saltBytes   = Base64.decode(authInfo.substring(pos + 1));
      digestBytes = Base64.decode(authValue);
    }
    catch (Exception e)
    {
        logger.traceException(e);
        return false;
    }

    return encodeAndMatch(plaintextPassword, saltBytes, digestBytes, iterations);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isReversible()
  {
    return false;
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public ByteString getPlaintextValue(ByteSequence storedPassword)
      throws DirectoryException
  {
    LocalizableMessage message =
        ERR_PWSCHEME_NOT_REVERSIBLE.get(STORAGE_SCHEME_NAME_PKCS5S2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
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
        ERR_PWSCHEME_NOT_REVERSIBLE.get(AUTH_PASSWORD_SCHEME_NAME_PKCS5S2);
    throw new DirectoryException(ResultCode.CONSTRAINT_VIOLATION, message);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean isStorageSchemeSecure()
  {
    return true;
  }



  /**
   * Generates an encoded password string from the given clear-text password.
   * This method is primarily intended for use when it is necessary to generate
   * a password with the server offline (e.g., when setting the initial root
   * user password).
   *
   * @param  passwordBytes  The bytes that make up the clear-text password.
   *
   * @return  The encoded password string, including the scheme name in curly
   *          braces.
   *
   * @throws  org.opends.server.types.DirectoryException  If a problem occurs during processing.
   */
  public static String encodeOffline(byte[] passwordBytes)
      throws DirectoryException
  {
    byte[] saltBytes = new byte[NUM_SALT_BYTES];
    byte[] digestBytes;

    try
    {
      SecureRandom.getInstance(SECURE_PRNG_SHA1).nextBytes(saltBytes);

      char[] plaintextChars = Arrays.toString(passwordBytes).toCharArray();
      KeySpec spec = new PBEKeySpec(plaintextChars, saltBytes,iterations,
          SHA1_LENGTH * 8);
      digestBytes = SecretKeyFactory
          .getInstance(MESSAGE_DIGEST_ALGORITHM_PBKDF2)
          .generateSecret(spec).getEncoded();
    }
    catch (Exception e)
    {
      logger.traceException(e);
      LocalizableMessage message = ERR_PWSCHEME_CANNOT_ENCODE_PASSWORD.get(
          CLASS_NAME, getExceptionMessage(e));
      throw new DirectoryException(DirectoryServer.getServerErrorResultCode(),
          message, e);
    }

    // Append the hashed value to the salt and base64-the whole thing.
    byte[] hashPlusSalt = concatenateSaltPlusHash(saltBytes, digestBytes);

    return "{" + STORAGE_SCHEME_NAME_PKCS5S2 + "}"  +
        Base64.encode(hashPlusSalt);
  }


  private boolean encodeAndMatch(ByteSequence plaintext,
                                 byte[] saltBytes, byte[] digestBytes, int iterations)
  {
    byte[] userDigestBytes;

    try
    {
      userDigestBytes = encodeWithSalt(plaintext, saltBytes, iterations);
    }
    catch (Exception e)
    {
        return false;
    }
    return Arrays.equals(digestBytes, userDigestBytes);
  }


  private byte[] createRandomSaltAndEncode(ByteSequence plaintext, byte[] saltBytes) throws DirectoryException {
    byte[] digestBytes;

    synchronized(factoryLock)
    {
      random.nextBytes(saltBytes);
      digestBytes = encodeWithSalt(plaintext, saltBytes, iterations);
    }
    return digestBytes;
  }

  private byte[] encodeWithSalt(ByteSequence plaintext, byte[] saltBytes, int iterations) throws DirectoryException {
    byte[] digestBytes;
    char[] plaintextChars = null;
    try
    {
      plaintextChars = plaintext.toString().toCharArray();
      KeySpec spec = new PBEKeySpec(
          plaintextChars, saltBytes,
          iterations, SHA1_LENGTH * 8);
      digestBytes = factory.generateSecret(spec).getEncoded();
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
      if (plaintextChars != null)
      {
        Arrays.fill(plaintextChars, '0');
      }
    }
    return digestBytes;
  }

  private static byte[] concatenateSaltPlusHash(byte[] saltBytes, byte[] digestBytes) {
    byte[] hashPlusSalt = new byte[digestBytes.length + NUM_SALT_BYTES];

    System.arraycopy(saltBytes, 0, hashPlusSalt, 0, NUM_SALT_BYTES);
    System.arraycopy(digestBytes, 0, hashPlusSalt, NUM_SALT_BYTES,
        digestBytes.length);
    return hashPlusSalt;
  }

}
