package com.apk.axml.utils;

import android.text.TextUtils;

import java.io.IOException;

public class AttrChunk extends Chunk<Chunk.EmptyHeader> {

    private final StartTagChunk startTagChunk;
    public String prefix;
    public String name;
    public String namespace;
    public String rawValue;

    public AttrChunk(StartTagChunk startTagChunk) {
        super(startTagChunk);
        this.startTagChunk = startTagChunk;
        header.size=20;
    }

    public final ValueChunk value = new ValueChunk(this);

    @Override
    public void preWrite() {
        value.calc();
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(startTagChunk.stringIndex(null, TextUtils.isEmpty(namespace) ? null : namespace));
        w.write(startTagChunk.stringIndex(namespace,name));
        if (value.type == 0x03)
            w.write(startTagChunk.stringIndex(null,rawValue));
        else
            w.write(-1);
        value.write(w);
    }

}