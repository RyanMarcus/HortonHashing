package info.rmarcus.horton;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class HortonHashBucket<K, V> {
	private boolean typeA;
	private List<KVPair<K, V>> entries;

	public HortonHashBucket(int capacity) {
		entries = new ArrayList<>(capacity);
		typeA = true;
		for (int i = 0; i < capacity; i++)
			entries.add(null);
	}

	public boolean hasEmptySlot() {
		return entries.stream().anyMatch(kv -> kv == null);
	}
	
	public int getNumEmptySlots() {
		return (int) entries.stream()
				.filter(kv -> kv == null)
				.count();
	}
	
	public boolean insertIfEmptyAvailable(K key, V value) {
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) == null) {
				entries.set(i, new KVPair<K, V>(key, value));
				return true;
			}
		}
		
		return false;
	}
	
	public V get(K key) {
		for (int i = (typeA ? 0 : 1); i < getCapacity(); i++) {
			if (entries.get(i) == null)
				continue; 
			
			if (entries.get(i).key.equals(key))
				return entries.get(i).value;
		}
		
		return null;
	}
	
	public KVPair<K, V> convertToTypeB() {
		KVPair<K, V> toR = entries.get(0);
		entries.set(0, new RedirectList<K, V>());
		typeA = false;
		return toR;
	}
	
	public void revertToTypeA(KVPair<K, V> toAddBack) {
		entries.set(0, toAddBack);
		typeA = true;
	}

	public KVPair<K, V> getKVPair(int idx) {
		return (typeA ? entries.get(idx) : entries.get(idx + 1));
	}
	
	public K getKey(int idx) {
		return getKVPair(idx).key;
	}
	
	public int getCapacity() {
		return (typeA ? entries.size() : entries.size() - 1);
	}

	public boolean isTypeA() {
		return typeA;
	}
	
	public void setRedirectListEntry(int idx, short value) {
		if (typeA)
			throw new HortonHashMapRuntimeException("Trying to set a redirect table entry on a type A bucket");
		
		((RedirectList<K, V>) entries.get(0)).redirectList[idx] = value;
	}
	
	public short getRedirectListEntry(int idx) {
		if (typeA)
			throw new HortonHashMapRuntimeException("Trying to set a redirect table entry on a type A bucket");
	
		return ((RedirectList<K, V>) entries.get(0)).redirectList[idx];

	}

	public Set<java.util.Map.Entry<K, V>> entrySet() {
		return entries.stream()
				.filter(kv -> kv != null)
				.filter(kv -> kv.key != null)
				.map(kv -> new SimpleEntry<K, V>(kv.key, kv.value))
				.collect(Collectors.toSet());
	}
	

}
