package com.apk.axml.utils;

import android.annotation.TargetApi;
import android.graphics.Color;
import android.os.Build;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValueChunk extends Chunk<Chunk.EmptyHeader> {

    private final AttrChunk attrChunk;
    private String realString;
    final short size = 8;
    final byte res0 = 0;
    byte type = -1;
    int data = -1;

    static class ValPair {
        final int pos;
        final String val;

        @TargetApi(Build.VERSION_CODES.GINGERBREAD)
        public ValPair(Matcher m) {
            int c = m.groupCount();
            for (int i = 1; i <= c; ++i) {
                String s = m.group(i);
                if (s == null || s.isEmpty()) continue;
                pos = i;
                val = s;
                return;
            }
            pos = -1;
            val = m.group();
        }
    }

    final Pattern explicitType = Pattern.compile("^!(?:(string|str|null|)!)?(.*)");
    final Pattern types = Pattern.compile(("^(?:" +
            "(@null)" +
            "|(@\\+?(?:\\w+:)?\\w+/\\w+|@(?:\\w+:)?[0-9a-zA-Z]+)" +
            "|(true|false)" +
            "|([-+]?\\d+)" +
            "|(0x[0-9a-zA-Z]+)" +
            "|([-+]?\\d+(?:\\.\\d+)?)" +
            "|([-+]?\\d+(?:\\.\\d+)?(?:dp|dip|in|px|sp|pt|mm))" +
            "|([-+]?\\d+(?:\\.\\d+)?(?:%))" +
            "|(\\#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8}))" +
            "|(match_parent|wrap_content|fill_parent)" +
            ")$").replaceAll("\\s+", ""));

    public ValueChunk(AttrChunk parent) {
        super(parent);
        header.size = 8;
        this.attrChunk = parent;
    }

    @Override
    public void preWrite() {
        evaluate();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(size);
        w.write(res0);
        if (type == 0x03){
            data = stringIndex(null,realString);
        }
        w.write(type);
        w.write(data);
    }

    public int evalcomplex(String val) {
        int unit;
        int radix;
        int base;
        String num;

        if (val.endsWith("%")) {
            num = getNumber(val,1);
            unit = 0;
        } else if (val.endsWith("dp")) {
            unit = 1;
            num = getNumber(val,2);
        } else if (val.endsWith("dip")) {
            unit = 1;
            num = getNumber(val,3);
        } else if (val.endsWith("sp")) {
            unit = 2;
            num = getNumber(val,2);
        } else if (val.endsWith("px")) {
            unit = 0;
            num = getNumber(val,2);
        } else if (val.endsWith("pt")) {
            unit = 3;
            num = getNumber(val,2);
        } else if (val.endsWith("in")) {
            unit = 4;
            num = getNumber(val,2);
        } else if (val.endsWith("mm")) {
            unit = 5;
            num = getNumber(val,2);
        } else {
            throw new RuntimeException("invalid unit");
        }
        double f = Double.parseDouble(num);
        if (f < 1 && f > -1) {
            base = (int) (f * (1 << 23));
            radix = 3;
        } else if (f < 0x100 && f > -0x100) {
            base = (int) (f * (1 << 15));
            radix = 2;
        } else if (f < 0x10000 && f > -0x10000) {
            base = (int) (f * (1 << 7));
            radix = 1;
        } else {
            base = (int) f;
            radix = 0;
        }
        return (base << 8) | (radix << 4) | unit;
    }

    private static String getNumber(String val, int number) {
        return val.substring(0, val.length() - number);
    }

    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
public void evaluate() {
    Matcher m = explicitType.matcher(attrChunk.rawValue);
    if (m.find()) {
        String t = m.group(1);
        String v = m.group(2);
        if (t == null || t.isEmpty() || t.equals("string") || t.equals("str")) {
            type = 0x03;
            realString = v;
            stringPool().addString(realString);
        } else {
            throw new RuntimeException();
        }
    } else {
        m = types.matcher(attrChunk.rawValue);
        if (m.find()) {
            ValPair vp = new ValPair(m);
            switch (vp.pos) {
                case 1:
                    type = 0x00;
                    data = 0;
                    break;
                case 2:
                    type = 0x01;
                    data = getReferenceResolver().resolve(this, vp.val);
                    break;
                case 3:
                    type = 0x12;
                    data = "true".equalsIgnoreCase(vp.val) ? 1 : 0;
                    break;
                case 4:
                    type = 0x10;
                    try {
                        data = Integer.parseInt(vp.val);
                    } catch (NumberFormatException e) {
                        type = 0x03;
                        realString = vp.val;
                        stringPool().addString(realString);
                    }
                    break;
                case 5:
                    type = 0x11;
                    try {
                        data = Integer.parseInt(vp.val.substring(2), 16);
                    } catch (NumberFormatException e) {
                        type = 0x03;
                        realString = vp.val;
                        stringPool().addString(realString);
                    }
                    break;
                case 6:
                    type = 0x04;
                    try {
                        data = Float.floatToIntBits(Float.parseFloat(vp.val));
                    } catch (NumberFormatException e) {
                        type = 0x03;
                        realString = vp.val;
                        stringPool().addString(realString);
                    }
                    break;
                case 7:
                    type = 0x05;
                    data = evalcomplex(vp.val);
                    break;
                case 8:
                    type = 0x06;
                    data = evalcomplex(vp.val);
                    break;
                case 9:
                    type = 0x1c;
                    data = Color.parseColor(vp.val);
                    break;
                case 10:
                    type = 0x10;
                    data = "wrap_content".equalsIgnoreCase(vp.val) ? -2 : -1;
                    break;
                default:
                    type = 0x03;
                    realString = vp.val;
                    stringPool().addString(realString);
                    break;
            }
        } else {
            type = 0x03;
            realString = attrChunk.rawValue;
            stringPool().addString(realString);
        }
    }
}


}