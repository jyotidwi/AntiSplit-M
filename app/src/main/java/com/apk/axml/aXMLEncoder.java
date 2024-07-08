package com.apk.axml;

import android.content.Context;

import com.apk.axml.utils.Chunk;
import com.apk.axml.utils.IntWriter;
import com.apk.axml.utils.StringPoolChunk;
import com.apk.axml.utils.TagChunk;
import com.apk.axml.utils.XmlChunk;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on January 22, 2023
 * Based on the original work of @hzw1199 (https://github.com/hzw1199/xml2axml/)
 * & @WindySha (https://github.com/WindySha/Xpatch)
 */
public class aXMLEncoder {

    public static class Config {
        public static final StringPoolChunk.Encoding encoding = StringPoolChunk.Encoding.UNICODE;
        public static final int defaultReferenceRadix = 16;
    }

    public byte[] encodeString(Context context, String xml) throws XmlPullParserException, IOException {
        XmlPullParserFactory f = XmlPullParserFactory.newInstance();
        f.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);
        XmlPullParser p = f.newPullParser();
        p.setInput(new StringReader(xml));
        return encode(context,p);
    }

    public static byte[] encode(Context context, XmlPullParser p) throws XmlPullParserException, IOException {
        XmlChunk chunk = new XmlChunk(context);
        TagChunk current = null;
        for (int i=p.getEventType(); i!=XmlPullParser.END_DOCUMENT; i=p.next()) {
            switch (i){
                case XmlPullParser.START_TAG:
                    current = new TagChunk(current == null ? chunk : current, p);
                    break;
                case XmlPullParser.END_TAG:
                    Chunk c = current.getParent();
                    current = c instanceof TagChunk?(TagChunk)c:null;
                    break;
                default:
                    break;

            }
        }
        ByteArrayOutputStream os=new ByteArrayOutputStream();
        IntWriter w = new IntWriter(os);
        chunk.write(w);
        w.close();
        return os.toByteArray();
    }

}