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
package com.reandroid.archive.block;

import com.reandroid.archive.block.pad.SchemePadding;
import com.reandroid.arsc.io.BlockReader;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;

public class ApkSignatureBlock extends LengthPrefixedList<SignatureInfo>
        implements Comparator<SignatureInfo> {

    public ApkSignatureBlock(SignatureFooter signatureFooter){
        super(true);
        setBottomBlock(signatureFooter);
    }

    public void sortSignatures(){
        sort(this);
    }
    public void updatePadding(){
        SchemePadding schemePadding = getOrCreateSchemePadding();
        schemePadding.setPadding(0);
        sortSignatures();
        refresh();
        int size = countBytes();
        int alignment = 4096;
        int padding = (alignment - (size % alignment)) % alignment;
        schemePadding.setPadding(padding);
        refresh();
    }
    private SchemePadding getOrCreateSchemePadding(){
        SignatureInfo signatureInfo = getSignature(SignatureId.PADDING);
        if(signatureInfo == null){
            signatureInfo = new SignatureInfo();
            signatureInfo.setId(SignatureId.PADDING);
            signatureInfo.setSignatureScheme(new SchemePadding());
            add(signatureInfo);
        }
        SignatureScheme scheme = signatureInfo.getSignatureScheme();
        if(!(scheme instanceof SchemePadding)){
            scheme = new SchemePadding();
            signatureInfo.setSignatureScheme(scheme);
        }
        return (SchemePadding) scheme;
    }
    public SignatureInfo getSignature(SignatureId signatureId){
        for(SignatureInfo signatureInfo : this){
            if(signatureInfo.getId().equals(signatureId)){
                return signatureInfo;
            }
        }
        return null;
    }
    public SignatureFooter getSignatureFooter(){
        return (SignatureFooter) getBottomBlock();
    }
    @Override
    public SignatureInfo newInstance() {
        return new SignatureInfo();
    }
    @Override
    protected void onRefreshed(){
        SignatureFooter footer = getSignatureFooter();
        footer.updateMagic();
        super.onRefreshed();
        footer.setSignatureSize(getDataSize());
    }

    public void read(File file) throws IOException {
        super.readBytes(new BlockReader(file));
    }


    @Override
    public int compare(SignatureInfo info1, SignatureInfo info2) {
        return info1.getId().compareTo(info2.getId());
    }

}
