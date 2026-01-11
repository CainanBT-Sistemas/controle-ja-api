package com.cainanbt.softwares.controleja.services;

public interface UserLogsService {
    void createInfoLog(String description);
    void createWarnLog(String description);
    void createErrorLog(String description,String cause,String errorMessage,String errorStak);
}
