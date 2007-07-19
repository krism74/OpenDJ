import org.opends.quicksetup.util.InProcessServerController;
import org.opends.quicksetup.util.ServerHealthChecker;
import org.opends.quicksetup.Installation;
import org.opends.quicksetup.ApplicationException;
import org.opends.server.types.InitializationException;
import org.opends.server.config.ConfigException;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;

/**
 */
public class Scratch {

  public static void main(String[] args) {
    Installation i = new Installation("/home/ksuter/dev/test/OpenDS");
    ServerHealthChecker shc = new ServerHealthChecker(i);
    try {
      shc.checkServer();
      List<String> msgs = shc.getProblemMessages();
      if (msgs != null && msgs.size() > 0) {
        for(String msg : msgs) {
          System.out.println(msg);
        }
      } else {
        System.out.println("Server is healthy");
      }
    } catch (ApplicationException e) {
      e.printStackTrace();
    }
  }
}
