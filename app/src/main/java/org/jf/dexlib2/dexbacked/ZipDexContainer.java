/*
 * Copyright 2016, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 * Neither the name of Google Inc. nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.jf.dexlib2.dexbacked;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile.NotADexFile;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.MultiDexContainer;
import org.jf.dexlib2.util.DexUtil;
import org.jf.dexlib2.util.DexUtil.InvalidFile;
import org.jf.dexlib2.util.DexUtil.UnsupportedFile;
import org.jf.util.io.ByteStreams;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Represents a zip file that contains dex files (i.e. an apk or jar file)
 */
public class ZipDexContainer implements MultiDexContainer<DexBackedDexFile> {

    private final File zipFilePath;
     private final Opcodes opcodes;

    /**
     * Constructs a new ZipDexContainer for the given zip file
     *
     * @param zipFilePath The path to the zip file
     */
    public ZipDexContainer( File zipFilePath,  Opcodes opcodes) {
        this.zipFilePath = zipFilePath;
        this.opcodes = opcodes;
    }

    /**
     * Gets a list of the names of dex files in this zip file.
     *
     * @return A list of the names of dex files in this zip file
     */
    
    @Override
    public List<String> getDexEntryNames() throws IOException {
        List<String> entryNames = new ArrayList<>();
        ZipFile zipFile = getZipFile();
        try {
            Enumeration<? extends ZipEntry> entriesEnumeration = zipFile.entries();

            while (entriesEnumeration.hasMoreElements()) {
                ZipEntry entry = entriesEnumeration.nextElement();

                if (!isDex(zipFile, entry)) {
                    continue;
                }

                entryNames.add(entry.getName());
            }

            return entryNames;
        } finally {
            zipFile.close();
        }
    }

    /**
     * Loads a dex file from a specific named entry.
     *
     * @param entryName The name of the entry
     * @return A ZipDexFile, or null if there is no entry with the given name
     * @throws NotADexFile If the entry isn't a dex file
     */

    @Override
    public DexEntry<DexBackedDexFile> getEntry( String entryName) throws IOException {
        ZipFile zipFile = getZipFile();
        try {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }

            return loadEntry(zipFile, entry);
        } finally {
            zipFile.close();
        }
    }

    public boolean isZipFile() {
        ZipFile zipFile = null;
        try {
            zipFile = getZipFile();
            return true;
        } catch (IOException ex) {
            return false;
        } catch (NotAZipFileException ex) {
            return false;
        } finally {
            if(zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException ex) {
                    // just eat it
                }
            }
        }
    }

    protected boolean isDex( ZipFile zipFile,  ZipEntry zipEntry) throws IOException {
        InputStream inputStream = new BufferedInputStream(zipFile.getInputStream(zipEntry));
        try {
            DexUtil.verifyDexHeader(inputStream);
        } catch (NotADexFile ex) {
            return false;
        } catch (InvalidFile ex) {
            return false;
        } catch (UnsupportedFile ex) {
            return false;
        } finally {
            inputStream.close();
        }
        return true;
    }

    protected ZipFile getZipFile() throws IOException {
        try {
            return new ZipFile(zipFilePath);
        } catch (IOException ex) {
            throw new NotAZipFileException();
        }
    }

    
    protected DexEntry loadEntry( ZipFile zipFile,  ZipEntry zipEntry) throws IOException {
        InputStream inputStream = zipFile.getInputStream(zipEntry);
        try {
            byte[] buf = ByteStreams.toByteArray(inputStream);

            return new DexEntry() {
                
                @Override
                public String getEntryName() {
                    return zipEntry.getName();
                }

                
                @Override
                public DexFile getDexFile() {
                    return new DexBackedDexFile(opcodes, buf);
                }

                
                @Override
                public MultiDexContainer getContainer() {
                    return ZipDexContainer.this;
                }
            };
        } finally {
            inputStream.close();
        }
    }

    public static class NotAZipFileException extends RuntimeException {
    }
}
