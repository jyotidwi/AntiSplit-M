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

package org.jf.dexlib2.writer.pool;

import org.jf.dexlib2.DebugItemType;
import org.jf.dexlib2.HiddenApiRestriction;
import org.jf.dexlib2.ReferenceType;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.formatter.DexFormatter;
import org.jf.dexlib2.iface.*;
import org.jf.dexlib2.iface.debug.*;
import org.jf.dexlib2.iface.instruction.DualReferenceInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.reference.*;
import org.jf.dexlib2.iface.value.ArrayEncodedValue;
import org.jf.dexlib2.iface.value.EncodedValue;
import org.jf.dexlib2.writer.ClassSection;
import org.jf.dexlib2.writer.DebugWriter;
import org.jf.dexlib2.writer.util.StaticInitializerUtil;
import org.jf.util.AbstractForwardSequentialList;
import org.jf.util.ExceptionWithContext;
import org.jf.util.collection.EmptyList;
import org.jf.util.collection.Iterables;
import org.jf.util.collection.ListUtil;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.function.Predicate;

public class ClassPool extends BasePool<String, PoolClassDef> implements ClassSection<CharSequence, CharSequence,
        TypeListPool.Key<? extends Collection<? extends CharSequence>>, PoolClassDef, Field, PoolMethod,
        Set<? extends Annotation>, ArrayEncodedValue> {

    public ClassPool( DexPool dexPool) {
        super(dexPool);
    }

    public void intern( ClassDef classDef) {
        PoolClassDef poolClassDef = new PoolClassDef(classDef);

        PoolClassDef prev = internedItems.put(poolClassDef.getType(), poolClassDef);
        if (prev != null) {
            throw new ExceptionWithContext("Class %s has already been interned", poolClassDef.getType());
        }

        dexPool.typeSection.intern(poolClassDef.getType());
        dexPool.typeSection.internNullable(poolClassDef.getSuperclass());
        dexPool.typeListSection.intern(poolClassDef.getInterfaces());
        dexPool.stringSection.internNullable(poolClassDef.getSourceFile());

        HashSet<String> fields = new HashSet<String>();
        for (Field field: poolClassDef.getFields()) {
            String fieldDescriptor = DexFormatter.INSTANCE.getShortFieldDescriptor(field);
            if (!fields.add(fieldDescriptor)) {
                throw new ExceptionWithContext("Multiple definitions for field %s->%s",
                        poolClassDef.getType(), fieldDescriptor);
            }
            dexPool.fieldSection.intern(field);

            EncodedValue initialValue = field.getInitialValue();
            if (initialValue != null) {
                dexPool.internEncodedValue(initialValue);
            }

            dexPool.annotationSetSection.intern(field.getAnnotations());

            ArrayEncodedValue staticInitializers = getStaticInitializers(poolClassDef);
            if (staticInitializers != null) {
                dexPool.encodedArraySection.intern(staticInitializers);
            }
        }

        HashSet<String> methods = new HashSet<String>();
        for (PoolMethod method: poolClassDef.getMethods()) {
            String methodDescriptor = DexFormatter.INSTANCE.getShortMethodDescriptor(method);
            if (!methods.add(methodDescriptor)) {
                throw new ExceptionWithContext("Multiple definitions for method %s->%s",
                        poolClassDef.getType(), methodDescriptor);
            }
            dexPool.methodSection.intern(method);
            internCode(method);
            internDebug(method);
            dexPool.annotationSetSection.intern(method.getAnnotations());

            for (MethodParameter parameter: method.getParameters()) {
                dexPool.annotationSetSection.intern(parameter.getAnnotations());
            }
        }

        dexPool.annotationSetSection.intern(poolClassDef.getAnnotations());
    }

    private void internCode( Method method) {
        // this also handles parameter names, which aren't directly tied to the MethodImplementation, even though the debug items are
        boolean hasInstruction = false;

        MethodImplementation methodImpl = method.getImplementation();
        if (methodImpl != null) {
            for (Instruction instruction: methodImpl.getInstructions()) {
                hasInstruction = true;
                if (instruction instanceof ReferenceInstruction) {
                    ReferenceInstruction refInst = (ReferenceInstruction)instruction;
                    internReference(refInst.getReference(), refInst.getReferenceType());
                }
                if (instruction instanceof DualReferenceInstruction) {
                    DualReferenceInstruction dualRefInst = (DualReferenceInstruction) instruction;
                    internReference(dualRefInst.getReference2(), dualRefInst.getReferenceType2());
                }
            }

            List<? extends TryBlock> tryBlocks = methodImpl.getTryBlocks();
            if (!hasInstruction && tryBlocks.size() > 0) {
                throw new ExceptionWithContext("Method %s has no instructions, but has try blocks.", method);
            }

            for (TryBlock<? extends ExceptionHandler> tryBlock: methodImpl.getTryBlocks()) {
                for (ExceptionHandler handler: tryBlock.getExceptionHandlers()) {
                    dexPool.typeSection.internNullable(handler.getExceptionType());
                }
            }
        }
    }

    private void internReference( Reference reference, int referenceType) {
        switch (referenceType) {
            case ReferenceType.STRING:
                dexPool.stringSection.intern((StringReference)reference);
                break;
            case ReferenceType.TYPE:
                dexPool.typeSection.intern(((TypeReference)reference).getType());
                break;
            case ReferenceType.FIELD:
                dexPool.fieldSection.intern((FieldReference) reference);
                break;
            case ReferenceType.METHOD:
                dexPool.methodSection.intern((MethodReference)reference);
                break;
            case ReferenceType.METHOD_PROTO:
                dexPool.protoSection.intern((MethodProtoReference)reference);
                break;
            case ReferenceType.CALL_SITE:
                dexPool.callSiteSection.intern((CallSiteReference) reference);
                break;
            default:
                throw new ExceptionWithContext("Unrecognized reference type: %d",
                        referenceType);
        }
    }

    private void internDebug( Method method) {
        for (MethodParameter param: method.getParameters()) {
            String paramName = param.getName();
            if (paramName != null) {
                dexPool.stringSection.intern(paramName);
            }
        }

        MethodImplementation methodImpl = method.getImplementation();
        if (methodImpl != null) {
            for (DebugItem debugItem: methodImpl.getDebugItems()) {
                switch (debugItem.getDebugItemType()) {
                    case DebugItemType.START_LOCAL:
                        StartLocal startLocal = (StartLocal)debugItem;
                        dexPool.stringSection.internNullable(startLocal.getName());
                        dexPool.typeSection.internNullable(startLocal.getType());
                        dexPool.stringSection.internNullable(startLocal.getSignature());
                        break;
                    case DebugItemType.SET_SOURCE_FILE:
                        dexPool.stringSection.internNullable(((SetSourceFile) debugItem).getSourceFile());
                        break;
                }
            }
        }
    }

    private List<PoolClassDef> sortedClasses = null;

    @Override
    public Collection<? extends PoolClassDef> getSortedClasses() {
        if (sortedClasses == null) {
            sortedClasses = ListUtil.sortedCopy(internedItems.values());
        }
        return sortedClasses;
    }

    
    @Override
    public Map.Entry<? extends PoolClassDef, Integer> getClassEntryByType( CharSequence name) {
        if (name == null) {
            return null;
        }

        final PoolClassDef classDef = internedItems.get(name.toString());
        if (classDef == null) {
            return null;
        }

        return new Map.Entry<PoolClassDef, Integer>() {
            @Override public PoolClassDef getKey() {
                return classDef;
            }

            @Override public Integer getValue() {
                return classDef.classDefIndex;
            }

            @Override public Integer setValue(Integer value) {
                return classDef.classDefIndex = value;
            }
        };
    }


    @Override
    public CharSequence getType( PoolClassDef classDef) {
        return classDef.getType();
    }

    @Override
    public int getAccessFlags( PoolClassDef classDef) {
        return classDef.getAccessFlags();
    }

    
    @Override
    public CharSequence getSuperclass( PoolClassDef classDef) {
        return classDef.getSuperclass();
    }

    
    @Override
    public TypeListPool.Key<List<String>> getInterfaces( PoolClassDef classDef) {
        return classDef.interfaces;
    }

    
    @Override
    public CharSequence getSourceFile( PoolClassDef classDef) {
        return classDef.getSourceFile();
    }

    
    @Override
    public ArrayEncodedValue getStaticInitializers(
             PoolClassDef classDef) {
        return StaticInitializerUtil.getStaticInitializers(classDef.getStaticFields());
    }


    @Override
    public Collection<? extends Field> getSortedStaticFields( PoolClassDef classDef) {
        return classDef.getStaticFields();
    }


    @Override
    public Collection<? extends Field> getSortedInstanceFields( PoolClassDef classDef) {
        return classDef.getInstanceFields();
    }


    @Override
    public Collection<? extends Field> getSortedFields( PoolClassDef classDef) {
        return classDef.getFields();
    }


    @Override
    public Collection<PoolMethod> getSortedDirectMethods( PoolClassDef classDef) {
        return classDef.getDirectMethods();
    }


    @Override
    public Collection<PoolMethod> getSortedVirtualMethods( PoolClassDef classDef) {
        return classDef.getVirtualMethods();
    }


    @Override
    public Collection<? extends PoolMethod> getSortedMethods( PoolClassDef classDef) {
        return classDef.getMethods();
    }

    @Override
    public int getFieldAccessFlags( Field field) {
        return field.getAccessFlags();
    }

    @Override
    public int getMethodAccessFlags( PoolMethod method) {
        return method.getAccessFlags();
    }


    @Override
    public Set<HiddenApiRestriction> getFieldHiddenApiRestrictions( Field field) {
        return field.getHiddenApiRestrictions();
    }


    @Override
    public Set<HiddenApiRestriction> getMethodHiddenApiRestrictions( PoolMethod poolMethod) {
        return poolMethod.getHiddenApiRestrictions();
    }

    
    @Override
    public Set<? extends Annotation> getClassAnnotations( PoolClassDef classDef) {
        Set<? extends Annotation> annotations = classDef.getAnnotations();
        if (annotations.size() == 0) {
            return null;
        }
        return annotations;
    }

    
    @Override
    public Set<? extends Annotation> getFieldAnnotations( Field field) {
        Set<? extends Annotation> annotations = field.getAnnotations();
        if (annotations.size() == 0) {
            return null;
        }
        return annotations;
    }

    
    @Override
    public Set<? extends Annotation> getMethodAnnotations( PoolMethod method) {
        Set<? extends Annotation> annotations = method.getAnnotations();
        if (annotations.size() == 0) {
            return null;
        }
        return annotations;
    }

    private static final Predicate<MethodParameter> HAS_PARAMETER_ANNOTATIONS = new Predicate<MethodParameter>() {
        @Override
        public boolean test(MethodParameter input) {
            return input.getAnnotations().size() > 0;
        }
    };

    private static final Function<MethodParameter, Set<? extends Annotation>> PARAMETER_ANNOTATIONS =
            new Function<MethodParameter, Set<? extends Annotation>>() {
                @Override
                public Set<? extends Annotation> apply(MethodParameter input) {
                    return input.getAnnotations();
                }
            };

    
    @Override
    public List<? extends Set<? extends Annotation>> getParameterAnnotations(
             final PoolMethod method) {
        final List<? extends MethodParameter> parameters = method.getParameters();
        boolean hasParameterAnnotations = Iterables.any(parameters, HAS_PARAMETER_ANNOTATIONS);

        if (hasParameterAnnotations) {
            return new AbstractForwardSequentialList<Set<? extends Annotation>>() {

                @Override
                public Iterator<Set<? extends Annotation>> iterator() {
                    return Iterables.transform(parameters, PARAMETER_ANNOTATIONS).iterator();
                }

                @Override public int size() {
                    return parameters.size();
                }
            };
        }
        return null;
    }

    
    @Override
    public Iterable<? extends DebugItem> getDebugItems( PoolMethod method) {
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            return impl.getDebugItems();
        }
        return null;
    }

    
    @Override
    public Iterable<CharSequence> getParameterNames( PoolMethod method) {
        return Iterables.transform(method.getParameters(), new Function<MethodParameter, CharSequence>() {
             @Override public CharSequence apply(MethodParameter input) {
                return input.getName();
            }
        });
    }

    @Override
    public int getRegisterCount( PoolMethod method) {
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            return impl.getRegisterCount();
        }
        return 0;
    }

    
    @Override
    public Iterable<? extends Instruction> getInstructions( PoolMethod method) {
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            return impl.getInstructions();
        }
        return null;
    }


    @Override
    public List<? extends TryBlock<? extends ExceptionHandler>> getTryBlocks(
             PoolMethod method) {
        MethodImplementation impl = method.getImplementation();
        if (impl != null) {
            return impl.getTryBlocks();
        }
        return EmptyList.of();
    }

    
    @Override
    public CharSequence getExceptionType( ExceptionHandler handler) {
        return handler.getExceptionType();
    }


    @Override
    public MutableMethodImplementation makeMutableMethodImplementation( PoolMethod poolMethod) {
        return new MutableMethodImplementation(poolMethod.getImplementation());
    }

    @Override
    public void setAnnotationDirectoryOffset( PoolClassDef classDef, int offset) {
        classDef.annotationDirectoryOffset = offset;
    }

    @Override
    public int getAnnotationDirectoryOffset( PoolClassDef classDef) {
        return classDef.annotationDirectoryOffset;
    }

    @Override
    public void setAnnotationSetRefListOffset( PoolMethod method, int offset) {
        method.annotationSetRefListOffset = offset;

    }
    @Override
    public int getAnnotationSetRefListOffset( PoolMethod method) {
        return method.annotationSetRefListOffset;
    }

    @Override
    public void setCodeItemOffset( PoolMethod method, int offset) {
        method.codeItemOffset = offset;
    }

    @Override
    public int getCodeItemOffset( PoolMethod method) {
        return method.codeItemOffset;
    }

    @Override
    public void writeDebugItem( DebugWriter<CharSequence, CharSequence> writer,
                                         DebugItem debugItem) throws IOException {
        switch (debugItem.getDebugItemType()) {
            case DebugItemType.START_LOCAL: {
                StartLocal startLocal = (StartLocal)debugItem;
                writer.writeStartLocal(startLocal.getCodeAddress(),
                        startLocal.getRegister(),
                        startLocal.getName(),
                        startLocal.getType(),
                        startLocal.getSignature());
                break;
            }
            case DebugItemType.END_LOCAL: {
                EndLocal endLocal = (EndLocal)debugItem;
                writer.writeEndLocal(endLocal.getCodeAddress(), endLocal.getRegister());
                break;
            }
            case DebugItemType.RESTART_LOCAL: {
                RestartLocal restartLocal = (RestartLocal)debugItem;
                writer.writeRestartLocal(restartLocal.getCodeAddress(), restartLocal.getRegister());
                break;
            }
            case DebugItemType.PROLOGUE_END: {
                writer.writePrologueEnd(debugItem.getCodeAddress());
                break;
            }
            case DebugItemType.EPILOGUE_BEGIN: {
                writer.writeEpilogueBegin(debugItem.getCodeAddress());
                break;
            }
            case DebugItemType.LINE_NUMBER: {
                LineNumber lineNumber = (LineNumber)debugItem;
                writer.writeLineNumber(lineNumber.getCodeAddress(), lineNumber.getLineNumber());
                break;
            }
            case DebugItemType.SET_SOURCE_FILE: {
                SetSourceFile setSourceFile = (SetSourceFile)debugItem;
                writer.writeSetSourceFile(setSourceFile.getCodeAddress(), setSourceFile.getSourceFile());
            }
            default:
                throw new ExceptionWithContext("Unexpected debug item type: %d", debugItem.getDebugItemType());
        }
    }

    @Override
    public int getItemIndex( PoolClassDef classDef) {
        return classDef.classDefIndex;
    }


    @Override
    public Collection<? extends Map.Entry<PoolClassDef, Integer>> getItems() {
        class MapEntry implements Map.Entry<PoolClassDef, Integer> {
             private final PoolClassDef classDef;

            public MapEntry( PoolClassDef classDef) {
                this.classDef = classDef;
            }

            @Override public PoolClassDef getKey() {
                return classDef;
            }

            @Override public Integer getValue() {
                return classDef.classDefIndex;
            }

            @Override public Integer setValue(Integer value) {
                int prev = classDef.classDefIndex;
                classDef.classDefIndex = value;
                return prev;
            }
        }

        return new AbstractCollection<Entry<PoolClassDef, Integer>>() {
             @Override public Iterator<Entry<PoolClassDef, Integer>> iterator() {
                return new Iterator<Entry<PoolClassDef, Integer>>() {
                    Iterator<PoolClassDef> iter = internedItems.values().iterator();

                    @Override public boolean hasNext() {
                        return iter.hasNext();
                    }

                    @Override public Entry<PoolClassDef, Integer> next() {
                        return new MapEntry(iter.next());
                    }

                    @Override public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override public int size() {
                return internedItems.size();
            }
        };
    }
}
