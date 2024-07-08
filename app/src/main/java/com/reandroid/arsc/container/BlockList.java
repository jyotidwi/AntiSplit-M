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
package com.reandroid.arsc.container;

import com.reandroid.arsc.base.Block;
import com.reandroid.arsc.base.BlockCounter;
import com.reandroid.arsc.base.BlockRefresh;
import com.reandroid.arsc.base.Creator;
import com.reandroid.utils.collection.ArrayCollection;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class BlockList<T extends Block> extends Block implements BlockRefresh {
    private ArrayCollection<T> mItems;
    private final Creator<? extends T> mCreator;

    public BlockList(Creator<? extends T> creator){
        super();
        mItems = ArrayCollection.empty();
        this.mCreator = creator;
    }
    public BlockList(){
        this(null);
    }

    public Creator<? extends T> getCreator() {
        return mCreator;
    }

    public void ensureSize(int size){
        if(size > this.size()){
            setSize(size);
        }
    }
    public void setSize(int size){
        if(size == 0){
            lockList();
        }else if(mCreator != null || size < size()){
            unlockList();
            mItems.setSize(size);
        }
    }

    public void setElements(T[] elements){
        if(elements == null || elements.length == 0){
            lockList();
            return;
        }
        unlockList();
        Creator<? extends T> creator = getCreator();
        int length = elements.length;
        for(int i = 0; i < length; i++){
            T item = elements[i];
            if(item == null && creator != null){
                item = creator.newInstanceAt(i);
                elements[i] = item;
            }
            onItemCreated(i, item);
        }
        mItems.setElements(elements);
        onChanged();
    }
    void onItemCreated(int index, T item){
        if(item == null){
            return;
        }
        item.setIndex(index);
        item.setParent(this);
    }

    public T createNext(){
        Creator<? extends T> creator = getCreator();
        T item = creator.newInstanceAt(size());
        add(item);
        return item;
    }
    public T getFirst(){
        int size = size();
        for(int i = 0; i < size; i++){
            T item = get(i);
            if(item != null){
                return item;
            }
        }
        return null;
    }
    public T getLast(){
        int size = size() - 1;
        for(int i = size; i >= 0; i--){
            T item = get(i);
            if(item != null){
                return item;
            }
        }
        return null;
    }

    public Iterator<T> clonedIterator(){
        return mItems.clonedIterator();
    }

    public Iterator<T> iterator(){
        return mItems.iterator();
    }
    public Iterator<T> iterator(int start, int length){
        return mItems.iterator(start, length);
    }
    public Iterator<T> iterator(com.abdurazaaqmohammed.AntiSplit.main.Predicate<? super T> filter){
        return mItems.iterator(filter);
    }
    public<T1> Iterator<T1> iterator(Class<T1> instance){
        return mItems.iterator(instance);
    }
    public void clearChildes(){
        if(mItems.isEmpty()){
            return;
        }
        int size = size();
        for (int i = 0; i < size; i++){
            remove(size() - 1, false);
        }
        lockList();
        onChanged();
    }
    public void destroy(){
        mItems.clear();
        lockList();
        onChanged();
    }
    public void sort(Comparator<? super T> comparator){
        if(size() < 2){
            return;
        }
        if(mItems.sortItems(comparator)){
            updateIndex();
        }
    }

    public int remove(com.abdurazaaqmohammed.AntiSplit.main.Predicate<? super T> filter){
        int count = 0;
        int minIndex = size();
        for(int i = 0; i < size(); i++){
            T item = get(i);
            if(filter.test(item)){
                remove(i, false);
                if(i < minIndex){
                    minIndex = i;
                }
                count ++;
                i --;
            }
        }
        updateIndex(minIndex);
        return count;
    }
    public T remove(int index){
        return remove(index, true);
    }
    private T remove(int index, boolean updateIndex){
        T item = mItems.remove(index);
        if(item == null){
            return null;
        }
        onPreRemove();
        item.setParent(null);
        item.setIndex(-1);
        if(updateIndex){
            updateIndex(index);
        }
        onChanged();
        return item;
    }
    public boolean remove(T item){
        return remove(item, true);
    }
    private boolean remove(T item, boolean updateIndex){
        onPreRemove();
        int index = -1;
        if(item != null){
            index = mItems.indexOfFast(item, item.getIndex());
            if(index < 0){
                index = mItems.indexOfFast(item);
            }
            item.setParent(null);
            item.setIndex(-1);
        }
        if(index < 0){
            index = mItems.indexOfFast(item);
        }
        boolean removed = mItems.remove(index) != null;
        if(updateIndex && removed){
            updateIndex(index);
        }
        onChanged();
        return removed;
    }
    public void onPreRemove(){

    }


    public void moveTo(T item, int index){
        if(index < 0){
            index = 0;
        }
        int i = item.getIndex();
        mItems.move(i, index);
        updateIndex(i, index);
    }
    public void set(int index, T item){
        if(item == null){
            return;
        }
        unlockList();
        item.setIndex(index);
        item.setParent(this);
        mItems.set(index, item);
        onChanged();
    }
    public void addAll(int index, T[] items){
        if(items == null){
            return;
        }
        int length = items.length;
        if(length == 0){
            return;
        }
        unlockList();
        mItems.addAll(index, items);
        for (T item : items) {
            if (item == null) {
                continue;
            }
            item.setIndex(index);
            item.setParent(this);
            index++;
        }
        updateIndex(index);
        onChanged();
    }
    public void add(int index, T item){
        if(item == null){
            return;
        }
        unlockList();
        item.setIndex(index);
        item.setParent(this);
        mItems.add(index, item);
        updateIndex(index);
        onChanged();
    }
    private void updateIndex(){
        updateIndex(0);
    }
    private boolean updateIndex(int start){
        return updateIndex(start, size());
    }
    private boolean updateIndex(int start, int end){
        if(start < 0){
            start = 0;
        }
        if(start > end){
            int i = start;
            start = end;
            end = i;
        }
        end = end + 1;
        boolean changed = false;
        int count = size();
        if(end > count){
            end = count;
        }
        List<T> items = this.getChildes();
        for (int i = start; i < end; i++){
            T item = items.get(i);
            if(item.getIndex() != i){
                item.setIndex(i);
                changed = true;
            }
        }
        return changed;
    }
    public void add(T item){
        if(item == null){
            return;
        }
        unlockList();
        int index = size();
        item.setIndex(index);
        item.setParent(this);
        mItems.add(item);
        onChanged();
    }
    public T get(int i){
        if(i>=mItems.size() || i<0){
            return null;
        }
        return mItems.get(i);
    }
    public int getCount(){
        return size();
    }
    public int size(){
        return mItems.size();
    }

    public void trimToSize(){
        mItems.trimToSize();
        if(mItems.size() == 0){
            lockList();
        }
    }
    public boolean contains(Object obj){
        return mItems.contains(obj);
    }
    public boolean containsExact(Object obj){
        return !mItems.containsFast(obj);
    }
    public Object[] toArray(){
        return mItems.toArray();
    }
    public <T1> T1[] toArray(T1[] ts) {
        return mItems.toArray(ts);
    }

    public List<T> getChildes(){
        return mItems;
    }
    private void lockList(){
        if(mItems.isImmutableEmpty()){
            return;
        }
        mItems = ArrayCollection.empty();
    }
    private void unlockList(){
        if(!mItems.isImmutableEmpty()){
            return;
        }
        mItems = new ArrayCollection<>();
        updateCreator();
    }
    private void updateCreator(){
        Creator<? extends T> creator = getCreator();
        if(creator == null){
            mItems.setInitializer(null);
            return;
        }
        ArrayCollection.Initializer<T> initializer = new ArrayCollection.Initializer<T>() {
            @Override
            public T createNewItem(int index) {
                T item = creator.newInstanceAt(index);
                onItemCreated(index, item);
                return item;
            }
            @Override
            public T[] newArray(int length) {
                return creator.newArrayInstance(length);
            }
        };
        mItems.setInitializer(initializer);
    }
    @Override
    public final void refresh(){
        if(isNull()){
            return;
        }
        trimToSize();
        onPreRefresh();
        refreshChildes();
        onRefreshed();
        onChanged();
    }
    protected void onPreRefresh(){
    }
    protected void onRefreshed(){
        onChanged();
    }
    public void onChanged(){
        mItems.onChanged();
    }
    private void refreshChildes(){
        Iterator<?> iterator = iterator();
        while (iterator.hasNext()){
            Object item = iterator.next();
            if(item instanceof BlockRefresh){
                ((BlockRefresh) item).refresh();
            }
        }
    }
    @Override
    public byte[] getBytes() {
        byte[] results = null;
        Iterator<T> iterator = iterator();
        while (iterator.hasNext()){
            results = addBytes(results, iterator.next().getBytes());
        }
        return results;
    }
    @Override
    public int countBytes() {
        int result = 0;
        int size = size();
        for (int i = 0; i < size; i++){
            T item = get(i);
            if(item == null){
                continue;
            }
            result += item.countBytes();
        }
        return result;
    }

    @Override
    public void onCountUpTo(BlockCounter counter) {
        if(counter.FOUND){
            return;
        }
        counter.setCurrent(this);
        if(counter.END == this){
            counter.FOUND = true;
            return;
        }
        int size = size();
        for (int i = 0; i < size && !counter.FOUND; i++){
            T item = get(i);
            if(item == null){
                continue;
            }
            item.onCountUpTo(counter);
        }
    }

    @Override
    protected int onWriteBytes(OutputStream stream) throws IOException {
        int result = 0;
        int size = size();
        for (int i = 0; i < size; i++){
            T item = get(i);
            if(item == null){
                continue;
            }
            result += item.writeBytes(stream);
        }
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        BlockList<?> blockList = (BlockList<?>) obj;
        return mItems.equals(blockList.mItems);
    }
    @Override
    public int hashCode() {
        return mItems.hashCode();
    }

    @Override
    public String toString() {
        return "size=" + size();
    }

    @SuppressWarnings("unchecked")
    public static<T1 extends Block> BlockList<T1> empty(){
        return (BlockList<T1>) empty_list;
    }

    private static final BlockList<?> empty_list = new BlockList<Block>(){
        @Override
        public void add(Block item) {
            throw new IllegalArgumentException("Empty BlockList");
        }
        @Override
        public void add(int index, Block item) {
            throw new IllegalArgumentException("Empty BlockList");
        }

        @Override
        public void setSize(int size) {
            if(size != 0){
                throw new IllegalArgumentException("Empty BlockList");
            }
        }
        @Override
        public int size() {
            return 0;
        }
    };
}
