package com.example.agentplatform.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class ToolCall {
    public String toolName;
    public Map<String, Object> input = new LinkedHashMap<>();
    public Map<String, Object> output = new LinkedHashMap<>();
    public String status;
    public String error;
}
