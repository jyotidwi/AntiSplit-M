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

import com.reandroid.archive.ArchiveFile;
import com.reandroid.archive.BlockInputSource;
import com.reandroid.archive.FileInputSource;
import com.reandroid.archive.InputSource;
import com.reandroid.archive.WriteProgress;
import com.reandroid.archive.ZipEntryMap;
import com.reandroid.archive.block.ApkSignatureBlock;
import com.reandroid.archive.io.ArchiveFileEntrySource;
import com.reandroid.archive.writer.ApkFileWriter;
import com.reandroid.archive.writer.ApkStreamWriter;
import com.reandroid.arsc.ApkFile;
import com.reandroid.arsc.array.PackageArray;
import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.chunk.PackageBlock;
import com.reandroid.arsc.chunk.TableBlock;
import com.reandroid.arsc.chunk.TypeBlock;
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock;
import com.reandroid.arsc.chunk.xml.ResXmlDocument;
import com.reandroid.arsc.item.TableString;
import com.reandroid.arsc.model.FrameworkTable;
import com.reandroid.arsc.pool.TableStringPool;
import com.reandroid.arsc.value.Entry;
import com.reandroid.arsc.value.ResConfig;
import com.reandroid.utils.collection.ArrayCollection;
import com.reandroid.utils.collection.CollectionUtil;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;

public class ApkModule implements ApkFile, Closeable {
    private String moduleName;
    private final ZipEntryMap zipEntryMap;
    private boolean loadDefaultFramework = true;
    private boolean mDisableLoadFramework = false;
    private TableBlock mTableBlock;
    private InputSource mTableOriginalSource;
    private AndroidManifestBlock mManifestBlock;
    private InputSource mManifestOriginalSource;
    private final UncompressedFiles mUncompressedFiles;
    private APKLogger apkLogger;
    private ApkType mApkType;
    private ApkSignatureBlock apkSignatureBlock;
    private Integer preferredFramework;
    private Closeable mCloseable;
    private final List<TableBlock> mExternalFrameworks;

    public ApkModule(String moduleName, ZipEntryMap zipEntryMap){
        this.moduleName = moduleName;
        this.zipEntryMap = zipEntryMap;
        this.mUncompressedFiles=new UncompressedFiles();
        this.mUncompressedFiles.addPath(zipEntryMap);
        this.mExternalFrameworks = new ArrayCollection<>();
        this.zipEntryMap.setModuleName(moduleName);
    }

    public ApkModule(){
        this("base", new ZipEntryMap());
    }




    public ApkSignatureBlock getApkSignatureBlock() {
        return apkSignatureBlock;
    }
    public void setApkSignatureBlock(ApkSignatureBlock apkSignatureBlock) {
        this.apkSignatureBlock = apkSignatureBlock;
    }




    public String getSplit(){
        if(!hasAndroidManifest()){
            return null;
        }
        return getAndroidManifest().getSplit();
    }
    public List<TableBlock> getLoadedFrameworks(){
        List<TableBlock> results = new ArrayCollection<>();
        if(!hasTableBlock()){
            return results;
        }
        TableBlock tableBlock = getTableBlock(false);
        results.addAll(tableBlock.getFrameWorks());
        return results;
    }

    public FrameworkApk getLoadedFramework(Integer version, boolean onlyAndroid){
        for(TableBlock tableBlock : getLoadedFrameworks()){
            if(!(tableBlock instanceof FrameworkTable)){
                continue;
            }
            FrameworkTable frame = (FrameworkTable) tableBlock;
            if(onlyAndroid && !isAndroid(frame)){
                continue;
            }
            if(version == null || version.equals(frame.getVersionCode())){
                return (FrameworkApk) frame.getApkFile();
            }
        }
        return null;
    }
    public FrameworkApk initializeAndroidFramework(TableBlock tableBlock, Integer version) throws IOException {
        if(mDisableLoadFramework || tableBlock == null || isAndroid(tableBlock)){
            return null;
        }
        FrameworkApk exist = getLoadedFramework(version, true);
        if(exist != null){
            return exist;
        }
        logMessage("Initializing android framework ...");
        FrameworkApk frameworkApk;
        if(version == null){
            logMessage("Can not read framework version, loading latest");
            frameworkApk = AndroidFrameworks.getLatest();
        }else {
            logMessage("Loading android framework for version: " + version);
            frameworkApk = AndroidFrameworks.getBestMatch(version);
        }
        FrameworkTable frameworkTable = frameworkApk.getTableBlock();
        tableBlock.addFramework(frameworkTable);
        logMessage("Initialized framework: " + frameworkApk.getName()
                + " (" + frameworkApk.getVersionName() + ")");
        return frameworkApk;
    }

    private boolean isAndroid(TableBlock tableBlock){
        if(tableBlock instanceof FrameworkTable){
            FrameworkTable frameworkTable = (FrameworkTable) tableBlock;
            return frameworkTable.isAndroid();
        }
        return false;
    }

    public Integer getAndroidFrameworkVersion(){
        if(preferredFramework != null){
            return preferredFramework;
        }
        if(!hasAndroidManifest()){
            return null;
        }
        AndroidManifestBlock manifest = getAndroidManifest();
        Integer version = manifest.getCompileSdkVersion();
        if(version == null){
            version = manifest.getPlatformBuildVersionCode();
        }
        Integer target = manifest.getTargetSdkVersion();
        if(version == null){
            version = target;
        }else if(target != null && target > version){
            version = target;
        }
        return version;
    }


    public List<DexFileInputSource> listDexFiles(){
        List<DexFileInputSource> results = new ArrayCollection<>();
        for(InputSource source: getInputSources()){
            if(DexFileInputSource.isDexName(source.getAlias())){
                DexFileInputSource inputSource;
                if(source instanceof DexFileInputSource){
                    inputSource = (DexFileInputSource)source;
                }else {
                    inputSource = new DexFileInputSource(source.getAlias(), source);
                }
                results.add(inputSource);
            }
        }
        DexFileInputSource.sort(results);
        return results;
    }
    public boolean isBaseModule(){
        if(!hasAndroidManifest()){
            return false;
        }
        AndroidManifestBlock manifest;
        try {
            manifest= getAndroidManifest();
            return !(manifest.isSplit() || manifest.getMainActivity()==null);
        } catch (Exception ignored) {
            return false;
        }
    }
    public String getModuleName(){
        return moduleName;
    }

    public void writeApk(File file) throws IOException {
        writeApk(file, null);
    }
    public void writeApk(File file, WriteProgress progress) throws IOException {
        ApkFileWriter writer = createApkFileWriter(file);
        writer.setWriteProgress(progress);
        writer.write();
    }
    public void writeApk(OutputStream outputStream) throws IOException {
        createApkStreamWriter(outputStream).write();
    }
    public ApkStreamWriter createApkStreamWriter(OutputStream outputStream) {
        ZipEntryMap zipEntryMap = getZipEntryMap();
        UncompressedFiles uf = getUncompressedFiles();
        uf.apply(zipEntryMap);
        ApkStreamWriter writer = new ApkStreamWriter(outputStream,
                zipEntryMap.toArray(true));
        writer.setApkSignatureBlock(getApkSignatureBlock());
        writer.setArchiveInfo(zipEntryMap.getArchiveInfo());
        return writer;
    }
    public ApkFileWriter createApkFileWriter(File file) throws IOException {
        ZipEntryMap zipEntryMap = getZipEntryMap();
        UncompressedFiles uf = getUncompressedFiles();
        uf.apply(zipEntryMap);
        ApkFileWriter writer = new ApkFileWriter(file, zipEntryMap.toArray(true));
        writer.setApkSignatureBlock(getApkSignatureBlock());
        writer.setArchiveInfo(zipEntryMap.getArchiveInfo());
        return writer;
    }


    public UncompressedFiles getUncompressedFiles(){
        return mUncompressedFiles;
    }

    public List<ResFile> listResFiles() {
        return listResFiles(0, null);
    }
    public List<ResFile> listResFiles(int resourceId, ResConfig resConfig) {
        List<ResFile> results = new ArrayCollection<>();
        TableBlock tableBlock = getTableBlock();
        if (tableBlock == null){
            return results;
        }
        TableStringPool stringPool= tableBlock.getStringPool();
        for(InputSource inputSource : getInputSources()){
            String name = inputSource.getAlias();
            Iterator<TableString> iterator = stringPool.getItems(name);
            while (iterator.hasNext()){
                TableString tableString = iterator.next();
                List<Entry> entryList = filterResFileEntries(tableString, resourceId, resConfig);
                if(!entryList.isEmpty()) {
                    ResFile resFile = new ResFile(inputSource, entryList);
                    results.add(resFile);
                }
            }
        }
        return results;
    }

    private List<Entry> filterResFileEntries(TableString tableString, int resourceId, ResConfig resConfig){
        Iterator<Entry> itr = tableString.getEntries(item -> {
            if(!item.isScalar() ||
                    !TypeBlock.canHaveResourceFile(item.getTypeName())){
                return false;
            }
            if(resourceId != 0 && resourceId != item.getResourceId()){
                return false;
            }
            return resConfig == null || resConfig.equals(item.getResConfig());
        });
        return CollectionUtil.toList(itr);
    }
    public String getPackageName(){
        if(hasAndroidManifest()){
            return getAndroidManifest().getPackageName();
        }
        if(!hasTableBlock()){
            return null;
        }
        TableBlock tableBlock=getTableBlock();
        PackageArray pkgArray = tableBlock.getPackageArray();
        PackageBlock pkg = pkgArray.get(0);
        if(pkg==null){
            return null;
        }
        return pkg.getName();
    }
    public void setPackageName(String name) {
        String old=getPackageName();
        if(hasAndroidManifest()){
            getAndroidManifest().setPackageName(name);
        }
        if(!hasTableBlock()){
            return;
        }
        TableBlock tableBlock=getTableBlock();
        PackageArray pkgArray = tableBlock.getPackageArray();
        for(PackageBlock pkg:pkgArray.listItems()){
            if(pkgArray.size()==1){
                pkg.setName(name);
                continue;
            }
            String pkgName=pkg.getName();
            if(pkgName.startsWith(old)){
                pkgName=pkgName.replace(old, name);
                pkg.setName(pkgName);
            }
        }
    }
    // Use hasAndroidManifest
    @Deprecated
    public boolean hasAndroidManifestBlock(){
        return hasAndroidManifest();
    }
    public boolean hasAndroidManifest(){
        return mManifestBlock!=null
                || getZipEntryMap().getInputSource(AndroidManifestBlock.FILE_NAME)!=null;
    }
    public boolean hasTableBlock(){
        return mTableBlock!=null
                || getZipEntryMap().getInputSource(TableBlock.FILE_NAME)!=null;
    }
    public void destroy(){
        getZipEntryMap().clear();
        AndroidManifestBlock manifestBlock = this.mManifestBlock;
        if(manifestBlock!=null){
            manifestBlock.destroy();
            this.mManifestBlock = null;
        }
        TableBlock tableBlock = this.mTableBlock;
        if(tableBlock!=null){
            mExternalFrameworks.clear();
            tableBlock.clear();
            this.mTableBlock = null;
        }
        try {
            close();
        } catch (IOException ignored) {
        }
    }
    public void setManifest(AndroidManifestBlock manifestBlock){
        ZipEntryMap archive = getZipEntryMap();
        if(manifestBlock==null){
            mManifestBlock = null;
            mManifestOriginalSource = null;
            archive.remove(AndroidManifestBlock.FILE_NAME);
            return;
        }
        manifestBlock.setApkFile(this);
        BlockInputSource<AndroidManifestBlock> source =
                new BlockInputSource<>(AndroidManifestBlock.FILE_NAME, manifestBlock);
        source.setMethod(ZipEntry.STORED);
        source.setSort(0);
        archive.add(source);
        mManifestBlock = manifestBlock;
    }
    public void setTableBlock(TableBlock tableBlock){
        ZipEntryMap archive = getZipEntryMap();
        if(tableBlock == null){
            mTableBlock = null;
            mTableOriginalSource = null;
            archive.remove(TableBlock.FILE_NAME);
            return;
        }
        tableBlock.setApkFile(this);
        BlockInputSource<TableBlock> source =
                new BlockInputSource<>(TableBlock.FILE_NAME, tableBlock);
        archive.add(source);
        source.setMethod(ZipEntry.STORED);
        source.setSort(1);
        getUncompressedFiles().addPath(source);
        mTableBlock = tableBlock;
        updateExternalFramework();
    }
    /**
     * Use getAndroidManifest()
     * */
    @Deprecated
    public AndroidManifestBlock getAndroidManifestBlock(){
        return getAndroidManifest();
    }
    @Override
    public AndroidManifestBlock getAndroidManifest() {
        if(mManifestBlock!=null){
            return mManifestBlock;
        }
        InputSource inputSource = getInputSource(AndroidManifestBlock.FILE_NAME);
        if(inputSource == null){
            return null;
        }
        setManifestOriginalSource(inputSource);
        InputStream inputStream = null;
        try {
            inputStream = inputSource.openStream();
            AndroidManifestBlock manifestBlock=AndroidManifestBlock.load(inputStream);
            inputStream.close();
            BlockInputSource<AndroidManifestBlock> blockInputSource=new BlockInputSource<>(inputSource.getName(),manifestBlock);
            blockInputSource.setSort(inputSource.getSort());
            blockInputSource.setMethod(inputSource.getMethod());
            addInputSource(blockInputSource);
            manifestBlock.setApkFile(this);
            TableBlock tableBlock = this.mTableBlock;
            if(tableBlock != null){
                int packageId = manifestBlock.guessCurrentPackageId();
                if(packageId != 0){
                    manifestBlock.setPackageBlock(tableBlock.pickOne(packageId));
                }else {
                    manifestBlock.setPackageBlock(tableBlock.pickOne());
                }
            }
            mManifestBlock = manifestBlock;
            onManifestBlockLoaded(manifestBlock);
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
        return mManifestBlock;
    }
    private void onManifestBlockLoaded(AndroidManifestBlock manifestBlock){
        initializeApkType(manifestBlock);
    }
    public TableBlock getTableBlock(boolean initFramework) {
        if(mTableBlock==null){
            if(!hasTableBlock()){
                return null;
            }
            try {
                mTableBlock = loadTableBlock();
                if(initFramework && loadDefaultFramework){
                    Integer version = getAndroidFrameworkVersion();
                    initializeAndroidFramework(mTableBlock, version);
                }
                updateExternalFramework();
            } catch (IOException exception) {
                throw new IllegalArgumentException(exception);
            }
        }
        return mTableBlock;
    }
    private void updateExternalFramework(){
        TableBlock tableBlock = mTableBlock;
        if(tableBlock == null){
            return;
        }
        for(TableBlock framework : mExternalFrameworks){
            tableBlock.addFramework(framework);
        }
    }

    public InputSource getManifestOriginalSource(){
        InputSource inputSource = this.mManifestOriginalSource;
        if(inputSource == null){
            inputSource = getInputSource(AndroidManifestBlock.FILE_NAME);
            mManifestOriginalSource = inputSource;
        }
        return inputSource;
    }
    private void setManifestOriginalSource(InputSource inputSource){
        if(mManifestOriginalSource == null
                && !(inputSource instanceof BlockInputSource)){
            mManifestOriginalSource = inputSource;
        }
    }

    public InputSource getTableOriginalSource(){
        InputSource inputSource = this.mTableOriginalSource;
        if(inputSource == null){
            inputSource = getInputSource(TableBlock.FILE_NAME);
            mTableOriginalSource = inputSource;
        }
        return inputSource;
    }
    private void setTableOriginalSource(InputSource inputSource){
        if(mTableOriginalSource == null
                && !(inputSource instanceof BlockInputSource)){
            mTableOriginalSource = inputSource;
        }
    }
    @Override
    public TableBlock getTableBlock() {
        if(mTableBlock != null){
            return mTableBlock;
        }
        checkExternalFramework();
        checkSelfFramework();
        return getTableBlock(!mDisableLoadFramework);
    }
    @Override
    public TableBlock getLoadedTableBlock(){
        return mTableBlock;
    }
    private void checkExternalFramework(){
        if(mDisableLoadFramework || preferredFramework != null){
            return;
        }
        if(mExternalFrameworks.size() == 0){
            return;
        }
        mDisableLoadFramework = true;
    }
    private void checkSelfFramework(){
        if(mDisableLoadFramework || preferredFramework != null){
            return;
        }
        AndroidManifestBlock manifest = getAndroidManifest();
        if(manifest == null){
            return;
        }
        if(manifest.isCoreApp() == null
                || !"android".equals(manifest.getPackageName())){
            return;
        }
        if(manifest.guessCurrentPackageId() != 0x01){
            return;
        }
        logMessage("Looks like framework apk, skip loading framework");
        mDisableLoadFramework = true;
    }

    @Override
    public ResXmlDocument loadResXmlDocument(String path) throws IOException{
        InputSource inputSource = getInputSource(path);
        if(inputSource == null){
            throw new FileNotFoundException("No such file in apk: " + path);
        }
        return loadResXmlDocument(inputSource);
    }
    public ResXmlDocument loadResXmlDocument(InputSource inputSource) throws IOException{
        ResXmlDocument resXmlDocument = null;
        if(inputSource instanceof BlockInputSource){
            Block block = ((BlockInputSource<?>) inputSource).getBlock();
            if(block instanceof ResXmlDocument){
                resXmlDocument = (ResXmlDocument) block;
            }
        }
        if(resXmlDocument == null){
            resXmlDocument = new ResXmlDocument();
            resXmlDocument.readBytes(inputSource.openStream());
        }
        resXmlDocument.setApkFile(this);
        if(resXmlDocument.getPackageBlock() == null){
            resXmlDocument.setPackageBlock(findPackageForPath(inputSource.getAlias()));
        }
        return resXmlDocument;
    }
    private PackageBlock findPackageForPath(String path) {
        TableBlock tableBlock = getTableBlock();
        if(tableBlock == null){
            return null;
        }
        if(tableBlock.size() == 1){
            return tableBlock.get(0);
        }
        PackageBlock packageBlock = CollectionUtil.getFirst(
                tableBlock.getStringPool().getUsers(PackageBlock.class, path));
        if(packageBlock == null){
            packageBlock = tableBlock.pickOne();
        }
        return packageBlock;
    }

    private ApkType initializeApkType(AndroidManifestBlock manifestBlock){
        if(mApkType!=null){
            return mApkType;
        }
        ApkType apkType = null;
        if(manifestBlock!=null){
            apkType = manifestBlock.guessApkType();
        }
        if(apkType != null){
            mApkType = apkType;
        }else {
            apkType = ApkType.UNKNOWN;
        }
        return apkType;
    }

    // If we need TableStringPool only, this loads pool without
    // loading packages and other chunk blocks for faster and less memory usage
    public TableStringPool getVolatileTableStringPool() throws IOException{
        if(mTableBlock!=null){
            return mTableBlock.getStringPool();
        }
        InputSource inputSource = getInputSource(TableBlock.FILE_NAME);
        if(inputSource==null){
            throw new IOException("Module don't have: "+TableBlock.FILE_NAME);
        }
        if((inputSource instanceof ArchiveFileEntrySource)
                ||(inputSource instanceof FileInputSource)){
            InputStream inputStream = inputSource.openStream();
            TableStringPool stringPool = TableStringPool.readFromTable(inputStream);
            inputStream.close();
            return stringPool;
        }
        return getTableBlock().getStringPool();
    }
    TableBlock loadTableBlock() throws IOException {
        InputSource inputSource = getInputSource(TableBlock.FILE_NAME);
        if(inputSource == null){
            throw new IOException("Entry not found: "+TableBlock.FILE_NAME);
        }
        TableBlock tableBlock;
        if(inputSource instanceof BlockInputSource){
            tableBlock = (TableBlock) ((BlockInputSource<?>) inputSource).getBlock();
        }else {
            setTableOriginalSource(inputSource);
            InputStream inputStream = inputSource.openStream();
            tableBlock = TableBlock.load(inputStream);
            inputStream.close();
        }
        BlockInputSource<TableBlock> blockInputSource=new BlockInputSource<>(inputSource.getName(), tableBlock);
        blockInputSource.setMethod(inputSource.getMethod());
        blockInputSource.setSort(inputSource.getSort());
        getZipEntryMap().add(blockInputSource);
        tableBlock.setApkFile(this);
        return tableBlock;
    }
    @Override
    public void add(InputSource inputSource){
        if(inputSource == null){
            return;
        }
        String path = inputSource.getAlias();
        if(AndroidManifestBlock.FILE_NAME.equals(path)){
            InputSource manifestSource = getManifestOriginalSource();
            if(manifestSource != inputSource){
                mManifestBlock = null;
            }
            setManifestOriginalSource(inputSource);
        }else if(TableBlock.FILE_NAME.equals(path)){
            InputSource table = getTableOriginalSource();
            if(inputSource != table){
                mTableBlock = null;
            }
            setTableOriginalSource(inputSource);
        }
        addInputSource(inputSource);
    }

    @Override
    public boolean containsFile(String path) {
        return getZipEntryMap().contains(path);
    }

    @Override
    public InputSource getInputSource(String path){
        return getZipEntryMap().getInputSource(path);
    }

    private void addInputSource(InputSource inputSource){
        getZipEntryMap().add(inputSource);
    }

    public InputSource[] getInputSources(){
        return getZipEntryMap().toArray();
    }
    public ZipEntryMap getZipEntryMap() {
        return zipEntryMap;
    }
    public void setLoadDefaultFramework(boolean loadDefaultFramework) {
        this.loadDefaultFramework = loadDefaultFramework;
        this.mDisableLoadFramework = !loadDefaultFramework;
    }

    public void merge(ApkModule module) throws IOException {
        if(module==null||module==this){
            return;
        }
        mergeDexFiles(module);
        mergeTable(module);
        mergeFiles(module);
        getUncompressedFiles().merge(module.getUncompressedFiles());
    }
    private void mergeTable(ApkModule module) {
        if(!module.hasTableBlock()){
            return;
        }
        logMessage("Merging resource table: "+module.getModuleName());
        TableBlock exist;
        if(!hasTableBlock()){
            exist=new TableBlock();
            BlockInputSource<TableBlock> inputSource=new BlockInputSource<>(TableBlock.FILE_NAME, exist);
            addInputSource(inputSource);
        }else{
            exist=getTableBlock();
        }
        TableBlock coming=module.getTableBlock();
        exist.merge(coming);
    }
    private void mergeFiles(ApkModule module) {
        ZipEntryMap entryMapExist = getZipEntryMap();
        ZipEntryMap entryMapComing = module.getZipEntryMap();
        Map<String, InputSource> comingAlias = entryMapComing.toAliasMap();
        Map<String, InputSource> existAlias = entryMapExist.toAliasMap();
        UncompressedFiles uncompressedFiles = module.getUncompressedFiles();
        for(InputSource inputSource:comingAlias.values()){
            if(existAlias.containsKey(inputSource.getAlias())
                    || existAlias.containsKey(inputSource.getName())){
                continue;
            }
            if(DexFileInputSource.isDexName(inputSource.getName())){
                continue;
            }
            if(inputSource.getAlias().startsWith("lib/")){
                uncompressedFiles.removePath(inputSource.getAlias());
            }
            entryMapExist.add(inputSource);
        }
    }
    private void mergeDexFiles(ApkModule module){
        UncompressedFiles uncompressedFiles = module.getUncompressedFiles();
        List<DexFileInputSource> existList = listDexFiles();
        List<DexFileInputSource> comingList = module.listDexFiles();
        ZipEntryMap zipEntryMap = getZipEntryMap();
        int index=0;
        if(existList.size()>0){
            index=existList.get(existList.size()-1).getDexNumber();
            if(index==0){
                index=2;
            }else {
                index++;
            }
        }
        for(DexFileInputSource source : comingList){
            uncompressedFiles.removePath(source.getAlias());
            String name = DexFileInputSource.getDexName(index);
            DexFileInputSource add = new DexFileInputSource(name, source.getInputSource());
            zipEntryMap.add(add);
            logMessage("Added [" + module.getModuleName() +"] "
                    + source.getAlias() + " -> " + name);
            index++;
            if(index==1){
                index=2;
            }
        }
    }
    public APKLogger getApkLogger(){
        return apkLogger;
    }
    public void setAPKLogger(APKLogger logger) {
        this.apkLogger = logger;
    }
    void logMessage(String msg) {
        if(apkLogger!=null){
            apkLogger.logMessage(msg);
        }
    }
    public void setCloseable(Closeable closeable){
        this.mCloseable = closeable;
    }
    @Override
    public void close() throws IOException {
        Closeable closeable = this.mCloseable;
        if(closeable != null){
            closeable.close();
        }
    }
    @Override
    public String toString(){
        return getModuleName();
    }

    public static ApkModule loadApkFile(File apkFile, String moduleName) throws IOException {
        ArchiveFile archive = new ArchiveFile(apkFile);
        ApkModule apkModule = new ApkModule(moduleName, archive.createZipEntryMap());
        apkModule.setApkSignatureBlock(archive.getApkSignatureBlock());
        apkModule.setCloseable(archive);
        return apkModule;
    }
}