package info.rmarcus.horton;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class MeasurePerformance {

	private static final int NUM_INSERTS = 2000000;
	private static final int NUM_LOOKUPS = 2000000;
	
	public static void main(String[] args) {
		HortonHashMap<Integer, Integer> m = new HortonHashMap<>(NUM_INSERTS/5, 5);
		Map<Integer, Integer> m2 = new HashMap<>(200000);
		Random r;
		long t;
		

		
		
		r = new Random(5);
		t = System.currentTimeMillis();
		for (int i = 0; i < NUM_INSERTS; i++) {
			m2.put(r.nextInt(2000000), r.nextInt());
		}
		t = System.currentTimeMillis() - t;
		System.out.println("Inserts for HashMap: " + t);
		
		
		r = new Random(5);
		t = System.currentTimeMillis();
		for (int i = 0; i < NUM_INSERTS; i++) {
			m.put(r.nextInt(2000000), r.nextInt());
		}
		t = System.currentTimeMillis() - t;
		System.out.println("Inserts for Horton: " + t);
		
		r = new Random(5);
		t = System.currentTimeMillis();
		for (int i = 0; i < NUM_LOOKUPS; i++) {
			m2.get(r.nextInt()+2000000);
		}
		t = System.currentTimeMillis() - t;
		System.out.println("Lookups for HashMap: " + t);
		
		
		r = new Random(5);
		t = System.currentTimeMillis();
		for (int i = 0; i < NUM_LOOKUPS; i++) {
			m.get(r.nextInt()+2000000);
		}
		t = System.currentTimeMillis() - t;
		System.out.println("Lookups for Horton: " + t);
		
		m.dumpStats();

	}

}
