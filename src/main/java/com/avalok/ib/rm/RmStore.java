package com.avalok.ib.rm;

import java.util.Map;

/** Risk-key outlet. Values are already JSON strings and are not wrapped again. */
public interface RmStore {
	void set(String key, String value);
	void setex(String key, int seconds, String value);
	void hset(String key, String field, String value);
	void hdel(String key, String field);
	String hget(String key, String field);
	Map<String, String> hgetAll(String key);
	void publish(String channel, String message);
}
