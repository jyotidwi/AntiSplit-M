package com.apk.axml;

import android.annotation.TargetApi;
import android.os.Build;

import com.apk.axml.utils.AXmlResourceParser;
import com.apk.axml.utils.TypedValue;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/*
 * Created by APK Explorer & Editor <apkeditor@protonmail.com> on January 22, 2023
 * Based on the original work of @hzw1199 (https://github.com/hzw1199/xml2axml/)
 * & @WindySha (https://github.com/WindySha/Xpatch)
 */
public class aXMLDecoder {

	public aXMLDecoder() {
	}

	@TargetApi(Build.VERSION_CODES.KITKAT)
	public String decode(InputStream inputStream) throws XmlPullParserException, IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		PrintStream printStream = new PrintStream(os);
		AXmlResourceParser parser = new AXmlResourceParser();
		parser.open(inputStream);
		StringBuilder indent = new StringBuilder(10);
		final String indentStep = "	";
		while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
			switch (parser.next()) {
				case XmlPullParser.START_DOCUMENT: {
					log(printStream, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
					break;
				}
				case XmlPullParser.START_TAG: {
					log(printStream, "%s<%s%s", indent,
							getNamespacePrefix(parser.getPrefix()),parser.getName());
					indent.append(indentStep);

					int namespaceCountBefore=parser.getNamespaceCount(parser.getDepth()-1);
					int namespaceCount=parser.getNamespaceCount(parser.getDepth());
					for (int i=namespaceCountBefore; i!=namespaceCount; ++i) {
						log(printStream, "%sxmlns:%s=\"%s\"",
								indent,
								parser.getNamespacePrefix(i),
								parser.getNamespaceUri(i));
					}

					for (int i=0;i!=parser.getAttributeCount();++i) {
						log(printStream, "%s%s%s=\"%s\"", indent,
								getNamespacePrefix(parser.getAttributePrefix(i)),
								parser.getAttributeName(i),
								getAttributeValue(parser,i));
					}
					log(printStream, "%s>", indent);
					break;
				}
				case XmlPullParser.END_TAG: {
					indent.setLength(indent.length()-indentStep.length());
					log(printStream, "%s</%s%s>", indent,
							getNamespacePrefix(parser.getPrefix()),
							parser.getName());
					break;
				}
				case XmlPullParser.TEXT: {
					log(printStream, "%s%s", indent, parser.getText());
					break;
				}
			}
		}
		byte[] bs = os.toByteArray();
		printStream.close();
		return new String(bs, StandardCharsets.UTF_8);
	}

	private static void log(PrintStream printStream, String format,Object...arguments) {
		printStream.printf(format,arguments);
		printStream.println();
	}
	
	private static String getNamespacePrefix(String prefix) {
		if (prefix == null || prefix.length() == 0) {
			return "";
		}
		return prefix+":";
	}

	private static String getAttributeValue(AXmlResourceParser parser, int index) {
		int type = parser.getAttributeValueType(index);
		int data = parser.getAttributeValueData(index);
		if (type == TypedValue.TYPE_STRING) {
			return parser.getAttributeValue(index);
		}
		if (type == TypedValue.TYPE_ATTRIBUTE) {
			return String.format("?%s%08X", getPackage(data), data);
		}
		if (type == TypedValue.TYPE_REFERENCE) {
			return String.format("@%s%08X", getPackage(data), data);
		}
		if (type == TypedValue.TYPE_FLOAT) {
			return String.valueOf(Float.intBitsToFloat(data));
		}
		if (type == TypedValue.TYPE_INT_HEX) {
			return String.format("0x%08X", data);
		}
		if (type == TypedValue.TYPE_INT_BOOLEAN) {
			return data !=0 ?"true" : "false";
		}
		if (type == TypedValue.TYPE_DIMENSION) {
			return complexToFloat(data) +
					DIMENSION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
		}
		if (type == TypedValue.TYPE_FRACTION) {
			return complexToFloat(data) +
					FRACTION_UNITS[data & TypedValue.COMPLEX_UNIT_MASK];
		}
		if (type >= TypedValue.TYPE_FIRST_COLOR_INT && type <= TypedValue.TYPE_LAST_COLOR_INT) {
			return String.format("#%08X", data);
		}
		if (type >= TypedValue.TYPE_FIRST_INT && type <= TypedValue.TYPE_LAST_INT) {
			return String.valueOf(data);
		}
		return String.format("<0x%X, type 0x%02X>", data, type);
	}
	
	private static String getPackage(int id) {
		if (id >>> 24 == 1) {
			return "android:";
		}
		return "";
	}

	public static float complexToFloat(int complex) {
		return (float)(complex & 0xFFFFFF00)*RADIX_MULTS[(complex >> 4) & 3];
	}
	
	private static final float[] RADIX_MULTS = {
		0.00390625F, 3.051758E-005F, 1.192093E-007F, 4.656613E-010F
	};

	private static final String[] DIMENSION_UNITS = {
		"px", "dip", "sp", "pt", "in", "mm", "", ""
	};

	private static final String[] FRACTION_UNITS = {
		"%", "%p", "", "", "", "", "", ""
	};

}