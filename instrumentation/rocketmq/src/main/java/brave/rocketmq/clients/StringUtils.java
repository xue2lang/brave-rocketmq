package brave.rocketmq.clients;

/**
 * @author JoeKerouac
 * @date 2023-01-11 18:22
 */
public class StringUtils {

  static final String EMPTY = "";

  static String getOrEmpty(String obj) {
    if (obj == null) {
      return EMPTY;
    } else {
      return obj;
    }
  }
}
