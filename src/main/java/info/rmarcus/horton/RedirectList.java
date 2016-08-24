package info.rmarcus.horton;

public class RedirectList<K, V> extends KVPair<K, V> {

	public short[] redirectList;
	public static final int REDIRECT_TABLE_SIZE = 8;

	
	public RedirectList() {
		super(null, null);
		
		redirectList = new short[REDIRECT_TABLE_SIZE];
		for (int i = 0; i < REDIRECT_TABLE_SIZE; i++) {
			redirectList[i] = -1;
		}
	}
	
	
}
