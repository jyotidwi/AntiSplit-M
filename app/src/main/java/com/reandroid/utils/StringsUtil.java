/*
 *  Copyright (C) 2022 github.com/REAndroid
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.reandroid.utils;

import java.util.Collection;
import java.util.Iterator;

public class StringsUtil {

    public static final String EMPTY = ObjectsUtil.of("");

    public static boolean containsUpperAZ(String text){
        if(isEmpty(text)){
            return false;
        }
        int length = text.length();
        for(int i = 0; i < length; i++){
            if(isUpperAZ(text.charAt(i))){
                return true;
            }
        }
        return false;
    }

    public static boolean isUpperAZ(char ch){
        return ch >= 'A' && ch <= 'Z';
    }


    public static int compare(String[] strings1, String[] strings2){
        if(strings1 == strings2){
            return 0;
        }
        if(strings1 == null){
            return 1;
        }
        if(strings2 == null){
            return -1;
        }
        int length1 = strings1.length;
        int length2 = strings2.length;
        if(length1 == 0 && length2 == 0){
            return 0;
        }
        if(length1 == 0){
            return 1;
        }
        if(length2 == 0){
            return -1;
        }
        int length = length1;
        if(length > length2){
            length = length2;
        }
        for(int i = 0; i < length; i++){
            String s1 = strings1[i];
            String s2 = strings2[i];
            int c = compareStrings(s1, s2);
            if(c == 0){
                continue;
            }
            if(length1 == length2 || i < length -1){
                return c;
            }
        }
        return Integer.compare(length1, length2);
    }
    public static String remove(String text, char[] search) {
        if(text == null){
            return null;
        }
        int length = text.length();
        if(length == 0){
            return null;
        }
        int count = countChar(text, search, false);
        if(count == 0){
            return text;
        }
        StringBuilder builder = new StringBuilder(length - count);
        for(int i = 0; i < length; i++){
            char ch = text.charAt(i);
            if(!contains(search, ch)){
                builder.append(ch);
            }
        }
        if(builder.length() == 0){
            return null;
        }
        return builder.toString();
    }
    public static String[] removeEmpty(String[] strings){
        if(strings == null){
            return null;
        }
        int length = strings.length;
        if(length == 0){
            return strings;
        }
        int count = 0;
        for(int i = 0; i < length; i++){
            if(isEmpty(strings[i])){
                strings[i] = null;
            }else {
                count ++;
            }
        }
        if(count == length){
            return strings;
        }
        String[] results = new String[count];
        int index = 0;
        for (String str : strings) {
            if (str != null) {
                results[index] = str;
                index++;
            }
        }
        return results;
    }
    public static String[] split(String text, char search){
        return split(text, search, true);
    }
    public static String[] split(String text, char[] search){
        return split(text, search, true);
    }
    public static String[] split(String text, char[] search, boolean skipConsecutive) {
        if(text == null || text.length() == 0){
            return new String[0];
        }
        int count = countChar(text, search, skipConsecutive);
        if(count == 0){
            return new String[]{text};
        }
        String[] results = new String[count + 1];
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean previousMatch = false;
        int length = text.length();
        for(int i = 0; i < length; i++){
            char ch = text.charAt(i);
            if(contains(search, ch)){
                if(!previousMatch || !skipConsecutive){
                    previousMatch = true;
                    results[index] = builder.toString();
                    builder = new StringBuilder();
                    index++;
                }
            }else {
                previousMatch = false;
                builder.append(ch);
            }
        }
        if(index < results.length){
            results[index] = builder.toString();
        }
        return results;
    }
    public static String[] split(String text, char search, boolean skipConsecutive) {
        if(text == null || text.length() == 0){
            return new String[0];
        }
        int count = countChar(text, search, skipConsecutive);
        if(count == 0){
            return new String[]{text};
        }
        String[] results = new String[count + 1];
        int index = 0;
        StringBuilder builder = new StringBuilder();
        boolean previousMatch = false;
        int length = text.length();
        for(int i = 0; i < length; i++){
            char ch = text.charAt(i);
            if(ch == search){
                if(!previousMatch || !skipConsecutive){
                    previousMatch = true;
                    results[index] = builder.toString();
                    builder = new StringBuilder();
                    index++;
                }
            }else {
                previousMatch = false;
                builder.append(ch);
            }
        }
        if(index < results.length){
            results[index] = builder.toString();
        }
        return results;
    }
    public static int countChar(String text, char[] search, boolean skipConsecutive) {
        if(text == null || text.length() == 0){
            return 0;
        }
        int length = text.length();
        int result = 0;
        boolean previousMatch = false;
        for(int i = 0; i < length; i++){
            char ch = text.charAt(i);
            if(contains(search, ch)){
                if(!previousMatch || !skipConsecutive){
                    result ++;
                    previousMatch = true;
                }
            }else {
                previousMatch = false;
            }
        }
        return result;
    }
    public static int countChar(String text, char search, boolean skipConsecutive) {
        if(text == null || text.length() == 0){
            return 0;
        }
        int length = text.length();
        int result = 0;
        boolean previousMatch = false;
        for(int i = 0; i < length; i++){
            char ch = text.charAt(i);
            if(ch == search){
                if(!previousMatch || !skipConsecutive){
                    result ++;
                    previousMatch = true;
                }
            }else {
                previousMatch = false;
            }
        }
        return result;
    }

    private static boolean contains(char[] chars, char ch){
        for(char c : chars){
            if(c == ch){
                return true;
            }
        }
        return false;
    }

    public static String toString(Collection<?> collection){
        return toString(collection, MAX_STRING_APPEND);
    }
    public static String toString(Collection<?> collection, int max) {
        return toString(collection.iterator(), max, collection.size());
    }
    public static String toString(Iterator<?> iterator, int max, int size) {
        return toString(", ", iterator, max, size);
    }
    public static String toString(String separator, Iterator<?> iterator, int max, int size) {
        if(iterator == null){
            return "null";
        }
        if(max < 0 && size >= 0){
            max = size;
        }
        int count = 0;
        StringBuilder elements = new StringBuilder();
        while (iterator.hasNext() && (max < 0 || count < max)){
            if(count != 0){
                elements.append(separator);
            }
            elements.append(iterator.next());
            count ++;
        }
        if(max < 0){
            size = count;
        }
        StringBuilder builder = new StringBuilder(elements.length() + 20);
        if(size >= 0){
            builder.append("size=");
            builder.append(size);
            builder.append(' ');
        }
        builder.append('[');
        builder.append(elements);
        if(count < size){
            builder.append(" ... ");
        }
        builder.append(']');
        return builder.toString();
    }
    public static String toString(Object[] elements){
        return toString(elements, MAX_STRING_APPEND);
    }
    public static String toString(Object[] elements, int max){
        if(elements == null){
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("length=");
        builder.append(elements.length);
        builder.append(" [");
        if(max < 0 || max > elements.length){
            max = elements.length;
        }
        int count;
        for (count = 0; count < max; count++){
            if(count != 0){
                builder.append(", ");
            }
            builder.append(elements[count]);
        }
        if(count < elements.length){
            builder.append(" ... ");
        }
        builder.append(']');
        return builder.toString();
    }
    public static String emptyToNull(String text){
        if(isEmpty(text)){
            return null;
        }
        return text;
    }
    public static boolean isEmpty(String text){
        return text == null || text.length() == 0;
    }
    public static String toUpperCase(String str){
        if(str == null || str.length() == 0){
            return str;
        }
        char[] chars = str.toCharArray();
        boolean changed = false;
        for(int i = 0; i < chars.length; i++){
            char ch = chars[i];
            char lower = toUpperCase(ch);
            if(ch == lower){
                continue;
            }
            chars[i] = lower;
            changed = true;
        }
        if(!changed){
            return str;
        }
        return new String(chars);
    }
    public static char toUpperCase(char ch){
        if(ch > 'z' || ch < 'a'){
            return ch;
        }
        int i = ch - 'a';
        return (char) (i + 'A');
    }
    public static String toLowercase(String str){
        char[] chars = str.toCharArray();
        boolean changed = false;
        for(int i = 0; i < chars.length; i++){
            char ch = chars[i];
            char lower = toLowercase(ch);
            if(ch == lower){
                continue;
            }
            chars[i] = lower;
            changed = true;
        }
        if(!changed){
            return str;
        }
        return new String(chars);
    }
    public static char toLowercase(char ch){
        if(ch > 'Z' || ch < 'A'){
            return ch;
        }
        int i = ch - 'A';
        return (char) (i + 'a');
    }

    public static String formatNumber(long number, long maximumValue){
        int minLength = Long.toString(maximumValue).length();
        return trailZeros(number, minLength);
    }
    public static String trailZeros(long number, int minLength){
        boolean negative = false;
        if(number < 0){
            negative = true;
            number = -number;
        }
        String text = Long.toString(number);
        int count = minLength - text.length();
        text = append(text, '0', count, true);
        if(negative){
            text = "-" + text;
        }
        return text;
    }


    public static String append(String text, char ch, int count, boolean prefix){
        StringBuilder builder = new StringBuilder(text.length() + count);
        if(!prefix){
            builder.append(text);
        }
        for(int i = 0; i < count; i++){
            builder.append(ch);
        }
        if(prefix){
            builder.append(text);
        }
        return builder.toString();
    }
    public static int compareToString(Object obj1, Object obj2) {
        return compareStrings(obj1 == null ? null : obj1.toString(),
                obj2 == null ? null : obj2.toString());
    }
    public static int compareStrings(String s1, String s2) {
        return CompareUtil.compare(s1, s2);
    }

    public static String join(Iterator<?> iterator, Object separator){
        StringBuilder builder = new StringBuilder();
        boolean appendOnce = false;
        while (iterator.hasNext()){
            if(appendOnce){
                builder.append(separator);
            }
            builder.append(iterator.next());
            appendOnce = true;
        }
        if(appendOnce){
            return builder.toString();
        }
        return EMPTY;
    }

    private static final int MAX_STRING_APPEND = 5;
}
