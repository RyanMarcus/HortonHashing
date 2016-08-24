package info.rmarcus.horton;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class HortonHashMap<K, V> extends AbstractMap<K, V> {
	private HortonHashBucket<K, V>[] buckets;
	private HortonHashUtil hhu;
	private int capacity;
	
	private long primaryBlockGets = 0;
	private long secondaryBlockGets = 0;

	public HortonHashMap(int numBuckets, int bucketCapacity) {
		init(numBuckets, bucketCapacity);
		hhu = new HortonHashUtil();
		this.capacity = bucketCapacity;
	}

	@SuppressWarnings("unchecked")
	private void init(int numBuckets, int bucketCapacity) {
		buckets = new HortonHashBucket[numBuckets];

		for (int i = 0; i < numBuckets; i++)
			buckets[i] = new HortonHashBucket<K, V>(bucketCapacity);
	}

	@Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
		Set<Entry<K, V>> toR = new HashSet<>();

		for (HortonHashBucket<K, V> hhb : buckets) {
			toR.addAll(hhb.entrySet());
		}

		return toR;
	}


	@Override
	public boolean containsKey(Object k) {
		return get(k) != null;
	}
	
	@Override
	public V put(K key, V value) {
		try {
			return checkedPut(key, value);
		} catch (HortonHashMapFullException e) {
			// double the number of buckets and rehash everything
			Set<Entry<K, V>> data = entrySet();
			V toR = get(key);
			init(buckets.length * 2, capacity);
			try {
				for (Entry<K, V> en : data) {
					checkedPut(en.getKey(), en.getValue());
				}
				checkedPut(key, value);
			} catch (HortonHashMapFullException e1) {
				// upgrade this exception to a runtime exception
				throw new HortonHashMapRuntimeException("Hash map full even after resize: " + e.getMessage());
			}
			return toR;
		}
	}

	@Override
	public V get(Object key) {
		KVPair<K, V> toR = getKV(key);
		
		if (toR == null)
			return null;
		
		return toR.value;
	}
	
	private KVPair<K, V> getKV(Object k) {
		KVPair<K, V> toR = null;
		
		@SuppressWarnings("unchecked")
		K key = (K) k;

		// check the primary bucket
		int primaryBucketIdx = getPrimaryBucket(key);
		HortonHashBucket<K, V> primaryBucket = buckets[primaryBucketIdx];
		toR = primaryBucket.getKVPair(key);
				
		// check to see if we had it in the primary block
		if (toR != null) {
			primaryBlockGets++;
			return toR;
		}
		
		// if this bucket doesn't have the key and it is
		// type a (no redirect list), then we don't have the key
		if (primaryBucket.isTypeA()) {
			primaryBlockGets++;
			return null;
		}

		secondaryBlockGets++;
		// now check the secondary block
		int tag = getTag(key);
		short rFunc = primaryBucket.getRedirectListEntry(tag);
		
		if (rFunc == -1)
			return null;
		
		int secondaryBucketIdx = getSecondaryBucket(primaryBucketIdx, rFunc, tag);
		HortonHashBucket<K, V> secondaryBucket = buckets[secondaryBucketIdx];
				
		toR = secondaryBucket.getKVPair(key);

		return toR;


	}

	private V checkedPut(K key, V value) throws HortonHashMapFullException {			
		KVPair<K, V> toR = getKV(key);
		
		if (toR != null) {
			V oldVal = toR.value;
			toR.value = value;
			return oldVal;
		}

		// first, we will get the index of the bucket indicated
		// by the primary hash function. If there's space in that
		// bucket, we will add this item as a primary entry.
		int bucketIdx = getPrimaryBucket(key);
		HortonHashBucket<K, V> primaryBucket = buckets[bucketIdx];
						
		if (primaryBucket.insertIfEmptyAvailable(key, value)) {
			// if we inserted the value as a primary key, we are done!
			return null;
		}
				
		// try and make space in this bucket for the key.
		try {
			displaceItems(bucketIdx);
		} catch (HortonHashMapFullException e) {
			// this is the case where no items can be moved,
			// so we need to insert this item as a secondary entry.
			
			// if the item is type A, we need to convert it into a 
			// type B bucket and then do two secondary inserts, one for
			// the old item and one for the current item.
			if (primaryBucket.isTypeA()) {
				KVPair<K, V> toSaveLater = primaryBucket.convertToTypeB();
				try {
					doSecondaryInsert(bucketIdx, toSaveLater.key, toSaveLater.value);
				} catch (HortonHashMapFullException e1) {
					// there's no room for this item, which means
					// we can't turn this bucket into a type B bucket
					// TODO we can try moving out an item that isn't in position 0
					// turn the bucket back into a type A, and put this key back
					primaryBucket.revertToTypeA(toSaveLater);
					throw new HortonHashMapFullException("Could not convert bucket " + bucketIdx + " to type B because the 0th item could not be displaced");
				}
			}

			// at this point, we have a type B bucket with no
			// items that can be displaced. try to do a secondary
			// insert, and, if we can't, say the hash map is full.
			// doSecondaryInsert will throw if it is full
			//System.out.println("Inserting key as a secondary item");
			doSecondaryInsert(bucketIdx, key, value);
			return null;
		}


		// now that some items have been displaced, there is
		// space to store the new item as a primary entry
		if (!primaryBucket.insertIfEmptyAvailable(key, value)) {
			throw new HortonHashMapRuntimeException("Could not place value into primary bucket even after displacement moved items out");
		}
		
		return null;
	}


	private void doSecondaryInsert(int bucketIdx, K key, V value) throws HortonHashMapFullException {
		// first, compute the slot in the redirect table
		int tagHash = getTag(key);
		
		HortonHashBucket<K, V> primaryBucket = buckets[bucketIdx];

		// check to see if we have an entry in this position yet
		// if we do, we have to use that as our secondary block.
		// if not, we can search for the best (lowest items) r fucn
		byte rFunc = primaryBucket.getRedirectListEntry(tagHash);

		if (rFunc == -1) {
			// select the best rFunc
			rFunc = findBestRHashFunc(bucketIdx, tagHash);
			primaryBucket.setRedirectListEntry(tagHash, rFunc);
		}
		
		int secondaryBucketIdx = getSecondaryBucket(bucketIdx, rFunc, tagHash);
		HortonHashBucket<K, V> secondaryBucket = buckets[secondaryBucketIdx];
		
		if (!secondaryBucket.insertIfEmptyAvailable(key, value)) {
			// the best secondary bucket was already full!
			// try to displace some keys from it. if that fails,
			// give up and say we are full.

			// displaceItems will throw if it cannot move anything out
			displaceItems(secondaryBucketIdx);
			
			// it is possible that displaceItems changed the rfunc we
			// are using. check to see if that's the case...
			if (rFunc != primaryBucket.getRedirectListEntry(tagHash)) {
				// we changed the rfunc for the element we are currently 
				// inserting! we can try one more time to insert into
				// the new bucket that the rfunc gives us. If that doesn't
				// work, we'll give up and say we are full.
				rFunc = primaryBucket.getRedirectListEntry(tagHash);
				secondaryBucketIdx = getSecondaryBucket(bucketIdx, rFunc, tagHash);
				secondaryBucket = buckets[secondaryBucketIdx];
			}
			
			if (!secondaryBucket.insertIfEmptyAvailable(key, value)) {
				// if this fails, it is because we displaced keys from
				// one secondary bucket into another, and that new secondary
				// bucket is also full. Declare the hash table full.
				throw new HortonHashMapFullException("Best items to displace used same redirect entry as item to insert, no space in new secondary block either.");
			}


		}

	}

	private void displaceItems(int bucketIdx) throws HortonHashMapFullException {
		// we will build a map indexing primary buckets to 
		// the set of indexes of secondary items that are in
		// the bucket w/ index bucketIdx
		Map<Integer, Set<Integer>> primaryBuckets = new HashMap<>();

		HortonHashBucket<K, V> b = buckets[bucketIdx];
		for (int i = 0; i < b.getCapacity(); i++) {
			// never displace a primary entry
			if (isPrimaryEntry(bucketIdx, i))
				continue;

			// if it is a secondary entry, add it to the map.
			int primaryBucketIdx = getPrimaryBucket(bucketIdx, i);
			primaryBuckets.putIfAbsent(primaryBucketIdx, new HashSet<>());
			primaryBuckets.get(primaryBucketIdx).add(i);
		}

		for (Entry<Integer, Set<Integer>> e : primaryBuckets.entrySet()) {
			// find the best new rFunc for these entries
			int reprIdx = e.getValue().iterator().next();
			K reprK = buckets[bucketIdx].getKey(reprIdx);
			int slot = getTag(reprK);
			byte bestRfunc = findBestRHashFunc(e.getKey(), slot);
			int newBucketIdx = getSecondaryBucket(e.getKey(), bestRfunc, slot);
			int emptySlots = buckets[newBucketIdx].getNumEmptySlots();

			// make sure that switching to this new rFunc will give us
			// a bucket with enough empty places for all the keys
			// we would need to move there.
			if (emptySlots < e.getValue().size())
				continue;
			
			// now move the items in e to this new bucket,
			// and change the rFunc entry in their primary bucket
			for (int itemIdx : e.getValue()) {
				KVPair<K, V> kp = buckets[bucketIdx].getKVPair(itemIdx);
				// add the item to the new bucket
				if (!buckets[newBucketIdx].insertIfEmptyAvailable(kp.key, kp.value)) 
					throw new HortonHashMapRuntimeException("Could not insert into bucket that should've had empty slots when relocating secondary keys in bucket " + bucketIdx);
				
				// remove the item from the old bucket
				buckets[bucketIdx].clearKVPair(itemIdx);
			}
			

			// we've moved the items, now change the entry.
			buckets[e.getKey()].setRedirectListEntry(slot, bestRfunc);

			return;
		}

		throw new HortonHashMapFullException("Could not find any keys that could be moved out of bucket " + bucketIdx);
	}

	private byte findBestRHashFunc(int bucketIdx, int slotIdx) {
		return IntStream.range(0, HortonHashUtil.NUM_R_HASH_FUNCS)
				.mapToObj(i -> i)
				.max((a, b) -> {
					int b1 = getSecondaryBucket(bucketIdx, a.shortValue(), slotIdx);
					int b2 = getSecondaryBucket(bucketIdx, b.shortValue(), slotIdx);

					return buckets[b1].getNumEmptySlots() -
							buckets[b2].getNumEmptySlots();
				}).get().byteValue();
	}

	private boolean isPrimaryEntry(int bucketIdx, int itemIdx) {
		return getPrimaryBucket(buckets[bucketIdx].getKey(itemIdx)) == bucketIdx;
	}

	private int getPrimaryBucket(K key) {
		return hhu.primaryHash(key) % buckets.length;
	}

	private int getPrimaryBucket(int bucketIdx, int itemIdx) {
		return getPrimaryBucket(buckets[bucketIdx].getKey(itemIdx));
	}

	private int getSecondaryBucket(int primaryBucketIdx, short rFuncIdx, int slotIdx) {
		return hhu.rHashFunc(primaryBucketIdx, slotIdx, rFuncIdx) % buckets.length;
	}


	private int getTag(K o) {
		return hhu.tagHash(o) % RedirectList.REDIRECT_TABLE_SIZE;
	}

	
	public void dumpStats() {
		System.out.println("Primary block gets: " + primaryBlockGets);
		System.out.println("Secondary block gets: " + secondaryBlockGets);

	}



}
