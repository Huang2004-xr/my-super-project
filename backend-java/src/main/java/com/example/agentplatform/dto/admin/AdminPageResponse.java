package com.example.agentplatform.dto.admin;

import java.util.List;

public class AdminPageResponse<T> {
    public List<T> items;
    public long total;
    public int page;
    public int size;
}