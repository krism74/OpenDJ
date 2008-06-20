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
 *      Portions Copyright 2008 Sun Microsystems, Inc.
 */
package org.opends.scratch.txn;



/**
 * A backend transaction configuration. Used to provide configuration
 * information to the transaction such as lock timeouts.
 */
public final class BackendTxnCfg
{

  // By default a transaction can not be canceled.
  private CancellationHandler cancellationHandler = CancellationHandler.DEFAULT;

  // The transaction lock timeout in milli-seconds (0 means no limit).
  private int lockTimeout = 0;



  /**
   * Creates a new backend transaction configuration with default
   * settings.
   */
  public BackendTxnCfg()
  {
    // No implementation required.
  }



  /**
   * Returns the transaction cancellation handler.
   *
   * @return The the transaction cancellation handler.
   */
  public CancellationHandler getCancellationHandler()
  {
    return cancellationHandler;
  }



  /**
   * Returns the transaction lock timeout in milli-seconds (0 means no
   * limit).
   *
   * @return The transaction lock timeout in milli-seconds (0 means no
   *         limit).
   */
  public int getLockTimeout()
  {
    return lockTimeout;
  }



  /**
   * Sets the transaction cancellation handler.
   *
   * @param handler
   *          The transaction cancellation handler.
   * @return This backend transaction configuration.
   */
  public BackendTxnCfg setCancellationHandler(CancellationHandler handler)
  {
    if (handler == null)
    {
      throw new NullPointerException("null cancellation handler");
    }

    this.cancellationHandler = handler;
    return this;
  }



  /**
   * Sets the transaction lock timeout in milli-seconds (0 means no
   * limit).
   *
   * @param lockTimeout
   *          The transaction lock timeout in milli-seconds (0 means
   *          no limit).
   * @return This backend transaction configuration.
   */
  public BackendTxnCfg setLockTimeout(int lockTimeout)
  {
    this.lockTimeout = (lockTimeout < 0) ? 0 : lockTimeout;
    return this;
  }
}
