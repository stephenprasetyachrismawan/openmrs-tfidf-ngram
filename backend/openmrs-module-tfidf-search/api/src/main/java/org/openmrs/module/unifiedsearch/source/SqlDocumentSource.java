package org.openmrs.module.unifiedsearch.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openmrs.api.db.hibernate.DbSessionFactory;

/**
 * Shared plumbing for the sources (see DocumentRepository.ENTITAS for the full
 * list). Bulk SQL is used on purpose: the service API would issue one query
 * per row and the index build has to touch every row of every source table
 * at start-up.
 */
public abstract class SqlDocumentSource implements DocumentSource {
	
	private DbSessionFactory sessionFactory;
	
	public void setSessionFactory(DbSessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	@SuppressWarnings("unchecked")
	protected List<Object[]> rows(String sql) {
		List<?> raw = sessionFactory.getCurrentSession().createSQLQuery(sql).list();
		List<Object[]> out = new ArrayList<Object[]>(raw.size());
		for (Object o : raw) {
			out.add(o instanceof Object[] ? (Object[]) o : new Object[] { o });
		}
		return out;
	}
	
	protected static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}
	
	protected static Integer integer(Object value) {
		return value == null ? null : Integer.valueOf(((Number) value).intValue());
	}
	
	/** Same semantics as MariaDB CONCAT_WS: nulls are skipped, empty strings are not. */
	protected static String concatWs(String separator, String... parts) {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (String part : parts) {
			if (part == null) {
				continue;
			}
			if (!first) {
				sb.append(separator);
			}
			sb.append(part);
			first = false;
		}
		return sb.toString();
	}
	
	/** Boolean columns come back as Boolean on some drivers and as a Number on others. */
	protected static boolean flag(Object value) {
		if (value == null) {
			return false;
		}
		if (value instanceof Boolean) {
			return ((Boolean) value).booleanValue();
		}
		return ((Number) value).intValue() != 0;
	}
	
	/** Returns the list stored under the key, creating it on first use. */
	protected static List<String> bucket(Map<Integer, List<String>> map, Integer key) {
		List<String> list = map.get(key);
		if (list == null) {
			list = new ArrayList<String>();
			map.put(key, list);
		}
		return list;
	}
	
	/** Adds a value only when it is non-null and non-empty, mirroring _lst() in the Python experiment. */
	protected static void addIfPresent(List<String> target, String value) {
		if (value != null && !value.isEmpty()) {
			target.add(value);
		}
	}
}
