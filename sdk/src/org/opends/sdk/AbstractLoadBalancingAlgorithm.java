package org.opends.sdk;

import com.sun.opends.sdk.util.Validator;
import com.sun.opends.sdk.util.StaticUtils;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Dec 15, 2009 Time: 3:49:17
 * PM To change this template use File | Settings | File Templates.
 */
public abstract class AbstractLoadBalancingAlgorithm
    implements LoadBalancingAlgorithm
{
  protected final List<MonitoredConnectionFactory> factoryList;

  protected AbstractLoadBalancingAlgorithm(ConnectionFactory<?>... factories)
  {
    Validator.ensureNotNull(factories);
    factoryList = new ArrayList<MonitoredConnectionFactory>(factories.length);
    for(ConnectionFactory<?> f : factories)
    {
      factoryList.add(new MonitoredConnectionFactory(f));
    }

    new MonitorThread().start();
  }

  protected class MonitoredConnectionFactory
      extends AbstractConnectionFactory<AsynchronousConnection>
      implements ResultHandler<AsynchronousConnection>
  {
    private final ConnectionFactory<?> factory;
    private volatile boolean isOperational;
    private volatile FutureResult<?> pendingConnectFuture;

    private MonitoredConnectionFactory(ConnectionFactory<?> factory)
    {
      this.factory = factory;
      this.isOperational = true;
    }

    public boolean isOperational()
    {
      return isOperational;
    }

    public void handleErrorResult(ErrorResultException error)
    {
      isOperational = false;
    }

    public void handleResult(AsynchronousConnection result)
    {
      isOperational = true;
      // TODO: Notify the server is back up
      result.close();
    }

    public FutureResult<? extends AsynchronousConnection>
      getAsynchronousConnection(
        final ResultHandler<? super AsynchronousConnection> resultHandler)
    {
      ResultHandler handler = new ResultHandler<AsynchronousConnection>()
      {
        public void handleErrorResult(ErrorResultException error)
        {
          isOperational = false;
          if(resultHandler != null)
          {
            resultHandler.handleErrorResult(error);
          }
          if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
          {
            StaticUtils.DEBUG_LOG
                .warning(String
                    .format(
                    "Connection factory " + factory +
                        " is no longer operational: "
                        + error.getMessage()));
          }
        }

        public void handleResult(AsynchronousConnection result)
        {
          isOperational = true;
          if(resultHandler != null)
          {
            resultHandler.handleResult(result);
          }
          if (StaticUtils.DEBUG_LOG.isLoggable(Level.WARNING))
          {
            StaticUtils.DEBUG_LOG
                .warning(String
                    .format(
                    "Connection factory " + factory +
                        " is now operational"));
          }
        }
      };
      return factory.getAsynchronousConnection(handler);
    }
  }

  private class MonitorThread extends Thread
  {
    private MonitorThread()
    {
      super("Connection Factory Health Monitor");
      this.setDaemon(true);
    }

    public void run()
    {
      while(true)
      {
        for(MonitoredConnectionFactory f : factoryList)
        {
          if(!f.isOperational && (f.pendingConnectFuture == null ||
              f.pendingConnectFuture.isDone()))
          {
            if (StaticUtils.DEBUG_LOG.isLoggable(Level.FINEST))
            {
              StaticUtils.DEBUG_LOG
                  .finest(String.format("Attempting connect on factory " + f));
            }
            f.pendingConnectFuture = f.factory.getAsynchronousConnection(f);
          }
        }
        try
        {
          sleep(10000);
        }
        catch (InterruptedException e)
        {
          // Ignore and just go around again...
        }
      }
    }
  }
}
