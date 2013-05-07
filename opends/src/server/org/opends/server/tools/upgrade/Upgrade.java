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
 *      Copyright 2013 ForgeRock AS
 */

package org.opends.server.tools.upgrade;



import static org.opends.messages.ToolMessages.*;
import static org.opends.server.tools.upgrade.FormattedNotificationCallback.*;
import static org.opends.server.tools.upgrade.UpgradeTasks.*;
import static org.opends.server.tools.upgrade.VerificationCallback.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.ConfirmationCallback;

import org.opends.messages.Message;
import org.opends.server.core.LockFileManager;
import org.opends.server.tools.ClientException;
import org.opends.server.util.BuildVersion;
import org.opends.server.util.StaticUtils;



/**
 * This class contains the table of upgrade tasks that need performing when
 * upgrading from one version to another.
 */
public final class Upgrade
{
  /**
   * Upgrade's logger.
   */
  private final static Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());

  /**
   * Upgrade supports version from 2.4.5.
   */
  private final static BuildVersion UPGRADESUPPORTSVERSIONFROM = BuildVersion
      .valueOf("2.4.5.0000");

  /**
   * The success exit code value.
   */
  public static final int EXIT_CODE_SUCCESS = 0;

  /**
   * The error exit code value.
   */
  public static final int EXIT_CODE_ERROR = 1;

  /**
   * The exit code value that will be used if upgrade requires manual
   * intervention.
   */
  public static final int EXIT_CODE_MANUAL_INTERVENTION = 2;

  /**
   * Developers should register upgrade tasks below.
   */
  private static final NavigableMap<BuildVersion, List<UpgradeTask>> TASKS =
      new TreeMap<BuildVersion, List<UpgradeTask>>();
  private static final List<UpgradeTask> MANDATORY_TASKS =
      new LinkedList<UpgradeTask>();

  static
  {
    // @formatter:off
    register("2.5.0.6869",
        modifyConfigEntry(INFO_UPGRADE_TASK_6869_SUMMARY.get(),
        "(objectClass= ds-cfg-collation-matching-rule)",
        "add: ds-cfg-collation",
        "ds-cfg-collation: de:1.3.6.1.4.1.42.2.27.9.4.28.1",
        "ds-cfg-collation: de-DE:1.3.6.1.4.1.42.2.27.9.4.28.1",
        "-",
        "delete: ds-cfg-collation",
        "ds-cfg-collation: de:1.3.6.1.4.1.142.2.27.9.4.28.1",
        "ds-cfg-collation: de-DE:1.3.6.1.4.1.142.2.27.9.4.28.1"));

    register("2.5.0.7192",
        modifyConfigEntry(INFO_UPGRADE_TASK_7192_SUMMARY.get(),
        "(objectClass= ds-cfg-password-policy)",
        "add: objectClass",
        "objectClass: ds-cfg-authentication-policy",
        "-",
        "add: ds-cfg-java-class",
        "ds-cfg-java-class: org.opends.server.core.PasswordPolicyFactory"));

    register("2.5.0.7364",
        modifyConfigEntry(INFO_UPGRADE_TASK_7364_SUMMARY.get(),
        "(ds-cfg-java-class= org.opends.server.loggers.TextAuditLogPublisher)",
        "add: objectClass",
        "objectClass: ds-cfg-file-based-audit-log-publisher",
        "-",
        "delete: objectClass",
        "objectClass: ds-cfg-file-based-access-log-publisher"));

    register("2.5.0.7748",
        newAttributeTypes(INFO_UPGRADE_TASK_7748_1_SUMMARY.get(),
        "00-core.ldif", "etag"),
        addConfigEntry(INFO_UPGRADE_TASK_7748_2_SUMMARY.get(),
        "dn: cn=etag,cn=Virtual Attributes,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-virtual-attribute",
        "objectClass: ds-cfg-entity-tag-virtual-attribute",
        "cn: etag",
        "ds-cfg-java-class: org.opends.server.extensions."
            + "EntityTagVirtualAttributeProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-attribute-type: etag",
        "ds-cfg-conflict-behavior: real-overrides-virtual",
        "ds-cfg-checksum-algorithm: adler-32",
        "ds-cfg-excluded-attribute: ds-sync-hist"));

    register("2.5.0.7834",
        addConfigEntry(INFO_UPGRADE_TASK_7834_SUMMARY.get(),
        "dn: cn=Password Expiration Time,cn=Virtual Attributes,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-virtual-attribute",
        "objectClass: ds-cfg-password-expiration-time-virtual-attribute",
        "cn: Password Expiration Time",
        "ds-cfg-java-class: org.opends.server.extensions."
            + "PasswordExpirationTimeVirtualAttributeProvider",
        "ds-cfg-enabled: true",
        "ds-cfg-attribute-type: ds-pwp-password-expiration-time",
        "ds-cfg-conflict-behavior: virtual-overrides-real"));

    register("2.5.0.7979",
        modifyConfigEntry(INFO_UPGRADE_TASK_7979_SUMMARY.get(),
        "(ds-cfg-java-class= org.opends.server.schema.CertificateSyntax)",
        "add: objectClass",
        "objectClass: ds-cfg-certificate-attribute-syntax",
        "-",
        "add: ds-cfg-strict-format",
        "ds-cfg-strict-format: false"));

    register("2.5.0.8124",
        modifyConfigEntry(INFO_UPGRADE_TASK_8124_SUMMARY.get(),
        "(ds-cfg-java-class= org.opends.server.schema.JPEGSyntax)",
        "add: objectClass",
        "objectClass: ds-cfg-jpeg-attribute-syntax",
        "-",
        "add: ds-cfg-strict-format",
        "ds-cfg-strict-format: false"));

    register("2.5.0.8133",
        modifyConfigEntry(INFO_UPGRADE_TASK_8133_SUMMARY.get(),
        "(ds-cfg-java-class= org.opends.server.schema.CountryStringSyntax)",
        "add: objectClass",
        "objectClass: ds-cfg-country-string-attribute-syntax",
        "-",
        "add: ds-cfg-strict-format",
        "ds-cfg-strict-format: false"));

    register("2.5.0.8214",
        modifyConfigEntryOptional(INFO_UPGRADE_TASK_8214_SUMMARY.get(),
        INFO_UPGRADE_TASK_8214_DESCRIPTION.get(),
        "(ds-cfg-java-class=org.opends.server.extensions."
            + "IsMemberOfVirtualAttributeProvider)",
        "add: ds-cfg-filter",
        "ds-cfg-filter: (|(objectClass=person)(objectClass=groupOfNames)"
            + "(objectClass=groupOfUniqueNames)(objectClass=groupOfEntries))",
        "-",
        "delete: ds-cfg-filter",
        "ds-cfg-filter: (objectClass=person)"));

    register("2.5.0.8387",
        modifyConfigEntry(INFO_UPGRADE_TASK_8387_SUMMARY.get(),
        "(objectClass= ds-cfg-dictionary-password-validator)",
        "add: ds-cfg-check-substrings",
        "ds-cfg-check-substrings: false"));

    register("2.5.0.8389",
        modifyConfigEntry(INFO_UPGRADE_TASK_8389_SUMMARY.get(),
        "(objectClass= ds-cfg-attribute-value-password-validator)",
        "add: ds-cfg-check-substrings",
        "ds-cfg-check-substrings: false"));

    register("2.5.0.8487",
        addConfigEntry(INFO_UPGRADE_TASK_8487_SUMMARY.get(),
        "dn: cn=PBKDF2,cn=Password Storage Schemes,cn=config",
        "changetype: add",
        "objectClass: top",
        "objectClass: ds-cfg-password-storage-scheme",
        "objectClass: ds-cfg-pbkdf2-password-storage-scheme",
        "cn: PBKDF2",
        "ds-cfg-java-class: org.opends.server.extensions."
            + "PBKDF2PasswordStorageScheme",
        "ds-cfg-enabled: true"));

    /*
     * All upgrades will refresh the server configuration schema and generate
     * a new upgrade folder.
     */
    registerLast(
        copySchemaFile("02-config.ldif"),
        updateConfigUpgradeFolder());

    // TODO for tests.
    /*register("2.5.0.8657",
       rebuildAllIndexes(Message.raw("This is fake Rebuild Task")));*/

    // FIXME: HTTP connection handler and access logger when complete.

    // @formatter:on
  }



  /**
   * Returns a list containing all the tasks which are required in order to
   * upgrade from {@code fromVersion} to {@code toVersion}.
   *
   * @param fromVersion
   *          The old version.
   * @param toVersion
   *          The new version.
   * @return A list containing all the tasks which are required in order to
   *         upgrade from {@code fromVersion} to {@code toVersion}.
   */
  public static List<UpgradeTask> getUpgradeTasks(
      final BuildVersion fromVersion, final BuildVersion toVersion)
  {
    final List<UpgradeTask> tasks = new LinkedList<UpgradeTask>();
    for (final List<UpgradeTask> subList : TASKS.subMap(fromVersion, false,
        toVersion, true).values())
    {
      tasks.addAll(subList);
    }
    tasks.addAll(MANDATORY_TASKS);
    return tasks;
  }



  /**
   * Upgrades the server from {@code fromVersion} to {@code toVersion}.
   *
   * @param fromVersion
   *          The old version.
   * @param toVersion
   *          The new version.
   * @param handler
   *          The call-back handler for interacting with the upgrade
   *          application.
   * @throws ClientException
   *           If an error occurred while performing the upgrade.
   */
  public static void upgrade(final BuildVersion fromVersion,
      final BuildVersion toVersion, final CallbackHandler handler)
      throws ClientException
  {
    /*
     * Context through which tasks can interact with the server installation
     * (e.g. config).
     */
    UpgradeContext context = new UpgradeContext(fromVersion, toVersion);

    // Checks and validate the version number.
    isVersionCanBeUpdated(context, handler);

    // Server offline ?
    checkIfServerIsRunning();

    context.notify(handler, INFO_UPGRADE_TITLE.get(), TITLE_CALLBACK);
    context.notify(handler, INFO_UPGRADE_SUMMARY.get(context.getFromVersion()
        .toString(), context.getToVersion().toString()), NOTICE_CALLBACK);
    context.notify(handler, INFO_UPGRADE_GENERAL_SEE_FOR_DETAILS
        .get(UpgradeUtils.getInstallationPath() + File.separator
            + UpgradeLog.UPGRADELOGNAME), NOTICE_CALLBACK);

    // Checks License.
    checkLicence(context, handler);

    /*
     * Get the list of required upgrade tasks.
     */
    final List<UpgradeTask> tasks = getUpgradeTasks(context.getFromVersion(),
        context.getToVersion());
    if (tasks.isEmpty())
    {
      changeBuildInfoVersion(context, handler);
      return;
    }

    /*
     * Verify tasks requirements.
     * Eg. if a task requires mandatory user interaction, like rebuild index,
     * and the application is non-interactive then, the process
     * may abort immediately.
     */
    verify(context, tasks, handler);

    /*
     * Asking upgrade requirements if needed to user.
     */
    context.notify(handler, INFO_UPGRADE_REQUIREMENTS.get(), TITLE_CALLBACK);
    interact(context, tasks, handler);

    // Starts upgrade.
    final int userResponse = context.confirmYN(handler,
        INFO_UPGRADE_DISPLAY_CONFIRM_START.get(), ConfirmationCallback.YES);
    if (userResponse == ConfirmationCallback.NO)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          INFO_UPGRADE_ABORTED_BY_USER.get());
    }

    try
    {
      // If the ignore errors mode is selected.
      if (context.checkCLIUserOption(handler, IGNORE_ERRORS_MODE)
          == ConfirmationCallback.YES)
      {
        context = new UpgradeContext(fromVersion, toVersion, true);
      }

      /*
       * Perform the upgrade tasks.
       */
      context.notify(handler, INFO_UPGRADE_PERFORMING_TASKS.get(),
          TITLE_CALLBACK);

      perform(context, tasks, handler);
      if (UpgradeTasks.countErrors == 0)
      {
        // At the end, and if only if succeed, we need to change the buildInfo
        // file with the version number updated.
        changeBuildInfoVersion(context, handler);
      }
      else
      {
        context.notify(handler,
            ERR_UPGRADE_FAILS.get(UpgradeTasks.countErrors), TITLE_CALLBACK);
      }
    }
    catch (final ClientException e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      context.notify(handler, e.getMessageObject());
      throw e;
    }
    catch (final Exception e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      context.notify(handler, ERR_UPGRADE_TASKS_FAIL.get(e.getMessage()));
      throw new ClientException(EXIT_CODE_ERROR, Message.raw(e.getMessage()));
    }
    finally
    {
      context.notify(handler, INFO_UPGRADE_GENERAL_SEE_FOR_DETAILS
          .get(UpgradeUtils.getInstallationPath() + File.separator
              + UpgradeLog.UPGRADELOGNAME), NOTICE_CALLBACK);
    }
  }



  private static void perform(final UpgradeContext context,
      final List<UpgradeTask> tasks, final CallbackHandler handler)
      throws ClientException
  {
    /*
     * Notify each task that the upgrade is about to be started.
     */
    for (final UpgradeTask task : tasks)
    {
      task.start(context, handler);
    }

    /*
     * Perform each task.
     */
    for (final UpgradeTask task : tasks)
    {
      task.perform(context, handler);
    }

    /*
     * Notify each task that the upgrade has completed. Tasks may do cleanup
     * work here, such as removing files.
     */
    for (final UpgradeTask task : tasks)
    {
      task.end(context, handler);
    }
  }



  private static void register(final String versionString,
      final UpgradeTask... tasks)
  {
    final BuildVersion version = version(versionString);
    List<UpgradeTask> taskList = TASKS.get(version);
    if (taskList == null)
    {
      TASKS.put(version, (taskList = new LinkedList<UpgradeTask>()));
    }
    taskList.addAll(Arrays.asList(tasks));
  }

  private static void registerLast(final UpgradeTask... tasks)
  {
    MANDATORY_TASKS.addAll(Arrays.asList(tasks));
  }

  private static void interact(final UpgradeContext context,
      final List<UpgradeTask> tasks, final CallbackHandler handler)
      throws ClientException
  {
    /*
     * Let tasks interact with the user in order to obtain user's selection.
     */
    for (final UpgradeTask task : tasks)
    {
      task.interact(context, handler);
    }
  }



  private static void verify(final UpgradeContext context,
      final List<UpgradeTask> tasks, final CallbackHandler handler)
      throws ClientException
  {
    /*
     * Let tasks interact with CLI to check if command line is correct.
     */
    for (final UpgradeTask task : tasks)
    {
      task.verify(context, handler);
    }
  }



  private static BuildVersion version(final String version)
  {
    // TODO Need to change it when change to GIT.
    return BuildVersion.valueOf(version);
  }



  /**
   * The server must be offline during the upgrade.
   *
   * @throws ClientException
   *           An exception is thrown if the server is currently running.
   */
  private final static void checkIfServerIsRunning() throws ClientException
  {
    final String lockFile = LockFileManager.getServerLockFileName();

    final Message message = ERR_UPGRADE_REQUIRES_SERVER_OFFLINE.get();
    try
    {
      final StringBuilder failureReason = new StringBuilder();
      if (!LockFileManager.acquireExclusiveLock(lockFile, failureReason))
      {
        throw new ClientException(EXIT_CODE_ERROR, message);
      }
    }
    catch (Exception e)
    {
      // Assume that if we cannot acquire the lock file the server is
      // running.
      throw new ClientException(EXIT_CODE_ERROR, message);
    }
  }



  /**
   * Checks if the version can be updated.
   *
   * @param context
   *          The current context which running the upgrade.
   * @param callbackHandler
   *          The callback handler in use.
   * @throws ClientException
   *           If an exception occurs - stops the process.
   */
  private static void isVersionCanBeUpdated(final UpgradeContext context,
      final CallbackHandler callbackHandler) throws ClientException
  {
    if (context.getFromVersion().equals(context.getToVersion()))
    {
      /*
       * If the server is already up to date then treat it as a successful
       * upgrade so that upgrade is idempotent.
       */
      final Message message = ERR_UPGRADE_VERSION_UP_TO_DATE.get(context
          .getToVersion().toString());
      throw new ClientException(EXIT_CODE_SUCCESS, message);
    }

    // TODO The upgrade only supports version >= 2.4.5.
    if (context.getFromVersion().compareTo(UPGRADESUPPORTSVERSIONFROM) < 0)
    {
      throw new ClientException(EXIT_CODE_ERROR,
          INFO_UPGRADE_VERSION_IS_NOT_SUPPORTED.get(UPGRADESUPPORTSVERSIONFROM
              .toString(), UPGRADESUPPORTSVERSIONFROM.toString()));
    }
  }



  /**
   * Writes the up to date's version number within the build info file.
   *
   * @param context
   *          The current context which running the upgrade.
   * @param callbackHandler
   *          The callback handler in use.
   * @throws ClientException
   *           If an exception occurs when displaying the message.
   * @throws IOException
   *           If an exception occurs when trying to write the file.
   */
  private static void changeBuildInfoVersion(final UpgradeContext context,
      final CallbackHandler callbackHandler) throws ClientException
  {
    FileWriter buildInfo = null;
    try
    {
      buildInfo = new FileWriter(new File(UpgradeUtils.configDirectory,
          Installation.BUILDINFO_RELATIVE_PATH), false);

      // Write the new version
      buildInfo.write(context.getToVersion().toString());

      context.notify(callbackHandler, INFO_UPGRADE_SUCCESSFUL.get(context
          .getFromVersion().toString(), context.getToVersion().toString()),
          TITLE_CALLBACK);

    }
    catch (IOException e)
    {
      throw new ClientException(EXIT_CODE_ERROR, Message.raw(e.getMessage()));
    }
    finally
    {
      StaticUtils.close(buildInfo);
    }
  }



  private static void checkLicence(final UpgradeContext context,
      final CallbackHandler handler) throws ClientException
  {
    // Check license
    if (!LicenseFile.isAlreadyApproved())
    {
      if (LicenseFile.exists())
      {
        context.notify(handler, Message.raw(LicenseFile.getText()));

        // If the user asks for no-prompt. We just display the license text.
        // User doesn't asks for no-prompt. We just display the license text
        // and force to accept it.
        context.notify(handler, INFO_LICENSE_DETAILS_CLI_LABEL.get());

        final int answer = context.confirmYN(handler,
            INFO_LICENSE_ACCEPT.get(), ConfirmationCallback.NO);

        if (answer == ConfirmationCallback.NO)
        {
          System.exit(EXIT_CODE_SUCCESS);
        }
        else if (answer == ConfirmationCallback.YES)
        {
          // Creates the file
          LicenseFile.setApproval(true);
          LicenseFile.createFileLicenseApproved();
        }
        else
        {
          context.notify(handler,
              INFO_LICENSE_CLI_ACCEPT_INVALID_RESPONSE.get());
        }
      }
    }
  }



  // Prevent instantiation.
  private Upgrade()
  {
    // Nothing to do.
  }
}
