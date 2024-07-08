package com.apk.axml.utils;

enum ChunkType {

    Null(0x0000, -1),
    StringPool(0x0001, 28),
    Xml(0x0003, 8),
    XmlStartNamespace(0x0100, 0x10),
    XmlEndNamespace(0x0101, 0x10),
    XmlStartElement(0x0102, 0x10),
    XmlEndElement(0x0103, 0x10),
    XmlResourceMap(0x0180, 8);

    public final short type;
    public final short headerSize;

    ChunkType(int type, int headerSize) {
        this.type = (short) type;
        this.headerSize = (short) headerSize;
    }

}