package com.sunnybear.library.preferences;

import java.lang.reflect.Type;

/**
 * @author Orhan Obut
 */
interface Parser {

    <T> T fromJson(String content, Type type) throws Exception;

    String toJson(Object body);
    
}
