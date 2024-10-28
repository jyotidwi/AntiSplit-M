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
package com.reandroid.apk;

import static com.abdurazaaqmohammed.AntiSplit.main.MainActivity.rss;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;
import android.widget.TextView;

import com.abdurazaaqmohammed.AntiSplit.R;
import com.abdurazaaqmohammed.AntiSplit.main.DeviceSpecsUtil;
import com.abdurazaaqmohammed.AntiSplit.main.MainActivity;
import com.abdurazaaqmohammed.AntiSplit.main.MismatchedSplitsException;
import com.android.apksig.apk.ApkUtils;
import com.reandroid.apkeditor.merge.LogUtil;
import com.reandroid.archive.BlockInputSource;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.archive.block.ApkSignatureBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.pool.TableStringPool;
import com.reandroid.arsc.pool.builder.StringPoolMerger;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipFile;

public class ApkBundle implements Closeable {
    private final Map<String, ApkModule> mModulesMap;

    public ApkBundle(){
        this.mModulesMap=new HashMap<>();
    }

    public ApkModule mergeModules() throws IOException {
        List<ApkModule> moduleList=getApkModuleList();
        if(moduleList.isEmpty()){
            throw new FileNotFoundException("Nothing to merge, empty modules");
        }
        ApkModule result = new ApkModule(generateMergedModuleName(), new ZipEntryMap());
        result.setLoadDefaultFramework(false);

        mergeStringPools(result);

        ApkModule base=getBaseModule();
        if(base==null){
            base=getLargestTableModule();
        }
        result.merge(base);
        ApkSignatureBlock signatureBlock = null;
        for(ApkModule module:moduleList){
            ApkSignatureBlock asb = module.getApkSignatureBlock();
            if(module==base){
                if(asb != null){
                    signatureBlock = asb;
                }
                continue;
            }
            if(signatureBlock == null){
                signatureBlock = asb;
            }
            result.merge(module);
        }

        result.setApkSignatureBlock(signatureBlock);

        if(result.hasTableBlock()){
            TableBlock tableBlock=result.getTableBlock();
            tableBlock.sortPackages();
            tableBlock.refresh();
        }
        result.getZipEntryMap().autoSortApkFiles();
        return result;
    }
    private void mergeStringPools(ApkModule mergedModule) throws IOException {
        if(!hasOneTableBlock() || mergedModule.hasTableBlock()){
            return;
        }
        LogUtil.logMessage("Merging string pools ... ");
        TableBlock createdTable = new TableBlock();
        BlockInputSource<TableBlock> inputSource=
                new BlockInputSource<>(TableBlock.FILE_NAME, createdTable);
        mergedModule.getZipEntryMap().add(inputSource);

        StringPoolMerger poolMerger = new StringPoolMerger();

        for(ApkModule apkModule:getModules()){
            if(!apkModule.hasTableBlock()){
                continue;
            }
            TableStringPool stringPool = apkModule.getVolatileTableStringPool();
            poolMerger.add(stringPool);
        }

        poolMerger.mergeTo(createdTable.getTableStringPool());

        LogUtil.logMessage("Merged string pools="+poolMerger.getMergedPools()
                +", style="+poolMerger.getMergedStyleStrings()
                +", strings="+poolMerger.getMergedStrings());
    }
    private String generateMergedModuleName(){
        Set<String> moduleNames=mModulesMap.keySet();
        String merged="merged";
        int i=1;
        String name=merged;
        while (moduleNames.contains(name)){
            name=merged+"_"+i;
            i++;
        }
        return name;
    }
    private ApkModule getLargestTableModule(){
        ApkModule apkModule=null;
        int chunkSize=0;
        for(ApkModule module:getApkModuleList()){
            if(!module.hasTableBlock()){
                continue;
            }
            TableBlock tableBlock=module.getTableBlock();
            int size=tableBlock.getHeaderBlock().getChunkSize();
            if(apkModule==null || size>chunkSize){
                chunkSize=size;
                apkModule=module;
            }
        }
        return apkModule;
    }
    public ApkModule getBaseModule(){
        for(ApkModule module:getApkModuleList()){
            if(module.isBaseModule()){
                return module;
            }
        }
        return null;
    }
    public List<ApkModule> getApkModuleList(){
        return new ArrayList<>(mModulesMap.values());
    }

    public void loadApkDirectory(File dir, boolean recursive, Context context) throws IOException, MismatchedSplitsException, InterruptedException {
        if(!dir.isDirectory()) throw new FileNotFoundException("No such directory: " + dir);
        List<File> apkList = recursive ? ApkUtil.recursiveFiles(dir, ".apk") : ApkUtil.listFiles(dir, ".apk");
        if(apkList.isEmpty()) throw new FileNotFoundException("No '*.apk' files in directory: " + dir);
        LogUtil.logMessage("Found apk files: "+apkList.size());
        int size = apkList.size();
        int[] versionCodes = new int[size];
        int base = -1;
        for(int i = 0; i < size; i++){
            File file = apkList.get(i);
            try(ZipFile zf = new ZipFile(file);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                InputStream is = zf.getInputStream(zf.getEntry("AndroidManifest.xml"))) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) byteArrayOutputStream.write(buffer, 0, bytesRead);
                versionCodes[i] = ApkUtils.getVersionCodeFromBinaryAndroidManifest(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
                if(DeviceSpecsUtil.isBaseApk(file.getName())) base = versionCodes[i];
            } catch (Exception e) {
                versionCodes[i] = -1;
            }
        }
        if(base == -1) {
            // It is a valid usage to merge only some files and then merge that merged file with the base later.
            base = versionCodes[0]; // Just set first file as base for checking.
        }
        List<File> mismatchedDpis = new ArrayList<>();
        StringBuilder mismatchedLangs = new StringBuilder();
        for(int i = 0; i < size; i++) {
            if(versionCodes[i] != base) {
                File f = apkList.get(i);
                String name = f.getName();
                LogUtil.logMessage(name + rss.getString(R.string.mismatch_base));
                if(DeviceSpecsUtil.isArch(name)) throw new MismatchedSplitsException("Error: Key (the app will not run without it) split (" + name + ") has a mismatched version code.");
                if(name.contains("dpi")) mismatchedDpis.add(f);
                else mismatchedLangs.append(", ").append(name);
            }
        }

        apkList.removeAll(mismatchedDpis);
        boolean hasDpi = false;
        for(File f : apkList) {
            if(f.getName().contains("dpi")) {
                hasDpi = true;
                break;
            }
        }
        if(!hasDpi && !mismatchedDpis.isEmpty()) throw new MismatchedSplitsException("Error: All DPI/resource splits selected have a mismatched version code.");
        String s = mismatchedLangs.toString();
        if(!TextUtils.isEmpty(s)) {
            final CountDownLatch latch = new CountDownLatch(1);
            TextView title = new TextView(context);
            title.setText(rss.getString(R.string.warning));
            title.setTextColor(MainActivity.textColor);
            title.setTextSize(25);
            TextView msg = new TextView(context);
            msg.setText(rss.getString(R.string.mismatch, s.replaceFirst(", ", "")));
            msg.setTextColor(MainActivity.textColor);
            MainActivity act = ((MainActivity) context);
            act.getHandler().post(() -> act.styleAlertDialog(new AlertDialog.Builder(context).setCustomTitle(title).setView(msg).setPositiveButton("OK", (dialog, which) -> {
                for(String filename : s.split(", ")) {
                    File f = new File(dir, filename);
                    f.delete();
                    apkList.remove(f);
                }
                latch.countDown();
            }).setNegativeButton(rss.getString(R.string.cancel), (dialog, which) -> {
                act.startActivity(new Intent(act, MainActivity.class));
                if(Build.VERSION.SDK_INT > 15) act.finishAffinity();
                else act.finish();
                latch.countDown();
            }).create(), null, false, null));
            latch.await();
        }
        load(apkList);
    }

    private void load(List<File> apkList) throws IOException {
        for(File file : apkList) {
            LogUtil.logMessage("Loading: "+file.getName());
            addModule(ApkModule.loadApkFile(file, ApkUtil.toModuleName(file)));
        }
    }

    public void addModule(ApkModule apkModule){
        apkModule.setLoadDefaultFramework(false);
        String name = apkModule.getModuleName();
        mModulesMap.remove(name);
        mModulesMap.put(name, apkModule);
    }
    public Collection<ApkModule> getModules(){
        return mModulesMap.values();
    }
    private boolean hasOneTableBlock(){
        for(ApkModule apkModule:getModules()){
            if(apkModule.hasTableBlock()){
                return true;
            }
        }
        return false;
    }
    @Override
    public void close() throws IOException {
        for(ApkModule module : mModulesMap.values()) {
            module.close();
        }
        mModulesMap.clear();
    }
}
