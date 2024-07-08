package com.apk.axml.utils;

import java.io.IOException;

public class EndTagChunk extends Chunk<EndTagChunk.H> {

    public static class H extends Chunk.NodeHeader {

        public H() {
            super(ChunkType.XmlEndElement);
            this.size = 24;
        }
    }

    public final StartTagChunk start;

    public EndTagChunk(Chunk parent, StartTagChunk start) {
        super(parent);
        this.start=start;
    }

    @Override
    public void writeEx(IntWriter w) throws IOException {
        w.write(stringIndex(null, start.namespace));
        w.write(stringIndex(null, start.name));
    }

}