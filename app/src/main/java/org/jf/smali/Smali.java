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

package org.jf.smali;

import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.extra.DexMarker;
import org.jf.dexlib2.writer.builder.DexBuilder;
import org.jf.dexlib2.writer.io.FileDataStore;
import org.jf.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class Smali {

    /**
     * Assemble the specified files, using the given options
     *
     * @param options a SmaliOptions object with the options to run smali with
     * @param input The files/directories to process
     * @return true if assembly completed with no errors, or false if errors were encountered
     */
    public static boolean assemble(final SmaliOptions options, String... input) throws IOException {
        return assemble(options, Arrays.asList(input));
    }

    /**
     * Assemble the specified files, using the given options
     *
     * @param options a SmaliOptions object with the options to run smali with
     * @param input The files/directories to process
     * @return true if assembly completed with no errors, or false if errors were encountered
     */
    public static boolean assemble(final SmaliOptions options, List<String> input) throws IOException {
        boolean success = false;
        final DexBuilder dexBuilder = new DexBuilder(Opcodes.forApi(options.apiLevel));
        if(options.markersListFile != null){
            List<DexMarker> markerList = DexMarker.readMarkers(new File(options.markersListFile));
            dexBuilder.addMarkers(markerList.iterator());
        }

        for (String fileToProcess: input) {
            File argFile = new File(fileToProcess);
            if (!argFile.exists()) {
                throw new IOException("Cannot find file or directory \"" + fileToProcess + "\"");
            }
            if(assemble(dexBuilder, argFile, options)){
                success = true;
            }

        }
        if (!success) {
            return false;
        }

        dexBuilder.writeTo(new FileDataStore(new File(options.outputDexFile)));

        return true;
    }
    private static boolean assemble(DexBuilder dexBuilder, File fileOrDir, final SmaliOptions options) throws IOException {

        if(fileOrDir.isFile()){
            if(!fileOrDir.getName().endsWith(".smali")){
                return true;
            }
            return assembleSmaliFile(fileOrDir, dexBuilder, options);
        }
        File[] files = fileOrDir.listFiles();
        if(files == null){
            return false;
        }
        boolean has_error = false;
        for(File file : files){
            if(!assemble(dexBuilder, file, options)){
                has_error = true;
            }
        }
        return !has_error;
    }

    /**
     * Prints the lexical tokens for the given files.
     *
     * @param options a SmaliOptions object with the options to use
     * @param input The files/directories to process
     * @return true if assembly completed with no errors, or false if errors were encountered
     */
    public static boolean printTokens(final SmaliOptions options, List<String> input) throws IOException {
        TreeSet<File> filesToProcessSet = new TreeSet<File>();

        for (String fileToProcess: input) {
            File argFile = new File(fileToProcess);

            if (!argFile.exists()) {
                throw new IllegalArgumentException("Cannot find file or directory \"" + fileToProcess + "\"");
            }

            if (argFile.isDirectory()) {
                getSmaliFilesInDir(argFile, filesToProcessSet);
            } else if (argFile.isFile()) {
                filesToProcessSet.add(argFile);
            }
        }

        boolean errors = false;

        for (final File file: filesToProcessSet) {
            try {
                errors |= !printTokensForSingleFile(file, options);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        if (errors) {
            return false;
        }

        return true;
    }

    private static void getSmaliFilesInDir( File dir,  Set<File> smaliFiles) {
        File[] files = dir.listFiles();
        if (files != null) {
            for(File file: files) {
                if (file.isDirectory()) {
                    getSmaliFilesInDir(file, smaliFiles);
                } else if (file.getName().endsWith(".smali")) {
                    smaliFiles.add(file);
                }
            }
        }
    }

    private static boolean assembleSmaliFile(File smaliFile, DexBuilder dexBuilder, SmaliOptions options)
            throws IOException {
        return false;
//        FileInputStream fis = null;
//        try {
//            fis = new FileInputStream(smaliFile);
//            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");
//
//            LexerErrorInterface lexer = new smaliFlexLexer(reader, options.apiLevel);
//            ((smaliFlexLexer)lexer).setSourceFile(smaliFile);
//            CommonTokenStream tokens = new CommonTokenStream((TokenSource)lexer);
//
//            if (options.printTokens) {
//                tokens.getTokens();
//
//                for (int i=0; i<tokens.size(); i++) {
//                    Token token = tokens.get(i);
//                    if (token.getChannel() == smaliParser.HIDDEN) {
//                        continue;
//                    }
//
//                    String tokenName;
//                    if (token.getType() == -1) {
//                        tokenName = "EOF";
//                    } else {
//                        tokenName = smaliParser.tokenNames[token.getType()];
//                    }
//                    System.out.println(tokenName + ": " + token.getText());
//                }
//
//                System.out.flush();
//            }
//
//            smaliParser parser = new smaliParser(tokens);
//            parser.setVerboseErrors(options.verboseErrors);
//            parser.setAllowOdex(options.allowOdexOpcodes);
//            parser.setApiLevel(options.apiLevel);
//
//            smaliParser.smali_file_return result = null;
//            try {
//                result = parser.smali_file();
//            } catch (RecognitionException ex) {
//                throw new IOException(ex);
//            }
//
//            if (parser.getNumberOfSyntaxErrors() > 0 || lexer.getNumberOfSyntaxErrors() > 0) {
//                return false;
//            }
//
//            CommonTree t = result.getTree();
//
//            CommonTreeNodeStream treeStream = new CommonTreeNodeStream(t);
//            treeStream.setTokenStream(tokens);
//
//            if (options.printTokens) {
//                System.out.println(t.toStringTree());
//            }
//
//            smaliTreeWalker dexGen = new smaliTreeWalker(treeStream);
//            dexGen.setApiLevel(options.apiLevel);
//
//            dexGen.setVerboseErrors(options.verboseErrors);
//            dexGen.setDexBuilder(dexBuilder);
//            try {
//                dexGen.smali_file();
//            } catch (RecognitionException ex) {
//                throw new IOException(ex);
//            }
//
//            return dexGen.getNumberOfSyntaxErrors() == 0;
//        } finally {
//            if (fis != null) {
//                fis.close();
//            }
//        }
    }

    private static boolean printTokensForSingleFile(File smaliFile, SmaliOptions options)
            throws Exception {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(smaliFile);
            InputStreamReader reader = new InputStreamReader(fis, "UTF-8");

//            LexerErrorInterface lexer = new smaliFlexLexer(reader, options.apiLevel);
//            ((smaliFlexLexer)lexer).setSourceFile(smaliFile);
//            CommonTokenStream tokens = new CommonTokenStream((TokenSource)lexer);
//            tokens.fill();
//
//            for (int i=0; i<tokens.size(); i++) {
//                Token token = tokens.get(i);
//                if (token.getChannel() == smaliParser.HIDDEN) {
//                    continue;
//                }
//
//                String tokenName;
//                if (token.getType() == -1) {
//                    tokenName = "EOF";
//                } else {
//                    tokenName = smaliParser.tokenNames[token.getType()];
//                }
//                System.out.println(tokenName + "(\"" + StringUtils.escapeString(token.getText()) + "\")");
//            }
            System.out.flush();

            return false;// lexer.getNumberOfSyntaxErrors() == 0;
        } finally {
            if (fis != null) {
                fis.close();
            }
        }
    }
}
