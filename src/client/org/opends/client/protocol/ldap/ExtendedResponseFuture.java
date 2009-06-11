package org.opends.client.protocol.ldap;

import org.opends.client.api.ExtendedResponseHandler;
import org.opends.common.api.raw.request.RawRequest;
import org.opends.common.api.raw.response.*;

import java.util.concurrent.Semaphore;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Jun 11, 2009 Time: 11:32:30
 * AM To change this template use File | Settings | File Templates.
 */
public class ExtendedResponseFuture extends ResultResponseFuture<RawExtendedResponse>
{
  private final Semaphore invokerLock;
  private final IntermediateResultInvoker intermediateInvoker =
      new IntermediateResultInvoker();

  private ExtendedResponseHandler handler;

  public ExtendedResponseFuture(int messageID, RawRequest orginalRequest,
                              ExtendedResponseHandler extendedResponseHandler,
                              LDAPConnection connection)
  {
    super(messageID, orginalRequest, extendedResponseHandler, connection);
    this.invokerLock = new Semaphore(1);
    this.handler = extendedResponseHandler;
  }

  private class IntermediateResultInvoker implements Runnable
  {
    RawIntermediateResponse intermediateResult;

    public void run()
    {
      handler.handleIntermediateResponse(intermediateResult);
      invokerLock.release();
    }
  }

  @Override
  public synchronized void setResult(RawExtendedResponse result)
  {
    if(latch.getCount() > 0)
    {
      this.result = result;
      latch.countDown();
      if(handler != null)
      {
        try
        {
          invokerLock.acquire();
          invokeHandler(this);
        }
        catch(InterruptedException ie)
        {
          // TODO: What should we do now?
        }
      }
    }
  }

  synchronized void setResult(RawIntermediateResponse intermediateResponse)
  {
    if(latch.getCount() > 0 && handler != null)
    {
      try
      {
        invokerLock.acquire();
        intermediateInvoker.intermediateResult = intermediateResponse;
        invokeHandler(intermediateInvoker);
      }
      catch(InterruptedException ie)
      {
        // TODO: What should we do now?
      }
    }
  }

  @Override
  public synchronized void failure(Throwable failure)
  {
    if(latch.getCount() > 0)
    {
      this.failure = failure;
      latch.countDown();
      if(handler != null)
      {
        try
        {
          invokerLock.acquire();
          invokeHandler(this);
        }
        catch(InterruptedException ie)
        {
          // TODO: What should we do now?
        }
      }
    }
  }

  @Override
  public void run()
  {
    super.run();
    invokerLock.release();
  }
}
