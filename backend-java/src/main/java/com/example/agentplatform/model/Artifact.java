package com.example.agentplatform.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class Artifact {
    public String type;
    public String title;
    public Map<String, Object> data = new LinkedHashMap<>();
}
