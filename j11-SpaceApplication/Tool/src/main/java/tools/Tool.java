package tools;
import toolstate.ToolState;
public interface Tool {
    void setToolState(ToolState toolState);
    ToolState getToolState();
    String getToolName();
}
