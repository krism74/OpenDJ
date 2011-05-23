/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt
 * or http://forgerock.org/license/CDDLv1.0.html.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at
 * trunk/opendj3/legal-notices/CDDLv1_0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the fields enclosed
 * by brackets "[]" replaced with your own identifying information:
 *      Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 *
 *
 *      Copyright 2010 Sun Microsystems, Inc.
 */

package org.forgerock.opendj.ldap.responses;



import org.forgerock.opendj.ldap.*;
import org.forgerock.opendj.ldap.requests.ExtendedRequest;



/**
 * This class provides a skeletal implementation of the
 * {@code ExtendedResultDecoder} interface, to minimize the effort required to
 * implement this interface.
 *
 * @param <S>
 *          The type of result.
 */
public abstract class AbstractExtendedResultDecoder<S extends ExtendedResult>
    implements ExtendedResultDecoder<S>
{
  /**
   * Creates a new abstract extended result decoder.
   */
  protected AbstractExtendedResultDecoder()
  {
    // Nothing to do.
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public S adaptDecodeException(final DecodeException exception)
      throws NullPointerException
  {
    final S adaptedResult = newExtendedErrorResult(ResultCode.PROTOCOL_ERROR,
        "", exception.getMessage());
    adaptedResult.setCause(exception.getCause());
    return adaptedResult;
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public <R extends ExtendedResult> ResultHandler<S> adaptExtendedResultHandler(
      final ExtendedRequest<R> request,
      final ResultHandler<? super R> resultHandler, final DecodeOptions options)
  {
    return new ResultHandler<S>()
    {

      @Override
      public void handleErrorResult(final ErrorResultException error)
      {
        final Result result = error.getResult();
        final R adaptedResult = request.getResultDecoder()
            .newExtendedErrorResult(result.getResultCode(),
                result.getMatchedDN(), result.getDiagnosticMessage());
        adaptedResult.setCause(result.getCause());
        resultHandler.handleErrorResult(ErrorResultException
            .wrap(adaptedResult));
      }



      @Override
      public void handleResult(final S result)
      {
        try
        {
          final R adaptedResult = request.getResultDecoder()
              .decodeExtendedResult(result, options);
          resultHandler.handleResult(adaptedResult);
        }
        catch (final DecodeException e)
        {
          final R adaptedResult = request.getResultDecoder()
              .adaptDecodeException(e);
          resultHandler.handleErrorResult(ErrorResultException
              .wrap(adaptedResult));
        }
      }

    };
  }



  /**
   * {@inheritDoc}
   */
  @Override
  public abstract S decodeExtendedResult(ExtendedResult result,
      DecodeOptions options) throws DecodeException;



  /**
   * {@inheritDoc}
   */
  @Override
  public abstract S newExtendedErrorResult(ResultCode resultCode,
      String matchedDN, String diagnosticMessage) throws NullPointerException;

}
