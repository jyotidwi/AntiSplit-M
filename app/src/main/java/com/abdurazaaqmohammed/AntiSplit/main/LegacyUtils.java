package com.abdurazaaqmohammed.AntiSplit.main;

import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

public class LegacyUtils {
    public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
    public final static Charset UTF_8 = Charset.forName("UTF-8");
    public final static Charset UTF_16 = Charset.forName("UTF-16LE");
    public static <T,U> T[] copyOfArray(U[] original, int newLength, Class<? extends T[]> newType) {
        @SuppressWarnings("unchecked")
        T[] copy = ((Object)newType == (Object)Object[].class)
                ? (T[]) new Object[newLength]
                : (T[]) Array.newInstance(newType.getComponentType(), newLength);
        System.arraycopy(original, 0, copy, 0,
                Math.min(original.length, newLength));
        return copy;
    }
    public static String byteArrayToString(byte[] byteArray, int offset, int length, Charset charset)  {
        CharsetDecoder decoder = charset.newDecoder();
        ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray, offset, length);
        CharBuffer charBuffer = null;
        try {
            charBuffer = decoder.decode(byteBuffer);
        } catch (CharacterCodingException e) {
            throw new RuntimeException(e);
        }

        return charBuffer.toString();
    }

    public static byte[] stringToByteArray(Charset charset, String str) {
        CharsetEncoder encoder = charset.newEncoder();

        try {
            ByteBuffer byteBuffer = encoder.encode(CharBuffer.wrap(str));
            byte[] byteArray = new byte[byteBuffer.remaining()];
            byteBuffer.get(byteArray);

            return byteArray;
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    public static byte[] copyOfByteArray(byte[] original, int newLength) {
        byte[] copy = new byte[newLength];
        System.arraycopy(original, 0, copy, 0, Math.min(original.length, newLength));
        return copy;
    }

}
