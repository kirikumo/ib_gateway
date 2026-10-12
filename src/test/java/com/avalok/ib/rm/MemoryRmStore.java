package com.avalok.ib.rm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory store for tests. Records each write and does not load Redis. */
public final class MemoryRmStore implements RmStore {
	public static final class Op {
		public final String kind;
		public final String key;
		public final String field;
		public final String value;
		public final int ttl;

		Op(String kind, String key, String field, String value, int ttl) {
			this.kind = kind;
			this.key = key;
			this.field = field;
			this.value = value;
			this.ttl = ttl;
		}
	}

	private final List<Op> ops = new ArrayList<>();
	private final Map<String, String> strings = new LinkedHashMap<>();
	private final Map<String, Map<String, String>> hashes = new LinkedHashMap<>();

	@Override
	public synchronized void set(String key, String value) {
		ops.add(new Op("SET", key, null, value, 0));
		strings.put(key, value);
	}

	@Override
	public synchronized void setex(String key, int seconds, String value) {
		ops.add(new Op("SETEX", key, null, value, seconds));
		strings.put(key, value);
	}

	@Override
	public synchronized void hset(String key, String field, String value) {
		ops.add(new Op("HSET", key, field, value, 0));
		hashes.computeIfAbsent(key, k -> new LinkedHashMap<>()).put(field, value);
	}

	@Override
	public synchronized void hdel(String key, String field) {
		ops.add(new Op("HDEL", key, field, null, 0));
		Map<String, String> hash = hashes.get(key);
		if (hash != null) hash.remove(field);
	}

	@Override
	public synchronized String hget(String key, String field) {
		ops.add(new Op("HGET", key, field, null, 0));
		return peek(key, field);
	}

	@Override
	public synchronized Map<String, String> hgetAll(String key) {
		ops.add(new Op("HGETALL", key, null, null, 0));
		Map<String, String> hash = hashes.get(key);
		if (hash == null) return new LinkedHashMap<>();
		return new LinkedHashMap<>(hash);
	}

	@Override
	public synchronized void publish(String channel, String message) {
		ops.add(new Op("PUBLISH", channel, null, message, 0));
	}

	public synchronized String get(String key) {
		return strings.get(key);
	}

	public synchronized String peek(String key, String field) {
		Map<String, String> hash = hashes.get(key);
		return hash == null ? null : hash.get(field);
	}

	public synchronized List<Op> snapshot() {
		return new ArrayList<>(ops);
	}

	public synchronized void clearOps() {
		ops.clear();
	}
}
