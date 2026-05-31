package xxx.com.bytebuddy.agent;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;

public class TestOne {

  public static void main(String[] args) throws Exception {

    new ByteBuddy()
    .redefine(Foo.class)
    .method(ElementMatchers.named("bar"))
    .intercept(MethodDelegation.to(MyInterceptor.class))
    .make()
    .load(Foo.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent())
    .getLoaded();
    
    System.out.println(new Foo().bar());
  }
  
  public class MyInterceptor {
    
    @RuntimeType
    public String bar() {
      return "xxxxxxxx";
    }
  }
}
