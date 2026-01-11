package com.cainanbt.softwares.controleja.utils;

import java.util.UUID;

public interface ID {
    static UUID generate(){
        return UUID.randomUUID();
    }

    static String toString(UUID id){
        return id.toString();
    }

    static UUID toUUID(String id){
        return UUID.fromString(id);
    }
}
