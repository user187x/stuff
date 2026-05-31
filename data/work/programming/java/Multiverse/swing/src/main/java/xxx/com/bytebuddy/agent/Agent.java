package xxx.com.bytebuddy.agent;

import java.lang.instrument.Instrumentation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;;

public class Agent {
  
  public static void premain(String arguments, Instrumentation instrumentation) throws Exception {

    new AgentBuilder.Default()
    .with(new AgentBuilder.InitializationStrategy.SelfInjection.Eager())
    .type(ElementMatchers.nameContains("PitBull"))
    .transform((builder, typeDescription, classLoader, module, protectionDomain) ->
      builder
      .defineMethod("getAge", int.class, Visibility.PUBLIC)
      .intercept(MethodDelegation.to(PitBullInterceptor.class))
      .defineMethod("toString", int.class, Visibility.PUBLIC)
      .intercept(MethodDelegation.to(PitBullInterceptor.class))
    )
    .installOn(instrumentation);
    
    
    // Get Class instance
    Class<?> clazz = Class.forName("io.xxx.PitBull");

    // Get the private constructor.
    Constructor<?> cons = clazz.getDeclaredConstructor();

    // Since it is private, make it accessible.
    cons.setAccessible(true);

    // Create new object. 
    Object pitBull = cons.newInstance();
    
    System.out.println("From the agent : " + pitBull);
    
    System.out.println("From the agent : ");
    Arrays.asList(pitBull.getClass().getDeclaredMethods()).stream()
    .map(Method::getName)
    .forEach(System.out::println);
    
    Method method = pitBull.getClass().getDeclaredMethod("getName");  
    String name = (String) method.invoke(pitBull);
    
    System.out.println(name);
  } 
  
  public static class PitBullInterceptor {
    
    private static int age = 3;
    
    public static int getAge() {
      return age;
    }
    
    @Override
    public String toString() {
      return "Intercept " + getClass().getName() + '@' + Integer.toHexString(hashCode());
    }
  }
}
