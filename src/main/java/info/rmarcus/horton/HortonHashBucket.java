package info.rmarcus.horton;

import java.util.AbstractMap.SimpleEntry;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class HortonHashBucket<K, V> {
	private boolean typeA;
	//private List<KVPair<K, V>> entries;
	private KVPair<K, V>[] entries;

	@SuppressWarnings("unchecked")
	public HortonHashBucket(int capacity) {
		typeA = true;
		entries = new KVPair[capacity];
	}


	
	public int getNumEmptySlots() {
		int toR = 0;
		for (int i = 0; i < entries.length; i++)
			if (entries[i] == null) toR++;
		
		return toR;
	}
	
	public boolean insertIfEmptyAvailable(K key, V value) {
		for (int i = 0; i < entries.length; i++) {
			if (entries[i] == null) {
				entries[i] = new KVPair<K, V>(key, value);
				return true;
			}
		}
		
		return false;
	}
	
	public V get(K key) {
		return getKVPair(key).value;
	}
	
	public KVPair<K, V> getKVPair(K key) {
		for (int i = (typeA ? 0 : 1); i < entries.length; i++) {
			if (entries[i] == null)
				continue; 
			
			if (entries[i].key.equals(key))
				return entries[i];
		}
		
		return null;
	}
	
	public KVPair<K, V> convertToTypeB() {
		KVPair<K, V> toR = entries[0];
		entries[0] = new RedirectList<K, V>();
		typeA = false;
		return toR;
	}
	
	public void revertToTypeA(KVPair<K, V> toAddBack) {
		entries[0] = toAddBack;
		typeA = true;
	}

	public KVPair<K, V> getKVPair(int idx) {
		return (typeA ? entries[idx] : entries[idx + 1]);
	}
	
	public K getKey(int idx) {
		return getKVPair(idx).key;
	}
	
	public int getCapacity() {
		return (typeA ? entries.length : entries.length - 1);
	}

	public boolean isTypeA() {
		return typeA;
	}
	
	public void setRedirectListEntry(int idx, short value) {
		if (typeA)
			throw new HortonHashMapRuntimeException("Trying to set a redirect table entry on a type A bucket");
		
		((RedirectList<K, V>) entries[0]).redirectList[idx] = value;
	}
	
	public short getRedirectListEntry(int idx) {
		if (typeA)
			throw new HortonHashMapRuntimeException("Trying to set a redirect table entry on a type A bucket");
	
		return ((RedirectList<K, V>) entries[0]).redirectList[idx];

	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return Arrays.stream(entries)
				.filter(kv -> kv != null)
				.filter(kv -> kv.key != null)
				.map(kv -> new SimpleEntry<K, V>(kv.key, kv.value))
				.collect(Collectors.toSet());
	}

	public void clearKVPair(int itemIdx) {
		entries[itemIdx] = null;
	}
	

}
