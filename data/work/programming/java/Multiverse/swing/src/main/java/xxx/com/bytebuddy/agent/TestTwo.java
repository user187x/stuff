package xxx.com.bytebuddy.agent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

public class TestTwo {

  public static void main(String[] args) throws Exception {

    Class<?> redefinedClass = new ByteBuddy()
    .redefine(Foo.class)
    .method(ElementMatchers.named("bar"))
    .intercept(FixedValue.value("Hello World"))
    .make()
    .load(Foo.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent())
    .getLoaded();
    
    System.out.println("Redefined Class " + redefinedClass.getName());
    
    Class<?> clazz = Class.forName("io.xxx.agent.Foo");
    
    Constructor<?> constructor = clazz.getDeclaredConstructor();
    constructor.setAccessible(true);
    
    Object foo = constructor.newInstance();
    
    Method method = foo.getClass().getDeclaredMethod("bar");  
    String name = (String) method.invoke(foo);
    
    System.out.println(name);
    
    System.out.println(new Foo().bar());
  }
}
