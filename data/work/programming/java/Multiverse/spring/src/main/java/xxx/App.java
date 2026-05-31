//package xxx;
//
//import java.util.List;
//import org.springframework.ai.support.ToolCallbacks;
//import org.springframework.ai.tool.ToolCallback;
//import org.springframework.boot.SpringApplication;
//import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.context.annotation.Bean;
//import xxx.service.Junk;
//
//@SpringBootApplication
//public class App {
//
//  public static void main(String[] args) {
//    SpringApplication.run(App.class, args);
//  }
//
//  // This bean exposes our Junk tools to the MCP framework
//  @Bean
//  public List<ToolCallback> junkToolCallbacks(Junk junk) {
//    // The Junk service is auto-injected by Spring
//    return ToolCallbacks.from(junk);
//  }
//}