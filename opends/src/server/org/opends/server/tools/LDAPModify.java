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
 *      Portions Copyright 2006-2007 Sun Microsystems, Inc.
 */
package org.opends.server.tools;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.atomic.AtomicInteger;

import org.opends.server.core.DirectoryServer;
import org.opends.server.protocols.asn1.ASN1Element;
import org.opends.server.protocols.asn1.ASN1Exception;
import org.opends.server.protocols.asn1.ASN1OctetString;
import org.opends.server.protocols.asn1.ASN1Sequence;
import org.opends.server.protocols.ldap.AddRequestProtocolOp;
import org.opends.server.protocols.ldap.AddResponseProtocolOp;
import org.opends.server.protocols.ldap.DeleteRequestProtocolOp;
import org.opends.server.protocols.ldap.DeleteResponseProtocolOp;
import org.opends.server.protocols.ldap.LDAPAttribute;
import org.opends.server.protocols.ldap.LDAPControl;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.protocols.ldap.LDAPMessage;
import org.opends.server.protocols.ldap.ModifyRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyResponseProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNRequestProtocolOp;
import org.opends.server.protocols.ldap.ModifyDNResponseProtocolOp;
import org.opends.server.protocols.ldap.SearchResultEntryProtocolOp;
import org.opends.server.protocols.ldap.ProtocolOp;
import org.opends.server.types.Attribute;
import org.opends.server.types.DebugLogLevel;
import org.opends.server.types.DN;
import org.opends.server.types.LDAPException;
import org.opends.server.types.LDIFImportConfig;
import org.opends.server.types.NullOutputStream;
import org.opends.server.types.RawAttribute;
import org.opends.server.types.RawModification;
import org.opends.server.util.AddChangeRecordEntry;
import org.opends.server.util.ChangeRecordEntry;
import org.opends.server.util.LDIFException;
import org.opends.server.util.LDIFReader;
import org.opends.server.util.ModifyChangeRecordEntry;
import org.opends.server.util.ModifyDNChangeRecordEntry;
import org.opends.server.util.PasswordReader;
import org.opends.server.util.args.ArgumentException;
import org.opends.server.util.args.ArgumentParser;
import org.opends.server.util.args.BooleanArgument;
import org.opends.server.util.args.FileBasedArgument;
import org.opends.server.util.args.IntegerArgument;
import org.opends.server.util.args.StringArgument;

import static org.opends.server.loggers.debug.DebugLogger.*;
import org.opends.server.loggers.debug.DebugTracer;
import static org.opends.server.messages.MessageHandler.*;
import static org.opends.server.messages.ToolMessages.*;
import static org.opends.server.protocols.ldap.LDAPResultCode.*;
import static org.opends.server.util.ServerConstants.*;
import static org.opends.server.util.StaticUtils.*;
import static org.opends.server.tools.ToolConstants.*;



/**
 * This class provides a tool that can be used to issue modify requests to the
 * Directory Server.
 */
public class LDAPModify
{
  /**
   * The tracer object for the debug logger.
   */
  private static final DebugTracer TRACER = getTracer();

  /**
   * The fully-qualified name of this class.
   */
  private static final String CLASS_NAME = "org.opends.server.tools.LDAPModify";

  // The message ID counter to use for requests.
  private AtomicInteger nextMessageID;

  // The print stream to use for standard error.
  private PrintStream err;

  // The print stream to use for standard output.
  private PrintStream out;

  // The LDIF file name.
  private String fileName = null;

  /**
   * Constructor for the LDAPModify object.
   *
   * @param  fileName       The name of the file containing the LDIF data to use
   *                        for the modifications.
   * @param  nextMessageID  The message ID counter to use for requests.
   * @param  out            The print stream to use for standard output.
   * @param  err            The print stream to use for standard error.
   */
  public LDAPModify(String fileName, AtomicInteger nextMessageID,
                    PrintStream out, PrintStream err)
  {
    if(fileName == null)
    {
      this.fileName = "Console";
    } else
    {
      this.fileName = fileName;
    }

    this.nextMessageID = nextMessageID;
    this.out           = out;
    this.err           = err;
  }


  /**
   * Read the specified change records from the given input stream
   * (file or stdin) and execute the given modify request.
   *
   * @param connection     The connection to use for this modify request.
   * @param is             The input stream to read the change records from.
   * @param modifyOptions  The constraints for the modify request.
   *
   * @throws  IOException  If a problem occurs while attempting to communicate
   *                       with the Directory Server.
   *
   * @throws  LDAPException  If the Directory Server returns an error response.
   */
  public void readAndExecute(LDAPConnection connection, InputStream is,
                             LDAPModifyOptions modifyOptions)
         throws IOException, LDAPException
  {
    ArrayList<LDAPControl> controls = modifyOptions.getControls();
    LDIFReader reader;

    // Create an LDIF import configuration to do this and then get the reader.

    try
    {
      LDIFImportConfig importConfig = new LDIFImportConfig(is);
      reader = new LDIFReader(importConfig);
    } catch (Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      int msgID = MSGID_LDIF_FILE_CANNOT_OPEN_FOR_READ;
      String message = getMessage(msgID, is, String.valueOf(e));
      throw new IOException(message);
    }

    while (true)
    {
      ChangeRecordEntry entry = null;

      try
      {
        entry = reader.readChangeRecord(modifyOptions.getDefaultAdd());
      } catch (LDIFException le)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, le);
        }
        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e);
            }
          }

          int msgID = MSGID_LDIF_FILE_INVALID_LDIF_ENTRY;
          String message = getMessage(msgID, le.getLineNumber(), fileName,
                                      String.valueOf(le));
          throw new IOException(message);
        }
        else
        {
          int msgID = MSGID_LDIF_FILE_INVALID_LDIF_ENTRY;
          String message = getMessage(msgID, le.getLineNumber(), fileName,
                                      String.valueOf(le));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          continue;
        }
      } catch (Exception e)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, e);
        }

        if (!modifyOptions.continueOnError())
        {
          try
          {
            reader.close();
          }
          catch (Exception e2)
          {
            if (debugEnabled())
            {
              TRACER.debugCaught(DebugLogLevel.ERROR, e2);
            }
          }

          int msgID = MSGID_LDIF_FILE_READ_ERROR;
          String message = getMessage(msgID, fileName, String.valueOf(e));
          throw new IOException(message);
        }
        else
        {
          int msgID = MSGID_LDIF_FILE_READ_ERROR;
          String message = getMessage(msgID, fileName, String.valueOf(e));
          err.println(wrapText(message, MAX_LINE_WIDTH));
          continue;
        }
      }

      // If the entry is null, then we have reached the end of the config file.
      if(entry == null)
      {
        try
        {
          reader.close();
        }
        catch (Exception e)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, e);
          }
        }

        break;
      }

      ProtocolOp protocolOp = null;
      ASN1OctetString asn1OctetStr =
           new ASN1OctetString(entry.getDN().toString());

      String operationType = "";
      int msgID = 0;
      switch(entry.getChangeOperationType())
      {
        case ADD:
          operationType = "ADD";
          AddChangeRecordEntry addEntry = (AddChangeRecordEntry) entry;
          List<Attribute> attrs = addEntry.getAttributes();
          ArrayList<RawAttribute> attributes =
              new ArrayList<RawAttribute>(attrs.size());
          for(Attribute a : attrs)
          {
            attributes.add(new LDAPAttribute(a));
          }
          protocolOp = new AddRequestProtocolOp(asn1OctetStr, attributes);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case DELETE:
          operationType = "DELETE";
          protocolOp = new DeleteRequestProtocolOp(asn1OctetStr);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case MODIFY:
          operationType = "MODIFY";
          ModifyChangeRecordEntry modEntry = (ModifyChangeRecordEntry) entry;
          ArrayList<RawModification> mods =
            new ArrayList<RawModification>(modEntry.getModifications());
          protocolOp = new ModifyRequestProtocolOp(asn1OctetStr, mods);
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        case MODIFY_DN:
          operationType = "MODIFY DN";
          ModifyDNChangeRecordEntry modDNEntry =
            (ModifyDNChangeRecordEntry) entry;
          if(modDNEntry.getNewSuperiorDN() != null)
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                 new ASN1OctetString(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN(),
                 new ASN1OctetString(
                          modDNEntry.getNewSuperiorDN().toString()));
          } else
          {
            protocolOp = new ModifyDNRequestProtocolOp(asn1OctetStr,
                 new ASN1OctetString(modDNEntry.getNewRDN().toString()),
                 modDNEntry.deleteOldRDN());
          }
          msgID = MSGID_PROCESSING_OPERATION;
          out.println(getMessage(msgID, operationType, asn1OctetStr));
          break;
        default:
          break;
      }

      if(!modifyOptions.showOperations())
      {
        LDAPMessage responseMessage = null;
        try
        {
          LDAPMessage message =
               new LDAPMessage(nextMessageID.getAndIncrement(), protocolOp,
                               controls);
          connection.getLDAPWriter().writeMessage(message);
          responseMessage = connection.getLDAPReader().readMessage();
        } catch(ASN1Exception ae)
        {
          if (debugEnabled())
          {
            TRACER.debugCaught(DebugLogLevel.ERROR, ae);
          }
          msgID = MSGID_OPERATION_FAILED;
          String message = getMessage(msgID, operationType);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
          if (!modifyOptions.continueOnError())
          {
            throw new IOException(ae.getMessage());
          }
          return;
        }

        int resultCode = 0;
        String errorMessage = null;
        DN matchedDN = null;
        List<String> referralURLs = null;
        switch(entry.getChangeOperationType())
        {
          case ADD:
            AddResponseProtocolOp addOp =
              responseMessage.getAddResponseProtocolOp();
            resultCode = addOp.getResultCode();
            errorMessage = addOp.getErrorMessage();
            matchedDN = addOp.getMatchedDN();
            referralURLs = addOp.getReferralURLs();
            break;
          case DELETE:
            DeleteResponseProtocolOp delOp =
              responseMessage.getDeleteResponseProtocolOp();
            resultCode = delOp.getResultCode();
            errorMessage = delOp.getErrorMessage();
            matchedDN = delOp.getMatchedDN();
            referralURLs = delOp.getReferralURLs();
            break;
          case MODIFY:
            ModifyResponseProtocolOp modOp =
              responseMessage.getModifyResponseProtocolOp();
            resultCode = modOp.getResultCode();
            errorMessage = modOp.getErrorMessage();
            matchedDN = modOp.getMatchedDN();
            referralURLs = modOp.getReferralURLs();
            break;
          case MODIFY_DN:
            ModifyDNResponseProtocolOp modDNOp =
              responseMessage.getModifyDNResponseProtocolOp();
            resultCode = modDNOp.getResultCode();
            errorMessage = modDNOp.getErrorMessage();
            matchedDN = modDNOp.getMatchedDN();
            referralURLs = modDNOp.getReferralURLs();
            break;
          default:
            break;
        }

        if(resultCode != SUCCESS && resultCode != REFERRAL)
        {
          msgID = MSGID_OPERATION_FAILED;
          String msg = getMessage(msgID, operationType);

          if(!modifyOptions.continueOnError())
          {
            throw new LDAPException(resultCode, errorMessage, msgID, msg,
                                    matchedDN, null);
          } else
          {
            LDAPToolUtils.printErrorMessage(err, msg, resultCode, errorMessage,
                                            matchedDN);
          }
        } else
        {
          msgID = MSGID_OPERATION_SUCCESSFUL;
          String msg = getMessage(msgID, operationType, asn1OctetStr);
          out.println(msg);

          if (errorMessage != null)
          {
            out.println(wrapText(errorMessage, MAX_LINE_WIDTH));
          }

          if (referralURLs != null)
          {
            out.println(referralURLs);
          }
        }


        for (LDAPControl c : responseMessage.getControls())
        {
          String oid = c.getOID();
          if (oid.equals(OID_LDAP_READENTRY_PREREAD))
          {
            ASN1OctetString controlValue = c.getValue();
            if (controlValue == null)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_NO_VALUE;
              err.println(wrapText(getMessage(msgID), MAX_LINE_WIDTH));
              continue;
            }

            SearchResultEntryProtocolOp searchEntry;
            try
            {
              byte[] valueBytes = controlValue.value();
              ASN1Element valueElement = ASN1Element.decode(valueBytes);
              searchEntry =
                   SearchResultEntryProtocolOp.decodeSearchEntry(valueElement);
            }
            catch (ASN1Exception ae)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE;
              err.println(wrapText(getMessage(msgID, ae.getMessage()),
                                   MAX_LINE_WIDTH));
              continue;
            }
            catch (LDAPException le)
            {
              msgID = MSGID_LDAPMODIFY_PREREAD_CANNOT_DECODE_VALUE;
              err.println(wrapText(getMessage(msgID, le.getMessage()),
                                   MAX_LINE_WIDTH));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toLDIF(buffer, 78);
            out.println(getMessage(MSGID_LDAPMODIFY_PREREAD_ENTRY));
            out.println(buffer);
          }
          else if (oid.equals(OID_LDAP_READENTRY_POSTREAD))
          {
            ASN1OctetString controlValue = c.getValue();
            if (controlValue == null)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_NO_VALUE;
              err.println(wrapText(getMessage(msgID), MAX_LINE_WIDTH));
              continue;
            }

            SearchResultEntryProtocolOp searchEntry;
            try
            {
              byte[] valueBytes = controlValue.value();
              ASN1Element valueElement = ASN1Element.decode(valueBytes);
              searchEntry =
                   SearchResultEntryProtocolOp.decodeSearchEntry(valueElement);
            }
            catch (ASN1Exception ae)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE;
              err.println(wrapText(getMessage(msgID, ae.getMessage()),
                                   MAX_LINE_WIDTH));
              continue;
            }
            catch (LDAPException le)
            {
              msgID = MSGID_LDAPMODIFY_POSTREAD_CANNOT_DECODE_VALUE;
              err.println(wrapText(getMessage(msgID, le.getMessage()),
                                   MAX_LINE_WIDTH));
              continue;
            }

            StringBuilder buffer = new StringBuilder();
            searchEntry.toLDIF(buffer, 78);
            out.println(getMessage(MSGID_LDAPMODIFY_POSTREAD_ENTRY));
            out.println(buffer);
          }
        }
      }
    }

  }

  /**
   * The main method for LDAPModify tool.
   *
   * @param  args  The command-line arguments provided to this program.
   */

  public static void main(String[] args)
  {
    int retCode = mainModify(args, true, System.out, System.err);

    if(retCode != 0)
    {
      System.exit(filterExitCode(retCode));
    }
  }


  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapmodify tool.
   *
   * @param  args  The command-line arguments provided to this program.
   *
   * @return The error code.
   */

  public static int mainModify(String[] args)
  {
    return mainModify(args, true, System.out, System.err);
  }


  /**
   * Parses the provided command-line arguments and uses that information to
   * run the ldapmodify tool.
   *
   * @param  args              The command-line arguments provided to this
   *                           program.
   * @param  initializeServer  Indicates whether to initialize the server.
   * @param  outStream         The output stream to use for standard output, or
   *                           <CODE>null</CODE> if standard output is not
   *                           needed.
   * @param  errStream         The output stream to use for standard error, or
   *                           <CODE>null</CODE> if standard error is not
   *                           needed.
   *
   * @return The error code.
   */

  public static int mainModify(String[] args, boolean initializeServer,
                               OutputStream outStream, OutputStream errStream)
  {
    PrintStream out;
    if (outStream == null)
    {
      out = NullOutputStream.printStream();
    }
    else
    {
      out = new PrintStream(outStream);
    }

    PrintStream err;
    if (errStream == null)
    {
      err = NullOutputStream.printStream();
    }
    else
    {
      err = new PrintStream(errStream);
    }


    LDAPConnectionOptions connectionOptions = new LDAPConnectionOptions();
    LDAPModifyOptions modifyOptions = new LDAPModifyOptions();
    LDAPConnection connection = null;

    BooleanArgument   continueOnError        = null;
    BooleanArgument   defaultAdd             = null;
    BooleanArgument   noop                   = null;
    BooleanArgument   reportAuthzID          = null;
    BooleanArgument   saslExternal           = null;
    BooleanArgument   showUsage              = null;
    BooleanArgument   startTLS               = null;
    BooleanArgument   trustAll               = null;
    BooleanArgument   useSSL                 = null;
    BooleanArgument   verbose                = null;
    FileBasedArgument bindPasswordFile       = null;
    FileBasedArgument keyStorePasswordFile   = null;
    FileBasedArgument trustStorePasswordFile = null;
    IntegerArgument   port                   = null;
    IntegerArgument   version                = null;
    StringArgument    assertionFilter        = null;
    StringArgument    bindDN                 = null;
    StringArgument    bindPassword           = null;
    StringArgument    certNickname           = null;
    StringArgument    controlStr             = null;
    StringArgument    encodingStr            = null;
    StringArgument    filename               = null;
    StringArgument    hostName               = null;
    StringArgument    keyStorePath           = null;
    StringArgument    keyStorePassword       = null;
    StringArgument    postReadAttributes     = null;
    StringArgument    preReadAttributes      = null;
    StringArgument    proxyAuthzID           = null;
    StringArgument    saslOptions            = null;
    StringArgument    trustStorePath         = null;
    StringArgument    trustStorePassword     = null;

    // Create the command-line argument parser for use with this program.
    String toolDescription = getMessage(MSGID_LDAPMODIFY_TOOL_DESCRIPTION);
    ArgumentParser argParser = new ArgumentParser(CLASS_NAME, toolDescription,
                                                  false);
    try
    {
      hostName = new StringArgument("host", OPTION_SHORT_HOST,
                                    OPTION_LONG_HOST, false, false, true,
                                    OPTION_VALUE_HOST, "localhost", null,
                                    MSGID_DESCRIPTION_HOST);
      argParser.addArgument(hostName);

      port = new IntegerArgument("port", OPTION_SHORT_PORT,
                                 OPTION_LONG_PORT, false, false, true,
                                 OPTION_VALUE_PORT, 389, null,
                                 MSGID_DESCRIPTION_PORT);
      argParser.addArgument(port);

      useSSL = new BooleanArgument("useSSL", OPTION_SHORT_USE_SSL,
                                   OPTION_LONG_USE_SSL,
                                   MSGID_DESCRIPTION_USE_SSL);
      argParser.addArgument(useSSL);

      startTLS = new BooleanArgument("startTLS", OPTION_SHORT_START_TLS,
                                     OPTION_LONG_START_TLS,
                                     MSGID_DESCRIPTION_START_TLS);
      argParser.addArgument(startTLS);

      bindDN = new StringArgument("bindDN", OPTION_SHORT_BINDDN,
                                  OPTION_LONG_BINDDN, false, false, true,
                                  OPTION_VALUE_BINDDN, null, null,
                                  MSGID_DESCRIPTION_BINDDN);
      argParser.addArgument(bindDN);

      bindPassword = new StringArgument("bindPassword", OPTION_SHORT_BINDPWD,
                                        OPTION_LONG_BINDPWD,
                                        false, false, true,
                                        OPTION_VALUE_BINDPWD,
                                        null, null,
                                        MSGID_DESCRIPTION_BINDPASSWORD);
      argParser.addArgument(bindPassword);

      bindPasswordFile =
           new FileBasedArgument("bindPasswordFile",
                                 OPTION_SHORT_BINDPWD_FILE,
                                 OPTION_LONG_BINDPWD_FILE,
                                 false, false,
                                 OPTION_VALUE_BINDPWD_FILE, null,
                                 null, MSGID_DESCRIPTION_BINDPASSWORDFILE);
      argParser.addArgument(bindPasswordFile);

      defaultAdd = new BooleanArgument("defaultAdd", 'a', "defaultAdd",
                                       MSGID_MODIFY_DESCRIPTION_DEFAULT_ADD);
      argParser.addArgument(defaultAdd);

      filename = new StringArgument("filename", OPTION_SHORT_FILENAME,
                                    OPTION_LONG_FILENAME, false, false,
                                    true, OPTION_VALUE_FILENAME, null, null,
                                    MSGID_LDAPMODIFY_DESCRIPTION_FILENAME);
      argParser.addArgument(filename);

      saslExternal = new BooleanArgument("useSASLExternal", 'r',
                                         "useSASLExternal",
                                         MSGID_DESCRIPTION_USE_SASL_EXTERNAL);
      argParser.addArgument(saslExternal);

      saslOptions = new StringArgument("saslOption", OPTION_SHORT_SASLOPTION,
                                       OPTION_LONG_SASLOPTION, false,
                                       true, true,
                                       OPTION_VALUE_SASLOPTION, null, null,
                                       MSGID_DESCRIPTION_SASL_PROPERTIES);
      argParser.addArgument(saslOptions);

      trustAll = new BooleanArgument("trustAll", 'X', "trustAll",
                                    MSGID_DESCRIPTION_TRUSTALL);
      argParser.addArgument(trustAll);

      keyStorePath = new StringArgument("keyStorePath",
                                        OPTION_SHORT_KEYSTOREPATH,
                                        OPTION_LONG_KEYSTOREPATH,
                                        false, false, true,
                                        OPTION_VALUE_KEYSTOREPATH,
                                        null, null,
                                        MSGID_DESCRIPTION_KEYSTOREPATH);
      argParser.addArgument(keyStorePath);

      keyStorePassword = new StringArgument("keyStorePassword",
                                            OPTION_SHORT_KEYSTORE_PWD,
                                            OPTION_LONG_KEYSTORE_PWD,
                                            false, false,
                                            true,
                                            OPTION_VALUE_KEYSTORE_PWD,
                                            null, null,
                                            MSGID_DESCRIPTION_KEYSTOREPASSWORD);
      argParser.addArgument(keyStorePassword);

      keyStorePasswordFile =
           new FileBasedArgument("keystorepasswordfile",
                                 OPTION_SHORT_KEYSTORE_PWD_FILE,
                                 OPTION_LONG_KEYSTORE_PWD_FILE,
                                 false, false, OPTION_VALUE_KEYSTORE_PWD_FILE,
                                 null, null,
                                 MSGID_DESCRIPTION_KEYSTOREPASSWORD_FILE);
      argParser.addArgument(keyStorePasswordFile);

      certNickname = new StringArgument("certnickname", 'N', "certNickname",
                                        false, false, true, "{nickname}", null,
                                        null, MSGID_DESCRIPTION_CERT_NICKNAME);
      argParser.addArgument(certNickname);

      trustStorePath = new StringArgument("trustStorePath",
                                          OPTION_SHORT_TRUSTSTOREPATH,
                                          OPTION_LONG_TRUSTSTOREPATH,
                                          false, false, true,
                                          OPTION_VALUE_TRUSTSTOREPATH,
                                          null, null,
                                          MSGID_DESCRIPTION_TRUSTSTOREPATH);
      argParser.addArgument(trustStorePath);

      trustStorePassword =
           new StringArgument("trustStorePassword", null,
                              OPTION_LONG_TRUSTSTORE_PWD ,
                              false, false, true,
                              OPTION_VALUE_TRUSTSTORE_PWD, null,
                              null, MSGID_DESCRIPTION_TRUSTSTOREPASSWORD);
      argParser.addArgument(trustStorePassword);

      trustStorePasswordFile =
           new FileBasedArgument("truststorepasswordfile",
                                 OPTION_SHORT_TRUSTSTORE_PWD_FILE,
                                 OPTION_LONG_TRUSTSTORE_PWD_FILE, false, false,
                                 OPTION_VALUE_TRUSTSTORE_PWD_FILE, null, null,
                                 MSGID_DESCRIPTION_TRUSTSTOREPASSWORD_FILE);
      argParser.addArgument(trustStorePasswordFile);

      proxyAuthzID = new StringArgument("proxy_authzid",
                                        OPTION_SHORT_PROXYAUTHID,
                                        OPTION_LONG_PROXYAUTHID, false,
                                        false, true,
                                        OPTION_VALUE_PROXYAUTHID, null, null,
                                        MSGID_DESCRIPTION_PROXY_AUTHZID);
      argParser.addArgument(proxyAuthzID);

      reportAuthzID = new BooleanArgument("reportauthzid", 'E',
                                          "reportAuthzID",
                                          MSGID_DESCRIPTION_REPORT_AUTHZID);
      argParser.addArgument(reportAuthzID);

      assertionFilter = new StringArgument("assertionfilter", null,
                                           OPTION_LONG_ASSERTION_FILE,
                                           false, false,
                                           true,
                                           OPTION_VALUE_ASSERTION_FILE,
                                           null, null,
                                           MSGID_DESCRIPTION_ASSERTION_FILTER);
      argParser.addArgument(assertionFilter);

      preReadAttributes = new StringArgument("prereadattrs", null,
                                             "preReadAttributes", false, false,
                                             true, "{attrList}", null, null,
                                             MSGID_DESCRIPTION_PREREAD_ATTRS);
      argParser.addArgument(preReadAttributes);

      postReadAttributes = new StringArgument("postreadattrs", null,
                                              "postReadAttributes", false,
                                              false, true, "{attrList}", null,
                                              null,
                                              MSGID_DESCRIPTION_POSTREAD_ATTRS);
      argParser.addArgument(postReadAttributes);

      controlStr =
           new StringArgument("control", 'J', "control", false, true, true,
                    "{controloid[:criticality[:value|::b64value|:<fileurl]]}",
                    null, null, MSGID_DESCRIPTION_CONTROLS);
      argParser.addArgument(controlStr);

      version = new IntegerArgument("version", OPTION_SHORT_PROTOCOL_VERSION,
                              OPTION_LONG_PROTOCOL_VERSION,
                              false, false, true,
                              OPTION_VALUE_PROTOCOL_VERSION, 3, null,
                              MSGID_DESCRIPTION_VERSION);
      argParser.addArgument(version);

      encodingStr = new StringArgument("encoding", 'i', "encoding",
                                      false, false,
                                      true, "{encoding}", null, null,
                                      MSGID_DESCRIPTION_ENCODING);
      argParser.addArgument(encodingStr);

      continueOnError = new BooleanArgument("continueOnError", 'c',
                                    "continueOnError",
                                    MSGID_DESCRIPTION_CONTINUE_ON_ERROR);
      argParser.addArgument(continueOnError);

      noop = new BooleanArgument("no-op", OPTION_SHORT_DRYRUN,
                                    OPTION_LONG_DRYRUN,
                                    MSGID_DESCRIPTION_NOOP);
      argParser.addArgument(noop);

      verbose = new BooleanArgument("verbose", 'v', "verbose",
                                    MSGID_DESCRIPTION_VERBOSE);
      argParser.addArgument(verbose);

      showUsage = new BooleanArgument("showUsage", OPTION_SHORT_HELP,
                                      OPTION_LONG_HELP,
                                      MSGID_DESCRIPTION_SHOWUSAGE);
      argParser.addArgument(showUsage);
      argParser.setUsageArgument(showUsage, out);
    } catch (ArgumentException ae)
    {
      int    msgID   = MSGID_CANNOT_INITIALIZE_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    // Parse the command-line arguments provided to this program.
    try
    {
      argParser.parseArguments(args);
    }
    catch (ArgumentException ae)
    {
      int    msgID   = MSGID_ERROR_PARSING_ARGS;
      String message = getMessage(msgID, ae.getMessage());

      err.println(wrapText(message, MAX_LINE_WIDTH));
      err.println(argParser.getUsage());
      return 1;
    }

    // If we should just display usage or version information,
    // then print it and exit.
    if (argParser.usageOrVersionDisplayed())
    {
      return 0;
    }

    if(bindPassword.isPresent() && bindPasswordFile.isPresent())
    {
      int    msgID   = MSGID_TOOL_CONFLICTING_ARGS;
      String message = getMessage(msgID, bindPassword.getLongIdentifier(),
                                  bindPasswordFile.getLongIdentifier());
      err.println(wrapText(message, MAX_LINE_WIDTH));
      return 1;
    }

    String hostNameValue = hostName.getValue();
    int portNumber = 389;
    try
    {
      portNumber = port.getIntValue();
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    try
    {
      int versionNumber = version.getIntValue();
      if(versionNumber != 2 && versionNumber != 3)
      {
        int msgID = MSGID_DESCRIPTION_INVALID_VERSION;
        err.println(wrapText(getMessage(msgID, versionNumber), MAX_LINE_WIDTH));
        return 1;
      }
      connectionOptions.setVersionNumber(versionNumber);
    } catch(ArgumentException ae)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, ae);
      }
      err.println(wrapText(ae.getMessage(), MAX_LINE_WIDTH));
      return 1;
    }

    String bindDNValue = bindDN.getValue();
    String fileNameValue = filename.getValue();
    String bindPasswordValue = bindPassword.getValue();
    if(bindPasswordValue != null && bindPasswordValue.equals("-"))
    {
      // read the password from the stdin.
      try
      {
        out.print(getMessage(MSGID_LDAPAUTH_PASSWORD_PROMPT, bindDNValue));
        char[] pwChars = PasswordReader.readPassword();
        bindPasswordValue = new String(pwChars);
      } catch(Exception ex)
      {
        if (debugEnabled())
        {
          TRACER.debugCaught(DebugLogLevel.ERROR, ex);
        }
        err.println(wrapText(ex.getMessage(), MAX_LINE_WIDTH));
        return 1;
      }
    } else if(bindPasswordValue == null)
    {
      // Read from file if it exists.
      bindPasswordValue = bindPasswordFile.getValue();
    }

    String keyStorePathValue = keyStorePath.getValue();
    String trustStorePathValue = trustStorePath.getValue();

    String keyStorePasswordValue = null;
    if (keyStorePassword.isPresent())
    {
      keyStorePasswordValue = keyStorePassword.getValue();
    }
    else if (keyStorePasswordFile.isPresent())
    {
      keyStorePasswordValue = keyStorePasswordFile.getValue();
    }

    String trustStorePasswordValue = null;
    if (trustStorePassword.isPresent())
    {
      trustStorePasswordValue = trustStorePassword.getValue();
    }
    else if (trustStorePasswordFile.isPresent())
    {
      trustStorePasswordValue = trustStorePasswordFile.getValue();
    }

    modifyOptions.setShowOperations(noop.isPresent());
    modifyOptions.setVerbose(verbose.isPresent());
    modifyOptions.setContinueOnError(continueOnError.isPresent());
    modifyOptions.setEncoding(encodingStr.getValue());
    modifyOptions.setDefaultAdd(defaultAdd.isPresent());

    if (controlStr.isPresent())
    {
      for (String ctrlString : controlStr.getValues())
      {
        LDAPControl ctrl = LDAPToolUtils.getControl(ctrlString, err);
        if(ctrl == null)
        {
          int    msgID   = MSGID_TOOL_INVALID_CONTROL_STRING;
          String message = getMessage(msgID, ctrlString);
          err.println(wrapText(message, MAX_LINE_WIDTH));
          err.println(argParser.getUsage());
          return 1;
        }
        modifyOptions.getControls().add(ctrl);
      }
    }

    if (proxyAuthzID.isPresent())
    {
      ASN1OctetString proxyValue = new ASN1OctetString(proxyAuthzID.getValue());

      LDAPControl proxyControl =
        new LDAPControl(OID_PROXIED_AUTH_V2, true, proxyValue);
      modifyOptions.getControls().add(proxyControl);
    }

    if (assertionFilter.isPresent())
    {
      String filterString = assertionFilter.getValue();
      LDAPFilter filter;
      try
      {
        filter = LDAPFilter.decode(filterString);

        LDAPControl assertionControl =
             new LDAPControl(OID_LDAP_ASSERTION, true,
                             new ASN1OctetString(filter.encode().encode()));
        modifyOptions.getControls().add(assertionControl);
      }
      catch (LDAPException le)
      {
        int    msgID   = MSGID_LDAP_ASSERTION_INVALID_FILTER;
        String message = getMessage(msgID, le.getMessage());
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    if (preReadAttributes.isPresent())
    {
      String valueStr = preReadAttributes.getValue();
      ArrayList<ASN1Element> attrElements = new ArrayList<ASN1Element>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(new ASN1OctetString(tokenizer.nextToken()));
      }

      ASN1OctetString controlValue =
           new ASN1OctetString(new ASN1Sequence(attrElements).encode());
      LDAPControl c = new LDAPControl(OID_LDAP_READENTRY_PREREAD, true,
                                      controlValue);
      modifyOptions.getControls().add(c);
    }

    if (postReadAttributes.isPresent())
    {
      String valueStr = postReadAttributes.getValue();
      ArrayList<ASN1Element> attrElements = new ArrayList<ASN1Element>();

      StringTokenizer tokenizer = new StringTokenizer(valueStr, ", ");
      while (tokenizer.hasMoreTokens())
      {
        attrElements.add(new ASN1OctetString(tokenizer.nextToken()));
      }

      ASN1OctetString controlValue =
           new ASN1OctetString(new ASN1Sequence(attrElements).encode());
      LDAPControl c = new LDAPControl(OID_LDAP_READENTRY_POSTREAD, true,
                                      controlValue);
      modifyOptions.getControls().add(c);
    }

    // Set the connection options.
    connectionOptions.setSASLExternal(saslExternal.isPresent());
    if(saslOptions.isPresent())
    {
      LinkedList<String> values = saslOptions.getValues();
      for(String saslOption : values)
      {
        if(saslOption.startsWith("mech="))
        {
          boolean val = connectionOptions.setSASLMechanism(saslOption);
          if(val == false)
          {
            return 1;
          }
        } else
        {
          boolean val = connectionOptions.addSASLProperty(saslOption);
          if(val == false)
          {
            return 1;
          }
        }
      }
    }

    connectionOptions.setUseSSL(useSSL.isPresent());
    connectionOptions.setStartTLS(startTLS.isPresent());
    connectionOptions.setReportAuthzID(reportAuthzID.isPresent());

    if(connectionOptions.useSASLExternal())
    {
      if(!connectionOptions.useSSL() && !connectionOptions.useStartTLS())
      {
        int    msgID   = MSGID_TOOL_SASLEXTERNAL_NEEDS_SSL_OR_TLS;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
      if(keyStorePathValue == null)
      {
        int    msgID   = MSGID_TOOL_SASLEXTERNAL_NEEDS_KEYSTORE;
        String message = getMessage(msgID);
        err.println(wrapText(message, MAX_LINE_WIDTH));
        return 1;
      }
    }

    connectionOptions.setVerbose(verbose.isPresent());

    LDAPModify ldapModify = null;
    try
    {
      if (initializeServer)
      {
        // Bootstrap and initialize directory data structures.
        DirectoryServer.bootstrapClient();
      }

      // Connect to the specified host with the supplied userDN and password.
      SSLConnectionFactory sslConnectionFactory = null;
      if(connectionOptions.useSSL() || connectionOptions.useStartTLS())
      {
        String clientAlias;
        if (certNickname.isPresent())
        {
          clientAlias = certNickname.getValue();
        }
        else
        {
          clientAlias = null;
        }

        sslConnectionFactory = new SSLConnectionFactory();
        sslConnectionFactory.init(trustAll.isPresent(), keyStorePathValue,
                                  keyStorePasswordValue, clientAlias,
                                  trustStorePathValue, trustStorePasswordValue);
        connectionOptions.setSSLConnectionFactory(sslConnectionFactory);
      }

      AtomicInteger nextMessageID = new AtomicInteger(1);
      connection = new LDAPConnection(hostNameValue, portNumber,
                                      connectionOptions, out, err);
      connection.connectToHost(bindDNValue, bindPasswordValue, nextMessageID);

      ldapModify = new LDAPModify(fileNameValue, nextMessageID, out, err);
      InputStream is = System.in;
      if(fileNameValue != null)
      {
        is = new FileInputStream(fileNameValue);
      }
      ldapModify.readAndExecute(connection, is, modifyOptions);
    } catch(LDAPException le)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, le);
      }
      LDAPToolUtils.printErrorMessage(err, le.getMessage(), le.getResultCode(),
                                      le.getErrorMessage(), le.getMatchedDN());
      int code = le.getResultCode();
      return code;
    } catch(LDAPConnectionException lce)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, lce);
      }
      LDAPToolUtils.printErrorMessage(err, lce.getMessage(),
                                      lce.getResultCode(),
                                      lce.getErrorMessage(),
                                      lce.getMatchedDN());
      int code = lce.getResultCode();
      return code;
    } catch(Exception e)
    {
      if (debugEnabled())
      {
        TRACER.debugCaught(DebugLogLevel.ERROR, e);
      }
      err.println(wrapText(e.getMessage(), MAX_LINE_WIDTH));
      return 1;
    } finally
    {
      if(connection != null)
      {
        if (ldapModify == null)
        {
          connection.close(null);
        }
        else
        {
          connection.close(ldapModify.nextMessageID);
        }
      }
    }
    return 0;
  }

}

