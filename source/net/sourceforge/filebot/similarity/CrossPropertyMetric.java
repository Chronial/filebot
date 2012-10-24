
package net.sourceforge.filebot.similarity;


import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.sourceforge.filebot.format.PropertyBindings;


public class CrossPropertyMetric implements SimilarityMetric {
	
	private SimilarityMetric metric;
	
	
	public CrossPropertyMetric(SimilarityMetric metric) {
		this.metric = metric;
	}
	
	
	public CrossPropertyMetric() {
		this.metric = new StringEqualsMetric();
	}
	
	
	@Override
	public float getSimilarity(Object o1, Object o2) {
		Map<String, Object> m1 = getProperties(o1);
		Map<String, Object> m2 = getProperties(o2);
		
		Set<String> keys = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		keys.addAll(m1.keySet());
		keys.retainAll(m2.keySet());
		
		if (keys.isEmpty()) {
			return 0;
		}
		
		float feedback = 0;
		for (String k : keys) {
			try {
				feedback += metric.getSimilarity(m1.get(k), m2.get(k));
			} catch (Exception e) {
				// ignore
			}
		}
		
		return feedback / keys.size();
	}
	
	
	protected Map<String, Object> getProperties(Object object) {
		return new PropertyBindings(object, null);
	}
	
}