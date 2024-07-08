/*
 * Copyright (c) 2002 JSON.org (now "Public Domain")
 * This is NOT property of REAndroid
 * This package is renamed from org.json.* to avoid class conflict when used on anroid platforms
*/
package com.reandroid.json;


import com.abdurazaaqmohammed.AntiSplit.main.Base64;

public class JsonUtil {

    public static byte[] parseBase64(String text){
        if(text == null || !text.startsWith(JSONItem.MIME_BIN_BASE64)){
            return null;
        }
        text = text.substring(JSONItem.MIME_BIN_BASE64.length());
        try{
            return Base64.getUrlDecoder().decode(text);
        }catch (Throwable throwable){
            throw new JSONException(throwable);
        }
    }

}
