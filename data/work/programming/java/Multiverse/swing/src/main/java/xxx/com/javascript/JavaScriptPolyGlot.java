package xxx.com.javascript;

import org.graalvm.polyglot.*;
import org.graalvm.polyglot.proxy.*;

public class JavaScriptPolyGlot {

  static String JS_CODE = "(function myFun(param){console.log('hello '+param);})";

  public static void main(String[] args) {
    System.out.println("Hello Java!");
    try (Context context = Context.create()) {
      Value value = context.eval("js", JS_CODE);
      value.execute(args[0]);
    }
  }
}