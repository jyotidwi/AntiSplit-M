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
package com.reandroid.archive;

import com.reandroid.archive.block.ApkSignatureBlock;
import com.reandroid.archive.io.ZipInput;
import com.reandroid.archive.model.CentralFileDirectory;
import com.reandroid.archive.model.LocalFileDirectory;
import com.reandroid.utils.ObjectsUtil;
import com.reandroid.utils.collection.ArrayIterator;
import com.reandroid.utils.collection.CollectionUtil;
import com.reandroid.utils.collection.ComputeIterator;

import java.io.Closeable;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

public abstract class Archive<T extends ZipInput> implements Closeable {

    private final T zipInput;
    private final ArchiveEntry[] entryList;
    private final ApkSignatureBlock apkSignatureBlock;

    public Archive(T zipInput) throws IOException {
        this.zipInput = zipInput;
        CentralFileDirectory cfd = new CentralFileDirectory();
        cfd.visit(zipInput);
        LocalFileDirectory lfd = new LocalFileDirectory(cfd);
        lfd.visit(zipInput);
        this.entryList  = lfd.buildArchiveEntryList();
        this.apkSignatureBlock = lfd.getApkSigBlock();
    }

    public ZipEntryMap createZipEntryMap(){
        return new ZipEntryMap(mapEntrySource());
    }

    public InputSource[] getInputSources(){
        // TODO: make InputSource for directory entry
        return getInputSources(ArchiveEntry::isFile);
    }
    public InputSource[] getInputSources(com.abdurazaaqmohammed.AntiSplit.main.Predicate<? super ArchiveEntry> filter){
        Iterator<InputSource> iterator = ComputeIterator.of(iterator(filter), this::createInputSource);
        List<InputSource> sourceList = CollectionUtil.toList(iterator);
        return sourceList.toArray(new InputSource[sourceList.size()]);
    }


    public LinkedHashMap<String, InputSource> mapEntrySource(){
        LinkedHashMap<String, InputSource> map = new LinkedHashMap<>(size());
        Iterator<ArchiveEntry> iterator = getFiles();
        while (iterator.hasNext()){
            ArchiveEntry entry = iterator.next();
            InputSource inputSource = createInputSource(entry);
            map.put(inputSource.getAlias(), inputSource);
        }
        return map;
    }

    public T getZipInput() {
        return zipInput;
    }

    abstract InputSource createInputSource(ArchiveEntry entry);
    public InputSource getEntrySource(String path){
        if(path == null){
            return null;
        }
        ArchiveEntry[] entryList = this.entryList;
        for (ArchiveEntry entry : entryList) {
            if (entry.isDirectory()) {
                continue;
            }
            if (path.equals(entry.getName())) {
                return createInputSource(entry);
            }
        }
        return null;
    }

    public Iterator<ArchiveEntry> getFiles() {
        return iterator(ArchiveEntry::isFile);
    }
    public Iterator<ArchiveEntry> iterator() {
        return new ArrayIterator<>(entryList);
    }
    public Iterator<ArchiveEntry> iterator(com.abdurazaaqmohammed.AntiSplit.main.Predicate<? super ArchiveEntry> filter) {
        return new ArrayIterator<>(entryList, filter);
    }
    public int size(){
        return entryList.length;
    }
    public ApkSignatureBlock getApkSignatureBlock() {
        return apkSignatureBlock;
    }

    @Override
    public void close() throws IOException {
        this.zipInput.close();
    }

    public static<T1 extends InputSource> PathTree<T1> buildPathTree(T1[] inputSources){
        PathTree<T1> root = PathTree.newRoot();
        for (T1 item : inputSources) {
            root.add(item.getAlias(), item);
        }
        return root;
    }

    public static Date dosToJavaDate(long dosTime) {
        final Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, (int) ((dosTime >> 25) & 0x7f) + 1980);
        cal.set(Calendar.MONTH, (int) ((dosTime >> 21) & 0x0f) - 1);
        cal.set(Calendar.DATE, (int) (dosTime >> 16) & 0x1f);
        cal.set(Calendar.HOUR_OF_DAY, (int) (dosTime >> 11) & 0x1f);
        cal.set(Calendar.MINUTE, (int) (dosTime >> 5) & 0x3f);
        cal.set(Calendar.SECOND, (int) (dosTime << 1) & 0x3e);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
    public static long javaToDosTime(long javaTime) {
        return javaToDosTime(new Date(javaTime));
    }
    public static long javaToDosTime(Date date) {
        if(date == null || date.getTime() == 0){
            return 0;
        }
        GregorianCalendar cal = new GregorianCalendar();
        cal.setTime(date);
        int year = cal.get(Calendar.YEAR);
        if(year < 1980){
            return 0;
        }
        int result = cal.get(Calendar.DATE);
        result = (cal.get(Calendar.MONTH) + 1 << 5) | result;
        result = ((cal.get(Calendar.YEAR) - 1980) << 9) | result;
        int time = cal.get(Calendar.SECOND) >> 1;
        time = (cal.get(Calendar.MINUTE) << 5) | time;
        time = (cal.get(Calendar.HOUR_OF_DAY) << 11) | time;
        return ((long) result << 16) | time;
    }


    public static final int STORED = ObjectsUtil.of(0);
    public static final int DEFLATED = ObjectsUtil.of(8);
}
