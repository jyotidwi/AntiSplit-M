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

package org.jf.dexlib2.builder;

import org.jf.dexlib2.base.BaseTryBlock;
import org.jf.dexlib2.iface.reference.TypeReference;
import org.jf.util.collection.ListUtil;

import java.util.List;

public class BuilderTryBlock extends BaseTryBlock<BuilderExceptionHandler> {
    // We only ever have one exception handler per try block. They are later merged as needed in TryListBuilder

    public final BuilderExceptionHandler exceptionHandler;

    public final Label start;
    // The end location is exclusive, it should point to the codeAddress of the instruction immediately after the last
    // covered instruction.

    public final Label end;

    public BuilderTryBlock( Label start,  Label end,  String exceptionType,
                            Label handler) {
        this.start = start;
        this.end = end;
        this.exceptionHandler = BuilderExceptionHandler.newExceptionHandler(exceptionType, handler);
    }

    public BuilderTryBlock( Label start,  Label end,  TypeReference exceptionType,
                            Label handler) {
        this.start = start;
        this.end = end;
        this.exceptionHandler = BuilderExceptionHandler.newExceptionHandler(exceptionType, handler);
    }

    public BuilderTryBlock( Label start,  Label end,  Label handler) {
        this.start = start;
        this.end = end;
        this.exceptionHandler = BuilderExceptionHandler.newExceptionHandler(handler);
    }

    @Override
    public int getStartCodeAddress() {
        return start.getCodeAddress();
    }

    @Override
    public int getCodeUnitCount() {
        return end.getCodeAddress() - start.getCodeAddress();
    }


    @Override
    public List<? extends BuilderExceptionHandler> getExceptionHandlers() {
        return ListUtil.newArrayList(exceptionHandler);
    }
}
