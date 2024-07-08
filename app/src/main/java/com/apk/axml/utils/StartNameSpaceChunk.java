package com.apk.axml.utils;

import java.io.IOException;

public class StartNameSpaceChunk extends Chunk<StartNameSpaceChunk.H> {

    public StartNameSpaceChunk(Chunk parent) {
        super(parent);
    }

    public static class H extends Chunk.NodeHeader{
        public H() {
            super(ChunkType.XmlStartNamespace);
            size = 0x18;
        }
    }

    public String prefix;
    public String uri;

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null,prefix));
        w.write(stringIndex(null,uri));
    }

}