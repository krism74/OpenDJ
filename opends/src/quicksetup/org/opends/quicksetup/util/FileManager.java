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

package org.opends.quicksetup.util;

import org.opends.quicksetup.*;
import org.opends.quicksetup.i18n.ResourceProvider;

import java.io.*;

/**
 * Utility class for use by applications containing methods for managing
 * file system files.  This class handles application notifications for
 * interesting events.
 */
public class FileManager {

  private Application application = null;

  /**
   * Creates a new file manager.
   * @param app Application managing files.
   */
  public FileManager(Application app) {
    this.application = app;
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file the path to be deleted.
   * @throws org.opends.quicksetup.ApplicationException if something goes wrong.
   */
  public void deleteRecursively(File file) throws ApplicationException {
    deleteRecursively(file, null);
  }

  /**
   * Deletes everything below the specified file.
   *
   * @param file   the path to be deleted.
   * @param filter the filter of the files to know if the file can be deleted
   *               directly or not.
   * @throws ApplicationException if something goes wrong.
   */
  public void deleteRecursively(File file, FileFilter filter)
          throws ApplicationException {
    operateRecursively(new DeleteOperation(file), filter);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @throws ApplicationException if something goes wrong.
   */
  public void copy(File objectFile, File destDir)
          throws ApplicationException
  {
    new CopyOperation(objectFile, destDir, false).apply();
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param overwrite    overwrite destination files.
   * @throws ApplicationException if something goes wrong.
   */
  public void copy(File objectFile, File destDir, boolean overwrite)
          throws ApplicationException
  {
    new CopyOperation(objectFile, destDir, overwrite).apply();
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir)
          throws ApplicationException
  {
    copyRecursively(objectFile, destDir, null);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param filter the filter of the files to know if the file can be copied
   *               directly or not.
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir, FileFilter filter)
          throws ApplicationException {
    copyRecursively(objectFile, destDir, filter, false);
  }

  /**
   * Copies everything below the specified file.
   *
   * @param objectFile   the file to be copied.
   * @param destDir      the directory to copy the file to
   * @param filter the filter of the files to know if the file can be copied
   *               directly or not.
   * @param overwrite    overwrite destination files.
   * @throws ApplicationException if something goes wrong.
   */
  public void copyRecursively(File objectFile, File destDir,
                              FileFilter filter, boolean overwrite)
          throws ApplicationException {
    operateRecursively(new CopyOperation(objectFile, destDir, overwrite),
            filter);
  }

  private void operateRecursively(FileOperation op, FileFilter filter)
          throws ApplicationException {
    File file = op.getObjectFile();
    if (file.exists()) {
      if (file.isFile()) {
        if (filter != null) {
          if (filter.accept(file)) {
            op.apply();
          }
        } else {
          op.apply();
        }
      } else {
        File[] children = file.listFiles();
        if (children != null) {
          for (File aChildren : children) {
            FileOperation newOp = op.copyForChild(aChildren);
            operateRecursively(newOp, filter);
          }
        }
        if (filter != null) {
          if (filter.accept(file)) {
            op.apply();
          }
        } else {
          op.apply();
        }
      }
    } else {
      // Just tell that the file/directory does not exist.
      String[] arg = {file.toString()};
      application.notifyListeners(application.getFormattedWarning(
              getMsg("file-does-not-exist", arg)));
    }
  }

  /**
   * A file operation.
   */
  private abstract class FileOperation {

    private File objectFile = null;

    /**
     * Creates a new file operation.
     * @param objectFile to be operated on
     */
    public FileOperation(File objectFile) {
      this.objectFile = objectFile;
    }

    /**
     * Gets the file to be operated on.
     * @return File to be operated on
     */
    protected File getObjectFile() {
      return objectFile;
    }

    /**
     * Make a copy of this class for the child file.
     * @param child to act as the new file object
     * @return FileOperation as the same type as this class
     */
    abstract public FileOperation copyForChild(File child);

    /**
     * Execute this operation.
     * @throws ApplicationException if there is a problem.
     */
    abstract public void apply() throws ApplicationException;

  }

  /**
   * A copy operation.
   */
  private class CopyOperation extends FileOperation {

    private File destination;

    private boolean overwrite;

    /**
     * Create a new copy operation.
     * @param objectFile to copy
     * @param destDir to copy to
     */
    public CopyOperation(File objectFile, File destDir, boolean overwrite) {
      super(objectFile);
      this.destination = new File(destDir, objectFile.getName());
      this.overwrite = overwrite;
    }

    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child) {
      return new CopyOperation(child, destination, overwrite);
    }

    /**
     * {@inheritDoc}
     */
    public void apply() throws ApplicationException {
      File objectFile = getObjectFile();
      String[] args = {objectFile.getAbsolutePath(),
              destination.getAbsolutePath()};

      // If overwriting and the destination exists then kill it
      if (destination.exists() && overwrite) {
        deleteRecursively(destination);
      }

      if (objectFile.isDirectory()) {
        if (!destination.exists()) {
            destination.mkdirs();
        }
      } else {
        if (!destination.exists()) {
          if (insureParentsExist(destination)) {
            application.notifyListeners(application.getFormattedWithPoints(
                    getMsg("progress-copying-file", args)));

            try {
              FileInputStream fis = new FileInputStream(objectFile);
              FileOutputStream fos = new FileOutputStream(destination);
              byte[] buf = new byte[1024];
              int i;
              while ((i = fis.read(buf)) != -1) {
                fos.write(buf, 0, i);
              }
              fis.close();
              fos.close();

              application.notifyListeners(application.getFormattedDone() +
                      application.getLineBreak());

            } catch (Exception e) {
              String errMsg = getMsg("error-copying-file", args);
              throw new ApplicationException(
                      ApplicationException.Type.FILE_SYSTEM_ERROR,
                      errMsg, null);
            }
          } else {
            String errMsg = getMsg("error-copying-file", args);
            throw new ApplicationException(
                    ApplicationException.Type.FILE_SYSTEM_ERROR, errMsg, null);
          }
        } else {
          application.notifyListeners(getMsg("info-ignoring-file", args) +
                  application.getLineBreak());
        }
      }
    }

    private boolean insureParentsExist(File f) {
      File parent = f.getParentFile();
      boolean b = parent.exists();
      if (!b) {
        b = parent.mkdirs();
      }
      return b;
    }
  }

  /**
   * A delete operation.
   */
  private class DeleteOperation extends FileOperation {

    /**
     * Creates a delete operation.
     * @param objectFile to delete
     */
    public DeleteOperation(File objectFile) {
      super(objectFile);
    }

    /**
     * {@inheritDoc}
     */
    public FileOperation copyForChild(File child) {
      return new DeleteOperation(child);
    }

    /**
     * {@inheritDoc}
     */
    public void apply() throws ApplicationException {
      File file = getObjectFile();
      String[] arg = {file.getAbsolutePath()};
      boolean isFile = file.isFile();

      if (isFile) {
        application.notifyListeners(application.getFormattedWithPoints(
                getMsg("progress-deleting-file", arg)));
      } else {
        application.notifyListeners(application.getFormattedWithPoints(
                getMsg("progress-deleting-directory", arg)));
      }

      boolean delete = false;
      /*
       * Sometimes the server keeps some locks on the files.
       * TODO: remove this code once stop-ds returns properly when server
       * is stopped.
       */
      int nTries = 5;
      for (int i = 0; i < nTries && !delete; i++) {
        delete = file.delete();
        if (!delete) {
          try {
            Thread.sleep(1000);
          }
          catch (Exception ex) {
            // do nothing;
          }
        }
      }

      if (!delete) {
        String errMsg;
        if (isFile) {
          errMsg = getMsg("error-deleting-file", arg);
        } else {
          errMsg = getMsg("error-deleting-directory", arg);
        }
        throw new ApplicationException(
                ApplicationException.Type.FILE_SYSTEM_ERROR, errMsg, null);
      }

      application.notifyListeners(application.getFormattedDone() +
              application.getLineBreak());
    }
  }

  private String getMsg(String key) {
    return ResourceProvider.getInstance().getMsg(key);
  }

  private String getMsg(String key, String... args) {
    return ResourceProvider.getInstance().getMsg(key, args);
  }

}
