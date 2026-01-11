package com.cainanbt.softwares.controleja.exceptions.models;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BadRequestException extends  RuntimeException {
    public BadRequestException(String title,String message){
        super(title+"\ndetail:"+message);
    }
}
