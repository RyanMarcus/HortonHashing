package info.rmarcus.horton;

public class KVPair<K, V> {
	public KVPair(K key, V value) {
		this.key = key;
		this.value = value;
	}
	
	public K key;
	public V value;
}
