/*
 * Copyright 2013, Google Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *     * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following disclaimer
 * in the documentation and/or other materials provided with the
 * distribution.
 *     * Neither the name of Google Inc. nor the names of its
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

package org.jf.dexlib2.writer.builder;

import org.jf.dexlib2.iface.reference.FieldReference;
import org.jf.dexlib2.immutable.reference.ImmutableFieldReference;
import org.jf.dexlib2.writer.FieldSection;

import java.util.Collection;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class BuilderFieldPool extends BaseBuilderPool
        implements FieldSection<BuilderStringReference, BuilderTypeReference, BuilderFieldReference, BuilderField> {

    private final ConcurrentMap<FieldReference, BuilderFieldReference> internedItems =
            new ConcurrentHashMap<>();

    public BuilderFieldPool( DexBuilder dexBuilder) {
        super(dexBuilder);
    }

     BuilderFieldReference internField( String definingClass, String name, String type) {
        ImmutableFieldReference fieldReference = new ImmutableFieldReference(definingClass, name, type);
        return internField(fieldReference);
    }


    public BuilderFieldReference internField( FieldReference fieldReference) {
        BuilderFieldReference ret = internedItems.get(fieldReference);
        if (ret != null) {
            return ret;
        }

        BuilderFieldReference dexPoolFieldReference = new BuilderFieldReference(
                dexBuilder.typeSection.internType(fieldReference.getDefiningClass()),
                dexBuilder.stringSection.internString(fieldReference.getName()),
                dexBuilder.typeSection.internType(fieldReference.getType()));
        ret = internedItems.putIfAbsent(dexPoolFieldReference, dexPoolFieldReference);
        return ret==null?dexPoolFieldReference:ret;
    }


    @Override
    public BuilderTypeReference getDefiningClass( BuilderFieldReference key) {
        return key.definingClass;
    }


    @Override
    public BuilderTypeReference getFieldType( BuilderFieldReference key) {
        return key.fieldType;
    }


    @Override
    public BuilderStringReference getName( BuilderFieldReference key) {
        return key.name;
    }

    @Override
    public int getFieldIndex( BuilderField builderField) {
        return builderField.fieldReference.getIndex();
    }

    @Override
    public int getItemIndex( BuilderFieldReference key) {
        return key.index;
    }


    @Override
    public Collection<? extends Entry<? extends BuilderFieldReference, Integer>> getItems() {
        return new BuilderMapEntryCollection<BuilderFieldReference>(internedItems.values()) {
            @Override protected int getValue( BuilderFieldReference key) {
                return key.index;
            }

            @Override protected int setValue( BuilderFieldReference key, int value) {
                int prev = key.index;
                key.index = value;
                return prev;
            }
        };
    }

    @Override
    public int getItemCount() {
        return internedItems.size();
    }
}
