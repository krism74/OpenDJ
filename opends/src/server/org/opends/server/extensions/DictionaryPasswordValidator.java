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
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Portions Copyright 2007 Sun Microsystems, Inc.
 */
package org.opends.server.extensions;



import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opends.server.admin.server.ConfigurationChangeListener;
import org.opends.server.admin.std.server.DictionaryPasswordValidatorCfg;
import org.opends.server.api.PasswordValidator;
import org.opends.server.config.ConfigException;
import org.opends.server.types.ConfigChangeResult;
import org.opends.server.types.ByteString;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DirectoryConfig;
import org.opends.server.types.Entry;
import org.opends.server.types.InitializationException;
import org.opends.server.types.Operation;
import org.opends.server.types.ResultCode;

import static org.opends.server.loggers.debug.DebugLogger.*;
import static org.opends.server.messages.ExtensionsMessages.*;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.util.StaticUtils.*;



/**
 * This class provides an OpenDS password validator that may be used to ensure
 * that proposed passwords are not contained in a specified dictionary.
 */
public class DictionaryPasswordValidator
       extends PasswordValidator<DictionaryPasswordValidatorCfg>
       implements ConfigurationChangeListener<DictionaryPasswordValidatorCfg>
{
  // The current configuration for this password validator.
  private DictionaryPasswordValidatorCfg currentConfig;

  // The current dictionary that we should use when performing the validation.
  private HashSet<String> dictionary;



  /**
   * Creates a new instance of this dictionary password validator.
   */
  public DictionaryPasswordValidator()
  {
    super();

    // No implementation is required here.  All initialization should be
    // performed in the initializePasswordValidator() method.
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void initializePasswordValidator(
                   DictionaryPasswordValidatorCfg configuration)
         throws ConfigException, InitializationException
  {
    configuration.addDictionaryChangeListener(this);
    currentConfig = configuration;

    dictionary = loadDictionary(configuration);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public void finalizePasswordValidator()
  {
    currentConfig.removeDictionaryChangeListener(this);
  }



  /**
   * {@inheritDoc}
   */
  @Override()
  public boolean passwordIsAcceptable(ByteString newPassword,
                                      Set<ByteString> currentPasswords,
                                      Operation operation, Entry userEntry,
                                      StringBuilder invalidReason)
  {
    // Get a handle to the current configuration.
    DictionaryPasswordValidatorCfg config = currentConfig;
    HashSet<String> dictionary = this.dictionary;


    // Check to see if the provided password is in the dictionary in the order
    // that it was provided.
    String password = newPassword.stringValue();
    if (! config.isCaseSensitiveValidation())
    {
      password = toLowerCase(password);
    }

    if (dictionary.contains(password))
    {
      int msgID = MSGID_DICTIONARY_VALIDATOR_PASSWORD_IN_DICTIONARY;
      invalidReason.append(getMessage(msgID));
      return false;
    }


    // If we should try the reversed value, then do that as well.
    if (config.isTestReversedPassword())
    {
      if (dictionary.contains(new StringBuilder(password).reverse().toString()))
      {
        int msgID = MSGID_DICTIONARY_VALIDATOR_PASSWORD_IN_DICTIONARY;
        invalidReason.append(getMessage(msgID));
        return false;
      }
    }


    // If we've gotten here, then the password is acceptable.
    return true;
  }



  /**
   * Loads the configured dictionary and returns it as a hash set.
   *
   * @param  configuration  the configuration for this password validator.
   *
   * @return  The hash set containing the loaded dictionary data.
   *
   * @throws  ConfigException  If the configured dictionary file does not exist.
   *
   * @throws  InitializationException  If a problem occurs while attempting to
   *                                   read from the dictionary file.
   */
  private HashSet<String> loadDictionary(
                               DictionaryPasswordValidatorCfg configuration)
          throws ConfigException, InitializationException
  {
    // Get the path to the dictionary file and make sure it exists.
    File dictionaryFile = getFileForPath(configuration.getDictionaryFile());
    if (! dictionaryFile.exists())
    {
      int    msgID   = MSGID_DICTIONARY_VALIDATOR_NO_SUCH_FILE;
      String message = getMessage(msgID, configuration.getDictionaryFile());
      throw new ConfigException(msgID, message);
    }


    // Read the contents of file into the dictionary as per the configuration.
    BufferedReader reader = null;
    HashSet<String> dictionary = new HashSet<String>();
    try
    {
      reader = new BufferedReader(new FileReader(dictionaryFile));
      String line = reader.readLine();
      while (line != null)
      {
        if (! configuration.isCaseSensitiveValidation())
        {
          line = line.toLowerCase();
        }

        dictionary.add(line);
        line = reader.readLine();
      }
    }
    catch (Exception e)
    {
      if (debugEnabled())
      {
        debugCaught(DebugLogLevel.ERROR, e);
      }

      int    msgID   = MSGID_DICTIONARY_VALIDATOR_CANNOT_READ_FILE;
      String message = getMessage(msgID, configuration.getDictionaryFile(),
                                  String.valueOf(e));
      throw new InitializationException(msgID, message);
    }
    finally
    {
      if (reader != null)
      {
        try
        {
          reader.close();
        } catch (Exception e) {}
      }
    }

    return dictionary;
  }



  /**
   * {@inheritDoc}
   */
  public boolean isConfigurationChangeAcceptable(
                      DictionaryPasswordValidatorCfg configuration,
                      List<String> unacceptableReasons)
  {
    // Make sure that we can load the dictionary.  If so, then we'll accept the
    // new configuration.
    try
    {
      loadDictionary(configuration);
    }
    catch (ConfigException ce)
    {
      unacceptableReasons.add(ce.getMessage());
      return false;
    }
    catch (InitializationException ie)
    {
      unacceptableReasons.add(ie.getMessage());
      return false;
    }
    catch (Exception e)
    {
      unacceptableReasons.add(getExceptionMessage(e));
      return false;
    }

    return true;
  }



  /**
   * {@inheritDoc}
   */
  public ConfigChangeResult applyConfigurationChange(
                      DictionaryPasswordValidatorCfg configuration)
  {
    ResultCode        resultCode          = ResultCode.SUCCESS;
    boolean           adminActionRequired = false;
    ArrayList<String> messages            = new ArrayList<String>();


    // Make sure we can load the dictionary.  If we can, then activate the new
    // configuration.
    try
    {
      dictionary    = loadDictionary(configuration);
      currentConfig = configuration;
    }
    catch (Exception e)
    {
      resultCode = DirectoryConfig.getServerErrorResultCode();
      messages.add(getExceptionMessage(e));
    }

    return new ConfigChangeResult(resultCode, adminActionRequired, messages);
  }
}

