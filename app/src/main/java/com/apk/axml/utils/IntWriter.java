package com.apk.axml.utils;

import java.io.IOException;
import java.io.OutputStream;

public class IntWriter {

    private final OutputStream os;
    private final boolean bigEndian = false;
    private int pos=0;

    public IntWriter(OutputStream os){
        this.os=os;
    }

    public void write(byte b) throws IOException {
        os.write(b);
        pos+=1;
    }

    public void write(short s) throws IOException {
        if (!bigEndian){
            os.write(s&0xff);
            os.write((s>>>8)&0xff);
        }else{
            os.write((s>>>8)&0xff);
            os.write(s&0xff);
        }
        pos+=2;
    }

    public void write(char x) throws IOException {
        write((short)x);
    }

    public void write(int x) throws IOException {
        if (!bigEndian){
            os.write(x&0xff);
            x>>>=8;
            os.write(x&0xff);
            x>>>=8;
            os.write(x&0xff);
            x>>>=8;
            os.write(x&0xff);
        }else{
            throw new RuntimeException();
        }
        pos+=4;
    }

    public void close() throws IOException {
        os.close();
    }

    public int getPos() {
        return pos;
    }

}