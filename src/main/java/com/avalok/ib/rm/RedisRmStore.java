package com.avalok.ib.rm;

import java.util.Collections;
import java.util.Map;

import com.bitex.util.Redis;

public final class RedisRmStore implements RmStore {
	@Override
	public void set(String key, String value) {
		Redis.set(key, value);
	}

	@Override
	public void setex(String key, int seconds, String value) {
		Redis.setex(key, seconds, value);
	}

	@Override
	public void hset(String key, String field, String value) {
		Redis.exec(j -> j.hset(key, field, value));
	}

	@Override
	public void hdel(String key, String field) {
		Redis.exec(j -> j.hdel(key, field));
	}

	@Override
	public String hget(String key, String field) {
		String[] out = new String[1];
		Redis.exec(j -> out[0] = j.hget(key, field));
		return out[0];
	}

	@Override
	public Map<String, String> hgetAll(String key) {
		Map<String, String>[] out = new Map[1];
		Redis.exec(j -> out[0] = j.hgetAll(key));
		return out[0] == null ? Collections.emptyMap() : out[0];
	}

	@Override
	public void publish(String channel, String message) {
		Redis.pub(channel, message);
	}
}
