package info.rmarcus.horton;

import static org.junit.Assert.assertTrue;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import org.junit.Test;

public class HortonHashTest {

	@Test
	public void insertTestBucketSize10() {
		Map<Integer, Integer> m = new HortonHashMap<>(1, 10);
		Map<Integer, Integer> m2 = new HashMap<>();
		
		Random r = new Random(5);
		
		for (int i = 0; i < 800000; i++) {
			int k = r.nextInt();
			int v = r.nextInt();
			
			m.put(k, v);
			m2.put(k, v);
		}
		
		checkMapsEqual(m, m2);
		
	}
	
	@Test
	public void insertTestBucketSize16() throws FileNotFoundException {		
		Map<Integer, Integer> m = new HortonHashMap<>(1, 16);
		Map<Integer, Integer> m2 = new HashMap<>();
		
		Random r = new Random(5);
	
		
		for (int i = 0; i < 800000; i++) {
			int k = r.nextInt();
			int v = r.nextInt();
			
			m.put(k, v);
			m2.put(k, v);
		}
		
		checkMapsEqual(m, m2);
	}
	
	@Test
	public void insertTestBucketSize32() throws FileNotFoundException {		
		Map<Integer, Integer> m = new HortonHashMap<>(1, 32);
		Map<Integer, Integer> m2 = new HashMap<>();
		
		Random r = new Random(5);
	
		
		for (int i = 0; i < 800000; i++) {
			int k = r.nextInt();
			int v = r.nextInt();
			
			m.put(k, v);
			m2.put(k, v);
		}
		
		checkMapsEqual(m, m2);
	}
	
	@Test
	public void insertTestBounded() {
		Map<Integer, Integer> m = new HortonHashMap<>(1, 10);
		Map<Integer, Integer> m2 = new HashMap<>();
		
		Random r = new Random(5);
		
		for (int i = 0; i < 2000000; i++) {
			int k = r.nextInt(100);
			int v = r.nextInt();
			
			m.put(k, v);
			m2.put(k, v);
		}
		
		checkMapsEqual(m, m2);
		
	}
	
	private <K, V> void checkMapsEqual(Map<K, V> m1, Map<K, V> m2) {
		
		for (Entry<K, V> e : m1.entrySet()) {
			assertTrue(e.getKey() != null);
			assertTrue(e.getValue() != null);
			assertTrue(m2.containsKey(e.getKey()));
			assertTrue("Key " + e.getKey() + " should have value " + e.getValue() + " in m2", m2.get(e.getKey()).equals(e.getValue()));
		}
		
		for (Entry<K, V> e : m2.entrySet()) {
			assertTrue(e.getKey() != null);
			assertTrue(e.getValue() != null);

			assertTrue("m1 should contain key " + e.getKey(), m1.containsKey(e.getKey()));
			
			assertTrue(m1.get(e.getKey()).equals(e.getValue()));
		}
		
	}
	
	

}
