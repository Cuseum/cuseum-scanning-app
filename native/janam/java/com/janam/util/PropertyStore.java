package com.janam.util;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.util.SparseArray;

/**
 * PropertyStore<K, V>
 * <p>
 * Generic, leak-safe store for mapping keys to properties.
 * Keys are held via WeakReference and automatically removed when garbage collected.
 *
 * @param <K> key type
 * @param <V> value type
 */
public final class PropertyStore<K, V>
{

	/**
	 * SparseArray keyed by key.hashCode().
	 * Each bucket contains a list of entries with weak references to keys.
	 */
	private final SparseArray<List<Entry>> store = new SparseArray<List<Entry>>();

	/**
	 * ReferenceQueue used to detect garbage collected keys.
	 */
	private final ReferenceQueue<K> refQueue = new ReferenceQueue<K>();

	/**
	 * Constructor
	 */
	public PropertyStore()
	{
		// No-op
	}

	/**
	 * Store or replace a property for a key.
	 *
	 * @param key   the key object
	 * @param value the property value
	 */
	public synchronized void put(K key, V value)
	{
		cleanup();

		if (key == null)
			return;

		int hash = key.hashCode();

		List<Entry> bucket = store.get(hash);
		if (bucket == null)
		{
			bucket = new ArrayList<Entry>();
			store.put(hash, bucket);
		}

		Iterator<Entry> it = bucket.iterator();
		while (it.hasNext())
		{
			Entry entry = it.next();
			K     k     = entry.keyRef.get();
			if (k == null)
			{
				it.remove();
				continue;
			}
			if (k == key)
			{
				entry.value = value;
				return;
			}
		}

		bucket.add(new Entry(key, value, refQueue));
	}

	/**
	 * Retrieve a property for a key.
	 *
	 * @param key the key object
	 * @return the associated property, or null if not found
	 */
	public synchronized V get(K key, V defaultValue)
	{
		cleanup();

		if (key == null)
			return defaultValue;

		int hash = key.hashCode();

		List<Entry> bucket = store.get(hash);
		if (bucket == null)
		{
			return defaultValue;
		}

		Iterator<Entry> it = bucket.iterator();
		while (it.hasNext())
		{
			Entry entry = it.next();
			K     k     = entry.keyRef.get();
			if (k == null)
			{
				it.remove();
				continue;
			}
			if (k == key)
			{
				return entry.value;
			}
		}

		return defaultValue;
	}

	/**
	 * Remove a property for a key.
	 *
	 * @param key the key object
	 */
	public synchronized void remove(K key)
	{
		cleanup();

		if (key == null)
			return;

		int hash = key.hashCode();

		List<Entry> bucket = store.get(hash);
		if (bucket == null)
		{
			return;
		}

		Iterator<Entry> it = bucket.iterator();
		while (it.hasNext())
		{
			Entry entry = it.next();
			K     k     = entry.keyRef.get();
			if (k == null || k == key)
			{
				it.remove();
			}
		}

		if (bucket.isEmpty())
		{
			store.remove(hash);
		}
	}

	/**
	 * Clear all entries.
	 */
	public synchronized void clear()
	{
		store.clear();
		while (refQueue.poll() != null)
		{
			// drain queue
		}
	}

	/**
	 * Internal entry class containing a weak reference to the key.
	 */
	public final class Entry
	{
		final KeyReference keyRef;
		V value;

		public Entry(K key, V value, ReferenceQueue<K> queue)
		{
			this.keyRef = new KeyReference(key, queue, key.hashCode());
			this.value  = value;
		}
	}

	/**
	 * WeakReference subclass that keeps the original hash code.
	 */
	private final class KeyReference extends WeakReference<K>
	{
		public final int hash;

		public KeyReference(K key, ReferenceQueue<K> queue, int hash)
		{
			super(key, queue);
			this.hash = hash;
		}
	}

	/**
	 * Cleanup stale entries from the ReferenceQueue.
	 */
	@SuppressWarnings("unchecked")
	public void cleanup()
	{
		KeyReference ref;
		while ((ref = (KeyReference) refQueue.poll()) != null)
		{
			List<Entry> bucket = store.get(ref.hash);
			if (bucket == null)
			{
				continue;
			}

			Iterator<Entry> it = bucket.iterator();
			while (it.hasNext())
			{
				Entry entry = it.next();
				if (entry.keyRef == ref)
				{
					it.remove();
					break;
				}
			}

			if (bucket.isEmpty())
			{
				store.remove(ref.hash);
			}
		}
	}

	@FunctionalInterface
	public interface IterationCallback<K>
	{
		void onIterate(K key);
	}

	public synchronized void forEach(IterationCallback<K> consumer)
	{
		cleanup();

		for (int i = 0; i < store.size(); i++)
		{
			List<Entry> bucket = store.valueAt(i);
			if (bucket == null) continue;

			Iterator<Entry> it = bucket.iterator();
			while (it.hasNext())
			{
				Entry entry = it.next();
				K     key   = entry.keyRef.get();

				if (key == null)
				{
					it.remove();
					continue;
				}

				consumer.onIterate(key);
			}
		}
	}
}
