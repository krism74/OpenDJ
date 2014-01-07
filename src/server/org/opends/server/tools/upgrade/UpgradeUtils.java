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
 *      Copyright 2013 ForgeRock AS
 */
package org.opends.server.tools.upgrade;

import java.io.*;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.AddRequest;
import org.forgerock.opendj.ldap.requests.ModifyRequest;
import org.forgerock.opendj.ldap.requests.Requests;
import org.forgerock.opendj.ldap.requests.SearchRequest;
import org.forgerock.opendj.ldap.schema.CoreSchema;
import org.forgerock.opendj.ldap.schema.Schema;
import org.forgerock.opendj.ldap.schema.SchemaBuilder;
import org.forgerock.opendj.ldap.schema.UnknownSchemaElementException;
import org.forgerock.opendj.ldif.EntryReader;
import org.forgerock.opendj.ldif.LDIF;
import org.forgerock.opendj.ldif.LDIFEntryReader;
import org.forgerock.opendj.ldif.LDIFEntryWriter;
import org.opends.server.core.DirectoryServer;
import org.opends.server.core.SchemaConfigManager;
import org.opends.server.util.ChangeOperationType;
import org.opends.server.util.SetupUtils;
import org.opends.server.util.StaticUtils;

import static org.opends.messages.ConfigMessages.INFO_CONFIG_FILE_HEADER;
import static org.opends.messages.ToolMessages.ERR_UPGRADE_UNKNOWN_OC_ATT;
import static org.opends.messages.ToolMessages.ERR_UPGRADE_CORRUPTED_TEMPLATE;
import static org.opends.server.tools.upgrade.FileManager.deleteRecursively;
import static org.opends.server.tools.upgrade.FileManager.rename;
import static org.opends.server.tools.upgrade.Installation.*;
import static org.opends.server.util.ServerConstants.EOL;

/**
 * Common utility methods needed by the upgrade.
 */
final class UpgradeUtils
{

  /**
   * Logger for the upgrade.
   */
  private final static Logger LOG = Logger
      .getLogger(UpgradeCli.class.getName());

  /** The config folder of the current instance. */
  static final File configDirectory = new File(getInstancePath(),
      Installation.CONFIG_PATH_RELATIVE);

  /** The config/schema folder of the current instance. */
  static final File configSchemaDirectory = new File(
      configDirectory, Installation.SCHEMA_PATH_RELATIVE);

  /** The template folder of the current installation. */
  static final File templateDirectory = new File(getInstallationPath(),
       Installation.TEMPLATE_RELATIVE_PATH);

  /** The template/config folder of the current installation. */
  static final File templateConfigDirectory = new File(templateDirectory,
       Installation.CONFIG_PATH_RELATIVE);

  /** The template/config/schema folder of the current installation. */
  static final File templateConfigSchemaDirectory = new File(
      templateConfigDirectory,
      Installation.SCHEMA_PATH_RELATIVE);

  /** The config/snmp/security folder of the current instance. */
  static final File configSnmpSecurityDirectory = new File(
      configDirectory + File.separator + Installation.SNMP_PATH_RELATIVE
          + File.separator + Installation.SECURITY_PATH_RELATIVE);

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @return the path of the installation of the directory server.
   */
  static String getInstallPathFromClasspath()
  {
    String installPath = DirectoryServer.getServerRoot();
    if (installPath != null)
    {
      return installPath;
    }

    /* Get the install path from the Class Path */
    final String sep = System.getProperty("path.separator");
    final String[] classPaths =
        System.getProperty("java.class.path").split(sep);
    String path = getInstallPath(classPaths);
    if (path != null)
    {
      final File f = new File(path).getAbsoluteFile();
      final File librariesDir = f.getParentFile();

      /*
       * Do a best effort to avoid having a relative representation (for
       * instance to avoid having ../../../).
       */
      try
      {
        installPath = librariesDir.getParentFile().getCanonicalPath();
      }
      catch (IOException ioe)
      {
        // Best effort
        installPath = librariesDir.getParent();
      }
    }
    return installPath;
  }

  private static String getInstallPath(final String[] classPaths)
  {
    for (String classPath : classPaths)
    {
      final String normPath = classPath.replace(File.separatorChar, '/');
      if (normPath.endsWith(Installation.OPENDJ_BOOTSTRAP_JAR_RELATIVE_PATH))
      {
        return classPath;
      }
    }
    return null;
  }

  /**
   * Returns the path of the installation of the directory server. Note that
   * this method assumes that this code is being run locally.
   *
   * @param installPath
   *          The installation path
   * @return the path of the installation of the directory server.
   */
  static String getInstancePathFromInstallPath(final String installPath)
  {
    String instancePathFileName = Installation.INSTANCE_LOCATION_PATH;
    final File _svcScriptPath =
        new File(installPath + File.separator
            + SVC_SCRIPT_FILE_NAME);

    // look for /etc/opt/opendj/instance.loc
    File f = new File(instancePathFileName);
    if (!_svcScriptPath.exists() || !f.exists())
    {
      // look for <installPath>/instance.loc
      instancePathFileName =
          installPath + File.separator
              + Installation.INSTANCE_LOCATION_PATH_RELATIVE;
      f = new File(instancePathFileName);
      if (!f.exists())
      {
        return installPath;
      }
    }

    BufferedReader reader;
    try
    {
      reader = new BufferedReader(new FileReader(instancePathFileName));
    }
    catch (Exception e)
    {
      return installPath;
    }

    // Read the first line and close the file.
    String line;
    try
    {
      line = reader.readLine();
      File instanceLoc = new File(line.trim());
      if (instanceLoc.isAbsolute())
      {
        return instanceLoc.getAbsolutePath();
      }
      else
      {
        return new File(installPath + File.separator + instanceLoc.getPath())
            .getAbsolutePath();
      }
    }
    catch (Exception e)
    {
      return installPath;
    }
    finally
    {
      StaticUtils.close(reader);
    }
  }

  /**
   * Returns the absolute path for the given file. It tries to get the canonical
   * file path. If it fails it returns the string representation.
   *
   * @param f
   *          File to get the path
   * @return the absolute path for the given file.
   */
  static String getPath(File f)
  {
    String path = null;
    if (f != null)
    {
      try
      {
        /*
         * Do a best effort to avoid having a relative representation (for
         * instance to avoid having ../../../).
         */
        f = f.getCanonicalFile();
      }
      catch (IOException ioe)
      {
        /*
         * This is a best effort to get the best possible representation of the
         * file: reporting the error is not necessary.
         */
      }
      path = f.toString();
    }
    return path;
  }

  /**
   * Returns the absolute path for the given parentPath and relativePath.
   *
   * @param parentPath
   *          the parent path.
   * @param relativePath
   *          the relative path.
   * @return the absolute path for the given parentPath and relativePath.
   */
  static String getPath(final String parentPath,
      final String relativePath)
  {
    return getPath(new File(new File(parentPath), relativePath));
  }

  /**
   * Returns <CODE>true</CODE> if we are running under windows and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we are running under windows and
   *         <CODE>false</CODE> otherwise.
   */
  static boolean isWindows()
  {
    return SetupUtils.isWindows();
  }

  /**
   * Returns <CODE>true</CODE> if we are running under Unix and
   * <CODE>false</CODE> otherwise.
   *
   * @return <CODE>true</CODE> if we are running under Unix and
   *         <CODE>false</CODE> otherwise.
   */
  static boolean isUnix()
  {
    return SetupUtils.isUnix();
  }

  /**
   * Determines whether one file is the parent of another.
   *
   * @param ancestor
   *          possible parent of <code>descendant</code>
   * @param descendant
   *          possible child 0f <code>ancestor</code>
   * @return return true if ancestor is a parent of descendant
   */
  static boolean isParentOf(final File ancestor, File descendant)
  {
    if (ancestor != null)
    {
      if (ancestor.equals(descendant))
      {
        return false;
      }
      while ((descendant != null) && !ancestor.equals(descendant))
      {
        descendant = descendant.getParentFile();
      }
    }
    return (ancestor != null) && (descendant != null);
  }

  /**
   * Returns <CODE>true</CODE> if the first provided path is under the second
   * path in the file system.
   * @param descendant the descendant candidate path.
   * @param path the path.
   * @return <CODE>true</CODE> if the first provided path is under the second
   * path in the file system; <code>false</code> otherwise or if
   * either of the files are null
   */
  static boolean isDescendant(File descendant, File path) {
    boolean isDescendant = false;
    if (descendant != null && path != null) {
      File parent = descendant.getParentFile();
      while ((parent != null) && !isDescendant) {
        isDescendant = path.equals(parent);
        if (!isDescendant) {
          parent = parent.getParentFile();
        }
      }
    }
    return isDescendant;
  }

  /**
   * Returns the instance root directory (the path where the instance is
   * installed).
   *
   * @return the instance root directory (the path where the instance is
   *         installed).
   */
  static String getInstancePath()
  {
    final String installPath = getInstallationPath();
    if (installPath == null)
    {
      return null;
    }

    return getInstancePathFromInstallPath(installPath);
  }

  /**
   * Returns the server's installation path.
   *
   * @return The server's installation path.
   */
  static String getInstallationPath()
  {
    // The upgrade runs from the bits extracted by BuildExtractor
    // in the staging directory.  However
    // we still want the Installation to point at the build being
    // upgraded so the install path reported in [installroot].
    String installationPath = System.getProperty("INSTALL_ROOT");
    if (installationPath == null)
    {
      final String path = getInstallPathFromClasspath();
      if (path != null)
      {
        final File f = new File(path);
        if (f.getParentFile() != null
            && f.getParentFile().getParentFile() != null
            && new File(f.getParentFile().getParentFile(),
                Installation.LOCKS_PATH_RELATIVE).exists())
        {
          installationPath = getPath(f.getParentFile().getParentFile());
        }
        else
        {
          installationPath = path;
        }
      }
    }
    return installationPath;
  }

  /**
   * Retrieves the backends from the current configuration file. The backends
   * must be enabled to be listed. No operations should be done within a
   * disabled backend.
   *
   * @return A backend list.
   */
  static List<String> getLocalBackendsFromConfig()
  {
    final Schema schema = getUpgradeSchema();

    final List<String> listBackends = new LinkedList<String>();
    LDIFEntryReader entryReader = null;
    try
    {
      entryReader =
          new LDIFEntryReader(new FileInputStream(new File(configDirectory,
              CURRENT_CONFIG_FILE_NAME))).setSchema(schema);

      final SearchRequest sr =
          Requests.newSearchRequest("", SearchScope.WHOLE_SUBTREE,
              "(&(objectclass=ds-cfg-local-db-backend)(ds-cfg-enabled=true))",
              "ds-cfg-base-dn");

      final EntryReader resultReader = LDIF.search(entryReader, sr, schema);

      while (resultReader.hasNext())
      {
        final Entry entry = resultReader.readEntry();
        listBackends.add(entry.getAttribute("ds-cfg-base-dn")
            .firstValueAsString());
      }
    }
    catch (Exception ex)
    {
      LOG.log(Level.SEVERE, ex.getMessage());
    }
    finally
    {
      StaticUtils.close(entryReader);
    }

    return listBackends;
  }

  /**
   * Updates the config file during the upgrade process.
   *
   *
   * @param configPath
   *          The original path to the file.
   * @param filter
   *          The filter to avoid files.
   * @param changeType
   *          The change type which must be applied to ldif lines.
   * @param lines
   *          The change record ldif lines.
   * @throws IOException
   *           If an Exception occurs during the input output methods.
   * @return The changes number that have occurred.
   */
  static int updateConfigFile(final String configPath,
      final Filter filter, final ChangeOperationType changeType,
      final String... lines) throws IOException
  {
    final File original = new File(configPath);
    final File copyConfig =
        File.createTempFile("copyConfig", ".tmp", original.getParentFile());

    int changeCount = 0;
    LDIFEntryReader entryReader = null;
    LDIFEntryWriter writer = null;
    try
    {
      final Schema schema = getUpgradeSchema();
      entryReader =
          new LDIFEntryReader(new FileInputStream(configPath))
              .setSchema(schema);

      writer = new LDIFEntryWriter(new FileOutputStream(copyConfig));
      writer.setWrapColumn(80);

      // Writes the header on the new file.
      writer.writeComment(INFO_CONFIG_FILE_HEADER.get());
      writer.setWrapColumn(0);

      boolean alreadyExist = false;
      String dn = null;
      if (filter == null && changeType == ChangeOperationType.ADD)
      {
        // For an Add, the first line should start with dn:
        dn = lines[0].replaceFirst("dn: ","");
      }
      final Matcher matcher =
          filter != null ? filter.matcher(schema) : Filter.alwaysFalse()
              .matcher(schema);
      while (entryReader.hasNext())
      {
        Entry entry = entryReader.readEntry();
        // Searching for the related entries
        if (matcher.matches(entry) == ConditionResult.TRUE)
        {
          try
          {
            final ModifyRequest mr =
                Requests.newModifyRequest(readLDIFLines(entry.getName(),
                    changeType, lines));
            entry = Entries.modifyEntryPermissive(entry, mr.getModifications());
            changeCount++;
            LOG.log(Level.INFO,
                String.format("The following entry has been modified : %s",
                    entry.getName()));
          }
          catch (Exception ex)
          {
            LOG.log(Level.SEVERE, ex.getMessage());
          }
        }
        if (dn != null // This is an ADD
            && entry.getName().equals(DN.valueOf(dn)))
        {
          LOG.log(Level.INFO, String.format("Entry %s found", entry.getName()
              .toString()));
          alreadyExist = true;
        }
        writer.writeEntry(entry);
      }

      // If it's an ADD and the entry doesn't exist yet
      if (dn != null && !alreadyExist)
      {
        final AddRequest ar = Requests.newAddRequest(lines);
        writer.writeEntry(ar);
        LOG.log(Level.INFO, String.format("Entry successfully added %s in %s",
            dn, original.getAbsolutePath()));
        changeCount++;
      }
    }
    catch (Exception ex)
    {
      throw new IOException(ex.getMessage());
    }
    finally
    {
      // The reader and writer must be close before renaming files.
      // Otherwise it causes exceptions under windows OS.
      StaticUtils.close(entryReader, writer);
    }

    try
    {
      // Renaming the file, overwriting previous one.
      rename(copyConfig, new File(configPath));
    }
    catch (IOException e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      deleteRecursively(original);
      throw e;
    }

    return changeCount;
  }

  /**
   * This task adds new attributes / object classes to the specified destination
   * file. The new attributes and object classes must be originally defined in
   * the template file.
   *
   * @param templateFile
   *          The file in which the new attribute/object definition can be read.
   * @param destination
   *          The file where we want to add the new definitions.
   * @param attributes
   *          Those attributes needed to be inserted into the new destination
   *          file.
   * @param objectClasses
   *          Those object classes needed to be inserted into the new
   *          destination file.
   * @return An integer which represents each time an attribute / object class
   *         is inserted successfully to the destination file.
   * @throws IOException
   *           If an unexpected IO error occurred while reading the entry.
   * @throws UnknownSchemaElementException
   *           Failure to find an attribute in the template schema indicates
   *           either a programming error (e.g. typo in the attribute name) or
   *           template corruption. Upgrade should stop.
   */
  static int updateSchemaFile(final File templateFile, final File destination,
      final String[] attributes, final String[] objectClasses)
      throws IOException, UnknownSchemaElementException
  {
    int changeCount = 0;
    LDIFEntryReader reader = null;
    BufferedReader br = null;
    FileWriter fw = null;
    File copy = null;
    try
    {
      reader = new LDIFEntryReader(new FileInputStream(templateFile));

      if (!reader.hasNext())
      {
        // Unless template are corrupted, this should not happen.
        throw new IOException(ERR_UPGRADE_CORRUPTED_TEMPLATE.get(
            templateFile.getPath()).toString());
      }
      final LinkedList<String> definitionsList = new LinkedList<String>();

      final Entry schemaEntry = reader.readEntry();

      Schema schema =
          new SchemaBuilder(Schema.getCoreSchema())
              .addSchema(schemaEntry, true).toSchema();
      if (attributes != null)
      {
        for (final String att : attributes)
        {
          try
          {
            final String definition =
                "attributeTypes: " + schema.getAttributeType(att);
            definitionsList.add(definition);
            LOG.log(Level.INFO, String.format("Added %s", definition));
          }
          catch (UnknownSchemaElementException e)
          {
            LOG.log(Level.SEVERE, ERR_UPGRADE_UNKNOWN_OC_ATT.get("attribute",
                att).toString());
            throw e;
          }
        }
      }

      if (objectClasses != null)
      {
        for (final String oc : objectClasses)
        {
          try
          {
            final String definition =
                "objectClasses: " + schema.getObjectClass(oc);
            definitionsList.add(definition);
            LOG.log(Level.INFO, String.format("Added %s", definition));
          }
          catch (UnknownSchemaElementException e)
          {
            LOG.log(Level.SEVERE, ERR_UPGRADE_UNKNOWN_OC_ATT.get(
                "object class", oc).toString());
            throw e;
          }
        }
      }
      // Then, open the destination file and write the new attribute
      // or objectClass definitions
      copy =
          File.createTempFile("copySchema", ".tmp",
              destination.getParentFile());
      br = new BufferedReader(new FileReader(destination));
      fw = new FileWriter(copy);
      String line = br.readLine();
      while (line != null && !"".equals(line))
      {
        fw.write(line + EOL);
        line = br.readLine();
      }
      for (final String definition : definitionsList)
      {
        writeLine(fw, definition, 80);
        changeCount++;
      }
      // Must be ended with a blank line
      fw.write(EOL);
    }
    finally
    {
      // The reader and writer must be close before writing files.
      // This causes exceptions under windows OS.
      StaticUtils.close(br, fw, reader);
    }

    // Writes the schema file.
    try
    {
      rename(copy, destination);
    }
    catch (IOException e)
    {
      LOG.log(Level.SEVERE, e.getMessage());
      deleteRecursively(copy);
      throw e;
    }

    return changeCount;
  }

  /**
   * Creates a new file in the config/upgrade folder. The new file is a
   * concatenation of entries of all files contained in the config/schema
   * folder.
   *
   * @param folder
   *          The folder containing the schema files.
   * @param revision
   *          The revision number of the current binary version.
   * @throws Exception
   *           If we cannot read the files contained in the folder where the
   *           schema files are supposed to be, or the file has errors.
   */
  static void updateConfigUpgradeSchemaFile(final File folder,
      final String revision) throws Exception
  {
    // We need to upgrade the schema.ldif.<rev> file contained in the
    // config/upgrade folder otherwise, we cannot enable the backend at
    // server's start. We need to read all files contained in config/schema
    // and add all attribute/object classes in this new super entry which
    // will be read at start-up.
    Entry theNewSchemaEntry = new LinkedHashMapEntry();
    LDIFEntryReader reader = null;
    LDIFEntryWriter writer = null;
    try
    {
      if (folder.isDirectory())
      {
        final FilenameFilter filter =
            new SchemaConfigManager.SchemaFileFilter();
        for (final File f : folder.listFiles(filter))
        {
          LOG.log(Level.INFO, String.format("Processing %s", f
              .getAbsolutePath()));
          reader = new LDIFEntryReader(new FileInputStream(f));
          try
          {
            while (reader.hasNext())
            {
              final Entry entry = reader.readEntry();
              theNewSchemaEntry.setName(entry.getName());
              for (final Attribute at : entry.getAllAttributes())
              {
                theNewSchemaEntry.addAttribute(at);
              }
            }
          }
          catch (Exception ex)
          {
            throw new Exception("Error parsing existing schema file "
                + f.getName() + " - " + ex.getMessage(), ex);
          }
        }

        // Creates a File object representing
        // config/upgrade/schema.ldif.revision which the server creates
        // the first time it starts if there are schema customizations.
        final File destination =
            new File(configDirectory, Installation.UPGRADE_PATH
                + File.separator + "schema.ldif." + revision);

        // Checks if the parent exists (eg. embedded
        // server doesn't seem to provide that folder)
        File parentDirectory = destination.getParentFile();
        if (!parentDirectory.exists())
        {
          LOG.log(Level.INFO, String.format("Parent file of %s doesn't exist",
              destination.getPath()));

          parentDirectory.mkdirs();

          LOG.log(Level.INFO, String.format("Parent directory %s created.",
              parentDirectory.getPath()));
        }
        if (!destination.exists())
        {
          destination.createNewFile();
        }

        LOG.log(Level.INFO, String.format("Writing entries in %s.", destination
            .getAbsolutePath()));

        writer = new LDIFEntryWriter(new FileOutputStream(destination));
        writer.writeEntry(theNewSchemaEntry);

        LOG.log(Level.INFO, String.format(
            "%s created and completed successfully.", destination
                .getAbsolutePath()));
      }
    }
    finally
    {
      StaticUtils.close(reader, writer);
    }
  }

  /**
   * Returns a schema used by upgrade(default octet string matching rule and
   * directory string syntax). Added attribute types which we know we are
   * sensitive to in the unit tests, e.g. ds-cfg-enabled (boolean syntax),
   * ds-cfg-filter(case ingnore), ds-cfg-collation (case ignore)... related to
   * upgrade tasks. See OPENDJ-1245.
   *
   * @return A schema which may used in the upgrade context.
   */
  final static Schema getUpgradeSchema() {
    final SchemaBuilder sb =
        new SchemaBuilder(Schema.getCoreSchema())
        .defaultMatchingRule(CoreSchema.getCaseExactMatchingRule())
        .defaultSyntax(CoreSchema.getDirectoryStringSyntax());

    // Adds ds-cfg-enabled / boolean syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.2 NAME 'ds-cfg-enabled'"
        + " EQUALITY booleanMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.7"
        + " SINGLE-VALUE X-ORIGIN 'OpenDS Directory Server' )", false);

    // Adds ds-cfg-filter / ignore match syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.279 NAME 'ds-cfg-filter'"
        + " EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15"
        + " X-ORIGIN 'OpenDS Directory Server' )", false);

    // Adds ds-cfg-collation / ignore match syntax
    sb.addAttributeType("( 1.3.6.1.4.1.26027.1.1.500 NAME 'ds-cfg-collation'"
        + " EQUALITY caseIgnoreMatch SYNTAX 1.3.6.1.4.1.1466.115.121.1.15"
        + " X-ORIGIN 'OpenDS Directory Server' )", false);

    return sb.toSchema().asNonStrictSchema();
  }

  private static String[] readLDIFLines(final DN dn,
      final ChangeOperationType changeType, final String... lines)
  {
    final String[] modifiedLines = new String[lines.length + 2];

    int index = 0;
    if (changeType == ChangeOperationType.MODIFY)
    {
      modifiedLines[0] = "dn: " + dn;
      modifiedLines[1] = "changetype: modify";
      index = 2;
    }
    for (final String line : lines)
    {
      modifiedLines[index] = line;
      index++;
    }
    return modifiedLines;
  }

  private static void writeLine(final FileWriter fw, final String line,
      final int wrapColumn) throws IOException
  {
    final int length = line.length();
    if (length > wrapColumn)
    {
      fw.write(line.subSequence(0, wrapColumn).toString());
      fw.write(EOL);
      int pos = wrapColumn;
      while (pos < length)
      {
        final int writeLength = Math.min(wrapColumn - 1, length - pos);
        fw.write(" ");
        fw.write(line.subSequence(pos, pos + writeLength).toString());
        fw.write(EOL);
        pos += wrapColumn - 1;
      }
    }
    else
    {
      fw.write(line);
      fw.write(EOL);
    }
  }

  // Prevent instantiation.
  private UpgradeUtils()
  {
    throw new AssertionError();
  }
}
