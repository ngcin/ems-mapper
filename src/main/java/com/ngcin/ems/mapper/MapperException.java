package com.ngcin.ems.mapper;

public class MapperException extends RuntimeException{
    public MapperException(String message) {
        super(message);
    }

    public MapperException(String message, Throwable cause) {
        super(message, cause);
    }
}
