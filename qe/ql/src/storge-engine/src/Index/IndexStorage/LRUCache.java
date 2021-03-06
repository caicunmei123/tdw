/**
* Tencent is pleased to support the open source community by making TDW available.
* Copyright (C) 2014 THL A29 Limited, a Tencent company. All rights reserved.
* Licensed under the Apache License, Version 2.0 (the "License"); you may not use 
* this file except in compliance with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software distributed 
* under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS 
* OF ANY KIND, either express or implied. See the License for the specific language governing
* permissions and limitations under the License.
*/
package IndexStorage;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LRUCache<K, V> implements Serializable {

  private static final long serialVersionUID = 1L;
  private static final int DEFAULT_CAPACITY = 100;
  protected Map<K, ValueEntry> map;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();
  private final Lock readLock = lock.readLock();
  private final Lock writeLock = lock.writeLock();
  private volatile int maxCapacity;
  public static int MINI_ACCESS = 5;

  public LRUCache() {
    this(DEFAULT_CAPACITY);
  }

  public LRUCache(int capacity) {
    if (capacity <= 0)
      throw new RuntimeException("cache capacity must bigger than 0");
    this.maxCapacity = capacity;
    this.map = new HashMap<K, ValueEntry>(maxCapacity);
  }

  public boolean containsKey(K key) {
    try {
      readLock.lock();
      return this.map.containsKey(key);
    } finally {
      readLock.unlock();
    }
  }

  public V put(K key, V value) {
    try {
      writeLock.lock();
      if ((map.size() > maxCapacity - 1) && !map.containsKey(key)) {
        Set<Map.Entry<K, ValueEntry>> entries = this.map.entrySet();
        removeRencentlyLeastAccess(entries);
      }
      ValueEntry new_value = new ValueEntry(value);
      ValueEntry old_value = map.put(key, new_value);
      if (old_value != null) {
        new_value.count = old_value.count;
        return old_value.value;
      } else
        return null;
    } finally {
      writeLock.unlock();
    }
  }

  protected void removeRencentlyLeastAccess(
      Set<Map.Entry<K, ValueEntry>> entries) {
    long least = 0;
    long earliest = 0;
    K toBeRemovedByCount = null;
    K toBeRemovedByTime = null;
    Iterator<Map.Entry<K, ValueEntry>> it = entries.iterator();
    if (it.hasNext()) {
      Map.Entry<K, ValueEntry> valueEntry = it.next();
      least = valueEntry.getValue().count.get();
      toBeRemovedByCount = valueEntry.getKey();
      earliest = valueEntry.getValue().lastAccess.get();
      toBeRemovedByTime = valueEntry.getKey();
    }
    while (it.hasNext()) {
      Map.Entry<K, ValueEntry> valueEntry = it.next();
      if (valueEntry.getValue().count.get() < least) {
        least = valueEntry.getValue().count.get();
        toBeRemovedByCount = valueEntry.getKey();
      }
      if (valueEntry.getValue().lastAccess.get() < earliest) {
        earliest = valueEntry.getValue().count.get();
        toBeRemovedByTime = valueEntry.getKey();
      }
    }
    if (least > MINI_ACCESS) {
      map.remove(toBeRemovedByTime);
    } else {
      map.remove(toBeRemovedByCount);
    }
  }

  public V get(K key) {
    try {
      readLock.lock();
      V value = null;
      ValueEntry valueEntry = map.get(key);
      if (valueEntry != null) {
        valueEntry.updateLastAccess();
        valueEntry.count.incrementAndGet();
        value = valueEntry.value;
      }
      return value;
    } finally {
      readLock.unlock();
    }
  }

  public void clear() {
    try {
      writeLock.lock();
      map.clear();
    } finally {
      writeLock.unlock();
    }
  }

  public int size() {
    try {
      readLock.lock();
      return map.size();
    } finally {
      readLock.unlock();
    }
  }

  public long getCount(K key) {
    try {
      readLock.lock();
      ValueEntry valueEntry = map.get(key);
      if (valueEntry != null) {
        return valueEntry.count.get();
      }
      return 0;
    } finally {
      readLock.unlock();
    }
  }

  public Collection<Map.Entry<K, V>> getAll() {
    try {
      readLock.lock();
      Set<K> keys = map.keySet();
      Map<K, V> tmp = new HashMap<K, V>();
      for (K key : keys) {
        tmp.put(key, map.get(key).value);
      }
      return new ArrayList<Map.Entry<K, V>>(tmp.entrySet());
    } finally {
      readLock.unlock();
    }
  }

  class ValueEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private V value;

    private AtomicLong count;

    private AtomicLong lastAccess;

    public ValueEntry(V value) {
      this.value = value;
      this.count = new AtomicLong(0);
      lastAccess = new AtomicLong(System.nanoTime());
    }

    public void updateLastAccess() {
      this.lastAccess.set(System.nanoTime());
    }
  }
}
