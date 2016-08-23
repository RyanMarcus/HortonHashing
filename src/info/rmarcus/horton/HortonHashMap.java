package info.rmarcus.horton;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

public class HortonHashMap<K, V> extends AbstractMap<K, V> {
	private List<HortonHashBucket<K, V>> buckets;
	private HortonHashUtil hhu;

	public HortonHashMap(int numBuckets, int bucketCapacity) {
		init(numBuckets, bucketCapacity);
		hhu = new HortonHashUtil();
	}

	private void init(int numBuckets, int bucketCapacity) {
		buckets = new ArrayList<>(numBuckets);

		for (int i = 0; i < numBuckets; i++)
			buckets.add(new HortonHashBucket<K, V>(bucketCapacity));
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
	public V put(K key, V value) {
		try {
			return checkedPut(key, value);
		} catch (HortonHashMapFullException e) {
			System.out.println("Growing table from "  + buckets.size());

			// double the number of buckets and rehash everything
			Set<Entry<K, V>> data = entrySet();
			V toR = get(key);
			init(buckets.size() * 2, buckets.get(0).getCapacity());
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
	public V get(Object k) {
		V toR = null;

		@SuppressWarnings("unchecked")
		K key = (K) k;

		// check the primary bucket
		int primaryBucketIdx = getPrimaryBucket(key);
		HortonHashBucket<K, V> primaryBucket = buckets.get(primaryBucketIdx);
		toR = primaryBucket.get(key);

		// if this bucket doesn't have the key and it is
		// type a (no redirect list), then we don't have the key
		if (toR == null && primaryBucket.isTypeA())
			return null;

		// check to see if we had it in the primary block
		if (toR != null)
			return toR;

		// now check the secondary block
		int tag = getTag(key);
		short rFunc = primaryBucket.getRedirectListEntry(tag);

		int secondaryBucketIdx = getSecondaryBucket(primaryBucketIdx, rFunc, tag);
		HortonHashBucket<K, V> secondaryBucket = buckets.get(secondaryBucketIdx);

		toR = secondaryBucket.get(key);

		return toR;


	}

	private V checkedPut(K key, V value) throws HortonHashMapFullException {
		V toR = get(key);

		// first, we will get the index of the bucket indicated
		// by the primary hash function. If there's space in that
		// bucket, we will add this item as a primary entry.
		int bucketIdx = getPrimaryBucket(key);
		HortonHashBucket<K, V> primaryBucket = buckets.get(bucketIdx);

		if (primaryBucket.insertIfEmptyAvailable(key, value)) {
			// if we inserted the value as a primary key, we are done!
			return toR;
		}

		// try and make space in this bucket for the key.
		try {
			displaceItems(bucketIdx);
		} catch (HortonHashMapFullException e) {
			// TODO Auto-generated catch block
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
			doSecondaryInsert(bucketIdx, key, value);
			return toR;
		}

		// now that some items have been displaced, there is
		// space to store the new item as a primary entry
		primaryBucket.insertIfEmptyAvailable(key, value);


		return toR;
	}


	private void doSecondaryInsert(int bucketIdx, K key, V value) throws HortonHashMapFullException {
		// first, compute the slot in the redirect table
		int tagHash = getTag(key);

		HortonHashBucket<K, V> primaryBucket = buckets.get(bucketIdx);

		// check to see if we have an entry in this position yet
		// if we do, we have to use that as our secondary block.
		// if not, we can search for the best (lowest items) r fucn
		short rFunc = primaryBucket.getRedirectListEntry(tagHash);

		if (rFunc == -1) {
			// select the best rFunc
			rFunc = findBestRHashFunc(bucketIdx, tagHash);
			primaryBucket.setRedirectListEntry(tagHash, rFunc);
		}

		int secondaryBucketIdx = getSecondaryBucket(bucketIdx, rFunc, tagHash);
		HortonHashBucket<K, V> secondaryBucket = buckets.get(secondaryBucketIdx);

		if (!secondaryBucket.insertIfEmptyAvailable(key, value)) {
			// the best secondary bucket was already full!
			// try to displace some keys from it. if that fails,
			// give up and say we are full.

			// displaceItems will throw if it cannot move anything out
			displaceItems(secondaryBucketIdx);
			secondaryBucket.insertIfEmptyAvailable(key, value);

		}

	}

	private void displaceItems(int bucketIdx) throws HortonHashMapFullException {
		// we will build a map indexing primary buckets to 
		// the set of indexes of secondary items that are in
		// the bucket w/ index bucketIdx
		Map<Integer, Set<Integer>> primaryBuckets = new HashMap<>();

		HortonHashBucket<K, V> b = buckets.get(bucketIdx);
		for (int i = (b.isTypeA() ? 0 : 1); i < b.getCapacity(); i++) {
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
			K reprK = buckets.get(bucketIdx).getKey(reprIdx);
			int slot = getTag(reprK);
			short bestRfunc = findBestRHashFunc(e.getKey(), slot);
			int newBucketIdx = getSecondaryBucket(e.getKey(), bestRfunc, slot);
			int emptySlots = buckets.get(newBucketIdx).getNumEmptySlots();

			// make sure that switching to this new rFunc will give us
			// a bucket with enough empty places for all the keys
			// we would need to move there.
			if (emptySlots < e.getValue().size())
				continue;

			// now move the items in e to this new bucket,
			// and change the rFunc entry in their primary bucket
			for (int itemIdx : e.getValue()) {
				KVPair<K, V> kp = buckets.get(bucketIdx).getKVPair(itemIdx);
				if (!buckets.get(newBucketIdx).insertIfEmptyAvailable(kp.key, kp.value))
					throw new HortonHashMapRuntimeException("Could not insert into bucket that should've had empty slots when relocating secondary keys in bucket " + bucketIdx);

			}

			// we've moved the items, now change the entry.
			buckets.get(e.getKey()).setRedirectListEntry(slot, bestRfunc);

			return;
		}

		throw new HortonHashMapFullException("Could not find any keys that could be moved out of bucket " + bucketIdx);
	}

	private short findBestRHashFunc(int bucketIdx, int slotIdx) {
		/*System.out.println("primary = " + bucketIdx + ", slot = " + slotIdx);
		for (short i = 0; i < HortonHashUtil.NUM_R_HASH_FUNCS; i++) {
			System.out.println("R = " + i + " gives bucket " + getSecondaryBucket(bucketIdx, i, slotIdx));
		}*/

		return IntStream.range(0, HortonHashUtil.NUM_R_HASH_FUNCS)
				.mapToObj(i -> i)
				.max((a, b) -> {
					int b1 = getSecondaryBucket(bucketIdx, a.shortValue(), slotIdx);
					int b2 = getSecondaryBucket(bucketIdx, b.shortValue(), slotIdx);

					return buckets.get(b1).getNumEmptySlots() -
							buckets.get(b2).getNumEmptySlots();
				}).get().shortValue();
	}

	private boolean isPrimaryEntry(int bucketIdx, int itemIdx) {
		return getPrimaryBucket(buckets.get(bucketIdx).getKey(itemIdx)) == bucketIdx;
	}

	private int getPrimaryBucket(K key) {
		return hhu.primaryHash(key) % buckets.size();
	}

	private int getPrimaryBucket(int bucketIdx, int itemIdx) {
		return getPrimaryBucket(buckets.get(bucketIdx).getKey(itemIdx));
	}

	private int getSecondaryBucket(int primaryBucketIdx, short rFuncIdx, int slotIdx) {
		return hhu.rHashFunc(primaryBucketIdx, slotIdx, rFuncIdx) % buckets.size();
	}


	private int getTag(K o) {
		return hhu.tagHash(o) % RedirectList.REDIRECT_TABLE_SIZE;
	}


	public static void main(String[] args) throws HortonHashMapFullException {
		HortonHashMap<Integer, Integer> m = new HortonHashMap<Integer, Integer>(1, 5);

		for (int i = 1; i < 100; i++) {
			//System.out.println("Key: " + i);
			m.put(i, i*5);
			for (int j = 1; j <= i; j++) {
				if (m.get(j) == null)
					System.out.println("Lost key " + j);
			}
		}


	}


}
