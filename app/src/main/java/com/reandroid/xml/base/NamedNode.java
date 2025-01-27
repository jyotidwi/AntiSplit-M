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
package com.reandroid.xml.base;

import android.text.TextUtils;

import com.reandroid.common.Namespace;

public interface NamedNode {

    String getName();
    void setName(String name);

    Namespace getNamespace();
    void setNamespace(Namespace namespace);

    default String getName(boolean includePrefix){
        String name = getName();
        if(includePrefix){
            String prefix = getPrefix();
            if(!TextUtils.isEmpty(prefix)){
                name = prefix + ":" + name;
            }
        }
        return name;
    }
    default String getPrefix(){
        Namespace namespace = getNamespace();
        if(namespace != null){
            return namespace.getPrefix();
        }
        return null;
    }
    default String getUri(){
        Namespace namespace = getNamespace();
        if(namespace != null){
            return namespace.getUri();
        }
        return null;
    }
}
