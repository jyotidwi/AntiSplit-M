package com.apk.axml.utils;

import android.annotation.TargetApi;
import android.os.Build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

public class StringBlock {

    int[] m_styleOffsets;
    private int[] m_stringOffsets;
    private byte[] m_strings;
    private boolean m_isUTF8;
    private static final int CHUNK_TYPE = 0x001C0001;
    private static final int UTF8_FLAG = 0x00000100;

    public static StringBlock read(IntReader reader) throws IOException {
        ChunkUtil.readCheckType(reader, CHUNK_TYPE);
        int chunkSize = reader.readInt();
        int stringCount = reader.readInt();
        int styleOffsetCount = reader.readInt();
        int flags = reader.readInt();
        int stringsOffset = reader.readInt();
        int stylesOffset = reader.readInt();

        StringBlock block = new StringBlock();
        block.m_isUTF8 = (flags & UTF8_FLAG) != 0;
        block.m_stringOffsets = reader.readIntArray(stringCount);
        if (styleOffsetCount != 0) {
            block.m_styleOffsets = reader.readIntArray(styleOffsetCount);
        }
        {
            int size = ((stylesOffset == 0) ? chunkSize : stylesOffset) - stringsOffset;
            block.m_strings = new byte[size];
            reader.readFully(block.m_strings);
        }
        if (stylesOffset != 0) {
            int size = (chunkSize - stylesOffset);
            block.m_strings = new byte[size];
            reader.readFully(block.m_strings);
        }

        return block;
    }

    public String getString(int index) {
        if (index < 0 || m_stringOffsets == null || index >= m_stringOffsets.length) {
            return null;
        }
        int offset = m_stringOffsets[index];
        int length;
        int[] val;
        if (m_isUTF8) {
            val = getUtf8(m_strings, offset);
            offset = val[0];
        } else {
            val = getUtf16(m_strings, offset);
            offset += val[0];
        }
        length = val[1];
        return decodeString(offset, length);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String decodeString(int offset, int length) {
        try {
            return (m_isUTF8 ? StandardCharsets.UTF_8.newDecoder() : StandardCharsets.UTF_16LE.newDecoder()).decode(
                    ByteBuffer.wrap(m_strings, offset, length)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static int getShort(byte[] array, int offset) {
        return (array[offset + 1] & 0xff) << 8 | array[offset] & 0xff;
    }

    private static int[] getUtf8(byte[] array, int offset) {
        int val = array[offset];
        int length;
        if ((val & 0x80) != 0) {
            offset += 2;
        } else {
            offset += 1;
        }
        val = array[offset];
        if ((val & 0x80) != 0) {
            offset += 2;
        } else {
            offset += 1;
        }
        length = 0;
        while (array[offset + length] != 0) {
            length++;
        }
        return new int[] {
                offset,
                length
        };
    }

    private static int[] getUtf16(byte[] array, int offset) {
        int val = (array[offset + 1] & 0xff) << 8 | array[offset] & 0xff;
        if (val == 0x8000) {
            int heigh = (array[offset + 3] & 0xFF) << 8;
            int low = (array[offset + 2] & 0xFF);
            return new int[]{4, (heigh + low) * 2};
        }
        return new int[] {
                2, val * 2
        };
    }

    public int find(String string) {
        if (string == null) {
            return -1;
        }
        for (int i=0; i!=m_stringOffsets.length; ++i) {
            int offset = m_stringOffsets[i];
            int length = getShort(m_strings, offset);
            if (length != string.length()) {
                continue;
            }
            int j = 0;
            for (;j !=length; ++j) {
                offset+=2;
                if (string.charAt(j) != getShort(m_strings, offset)) {
                    break;
                }
            }
            if (j == length) {
                return i;
            }
        }
        return -1;
    }

    private StringBlock() {
    }

}