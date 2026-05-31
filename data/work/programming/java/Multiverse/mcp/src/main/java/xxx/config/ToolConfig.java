package xxx.config;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xxx.service.ToolService;

@Configuration
public class ToolConfig {

  /**
   * Creates a bean that provides a list of tool callbacks for the AI model.
   * This specific bean wraps the McpService as a tool that can be called
   * by the AI.
   *
   * @param toolService The service to be exposed as a callable tool. Spring will
   * automatically inject this dependency.
   * @return A List containing the configured ToolCallback.
   */
  @Bean
  public List<ToolCallback> tools(ToolService toolService) {
    return List.of(ToolCallbacks.from(toolService));
  }
}
