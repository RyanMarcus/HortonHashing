package info.rmarcus.horton;

public class HortonHashUtil {

	public static final int NUM_R_HASH_FUNCS = 8;
	
	private int[] salts = {7927, 8039, 8117, 8221, 
						   8293, 8389, 8513, 9151};
	
	
	
	public int primaryHash(Object o) {
		return o.hashCode();
	}
	
	public int tagHash(Object o) {
		// silly. replace.
		return (new Integer(o.hashCode() * 18679)).hashCode();
	}
	
	public int rHashFunc(int bucketID, int slotID, int rFuncIdx) {
		// this is very silly and we should do something else later
		return new Integer(bucketID + (slotID+1) * salts[rFuncIdx % salts.length]).hashCode();
	}
	
}
