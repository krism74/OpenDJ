package org.opends.client.protocol.ldap;

import org.opends.common.api.raw.response.RawResponse;

import java.util.concurrent.Future;
import java.io.IOException;

/**
 * Created by IntelliJ IDEA. User: digitalperk Date: Jun 11, 2009 Time: 2:34:50
 * PM To change this template use File | Settings | File Templates.
 */
public interface ResponseFuture<R extends RawResponse> extends Future<R>
{
  public void abandon() throws IOException;  
}
